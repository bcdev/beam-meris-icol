package org.esa.beam.meris.icol.tm;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.util.ProductUtils;

import java.awt.Rectangle;
import java.util.Map;

/**
 * @author Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
@OperatorMetadata(alias = "Landsat.GaseousCorrection",
        version = "1.0",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2009 by Brockmann Consult",
        description = "Landsat TM gaseous absorbtion correction.")
public class TmGaseousCorrectionOp extends TmBasisOp {
    public static final String RHO_NG_BAND_PREFIX = "rho_ng";
    public static final String GAS_FLAGS = "gas_flags";
    public static final String TG_BAND_PREFIX = "tg";

    public static final int F_DO_CORRECT = 0;
    public static final int F_SUN70 = 1;
    public static final int F_ORINP0 = 2;
    public static final int F_OROUT0 = 3;

    public static final int NO_DATA_VALUE = -1;

    private Band[] rhoNgBands;    // gas corrected reflectance , output
    private Band[] tgBands;       // total gaseous transmission , optional output

    @SourceProduct(alias = "refl")
    private Product sourceProduct;
    @SourceProduct(alias = "atmFunctions")
    private Product atmFunctionsProduct;
    @TargetProduct
    private Product targetProduct;
    @Parameter
    boolean exportTg = true;

    @Override
    public void initialize() throws OperatorException {
        createTargetProduct();
    }
    
    private void createTargetProduct() {
    	targetProduct = createCompatibleProduct(sourceProduct, "TM", "TM_L2");

    	rhoNgBands = new Band[TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS];
        for (int i = 0; i < TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS; i++) {
            rhoNgBands[i] = targetProduct.addBand(RHO_NG_BAND_PREFIX + "_" + (i + 1), ProductData.TYPE_FLOAT32);
//            ProductUtils.copySpectralAttributes(sourceProduct.getBandAt(i), rhoNgBands[i]);
            ProductUtils.copySpectralBandProperties(sourceProduct.getBandAt(i), rhoNgBands[i]);
            rhoNgBands[i].setNoDataValueUsed(true);
            rhoNgBands[i].setNoDataValue(NO_DATA_VALUE);
        }

        if (exportTg) {
            tgBands = new Band[TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS];
        	for (int i = 0; i < TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS; i++) {
                tgBands[i] = targetProduct.addBand(TG_BAND_PREFIX + "_" + (i + 1), ProductData.TYPE_FLOAT32);
                tgBands[i].setNoDataValueUsed(true);
                tgBands[i].setNoDataValue(NO_DATA_VALUE);
            }
        }
        if (sourceProduct.getPreferredTileSize() != null) {
            targetProduct.setPreferredTileSize(sourceProduct.getPreferredTileSize());
        }
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle rectangle, ProgressMonitor pm) throws OperatorException {

        pm.beginTask("Processing frame...", rectangle.height + 1);
        try {

            Tile[] gaseousTransmittanceTile = new Tile[TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS];
            for (int i = 0; i < TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS; i++) {
                gaseousTransmittanceTile[i] =
                        getSourceTile(atmFunctionsProduct.getBand(TmConstants.LANDSAT5_GAS_TRANSMITTANCE_BAND_NAMES[i]), rectangle, pm);
            }

            Tile[] reflectanceTile = new Tile[TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS];
            for (int i = 0; i < TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS; i++) {
                reflectanceTile[i] =
                        getSourceTile(sourceProduct.getBand(TmConstants.LANDSAT5_REFLECTANCE_BAND_NAMES[i]), rectangle, pm);
            }

            Tile[] rhoNgTile = new Tile[TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS];
            for (int i = 0; i < TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS; i++) {
                rhoNgTile[i] = targetTiles.get(rhoNgBands[i]);
            }

            Tile[] tgTile = new Tile[TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS];
            for (int i = 0; i < TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS; i++) {
                tgTile[i] = targetTiles.get(tgBands[i]);
            }

            Tile[] rhoNg = new Tile[rhoNgBands.length];
            Tile[] tg = null;
            if (exportTg) {
                tg = new Tile[tgBands.length];
            }
            for (int i = 0; i < rhoNgBands.length; i++) {
                rhoNg[i] = targetTiles.get(rhoNgBands[i]);
                if (exportTg) {
                    tg[i] = targetTiles.get(tgBands[i]);
                }
            }

            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                    for (int bandId = 0; bandId < TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS; bandId++) {
                        double reflectance = reflectanceTile[bandId].getSampleDouble(x, y);
                        final double gaseousTransmittance = gaseousTransmittanceTile[bandId].getSampleDouble(x, y);
                        if (bandId != TmConstants.LANDSAT5_RADIANCE_6_BAND_INDEX) { // TM6 (temperature)
                            reflectance *= gaseousTransmittance;
                        }
                        rhoNgTile[bandId].setSample(x, y, reflectance);
                        tgTile[bandId].setSample(x, y, gaseousTransmittance);
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

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(TmGaseousCorrectionOp.class);
        }
    }
}
