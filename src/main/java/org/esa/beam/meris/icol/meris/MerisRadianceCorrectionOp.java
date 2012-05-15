/*
 * $Id: FresnelCoefficientOp.java,v 1.1 2007/03/27 12:51:41 marcoz Exp $
 *
 * Copyright (C) 2007 by Brockmann Consult (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation. This program is distributed in the hope it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
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
import org.esa.beam.meris.brr.GaseousCorrectionOp;
import org.esa.beam.meris.icol.common.AdjacencyEffectMaskOp;
import org.esa.beam.meris.icol.utils.OperatorUtils;
import org.esa.beam.meris.l2auxdata.L2AuxData;
import org.esa.beam.meris.l2auxdata.L2AuxDataProvider;
import org.esa.beam.util.BitSetter;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.math.MathUtils;

import java.awt.Rectangle;


/**
 * Operator providing an output product with AE corrected MERIS radiances.
 *
 * @author Marco Zuehlke, Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
@OperatorMetadata(alias = "Meris.IcolCorrectedRadiances",
                  version = "1.0",
                  internal = true,
                  authors = "Marco Zuehlke, Olaf Danne",
                  copyright = "(c) 2007 by Brockmann Consult",
                  description = "Writes the AE corrected radiances to output product.")
public class MerisRadianceCorrectionOp extends Operator {

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;
    @SourceProduct(alias = "refl")
    private Product rhoToaProduct;
    @SourceProduct(alias = "gascor")
    private Product gasCorProduct;
    @SourceProduct(alias = "ae_aerosol")
    private Product aeAerosolProduct;
    @SourceProduct(alias = "aemaskAerosol")
    private Product aemaskAerosolProduct;

    @TargetProduct
    private Product targetProduct;

    private L2AuxData auxData;


    @Override
    public void initialize() throws OperatorException {
        try {
            auxData = L2AuxDataProvider.getInstance().getAuxdata(l1bProduct);
        } catch (Exception e) {
            throw new OperatorException("could not load L2Auxdata", e);
        }

        String productType = l1bProduct.getProductType();
        int index = productType.indexOf("_1");
        productType = productType.substring(0, index) + "_1N";
        targetProduct = OperatorUtils.createCompatibleProduct(l1bProduct, "MER", productType, true);
        for (String bandName : l1bProduct.getBandNames()) {
            if (bandName.startsWith("radiance")) {
                ProductUtils.copyBand(bandName, l1bProduct, targetProduct, false);
            } else if (bandName.equals(EnvisatConstants.MERIS_DETECTOR_INDEX_DS_NAME) ||
                    bandName.endsWith(EnvisatConstants.MERIS_L1B_FLAGS_DS_NAME)) {
                if (!targetProduct.containsRasterDataNode(bandName)) {
                    ProductUtils.copyBand(bandName, l1bProduct, targetProduct, true);
                }
            }
        }
        ProductUtils.copyFlagBands(aeAerosolProduct, targetProduct, true);
        // finally make sure that icolized MERIS product contain all other bands of original product
        // (e.g. DEM-related bands corr_lat, corr_lon, altitude)
        for (String bandName : l1bProduct.getBandNames()) {
            if (!targetProduct.containsBand(bandName)) {
                ProductUtils.copyBand(bandName, l1bProduct, targetProduct, true);
            }
        }
    }

    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        String bandName = band.getName();
        if (bandName.equals("l1_flags")) {
            computeL1Flags(band, targetTile, pm);
        } else {
            computeRadiances(band, targetTile, pm);
        }
    }

    public void computeL1Flags(Band band, Tile targetTile, ProgressMonitor pm) {
        Rectangle rect = targetTile.getRectangle();
        pm.beginTask("Processing frame...", rect.height + 4);
        try {
            Tile sourceTile = getSourceTile(l1bProduct.getBand(band.getName()), rect);
            Tile gasCor0 = getSourceTile(gasCorProduct.getBand(GaseousCorrectionOp.RHO_NG_BAND_PREFIX + "_" + 1), rect);
            Tile aemaskAerosol = getSourceTile(aemaskAerosolProduct.getBand(AdjacencyEffectMaskOp.AE_MASK_AEROSOL),
                                               rect);
            Tile aeAerosol = getSourceTile(aeAerosolProduct.getBand(MerisAdjacencyEffectAerosolOp.AOT_FLAGS), rect);

            for (int y = rect.y; y < rect.y + rect.height; y++) {
                for (int x = rect.x; x < rect.x + rect.width; x++) {
                    int l1Flag = sourceTile.getSampleInt(x, y);
                    boolean aeApplied = false;
                    if (aemaskAerosol.getSampleInt(x, y) == 1 && gasCor0.getSampleFloat(x, y) != -1) {
                        aeApplied = !aeAerosol.getSampleBit(x, y, 1);
                    }
                    l1Flag &= 1 + 2 + 4 + 16 + 32 + 64 + 128; //erase SUSPECT
                    l1Flag = BitSetter.setFlag(l1Flag, 3, aeApplied); // set SUSPECT, if ae applied
                    targetTile.setSample(x, y, l1Flag);
                }
                checkForCancellation();
                pm.worked(1);
            }
        } finally {
            pm.done();
        }
    }

    public void computeRadiances(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Rectangle rect = targetTile.getRectangle();
        pm.beginTask("Processing frame...", rect.height + 4);
        try {
            final int bandNumber = band.getSpectralBandIndex() + 1;

            Tile sza = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME), rect);
            Tile detectorIndexTile = getSourceTile(l1bProduct.getBand(EnvisatConstants.MERIS_DETECTOR_INDEX_DS_NAME),
                                                   rect);
            Tile radianceR = getSourceTile(l1bProduct.getBand("radiance_" + bandNumber), rect);
            Tile rhoToaCorrTile = getSourceTile(rhoToaProduct.getBand("rho_toa_AEAC_" + bandNumber), rect);

            for (int y = rect.y; y < rect.y + rect.height; y++) {
                for (int x = rect.x; x < rect.x + rect.width; x++) {
                    int detectorIndex = detectorIndexTile.getSampleInt(x, y);
                    if (detectorIndex != -1) {
                        double rhoToa = rhoToaCorrTile.getSampleDouble(x, y);
                        double result = (rhoToa * Math.cos(sza.getSampleDouble(x, y) * MathUtils.DTOR) *
                                auxData.detector_solar_irradiance[bandNumber - 1][detectorIndex]) /
                                (Math.PI * auxData.seasonal_factor);
                        final double radianceOrig = radianceR.getSampleDouble(x, y);
                        // final consistency check: if corrected values are negative, revert correction
                        if (result <= 0.0) {
                            result = radianceOrig;
                        }
                        targetTile.setSample(x, y, result);
                    } else {
                        targetTile.setSample(x, y, radianceR.getSampleDouble(x, y));
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
            super(MerisRadianceCorrectionOp.class);
        }
    }
}
