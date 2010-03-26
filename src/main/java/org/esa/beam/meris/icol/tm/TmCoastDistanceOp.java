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
@OperatorMetadata(alias = "Landsat.CoastDistance",
        version = "1.0",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2009 by Brockmann Consult",
        description = "Coast distance computation.")
public class TmCoastDistanceOp extends TmBasisOp {
    public static final String COAST_DISTANCE = "coast_distance";
    public static final int NO_DATA_VALUE = 9999999; // in meters!

    private static final int MAX_LINE_LENGTH = 100000;
    private static final int SOURCE_EXTEND_RR = 80; //TODO
    private static final int SOURCE_EXTEND_FR = 320; //TODO

    private RectangleExtender rectCalculator;
    private int sourceExtend;
    private GeoCoding geocoding;
    private Band isLandBand;
    private Band isWaterBand;

    @SourceProduct(alias="refl")
    private Product sourceProduct;
    @SourceProduct(alias="land")
    private Product landProduct;
    @TargetProduct
    private Product targetProduct;
    @Parameter
    private String landExpression;
    @Parameter
    private String waterExpression;
    @Parameter
    private int resolution;
    @Parameter
    private boolean correctOverLand;

    @Override
    public void initialize() throws OperatorException {
    	targetProduct = createCompatibleProduct(sourceProduct, "coast_distance_"+ sourceProduct.getName(), "COASTD");

        final String productType = sourceProduct.getProductType();
        if (productType.indexOf("_RR") > -1) {
            sourceExtend = SOURCE_EXTEND_RR;
        } else {
            sourceExtend = SOURCE_EXTEND_FR;
        }

        Band band = targetProduct.addBand(COAST_DISTANCE, ProductData.TYPE_INT32);
        band.setNoDataValue(NO_DATA_VALUE);
        band.setNoDataValueUsed(true);

        geocoding = sourceProduct.getGeoCoding();
        rectCalculator = new RectangleExtender(new Rectangle(sourceProduct.getSceneRasterWidth(), sourceProduct.getSceneRasterHeight()), sourceExtend, sourceExtend);

        BandMathsOp bandArithmeticOpLand =
            BandMathsOp.createBooleanExpressionBand(landExpression, landProduct);
        isLandBand = bandArithmeticOpLand.getTargetProduct().getBandAt(0);

        BandMathsOp bandArithmeticOpWater =
            BandMathsOp.createBooleanExpressionBand(waterExpression, landProduct);
        isWaterBand = bandArithmeticOpWater.getTargetProduct().getBandAt(0);

        if (sourceProduct.getPreferredTileSize() != null) {
            targetProduct.setPreferredTileSize(sourceProduct.getPreferredTileSize());
        }

    }

    @Override
    public void computeTile(Band band, Tile coastDistance, ProgressMonitor pm) throws OperatorException {


        Rectangle targetRectangle = coastDistance.getRectangle();
        Rectangle sourceRectangle = rectCalculator.extend(targetRectangle);
        pm.beginTask("Processing frame...", targetRectangle.height);
        try {
        	Tile saa = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME), sourceRectangle, pm);
        	Tile isLand = getSourceTile(isLandBand, sourceRectangle, pm);
        	Tile isWater = getSourceTile(isWaterBand, sourceRectangle, pm);

            PixelPos startPix = new PixelPos();
            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                startPix.y = y;
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                    if (isWater.getSampleBoolean(x, y)) {
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
                            coastDistance.setSample(x, y, NO_DATA_VALUE);
                        } else {
                            final PixelPos landPix = findFirstLandPix(startPix, lineEndPix, isLand);
                            if (landPix != null) {
                                float distance = NavigationUtils.distanceInMeters(geocoding, startPix, landPix);
                                distance *= (resolution*1.0/ TmConstants.LANDSAT5_FR_ORIG);
                                coastDistance.setSample(x, y, (int) distance);
                            } else {
                            	coastDistance.setSample(x, y, NO_DATA_VALUE);
                            }
                        }
                    } else if (isLand.getSampleBoolean(x, y) && correctOverLand) {
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
                            coastDistance.setSample(x, y, NO_DATA_VALUE);
                        } else {
                            final PixelPos landPix = findFirstWaterPix(startPix, lineEndPix, isWater);
                            if (landPix != null) {
                                float distance = NavigationUtils.distanceInMeters(geocoding, startPix, landPix);
                                distance *= (resolution*1.0/ TmConstants.LANDSAT5_FR_ORIG);
                            	coastDistance.setSample(x, y, (int) distance);
                            } else {
                            	coastDistance.setSample(x, y, NO_DATA_VALUE);
                            }
                        }
//                         coastDistance.setSample(x, y, NO_DATA_VALUE);               what's this???
                    } else {      // sth. neither flagged clearly as water nor land...
                    	coastDistance.setSample(x, y, NO_DATA_VALUE);
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

    private PixelPos findFirstLandPix(final PixelPos startPixel, final PixelPos endPixel, final Tile isLand) {
        ShapeRasterizer.LineRasterizer lineRasterizer = new ShapeRasterizer.BresenhamLineRasterizer();
        final PixelPos[] landPixs = new PixelPos[1];
        landPixs[0] = null;
        final Rectangle isLandRect = isLand.getRectangle();
        ShapeRasterizer.LinePixelVisitor visitor = new ShapeRasterizer.LinePixelVisitor() {

            public void visit(int x, int y) {
                if (landPixs[0] == null &&
                        isLandRect.contains(x, y) &&
                        isLand.getSampleBoolean(x, y)) {
                    landPixs[0] = new PixelPos(x, y);
                }
            }
        };

        lineRasterizer.rasterize(MathUtils.floorInt(startPixel.x),
                                 MathUtils.floorInt(startPixel.y),
                                 MathUtils.floorInt(endPixel.x),
                                 MathUtils.floorInt(endPixel.y),
                                 visitor);
        return landPixs[0];
    }

    private PixelPos findFirstWaterPix(final PixelPos startPixel, final PixelPos endPixel, final Tile isWater) {
        ShapeRasterizer.LineRasterizer lineRasterizer = new ShapeRasterizer.BresenhamLineRasterizer();
        final PixelPos[] waterPixs = new PixelPos[1];
        waterPixs[0] = null;
        final Rectangle isWaterRect = isWater.getRectangle();
        ShapeRasterizer.LinePixelVisitor visitor = new ShapeRasterizer.LinePixelVisitor() {

            public void visit(int x, int y) {
                if (waterPixs[0] == null &&
                        isWaterRect.contains(x, y) &&
                        isWater.getSampleBoolean(x, y)) {
                    waterPixs[0] = new PixelPos(x, y);
                }
            }
        };

        lineRasterizer.rasterize(MathUtils.floorInt(startPixel.x),
                                 MathUtils.floorInt(startPixel.y),
                                 MathUtils.floorInt(endPixel.x),
                                 MathUtils.floorInt(endPixel.y),
                                 visitor);
        return waterPixs[0];
    }



    public static class Spi extends OperatorSpi {
        public Spi() {
            super(TmCoastDistanceOp.class);
        }
    }
}
