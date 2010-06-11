package org.esa.beam.meris.icol.common;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.standard.BandMathsOp;
import org.esa.beam.meris.brr.CloudClassificationOp;
import org.esa.beam.meris.icol.utils.NavigationUtils;
import org.esa.beam.meris.icol.utils.OperatorUtils;
import org.esa.beam.util.RectangleExtender;
import org.esa.beam.util.ShapeRasterizer;
import org.esa.beam.util.math.MathUtils;

import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.esa.beam.meris.icol.utils.OperatorUtils.subPm1;

/**
 * Operator for cloud distance computation for AE correction.
 *
 * @author Marco Zuehlke, Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
@OperatorMetadata(alias = "CloudDistance",
        version = "1.0",
        internal = true,
        authors = "Marco Zühlke, Olaf Danne",
        copyright = "(c) 2009 by Brockmann Consult",
        description = "Cloud distance computation.")
public class CloudDistanceOp extends Operator {
    public static final String CLOUD_DISTANCE = "cloud_distance";
    public static final int NO_DATA_VALUE = -1;

    private static final int MAX_LINE_LENGTH = 100000;
    private static final int SOURCE_EXTEND_RR = 80;
    private static final int SOURCE_EXTEND_FR = 320;

    private RectangleExtender rectCalculator;
    private int sourceExtend;
    private GeoCoding geocoding;

    @SourceProduct(alias="source")
    private Product sourceProduct;
    @SourceProduct(alias = "cloud")
    private Product cloudProduct;
    @TargetProduct
    private Product targetProduct;

    @Override
    public void initialize() throws OperatorException {
    	targetProduct = OperatorUtils.createCompatibleProduct(sourceProduct, "cloud_distance_"+ sourceProduct.getName(), "CLOUDD");

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
    }

    @Override
    public void computeTile(Band band, Tile cloudDistance, ProgressMonitor pm) throws OperatorException {

    	Rectangle targetRectangle = cloudDistance.getRectangle();
        Rectangle sourceRectangle = rectCalculator.extend(targetRectangle);
        pm.beginTask("Processing frame...", targetRectangle.height);
        try {
        	Tile saa = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME), targetRectangle, subPm1(pm));
            Tile cloudFlags = getSourceTile(cloudProduct.getBand(CloudClassificationOp.CLOUD_FLAGS), sourceRectangle, subPm1(pm));

            PixelPos startPix = new PixelPos();
            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                startPix.y = y;
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                    if (!cloudFlags.getSampleBit(x, y, CloudClassificationOp.F_CLOUD)) {
                        startPix.x = x;
                        double saaRad = saa.getSampleDouble(x, y) * MathUtils.DTOR + Math.PI;
                        final GeoPos startGeoPos = geocoding.getGeoPos(startPix, null);

                        cloudDistance.setSample(x, y, computeDistance(startPix, startGeoPos, saaRad, cloudFlags));
                    } else {
                    	cloudDistance.setSample(x, y, NO_DATA_VALUE);
                    }
                }
                checkForCancelation(pm);
                pm.worked(1);
            }
        } finally {
            pm.done();
        }
    }

    private int computeDistance(final PixelPos startPix, final GeoPos startGeoPos, double saaRad, Tile cloudFlags) {
        int trialLineLength = MAX_LINE_LENGTH;
        PixelPos lineEndPix;
        do {
            final GeoPos lineEndGeoPos = NavigationUtils.lineWithAngle(startGeoPos, trialLineLength, saaRad);
            lineEndPix = geocoding.getPixelPos(lineEndGeoPos, null);
            if (lineEndPix.x == -1 || lineEndPix.y == -1) {
                trialLineLength -= 10000;
            } else {
                trialLineLength = 0;
            }
        } while (trialLineLength > 0);

        if (lineEndPix.x == -1 || lineEndPix.y == -1) {
            return NO_DATA_VALUE;
        } else {
            final PixelPos pixelPos = findFirstCloudPix(startPix, lineEndPix, cloudFlags);
            if (pixelPos != null) {
                return (int) NavigationUtils.distanceInMeters(geocoding, startPix, pixelPos);
            } else {
                return NO_DATA_VALUE;
            }
        }
    }

    private PixelPos findFirstCloudPix(final PixelPos startPixel, final PixelPos endPixel,
                                       final Tile cloudFlags) {
        ShapeRasterizer.LineRasterizer lineRasterizer = new ShapeRasterizer.BresenhamLineRasterizer();
        final AtomicReference<PixelPos> result = new AtomicReference<PixelPos>();
        final Rectangle isCloudRect = cloudFlags.getRectangle();
        ShapeRasterizer.LinePixelVisitor visitor = new ShapeRasterizer.LinePixelVisitor() {

            public void visit(int x, int y) {
                if (result.get() == null &&
                        isCloudRect.contains(x, y) &&
                        cloudFlags.getSampleBit(x, y, CloudClassificationOp.F_CLOUD)) {
                    result.set(new PixelPos(x, y));
                }
            }
        };

        lineRasterizer.rasterize(MathUtils.floorInt(startPixel.x),
                                 MathUtils.floorInt(startPixel.y),
                                 MathUtils.floorInt(endPixel.x),
                                 MathUtils.floorInt(endPixel.y),
                                 visitor);
        return result.get();
    }


    public static class Spi extends OperatorSpi {
        public Spi() {
            super(CloudDistanceOp.class);
        }
    }
}