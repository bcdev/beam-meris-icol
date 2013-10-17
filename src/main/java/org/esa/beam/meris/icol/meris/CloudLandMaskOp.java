package org.esa.beam.meris.icol.meris;

import com.bc.ceres.core.ProgressMonitor;
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
import org.esa.beam.meris.icol.utils.OperatorUtils;

import javax.media.jai.BorderExtender;
import java.awt.Rectangle;

/**
 * Operator for extraction of a product with cloud/land masks given as 0.0/1.0,
 * to be used by JAI for retrieval of "convoluted flags"
 *
 * @author Olaf Danne
 */
@OperatorMetadata(
        alias = "CLFlags",
        version = "2.9.5",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2010 by Brockmann Consult",
        description = "Provides product with cloud/land masks given as 0.0/1.0.")
public class CloudLandMaskOp extends Operator {

    private Band isLandBand;
    private Band isCloudBand;

    @SourceProduct(alias = "land")
    private Product landProduct;
    @SourceProduct(alias = "cloud")
    private Product cloudProduct;

    @TargetProduct
    private Product targetProduct;

    @Parameter
    private String landExpression;
    @Parameter
    private String cloudExpression;

    private Band landFlagBand;
    private Band cloudFlagBand;

    public static final String LAND_MASK_NAME = "lcflag_1";
    public static final String CLOUD_MASK_NAME = "lcflag_2";

    @Override
    public void initialize() throws OperatorException {

        createTargetProduct();

        BandMathsOp bandArithmeticLandOp =
                BandMathsOp.createBooleanExpressionBand(landExpression, landProduct);
        isLandBand = bandArithmeticLandOp.getTargetProduct().getBandAt(0);

        BandMathsOp bandArithmeticCloudOp =
                BandMathsOp.createBooleanExpressionBand(cloudExpression, cloudProduct);
        isCloudBand = bandArithmeticCloudOp.getTargetProduct().getBandAt(0);
    }

    private void createTargetProduct() {
        targetProduct = OperatorUtils.createCompatibleProduct(cloudProduct, "ae_cloud_land_mask",
                                                              "MER_AE_CLMASK");

        landFlagBand = targetProduct.addBand(LAND_MASK_NAME, ProductData.TYPE_FLOAT32);
        cloudFlagBand = targetProduct.addBand(CLOUD_MASK_NAME, ProductData.TYPE_FLOAT32);
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        final Rectangle targetRect = targetTile.getRectangle();

        pm.beginTask("Processing cloud/land mask begin...", 0);
        try {
            // sources
            Tile isLand = getSourceTile(isLandBand, targetRect, BorderExtender.createInstance(BorderExtender.BORDER_COPY));
            Tile isCloud = getSourceTile(isCloudBand, targetRect, BorderExtender.createInstance(BorderExtender.BORDER_COPY));

            for (int y = targetRect.y; y < targetRect.y + targetRect.height; y++) {
                for (int x = targetRect.x; x < targetRect.x + targetRect.width; x++) {
                    boolean bValue;
                    if (targetBand == landFlagBand) {
                        bValue = isLand.getSampleBoolean(x, y);
                    } else {
                        bValue = isCloud.getSampleBoolean(x, y);
                    }
                    final float fValue = (bValue) ? 1.0f : 0.0f;
                    targetTile.setSample(x, y, fValue);
                }
                checkForCancellation();
                pm.worked(1);
            }
        } finally {
            pm.done();
        }
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(CloudLandMaskOp.class);
        }
    }
}
