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
import org.esa.beam.meris.icol.utils.LandsatUtils;
import org.esa.beam.meris.l2auxdata.L2AuxData;
import org.esa.beam.meris.l2auxdata.L2AuxdataProvider;
import org.esa.beam.util.ProductUtils;

import java.awt.Rectangle;
import java.util.Map;

/**
 * @author Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
@OperatorMetadata(alias = "Landsat.AtmosphericFunctions",
        version = "1.0",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2009 by Brockmann Consult",
        description = "Landsat atmospheric functions.")
public class TmGaseousTransmittanceOp extends TmBasisOp {

    private transient Band[] gaseousTransmittanceBands;

    private transient L2AuxData auxData;

    protected TmRayleighCorrection rayleighCorrection;

    final int NO_DATA_VALUE = -1;

    @SourceProduct(alias="l1g")
    private Product sourceProduct;
    @SourceProduct(alias="refl")
    private Product conversionProduct;
    @SourceProduct(alias="geometry")
    private Product geometryProduct;
    @TargetProduct
    private Product targetProduct;
    @Parameter
    private double ozoneContent;

    @Override
    public void initialize() throws OperatorException {
        try {
            // parts of MERIS auxdata can be used for Landsat as well,
            // but first we need a compatible MERIS product to read the L2 auxdata...
            Product merisCompatibleProduct = LandsatUtils.createMerisCompatibleProductForL2Auxdata(conversionProduct);
            auxData = L2AuxdataProvider.getInstance().getAuxdata(merisCompatibleProduct);
            rayleighCorrection = new TmRayleighCorrection(auxData);
        } catch (Exception e) {
            throw new OperatorException("could not load L2Auxdata", e);
        }

        final int sceneWidth = geometryProduct.getSceneRasterWidth();
        final int sceneHeight = geometryProduct.getSceneRasterHeight();
        targetProduct = new Product(sourceProduct.getName() + "_ICOL", "ICOL", sceneWidth, sceneHeight);

        ProductUtils.copyTiePointGrids(geometryProduct, targetProduct);
        ProductUtils.copyGeoCoding(geometryProduct, targetProduct);

        gaseousTransmittanceBands = new Band[TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS];

        for (int i = 0; i < TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS; i++) {

            gaseousTransmittanceBands[i] = targetProduct.addBand(TmConstants.LANDSAT5_GAS_TRANSMITTANCE_BAND_NAMES[i],
                                                                 ProductData.TYPE_FLOAT32);
            gaseousTransmittanceBands[i].setNoDataValueUsed(true);
            gaseousTransmittanceBands[i].setNoDataValue(NO_DATA_VALUE);
        }
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle rectangle, ProgressMonitor pm) throws OperatorException {

        pm.beginTask("Processing frame...", rectangle.height);
        try {
            Tile airMassTile = getSourceTile(geometryProduct.getBand(TmGeometryOp.AIR_MASS_BAND_NAME), rectangle, pm);

            Tile[] gaseousTransmittanceTile = new Tile[gaseousTransmittanceBands.length];
            for (int i = 0; i < gaseousTransmittanceBands.length; i++) {
                gaseousTransmittanceTile[i] = targetTiles.get(gaseousTransmittanceBands[i]);
            }


            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
				for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                    final float airMass = airMassTile.getSampleFloat(x, y);
                    for (int bandId = 0; bandId < TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS; bandId++) {
                        final double gaseousTransmittance =
                                Math.exp(-airMass*ozoneContent* TmConstants.LANDSAT5_O3_OPTICAL_THICKNESS[bandId]/0.32);
                        gaseousTransmittanceTile[bandId].setSample(x, y, gaseousTransmittance);
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
            super(TmGaseousTransmittanceOp.class);
        }
    }
}
