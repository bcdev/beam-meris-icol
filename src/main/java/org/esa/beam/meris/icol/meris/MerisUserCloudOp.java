package org.esa.beam.meris.icol.meris;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
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
import org.esa.beam.meris.icol.utils.OperatorUtils;
import org.esa.beam.util.ProductUtils;

import java.awt.Rectangle;

@OperatorMetadata(alias = "Meris.UserCloud",
                  version = "1.0",
                  internal = true,
                  authors = "Marco Zühlke",
                  copyright = "(c) 2010 by Brockmann Consult",
                  description = "Operator for user cloud masking.")
public class MerisUserCloudOp extends Operator {

    @SourceProduct
    private Product cloudClassification;
    @SourceProduct
    private Product cloudMask;

    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "An expression for the cloud mask.")
    private String cloudMaskExpression;


    private Band isCloudyBand;
    private Band cloudClassificationBand;

    @Override
    public void initialize() throws OperatorException {
        targetProduct = OperatorUtils.createCompatibleProduct(cloudClassification, "user_cloud",
                                                              cloudClassification.getProductType());

        ProductUtils.copyFlagBands(cloudClassification, targetProduct);
        cloudClassificationBand = cloudClassification.getBand(CloudClassificationOp.CLOUD_FLAGS);

        Band targetBand = ProductUtils.copyBand(CloudClassificationOp.PRESSURE_CTP, cloudClassification, targetProduct);
        targetBand.setSourceImage(cloudClassification.getBand(CloudClassificationOp.PRESSURE_CTP).getSourceImage());

        targetBand = ProductUtils.copyBand(CloudClassificationOp.PRESSURE_SURFACE, cloudClassification, targetProduct);
        targetBand.setSourceImage(cloudClassification.getBand(CloudClassificationOp.PRESSURE_SURFACE).getSourceImage());

        BandMathsOp baOp = BandMathsOp.createBooleanExpressionBand(cloudMaskExpression, cloudMask);
        isCloudyBand = baOp.getTargetProduct().getBandAt(0);

    }

    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        Rectangle rect = targetTile.getRectangle();
        pm.beginTask("Processing frame...", rect.height + 2);
        try {
            Tile isCloudyTile = getSourceTile(isCloudyBand, rect);
            Tile cloudClassificationTile = getSourceTile(cloudClassificationBand, rect);
            for (int y = rect.y; y < rect.y + rect.height; y++) {
                for (int x = rect.x; x < rect.x + rect.width; x++) {
                    int cloudFlags = cloudClassificationTile.getSampleInt(x, y);
                    boolean isCloudy = isCloudyTile.getSampleBoolean(x, y);
                    cloudFlags = setFlagValue(cloudFlags, CloudClassificationOp.F_CLOUD, isCloudy);
                    targetTile.setSample(x, y, cloudFlags);
                }
                checkForCancellation();
                pm.worked(1);
            }
        } finally {
            pm.done();
        }
    }

    private static int setFlagValue(int flags, int bitIndex, boolean cond) {
        return cond ? (flags | (1 << bitIndex)) : (flags & ~(1 << bitIndex));
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(MerisUserCloudOp.class);
        }
    }
}
