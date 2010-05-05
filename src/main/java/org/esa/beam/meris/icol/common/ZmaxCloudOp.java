package org.esa.beam.meris.icol.common;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.core.SubProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
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
import org.esa.beam.meris.icol.meris.MerisAeMaskOp;
import org.esa.beam.meris.icol.utils.OperatorUtils;
import org.esa.beam.util.math.MathUtils;

import java.awt.*;

/**
 * Operator providing Zmax for cloud contribution in AE correction.
 *
 * @author Olaf Danne
 */
@OperatorMetadata(alias = "ZmaxCloud",
        version = "1.0",
        internal = true,
        authors = "Marco Zühlke,Olaf Danne",
        copyright = "(c) 2007-2010 by Brockmann Consult",
        description = "Zmax computation for cloud.")
public class ZmaxCloudOp extends Operator {

    public static final String ZMAX_CLOUD = "zmaxcloud";

    private static final int NO_DATA_VALUE = -1;

    private Band isAemBand;
    private Band cloudDistanceBand;
    private double cloudDistanceNoDataValue;

    @SourceProduct(alias="l1b")
    private Product l1bProduct;
    @SourceProduct(alias="ae_mask")
    private Product aeMaskProduct;
    @SourceProduct(alias="cloudDistance")
    private Product cdProduct;
    @TargetProduct
    private Product targetProduct;
    @Parameter(defaultValue = MerisAeMaskOp.AE_MASK_RAYLEIGH + ".aep") //TODO replace with sensor neutral constant
    private String aeMaskExpression;

    @Override
    public void initialize() throws OperatorException {
    	targetProduct = OperatorUtils.createCompatibleProduct(l1bProduct, "zmaxcloud_" + l1bProduct.getName(), "ZMAXCLOUD");
        Band zmaxBand = targetProduct.addBand(ZMAX_CLOUD, ProductData.TYPE_FLOAT32);
        zmaxBand.setNoDataValue(NO_DATA_VALUE);
        zmaxBand.setNoDataValueUsed(true);

        BandMathsOp bandArithmeticOp = BandMathsOp.createBooleanExpressionBand(aeMaskExpression, aeMaskProduct);
        isAemBand = bandArithmeticOp.getTargetProduct().getBandAt(0);
        cloudDistanceBand = cdProduct.getBand("cloud_distance");
        cloudDistanceNoDataValue = cloudDistanceBand.getNoDataValue();
    }

    @Override
    public void computeTile(Band band, Tile zmax, ProgressMonitor pm) throws OperatorException {

    	final Rectangle targetRect = zmax.getRectangle();
        pm.beginTask("Processing frame...", targetRect.height + 3);
        try {
        	Tile sza = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME), targetRect, SubProgressMonitor.create(pm, 1));
            Tile cloudDistance = getSourceTile(cloudDistanceBand, targetRect, SubProgressMonitor.create(pm, 1));
        	Tile isAeMask = getSourceTile(isAemBand, targetRect, SubProgressMonitor.create(pm, 1));

            for (int y = targetRect.y; y < targetRect.y + targetRect.height; y++) {
                for (int x = targetRect.x; x < targetRect.x + targetRect.width; x++) {
                    float zMaxValue = NO_DATA_VALUE;
                    if (isAeMask.getSampleBoolean(x, y)) {
                        int distance = cloudDistance.getSampleInt(x, y);
                        if (distance != cloudDistanceNoDataValue) {
                            float szaValue = sza.getSampleFloat(x, y);
                            zMaxValue = (float) (distance / Math.tan(szaValue * MathUtils.DTOR));
                        }
                    }
                    zmax.setSample(x, y, zMaxValue);
                }
            }
            checkForCancelation(pm);
            pm.worked(1);
        } finally {
            pm.done();
        }
    }

    public static class Spi extends OperatorSpi {
        public Spi() {
            super(ZmaxCloudOp.class);
        }
    }
}