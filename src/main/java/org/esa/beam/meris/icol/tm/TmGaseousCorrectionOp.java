package org.esa.beam.meris.icol.tm;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.meris.icol.utils.OperatorUtils;

import javax.media.jai.BorderExtender;
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
    public static final String TG_BAND_PREFIX = "tg";
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
    	targetProduct = createCompatibleProduct(sourceProduct, "TM", "TM_L2");

        rhoNgBands = OperatorUtils.addBandGroup(sourceProduct, TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS,
                new int[]{}, targetProduct, RHO_NG_BAND_PREFIX, NO_DATA_VALUE, false);

        if (exportTg) {
            tgBands = OperatorUtils.addBandGroup(sourceProduct, TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS,
                    new int[]{}, targetProduct, TG_BAND_PREFIX, NO_DATA_VALUE, false);
        }
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle rectangle, ProgressMonitor pm) throws OperatorException {

        pm.beginTask("Processing frame...", rectangle.height + 1);
        try {

            Tile[] gaseousTransmittanceTile = new Tile[TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS];
            for (int i = 0; i < TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS; i++) {
                gaseousTransmittanceTile[i] =
                        getSourceTile(atmFunctionsProduct.getBand(TmConstants.LANDSAT5_GAS_TRANSMITTANCE_BAND_NAMES[i]), rectangle,
                                BorderExtender.createInstance(BorderExtender.BORDER_COPY));
            }

            Tile[] reflectanceTile = new Tile[TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS];
            for (int i = 0; i < TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS; i++) {
                reflectanceTile[i] =
                        getSourceTile(sourceProduct.getBand(TmConstants.LANDSAT5_REFLECTANCE_BAND_NAMES[i]), rectangle,
                                BorderExtender.createInstance(BorderExtender.BORDER_COPY));
            }

            Tile[] rhoNgTile = OperatorUtils.getTargetTiles(targetTiles, rhoNgBands);
            Tile[] tgTile = null;
            if (exportTg) {
                tgTile = OperatorUtils.getTargetTiles(targetTiles, tgBands);
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
                        if (exportTg) {
                            tgTile[bandId].setSample(x, y, gaseousTransmittance);
                        }
                    }
                }
                checkForCancellation(pm);
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
