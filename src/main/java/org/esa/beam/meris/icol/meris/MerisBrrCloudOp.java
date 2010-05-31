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
    @SourceProduct(alias="l1b")
       private Product l1bProduct;
       @SourceProduct(alias="brr")
       private Product brrProduct;
       @SourceProduct(alias="refl")
       private Product rad2reflProduct;
       @SourceProduct(alias="cloud")
       private Product cloudProduct;

       @TargetProduct
       private Product targetProduct;

    private transient Band invalidBand;

    @Override
    public void initialize() throws OperatorException {

        String productType = l1bProduct.getProductType();
        int index = productType.indexOf("_1");
        productType = productType.substring(0, index) + "_1N";
        targetProduct = createCompatibleBaseProduct("MER", productType);
        for (String bandName : brrProduct.getBandNames()) {
            if(!bandName.equals("l1_flags")) {
                ProductUtils.copyBand(bandName, brrProduct, targetProduct);
            }
        }
        ProductUtils.copyFlagBands(l1bProduct, targetProduct);

        BandMathsOp bandArithmeticOp = BandMathsOp.createBooleanExpressionBand("l1_flags.INVALID", l1bProduct);
        invalidBand = bandArithmeticOp.getTargetProduct().getBandAt(0);

        if (l1bProduct.getPreferredTileSize() != null) {
            targetProduct.setPreferredTileSize(l1bProduct.getPreferredTileSize());
        }
    }

     private Product createCompatibleBaseProduct(String name, String type) {
        final int sceneWidth = l1bProduct.getSceneRasterWidth();
        final int sceneHeight = l1bProduct.getSceneRasterHeight();

        Product tProduct = new Product(name, type, sceneWidth, sceneHeight);
        ProductUtils.copyTiePointGrids(l1bProduct, tProduct);
        ProductUtils.copyGeoCoding(l1bProduct, tProduct);
        tProduct.setStartTime(l1bProduct.getStartTime());
        tProduct.setEndTime(l1bProduct.getEndTime());
        return tProduct;
    }

    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {


            Rectangle rectangle = targetTile.getRectangle();
            pm.beginTask("Processing frame...", rectangle.height);
            try {
                final int bandNumber = band.getSpectralBandIndex() + 1;

                if (band.getName().startsWith("brr")) {
                    Tile brrTile = getSourceTile(brrProduct.getBand("brr_" + bandNumber), rectangle, pm);
                    Tile rad2reflTile = getSourceTile(rad2reflProduct.getBand("rho_toa_" + bandNumber), rectangle, pm);
                    Tile isInvalid = getSourceTile(invalidBand, rectangle, pm);

                    Tile surfacePressureTile = getSourceTile(cloudProduct.getBand(CloudClassificationOp.PRESSURE_SURFACE), rectangle, pm);
                    Tile cloudTopPressureTile = getSourceTile(cloudProduct.getBand(CloudClassificationOp.PRESSURE_CTP), rectangle, pm);
                    Tile cloudFlagsTile = getSourceTile(cloudProduct.getBand(CloudClassificationOp.CLOUD_FLAGS), rectangle, pm);

                    for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                        for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                            if (isInvalid.getSampleBoolean(x, y)) {
                                targetTile.setSample(x, y, -1.0);
                            } else {
                                final float brr = brrTile.getSampleFloat(x, y);
                                boolean isCloud = cloudFlagsTile.getSampleBit(x, y, CloudClassificationOp.F_CLOUD);
                                if (isCloud) {
                                    final float surfacePressure = surfacePressureTile.getSampleFloat(x, y);
                                    final float cloudTopPressure = cloudTopPressureTile.getSampleFloat(x, y);
                                    final float rad2refl = rad2reflTile.getSampleFloat(x, y);
                                    final float brrCorr = rad2refl * cloudTopPressure / surfacePressure;
                                    targetTile.setSample(x, y, brrCorr);
                                } else {
                                    // leave original value
                                    targetTile.setSample(x, y, brr);
                                }
                            }
                        }
                        pm.worked(1);
                    }
                } else if (!band.isFlagBand()) {
                    Tile sourceTile = getSourceTile(brrProduct.getBand(band.getName()), rectangle, pm);
                    for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                        for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                            targetTile.setSample(x, y, sourceTile.getSampleFloat(x, y));
                        }
                    }
                }
            } catch (Exception e) {
                throw new OperatorException(e);
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
