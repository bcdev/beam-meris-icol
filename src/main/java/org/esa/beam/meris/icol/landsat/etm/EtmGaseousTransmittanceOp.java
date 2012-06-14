package org.esa.beam.meris.icol.landsat.etm;

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
import org.esa.beam.meris.icol.landsat.common.LandsatConstants;
import org.esa.beam.meris.icol.landsat.common.DownscaleOp;
import org.esa.beam.meris.icol.utils.OperatorUtils;

import javax.media.jai.BorderExtender;
import java.awt.*;
import java.util.Map;

/**
 * Landsat 7 atmospheric functions for gaseous transmittance
 *
 * @author Olaf Danne
 */
@OperatorMetadata(alias = "Landsat7.Etm.AtmosphericFunctions",
        version = "1.0",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2009 by Brockmann Consult",
        description = "Landsat 7 atmospheric functions for gaseous transmittance.")
public class EtmGaseousTransmittanceOp extends Operator {

    final int NO_DATA_VALUE = -1;

    @SourceProduct(alias="l1g")
    private Product sourceProduct;
    @SourceProduct(alias="downscaled")
    private Product downscaledProduct;

    @TargetProduct
    private Product targetProduct;

    @Parameter
    private double ozoneContent;

    private Band[] gaseousTransmittanceBands;

    @Override
    public void initialize() throws OperatorException {
        targetProduct = OperatorUtils.createCompatibleProduct(downscaledProduct, sourceProduct.getName() + "_ICOL", "ICOL");

        gaseousTransmittanceBands = new Band[LandsatConstants.LANDSAT7_NUM_SPECTRAL_BANDS];
        for (int i = 0; i < LandsatConstants.LANDSAT7_NUM_SPECTRAL_BANDS; i++) {
            gaseousTransmittanceBands[i] = targetProduct.addBand(LandsatConstants.LANDSAT7_GAS_TRANSMITTANCE_BAND_NAMES[i],
                                                                 ProductData.TYPE_FLOAT32);
            gaseousTransmittanceBands[i].setNoDataValueUsed(true);
            gaseousTransmittanceBands[i].setNoDataValue(NO_DATA_VALUE);
        }
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle rectangle, ProgressMonitor pm) throws OperatorException {
        pm.beginTask("Processing frame...", rectangle.height + 1);
        try {
            final Tile airMassTile = getSourceTile(downscaledProduct.getBand(DownscaleOp.AIR_MASS_BAND_NAME), rectangle,
                    BorderExtender.createInstance(BorderExtender.BORDER_COPY));
            final Tile[] gaseousTransmittanceTile = OperatorUtils.getTargetTiles(targetTiles, gaseousTransmittanceBands);

            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
				for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                    final float airMass = airMassTile.getSampleFloat(x, y);
                    for (int bandId = 0; bandId < LandsatConstants.LANDSAT7_NUM_SPECTRAL_BANDS; bandId++) {
                        final double gaseousTransmittance =
                                Math.exp(-airMass*ozoneContent* LandsatConstants.LANDSAT7_O3_OPTICAL_THICKNESS[bandId]/0.32);
                        gaseousTransmittanceTile[bandId].setSample(x, y, gaseousTransmittance);
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
            super(EtmGaseousTransmittanceOp.class);
        }
    }
}
