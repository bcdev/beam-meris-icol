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
import org.esa.beam.meris.icol.utils.NavigationUtils;
import org.esa.beam.meris.icol.utils.OperatorUtils;
import org.esa.beam.util.RectangleExtender;
import org.esa.beam.util.ShapeRasterizer;
import org.esa.beam.util.math.MathUtils;

import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Operator for coast distance computation for AE correction.
 *
 * @author Marco Zuehlke, Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
@OperatorMetadata(alias = "CoastDistance",
        version = "1.0",
        internal = true,
        authors = "Olaf Danne, Marco Zuehlke",
        copyright = "(c) 2009 by Brockmann Consult",
        description = "Coast distance computation.")
public class CoastDistanceOp extends Operator {
    public static final String COAST_DISTANCE = "coast_distance";
    public static final int NO_DATA_VALUE = -1;

    private static final int MAX_LINE_LENGTH = 100000;
    private static final int SOURCE_EXTEND_RR = 80;
    private static final int SOURCE_EXTEND_FR = 320;

    private RectangleExtender rectCalculator;
    private int sourceExtend;
    private GeoCoding geocoding;
    private Band isLandBand;
    private Band isWaterBand;

    @SourceProduct(alias="source")
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
    private boolean correctOverLand;

    @Override
    public void initialize() throws OperatorException {
    	targetProduct = OperatorUtils.createCompatibleProduct(sourceProduct, COAST_DISTANCE + "_" + sourceProduct.getName(), "COASTD");

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

        BandMathsOp bandMathOpLand = BandMathsOp.createBooleanExpressionBand(landExpression, landProduct);
        isLandBand = bandMathOpLand.getTargetProduct().getBandAt(0);

        BandMathsOp bandMathOpWater = BandMathsOp.createBooleanExpressionBand(waterExpression, landProduct);
        isWaterBand = bandMathOpWater.getTargetProduct().getBandAt(0);
    }

    @Override
    public void computeTile(Band band, Tile coastDistance, ProgressMonitor pm) throws OperatorException {

        Rectangle targetRectangle = coastDistance.getRectangle();
        Rectangle sourceRectangle = rectCalculator.extend(targetRectangle);
        pm.beginTask("Processing frame...", targetRectangle.height);
        try {
        	Tile saa = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME), targetRectangle, pm);
        	Tile isLand = getSourceTile(isLandBand, sourceRectangle, pm);
        	Tile isWater = getSourceTile(isWaterBand, sourceRectangle, pm);

            PixelPos startPix = new PixelPos();
            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                startPix.y = y;
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                    double saaRad = 0;
                    GeoPos startGeoPos = null;
                    if (isWater.getSampleBoolean(x, y) || (isLand.getSampleBoolean(x, y) && correctOverLand)) {
                        startPix.x = x;
                        saaRad = saa.getSampleDouble(x, y) * MathUtils.DTOR + Math.PI;
                        startGeoPos = geocoding.getGeoPos(startPix, null);
                    }

                    if (isWater.getSampleBoolean(x, y)) {
                        coastDistance.setSample(x, y, computeDistance(startPix, startGeoPos, saaRad, isLand));
                    } else if (isLand.getSampleBoolean(x, y) && correctOverLand) {
                        coastDistance.setSample(x, y, computeDistance(startPix, startGeoPos, saaRad, isWater));
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

    private int computeDistance(final PixelPos startPix, final GeoPos startGeoPos, double saaRad, Tile mask) {
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
            final PixelPos pixelPos = findFirstMaskPixel(startPix, lineEndPix, mask);
            if (pixelPos != null) {
                return (int) NavigationUtils.distanceInMeters(geocoding, startPix, pixelPos);
            } else {
                return NO_DATA_VALUE;
            }
        }
    }

    public static PixelPos findFirstMaskPixel(final PixelPos startPixel, final PixelPos endPixel, final Tile mask) {
        ShapeRasterizer.LineRasterizer lineRasterizer = new ShapeRasterizer.BresenhamLineRasterizer();
        final AtomicReference<PixelPos> result = new AtomicReference<PixelPos>();
        final Rectangle maskRect = mask.getRectangle();
        ShapeRasterizer.LinePixelVisitor visitor = new ShapeRasterizer.LinePixelVisitor() {

            public void visit(int x, int y) {
                if (result.get() == null &&
                        maskRect.contains(x, y) &&
                        mask.getSampleBoolean(x, y)) {
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
            super(CoastDistanceOp.class);
        }
    }
}