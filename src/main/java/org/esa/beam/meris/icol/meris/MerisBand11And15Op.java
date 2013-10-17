package org.esa.beam.meris.icol.meris;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.meris.icol.utils.OperatorUtils;
import org.esa.beam.util.ProductUtils;

import java.awt.Rectangle;

/**
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
@OperatorMetadata(alias = "Meris.IcolCorrectBand11And15",
                  version = "2.9.5",
                  internal = true,
                  authors = "Olaf Danne",
                  copyright = "(c) 2009 by Brockmann Consult",
                  description = "Corrects MERIS band 11 and 15 and writes to output product.")
public class MerisBand11And15Op extends Operator {

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;
    @SourceProduct(alias = "refl")
    private Product refl1bProduct;
    @SourceProduct(alias = "corrRad")
    private Product corrReflProduct;    // rhoToa product

    @TargetProduct
    private Product targetProduct;

    @Override
    public void initialize() throws OperatorException {
        String productType = l1bProduct.getProductType();
        if (productType.indexOf("_1") != -1) {
            productType = productType.substring(0, productType.indexOf("_1")) + "_1N";
        }
        targetProduct = OperatorUtils.createCompatibleProduct(l1bProduct, "MER", productType, true);

        for (String bandName : corrReflProduct.getBandNames()) {
            if (!targetProduct.containsRasterDataNode(bandName)) {
                boolean copySrcImage = !bandName.equals("rho_toa_11") && !bandName.equals("rho_toa_15");
                ProductUtils.copyBand(bandName, corrReflProduct, targetProduct, copySrcImage);
            }
        }
        ProductUtils.copyBand(EnvisatConstants.MERIS_DETECTOR_INDEX_DS_NAME, l1bProduct, targetProduct, true);
    }

    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        if (band.getName().equals("rho_toa_11")) {
            computeRhoToa11(targetTile, band.getGeophysicalNoDataValue(), pm);
        } else {
            computeRhoToa15(targetTile, band.getGeophysicalNoDataValue(), pm);
        }
    }

    private void computeRhoToa11(Tile targetTile, double noDataValue, ProgressMonitor pm) {
        Rectangle rect = targetTile.getRectangle();
        pm.beginTask("Processing frame...", rect.height + 5);
        try {
            Tile l1bT10 = getSourceTile(refl1bProduct.getBand("rho_toa_" + 10), rect);
            Tile l1bT11 = getSourceTile(refl1bProduct.getBand("rho_toa_" + 11), rect);
            Tile l1bT12 = getSourceTile(refl1bProduct.getBand("rho_toa_" + 12), rect);

            Tile l1nT10 = getSourceTile(corrReflProduct.getBand("rho_toa_" + 10), rect);
            Tile l1nT12 = getSourceTile(corrReflProduct.getBand("rho_toa_" + 12), rect);

            for (int y = rect.y; y < rect.y + rect.height; y++) {
                for (int x = rect.x; x < rect.x + rect.width; x++) {
                    final float l1b10 = l1bT10.getSampleFloat(x, y);
                    if (l1b10 > 0.0) {
                        final float l1b11 = l1bT11.getSampleFloat(x, y);
                        final float l1b12 = l1bT12.getSampleFloat(x, y);

                        final float l1n10 = l1nT10.getSampleFloat(x, y);
                        final float l1n12 = l1nT12.getSampleFloat(x, y);

                        final float l1b11ref = 0.5f * (l1b10 + l1b12); // is this what RS means??
                        final float l1n11ref = 0.5f * (l1n10 + l1n12); // is this what RS means??
                        final float l1nb11 = l1b11 * l1n11ref / l1b11ref;

                        targetTile.setSample(x, y, l1nb11);
                    } else {
                        targetTile.setSample(x, y, noDataValue);
                    }
                }
                checkForCancellation();
                pm.worked(1);
            }
        } finally {
            pm.done();
        }
    }

    private void computeRhoToa15(Tile targetTile, double noDataValue, ProgressMonitor pm) {
        Rectangle rect = targetTile.getRectangle();
        pm.beginTask("Processing frame...", rect.height + 3);
        try {
            Tile l1bT14 = getSourceTile(refl1bProduct.getBand("rho_toa_" + 14), rect);
            Tile l1bT15 = getSourceTile(refl1bProduct.getBand("rho_toa_" + 15), rect);
            Tile l1nT14 = getSourceTile(corrReflProduct.getBand("rho_toa_" + 14), rect);

            for (int y = rect.y; y < rect.y + rect.height; y++) {
                for (int x = rect.x; x < rect.x + rect.width; x++) {
                    final float l1b14 = l1bT14.getSampleFloat(x, y);
                    if (l1b14 > 0.0) {
                        final float l1b15 = l1bT15.getSampleFloat(x, y);
                        final float l1n14 = l1nT14.getSampleFloat(x, y);
                        final float l1nb15 = l1b15 * l1n14 / l1b14;
                        targetTile.setSample(x, y, l1nb15);
                    } else {
                        targetTile.setSample(x, y, noDataValue);
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
            super(MerisBand11And15Op.class);
        }
    }
}
