package org.esa.beam.meris.icol.common;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
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
import org.esa.beam.meris.icol.utils.OperatorUtils;
import org.esa.beam.util.math.MathUtils;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

import static org.esa.beam.meris.icol.utils.OperatorUtils.subPm1;

/**
 * Operator providing Zmax for land or cloud contribution in AE correction.
 *
 * @author Olaf Danne
 */
@OperatorMetadata(alias = "Zmax",
                  version = "1.0",
                  internal = true,
                  authors = "Marco Zühlke, Olaf Danne",
                  copyright = "(c) 2007-2010 by Brockmann Consult",
                  description = "Zmax computation for land or cloud contribution in AE correction.")
public class ZmaxOp extends Operator {

    public static final String ZMAX = "zmax";

    private static final int NO_DATA_VALUE = -1;

    private Band aeMaskBand;
    private Map<Band, Band> distanceBandMap;

    @SourceProduct(alias = "source")
    private Product sourceProduct;
    @SourceProduct(alias = "ae_mask")
    private Product aeMaskProduct;
    @SourceProduct(alias = "distance")
    private Product distanceProduct;
    @TargetProduct
    private Product targetProduct;
    @Parameter(notEmpty = true)
    private String aeMaskExpression;
    @Parameter(notEmpty = true)
    private String distanceBandName;

    @Override
    public void initialize() throws OperatorException {
        targetProduct = OperatorUtils.createCompatibleProduct(sourceProduct, "zmax_" + sourceProduct.getName(), "ZMAX");
        int numZmax = distanceProduct.getNumBands();
        distanceBandMap = new HashMap<Band, Band>(numZmax);
        for (int i = 0; i < numZmax; i++) {
            Band zmaxBand = targetProduct.addBand(ZMAX + "_" + (i+1), ProductData.TYPE_FLOAT32);
            zmaxBand.setNoDataValue(NO_DATA_VALUE);
            zmaxBand.setNoDataValueUsed(true);

            Band distBand;
            if (numZmax == 1) {
                distBand = distanceProduct.getBand(distanceBandName);
            } else {
                distBand = distanceProduct.getBand(distanceBandName + "_" + (i+1));
            }
            distanceBandMap.put(zmaxBand, distBand);
        }

        BandMathsOp bandArithmeticOp = BandMathsOp.createBooleanExpressionBand(aeMaskExpression, aeMaskProduct);
        aeMaskBand = bandArithmeticOp.getTargetProduct().getBandAt(0);
    }

    @Override
    public void computeTile(Band zmaxBand, Tile zmax, ProgressMonitor pm) throws OperatorException {
        final Rectangle targetRect = zmax.getRectangle();

        pm.beginTask("Processing frame...", targetRect.height + 3);
        try {
            Tile sza = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME), targetRect, subPm1(pm));
            Tile aeMask = getSourceTile(aeMaskBand, targetRect, subPm1(pm));

            Band distanceBand = distanceBandMap.get(zmaxBand);
            Tile distance = getSourceTile(distanceBand, targetRect, subPm1(pm));
            double distanceNoDataValue = distanceBand.getNoDataValue();

            PixelPos pPix = new PixelPos();
            for (int y = targetRect.y; y < targetRect.y + targetRect.height; y++) {
                pPix.y = y;
                for (int x = targetRect.x; x < targetRect.x + targetRect.width; x++) {
                    pPix.x = x;
                    float zMaxValue = NO_DATA_VALUE;
                    if (aeMask.getSampleBoolean(x, y)) {
                        int distanceValue = distance.getSampleInt(x, y);
                        if (distanceValue != distanceNoDataValue) {
                            float szaValue = sza.getSampleFloat(x, y);
                            zMaxValue = (float) (distanceValue / Math.tan(szaValue * MathUtils.DTOR));
                        }
                    }
                    zmax.setSample(x, y, zMaxValue);
                }
                checkForCancelation(pm);
                pm.worked(1);
            }
        } finally {
            pm.done();
        }
    }
    
    public static double computeZmaxPart(Tile[] zmaxTiles, int x, int y, double scaleHeight) {
        double zmaxPart = computeZmaxPart(zmaxTiles[0], x, y, scaleHeight);
        for (int i = 1; i < zmaxTiles.length; i++) {
            zmaxPart += computeZmaxPart(zmaxTiles[i], x, y, scaleHeight);
        }
        return zmaxPart;
    }

    public static double computeZmaxPart(Tile zmaxTile, int x, int y, double scaleHeight) {
        double zmaxPart = 0.0;
        final float zmaxValue = zmaxTile.getSampleFloat(x, y);
        if (zmaxValue >= 0) {
            zmaxPart = Math.exp(-zmaxValue / scaleHeight);
        }
        return zmaxPart;
    }

    public static Tile[] getSourceTiles(Operator op, Product zmaxProduct, Rectangle targetRect, ProgressMonitor pm) {
        pm.beginTask("Processing frame...", zmaxProduct.getNumBands());
        try {
            Tile[] tiles = new Tile[zmaxProduct.getNumBands()];
            for (int i = 0; i < tiles.length; i++) {
                final Band band = zmaxProduct.getBandAt(i);
                tiles[i] = op.getSourceTile(band, targetRect, subPm1(pm));
            }
            return tiles;
        } finally {
            pm.done();
        }
    }

    public static Tile getSourceTile(Operator op, Product zmaxProduct, Rectangle targetRect, ProgressMonitor pm) {
        return op.getSourceTile(zmaxProduct.getBand(ZMAX + "_1"), targetRect, pm);
    }

    public static class Spi extends OperatorSpi {
        public Spi() {
            super(ZmaxOp.class);
        }
    }
}