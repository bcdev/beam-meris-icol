package org.esa.beam.meris.icol.meris;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.standard.BandMathsOp;
import org.esa.beam.meris.brr.CloudClassificationOp;
import org.esa.beam.meris.brr.LandClassificationOp;
import org.esa.beam.meris.icol.utils.OperatorUtils;
import org.esa.beam.util.ProductUtils;

import java.awt.Rectangle;

/**
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
@OperatorMetadata(alias = "Meris.BrrCloud",
                  version = "1.0",
                  internal = true,
                  authors = "Olaf Danne",
                  copyright = "(c) 2009 by Brockmann Consult",
                  description = "Sets BRR values to RS proposal in case of clouds.")
public class MerisBrrCloudOp extends Operator {

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;
    @SourceProduct(alias = "brr")
    private Product brrProduct;
    @SourceProduct(alias = "refl")
    private Product rad2reflProduct;
    @SourceProduct(alias = "cloud")
    private Product cloudProduct;
    @SourceProduct(alias = "land")
    private Product landProduct;

    @TargetProduct
    private Product targetProduct;

    private Band invalidBand;

    @Override
    public void initialize() throws OperatorException {
        String productType = l1bProduct.getProductType();
        productType = productType.substring(0, productType.indexOf("_1")) + "_1N";

        targetProduct = OperatorUtils.createCompatibleProduct(l1bProduct, "MER", productType);
        for (String bandName : brrProduct.getBandNames()) {
            if (!targetProduct.containsRasterDataNode(bandName)) {
                boolean copySrcImage = !bandName.startsWith("brr");
                ProductUtils.copyBand(bandName, brrProduct, targetProduct, copySrcImage);
            }
        }

        BandMathsOp bandArithmeticOp = BandMathsOp.createBooleanExpressionBand("l1_flags.INVALID", l1bProduct);
        invalidBand = bandArithmeticOp.getTargetProduct().getBandAt(0);
    }

    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Rectangle rectangle = targetTile.getRectangle();
        pm.beginTask("Processing frame...", rectangle.height + 6);
        try {
            final int bandNumber = band.getSpectralBandIndex() + 1;

            Tile brrTile = getSourceTile(brrProduct.getBand("brr_" + bandNumber), rectangle);
            Tile rad2reflTile = getSourceTile(rad2reflProduct.getBand("rho_toa_" + bandNumber), rectangle);
            Tile isInvalid = getSourceTile(invalidBand, rectangle);

            Tile surfacePressureTile = getSourceTile(cloudProduct.getBand(CloudClassificationOp.PRESSURE_SURFACE),
                                                     rectangle);
            Tile cloudTopPressureTile = getSourceTile(cloudProduct.getBand(CloudClassificationOp.PRESSURE_CTP),
                                                      rectangle);
            Tile cloudFlagsTile = getSourceTile(cloudProduct.getBand(CloudClassificationOp.CLOUD_FLAGS), rectangle);
            Tile landFlagsTile = getSourceTile(landProduct.getBand(LandClassificationOp.LAND_FLAGS), rectangle);

            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                    if (isInvalid.getSampleBoolean(x, y)) {
                        targetTile.setSample(x, y, -1.0);
                    } else {
                        if (cloudFlagsTile.getSampleBit(x, y, CloudClassificationOp.F_CLOUD)) {
                            final float surfacePressure = surfacePressureTile.getSampleFloat(x, y);
                            final float cloudTopPressure = cloudTopPressureTile.getSampleFloat(x, y);
                            final float rad2refl = rad2reflTile.getSampleFloat(x, y);
                            final float brrCorr = rad2refl * cloudTopPressure / surfacePressure;
                            targetTile.setSample(x, y, brrCorr);
                        } else if (landFlagsTile.getSampleBit(x, y, LandClassificationOp.F_ICE)) {
                            final float brrCorr = rad2reflTile.getSampleFloat(x, y);
                            targetTile.setSample(x, y, brrCorr);
                        } else {
                            // take value from rayleigh correction
                            final float brr = brrTile.getSampleFloat(x, y);
                            targetTile.setSample(x, y, brr);
                        }
                    }
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
            super(MerisBrrCloudOp.class);
        }
    }

}
