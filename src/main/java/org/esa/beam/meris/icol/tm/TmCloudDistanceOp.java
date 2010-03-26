package org.esa.beam.meris.icol.tm;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.meris.brr.CloudClassificationOp;
import org.esa.beam.meris.icol.utils.NavigationUtils;
import org.esa.beam.util.RectangleExtender;
import org.esa.beam.util.ShapeRasterizer;
import org.esa.beam.util.math.MathUtils;
import org.esa.beam.gpf.operators.standard.BandMathsOp;

import java.awt.Rectangle;

/**
 * @author Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
@OperatorMetadata(alias = "Landsat.CloudDistance",
        version = "1.0",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2009 by Brockmann Consult",
        description = "Land distance computation.")
public class TmCloudDistanceOp extends TmBasisOp {
    public static final String CLOUD_DISTANCE = "cloud_distance";
    public static final int NO_DATA_VALUE = -1;

    private static final int MAX_LINE_LENGTH = 100000;
    private static final int SOURCE_EXTEND_RR = 80; //TODO
    private static final int SOURCE_EXTEND_FR = 320; //TODO

    private RectangleExtender rectCalculator;
    private int sourceExtend;
    private GeoCoding geocoding;
    private Band isLandBand;

    @SourceProduct(alias="refl")
    private Product sourceProduct;
    @SourceProduct(alias="land")
    private Product landProduct;
    @SourceProduct(alias = "cloud")
    private Product cloudProduct;
    @TargetProduct
    private Product targetProduct;
    @Parameter
    private String landExpression;
    @Parameter
    private int resolution;

    @Override
    public void initialize() throws OperatorException {
    	targetProduct = createCompatibleProduct(sourceProduct, "cloud_distance_"+ sourceProduct.getName(), "CLOUDD");

        final String productType = sourceProduct.getProductType();
        if (productType.indexOf("_RR") > -1) {
            sourceExtend = SOURCE_EXTEND_RR;
        } else {
            sourceExtend = SOURCE_EXTEND_FR;
        }

        Band band = targetProduct.addBand(CLOUD_DISTANCE, ProductData.TYPE_INT32);
        band.setNoDataValue(NO_DATA_VALUE);
        band.setNoDataValueUsed(true);

        geocoding = sourceProduct.getGeoCoding();
        rectCalculator = new RectangleExtender(new Rectangle(sourceProduct.getSceneRasterWidth(), sourceProduct.getSceneRasterHeight()), sourceExtend, sourceExtend);

        BandMathsOp bandArithmeticOp1 =
            BandMathsOp.createBooleanExpressionBand(landExpression, landProduct);
        isLandBand = bandArithmeticOp1.getTargetProduct().getBandAt(0);
        if (sourceProduct.getPreferredTileSize() != null) {
            targetProduct.setPreferredTileSize(sourceProduct.getPreferredTileSize());
        }
    }

    @Override
    public void computeTile(Band band, Tile cloudDistance, ProgressMonitor pm) throws OperatorException {

    	Rectangle targetRectangle = cloudDistance.getRectangle();
        Rectangle sourceRectangle = rectCalculator.extend(targetRectangle);
        pm.beginTask("Processing frame...", targetRectangle.height);
        try {

        	Tile saa = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME), sourceRectangle, pm);
        	Tile isLand = getSourceTile(isLandBand, sourceRectangle, pm);
            Tile cloudFlags = getSourceTile(cloudProduct.getBand(CloudClassificationOp.CLOUD_FLAGS), targetRectangle, pm);

            PixelPos startPix = new PixelPos();
            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                startPix.y = y;
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                    boolean isCloud = cloudFlags.getSampleBit(x, y, CloudClassificationOp.F_CLOUD);
                    if (!isLand.getSampleBoolean(x, y) && !isCloud) {
                        startPix.x = x;

                        int trialLineLength = MAX_LINE_LENGTH;
                        PixelPos lineEndPix;
                        do {
                            final GeoPos startGeoPos = geocoding.getGeoPos(startPix, null);
                            final GeoPos lineEndGeoPos = NavigationUtils.lineWithAngle(startGeoPos,
                            		trialLineLength, saa.getSampleDouble(x, y) * MathUtils.DTOR + Math.PI);
                            lineEndPix = geocoding.getPixelPos(lineEndGeoPos, null);
                            if (lineEndPix.x == -1 || lineEndPix.y == -1) {
                                trialLineLength -= 10000;
                            } else {
                                trialLineLength = 0;
                            }
                        } while (trialLineLength > 0);

                        if (lineEndPix.x == -1 || lineEndPix.y == -1) {
                            cloudDistance.setSample(x, y, NO_DATA_VALUE);
                        } else {
                            final PixelPos cloudPix = findFirstCloudPix(startPix, lineEndPix, cloudFlags, isLand);
                            if (cloudPix != null) {
                                float distance = NavigationUtils.distanceInMeters(geocoding, startPix, cloudPix);
                                distance *= (resolution*1.0/ TmConstants.LANDSAT5_FR_ORIG);
                            	cloudDistance.setSample(x, y, (int) distance);
                            } else {
                            	cloudDistance.setSample(x, y, NO_DATA_VALUE);
                            }
                        }
                    } else {
                    	cloudDistance.setSample(x, y, NO_DATA_VALUE);
                    }
                }
                pm.worked(1);
            }
        } catch (Exception e) {
            throw new OperatorException(e);
        } finally {
            pm.done();
        }
    }

    private PixelPos findFirstCloudPix(final PixelPos startPixel, final PixelPos endPixel,
                                       final Tile cloudFlags, final Tile isLand) {
        ShapeRasterizer.LineRasterizer lineRasterizer = new ShapeRasterizer.BresenhamLineRasterizer();
        final PixelPos[] cloudPixs = new PixelPos[1];
        cloudPixs[0] = null;
        final Rectangle isCloudRect = cloudFlags.getRectangle();
        ShapeRasterizer.LinePixelVisitor visitor = new ShapeRasterizer.LinePixelVisitor() {

            public void visit(int x, int y) {
                if (cloudPixs[0] == null &&
                        isCloudRect.contains(x, y) &&
                        cloudFlags.getSampleBit(x, y, CloudClassificationOp.F_CLOUD) &&
                        !isLand.getSampleBoolean(x, y)) {
                    cloudPixs[0] = new PixelPos(x, y);
                }
            }
        };

        lineRasterizer.rasterize(MathUtils.floorInt(startPixel.x),
                                 MathUtils.floorInt(startPixel.y),
                                 MathUtils.floorInt(endPixel.x),
                                 MathUtils.floorInt(endPixel.y),
                                 visitor);
        return cloudPixs[0];
    }


    public static class Spi extends OperatorSpi {
        public Spi() {
            super(TmCloudDistanceOp.class);
        }
    }
}
