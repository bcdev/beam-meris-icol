package org.esa.beam.meris.icol.common;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.core.SubProgressMonitor;
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
    private Band distanceBand;
    private double distanceNoDataValue;

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
        Band zmaxBand = targetProduct.addBand(ZMAX, ProductData.TYPE_FLOAT32);
        zmaxBand.setNoDataValue(NO_DATA_VALUE);
        zmaxBand.setNoDataValueUsed(true);

        BandMathsOp bandArithmeticOp = BandMathsOp.createBooleanExpressionBand(aeMaskExpression, aeMaskProduct);
        aeMaskBand = bandArithmeticOp.getTargetProduct().getBandAt(0);
        distanceBand = distanceProduct.getBand(distanceBandName);
        distanceNoDataValue = distanceBand.getNoDataValue();
    }

    @Override
    public void computeTile(Band band, Tile zmax, ProgressMonitor pm) throws OperatorException {
        final Rectangle targetRect = zmax.getRectangle();

        pm.beginTask("Processing frame...", targetRect.height + 3);
        try {
            Tile sza = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME), targetRect, SubProgressMonitor.create(pm, 1));
            Tile distance = getSourceTile(distanceBand, targetRect, SubProgressMonitor.create(pm, 1));
            Tile aeMask = getSourceTile(aeMaskBand, targetRect, SubProgressMonitor.create(pm, 1));

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

    public static class Spi extends OperatorSpi {
        public Spi() {
            super(ZmaxOp.class);
        }
    }
}