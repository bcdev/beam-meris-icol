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
import com.bc.ceres.glevel.MultiLevelImage;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.meris.brr.GaseousCorrectionOp;
import org.esa.beam.meris.icol.common.AeMaskOp;
import org.esa.beam.meris.l2auxdata.L2AuxData;
import org.esa.beam.meris.l2auxdata.L2AuxdataProvider;
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
public class MerisRadianceCorrectionOp extends MerisBasisOp {

    private transient L2AuxData auxData;
    
    @SourceProduct(alias="l1b")
    private Product l1bProduct;
    @SourceProduct(alias="refl")
    private Product rhoToaProduct;
    @SourceProduct(alias="gascor")
    private Product gasCorProduct;
    @SourceProduct(alias="ae_ray")
    private Product aeRayProduct;
     @SourceProduct(alias="ae_aerosol")
    private Product aeAerosolProduct;
    @SourceProduct(alias="aemaskRayleigh")
    private Product aemaskRayleighProduct;
    @SourceProduct(alias="aemaskAerosol")
    private Product aemaskAerosolProduct;

    @TargetProduct
    private Product targetProduct;
    private static final int NO_DATA_VALUE = -1;


    @Override
    public void initialize() throws OperatorException {
        try {
            auxData = L2AuxdataProvider.getInstance().getAuxdata(l1bProduct);
        } catch (Exception e) {
            throw new OperatorException("could not load L2Auxdata", e);
        }

        String productType = l1bProduct.getProductType();
        int index = productType.indexOf("_1");
        productType = productType.substring(0, index) + "_1N";
        targetProduct = createCompatibleBaseProduct("MER", productType);
        for (String bandName : l1bProduct.getBandNames()) {
            if (bandName.startsWith("radiance")) {
                Band radianceBand = targetProduct.addBand(bandName, ProductData.TYPE_FLOAT32);
                Band sourceBand = l1bProduct.getBand(bandName);
                ProductUtils.copySpectralBandProperties(sourceBand, radianceBand);
                radianceBand.setSpectralBandIndex(Integer.parseInt(bandName.substring(9))-1);
                radianceBand.setNoDataValue(NO_DATA_VALUE);
            } else if (bandName.equals(EnvisatConstants.MERIS_DETECTOR_INDEX_DS_NAME)) {
                // this fails for the radiance bands because of the scaling
                // introduced in 'copyRasterDataNodeProperties'. Conversion
                // to reflectance later on leads to wrong results in 'setSample'.
                Band band = ProductUtils.copyBand(bandName, l1bProduct, targetProduct);
                band.setSourceImage(l1bProduct.getBand(bandName).getSourceImage());
            }
        }
        ProductUtils.copyFlagBands(l1bProduct, targetProduct);
        MultiLevelImage image = l1bProduct.getBand(EnvisatConstants.MERIS_L1B_FLAGS_DS_NAME).getSourceImage();
        targetProduct.getBand(EnvisatConstants.MERIS_L1B_FLAGS_DS_NAME).setSourceImage(image);

        ProductUtils.copyFlagBands(aeAerosolProduct, targetProduct);
        image = aeAerosolProduct.getBand(MerisAeAerosolOp.AOT_FLAGS).getSourceImage();
        targetProduct.getBand(MerisAeAerosolOp.AOT_FLAGS).setSourceImage(image);
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
            String bandName = band.getName();
			if (bandName.equals("l1_flags")) {
			    Tile sourceTile = getSourceTile(l1bProduct.getBand(bandName), rectangle, pm);
			    Tile gasCor0 = getSourceTile(gasCorProduct.getBand(GaseousCorrectionOp.RHO_NG_BAND_PREFIX + "_" + 1), rectangle, pm);
			    Tile aemaskAerosol = getSourceTile(aemaskAerosolProduct.getBand(AeMaskOp.AE_MASK_AEROSOL), rectangle, pm);
			    Tile aeAerosol = getSourceTile(aeAerosolProduct.getBand(MerisAeAerosolOp.AOT_FLAGS), rectangle, pm);
                for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                    for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                        int l1Flag = sourceTile.getSampleInt(x, y);
                        boolean aeApplied = false;
                        if (aemaskAerosol.getSampleInt(x, y) == 1 && gasCor0.getSampleFloat(x, y) != -1) {
                            aeApplied = true;
                            if (aeAerosol.getSampleBit(x, y, 1)) {
                                aeApplied = false;
                            }
                        }
                        l1Flag &= 1+2+4+16+32+64+128; //erase SUSPECT 
                        l1Flag = BitSetter.setFlag(l1Flag, 3, aeApplied); // set SUSPECT, if ae applied
                        targetTile.setSample(x, y, l1Flag);
                    }
                    pm.worked(1);
                }
            } else if (bandName.startsWith("radiance")) {
				final int bandNumber = band.getSpectralBandIndex() + 1;

				Tile sza = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME), rectangle, pm);
				Tile detectorIndexTile = getSourceTile(l1bProduct.getBand(EnvisatConstants.MERIS_DETECTOR_INDEX_DS_NAME), rectangle, pm);
                Tile radianceR = getSourceTile(l1bProduct.getBand("radiance_" +  bandNumber), rectangle, pm);
                Tile rhoToaCorrTile = getSourceTile(rhoToaProduct.getBand("rho_toa_AEAC_" +  bandNumber), rectangle, pm);

				for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
					for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                        int detectorIndex = detectorIndexTile.getSampleInt(x, y);
                        if (detectorIndex != -1) {
                            double rhoToa = rhoToaCorrTile.getSampleDouble(x, y);
                            double result = (rhoToa * Math.cos(sza.getSampleDouble(x, y) * MathUtils.DTOR) *
                                    auxData.detector_solar_irradiance[bandNumber - 1][detectorIndex]) /
                                    (Math.PI * auxData.seasonal_factor);
                            final double radianceOrig = radianceR.getSampleDouble(x, y);
//                            if (result == 0) {
//                                result = radianceOrig;
//                            }
                            // final consistency check:
                            double reldiff = 100.0*Math.abs(result - radianceOrig)/radianceOrig;
                            if (result <= 0.0 || reldiff > 20.0) {
//                            if (result <= 0.0) {
                                result = radianceOrig;
                            }
                            double radiance = result;
                            targetTile.setSample(x, y, radiance);
                        } else {
                            targetTile.setSample(x, y, NO_DATA_VALUE);
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
            super(MerisRadianceCorrectionOp.class);
        }
    }
}
