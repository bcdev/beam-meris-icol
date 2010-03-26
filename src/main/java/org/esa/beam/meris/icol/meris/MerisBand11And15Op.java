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
import org.esa.beam.util.ProductUtils;

import java.awt.Rectangle;

/**
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
@OperatorMetadata(alias = "Meris.IcolCorrectBand11And15",
        version = "1.0",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2009 by Brockmann Consult",
        description = "Corrects MERIS band 11 and 15 and writes to output product.")
public class MerisBand11And15Op extends Operator {

    @SourceProduct(alias="l1b")
    private Product l1bProduct;
    @SourceProduct(alias="refl")
    private Product refl1bProduct;
    @SourceProduct(alias="corrRad")
    private Product corrReflProduct;    // rhoToa product

    @TargetProduct
    private Product targetProduct;

    @Override
    public void initialize() throws OperatorException {

        String productType = l1bProduct.getProductType();
        int index = productType.indexOf("_1");
        productType = productType.substring(0, index) + "_1N";
        targetProduct = createCompatibleBaseProduct("MER", productType);
        for (String bandName : corrReflProduct.getBandNames()) {
            if(!bandName.equals("l1_flags")) {
//                if (bandName.equals("rho_toa_11") || bandName.equals("rho_toa_15")) {
//                    Band reflBand = targetProduct.addBand(bandName, ProductData.TYPE_FLOAT32);
//                    reflBand.setSpectralBandIndex(Integer.parseInt(bandName.substring(8))-1);
//                    reflBand.setNoDataValue(-1);
//                } else {
//                    ProductUtils.copyBand(bandName, corrReflProduct, targetProduct);
//                }
                ProductUtils.copyBand(bandName, corrReflProduct, targetProduct);
            }
        }
        ProductUtils.copyFlagBands(l1bProduct, targetProduct);
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

    private Tile[] getTileGroup(final Product inProduct, String bandPrefix, Rectangle rectangle, ProgressMonitor pm) throws OperatorException {
        final Tile[] bandData = new Tile[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];
        int j = 0;
        for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            bandData[j] = getSourceTile(inProduct.getBand(bandPrefix + "_" + (i + 1)), rectangle, pm);
            j++;
        }
        return bandData;
    }


    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {


        Rectangle rectangle = targetTile.getRectangle();
        pm.beginTask("Processing frame...", rectangle.height);
        try {
            String bandName = band.getName();

            if (bandName.equals("rho_toa_15"))
                            System.out.println("");

            if (!bandName.equals("rho_toa_11") && !bandName.equals("rho_toa_15")
			         || bandName.equals(EnvisatConstants.MERIS_DETECTOR_INDEX_DS_NAME)) {
                Tile sourceTile = getSourceTile(corrReflProduct.getBand(bandName), rectangle, pm);
                //  write reflectances as output  (RS, 17.07.09)

				for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
					for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
						targetTile.setSample(x, y, sourceTile.getSampleDouble(x, y));
					}
					pm.worked(1);
				}
			} else {
				final int bandNumber = band.getSpectralBandIndex() + 1;

                Tile[] l1bTile = getTileGroup(refl1bProduct, "rho_toa", rectangle, pm);
                Tile[] l1nTile = getTileGroup(corrReflProduct, "rho_toa", rectangle, pm);

				for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
					for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {

                        if (bandName.equals("rho_toa_15") && x == 150 && y == 150)
                            System.out.println("");

                        final float l1b10 = l1bTile[9].getSampleFloat(x, y);
                        final float l1b11 = l1bTile[10].getSampleFloat(x, y);
                        final float l1b12 = l1bTile[11].getSampleFloat(x, y);
                        final float l1b14 = l1bTile[13].getSampleFloat(x, y);
                        final float l1b15 = l1bTile[14].getSampleFloat(x, y);
                        final float l1n10 = l1nTile[9].getSampleFloat(x, y);
                        final float l1n12 = l1nTile[10].getSampleFloat(x, y);
                        final float l1n14 = l1nTile[12].getSampleFloat(x, y);

                        final float l1nb15 = l1b15 * l1n14 / l1b14;
                        final float l1b11ref = 0.5f * (l1b10 + l1b12); // is this what RS means??
                        final float l1n11ref = 0.5f * (l1n10 + l1n12); // is this what RS means??
                        final float l1nb11 = l1b11 * l1n11ref / l1b11ref;

                        if (l1b14 > 0.0 && l1b11ref > 0.0) {
                            if (bandName.equals("rho_toa_11")) {
                               targetTile.setSample(x, y, l1nb11);
                            } else {
                               targetTile.setSample(x, y, l1nb15);
                            }
                        } else {
                            targetTile.setSample(x, y, -1);
                        }
					}
					pm.worked(1);
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
            super(MerisBand11And15Op.class);
        }
    }
}
