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
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.MetadataAttribute;
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
import org.esa.beam.meris.brr.CloudClassificationOp;
import org.esa.beam.meris.brr.GaseousCorrectionOp;
import org.esa.beam.meris.icol.common.AdjacencyEffectMaskOp;
import org.esa.beam.meris.icol.utils.OperatorUtils;
import org.esa.beam.util.ProductUtils;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;


/**
 * Operator providing an output product with AE corrected MERIS TOA reflectances.
 *
 * @author Marco Zuehlke, Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
@OperatorMetadata(alias = "Meris.IcolCorrectedReflectances",
                  version = "2.9.5",
                  internal = true,
                  authors = "Marco Zühlke",
                  copyright = "(c) 2007 by Brockmann Consult",
                  description = "Corrects for the adjacency effect and computes rho TOA.")
public class MerisReflectanceCorrectionOp extends Operator {

    private static final int FLAG_AE_MASK_RAYLEIGH = 1;
    private static final int FLAG_AE_MASK_AEROSOL = 2;
    private static final int FLAG_LANDCONS = 4;
    private static final int FLAG_CLOUD = 8;
    private static final int FLAG_AE_APPLIED_RAYLEIGH = 16;
    private static final int FLAG_AE_APPLIED_AEROSOL = 32;
    private static final int FLAG_ALPHA_OUT_OF_RANGE = 64;
    private static final int FLAG_AOT_OUT_OF_RANGE = 128;
    private static final int FLAG_HIGH_TURBID_WATER = 256;
    private static final int FLAG_SUNGLINT = 512;

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;
    @SourceProduct(alias = "rhotoa")
    private Product rhoToaProduct;
    @SourceProduct(alias = "land")
    private Product landProduct;
    @SourceProduct(alias = "cloud")
    private Product cloudProduct;
    @SourceProduct(alias = "aemaskRayleigh")
    private Product aemaskRayleighProduct;
    @SourceProduct(alias = "aemaskAerosol")
    private Product aemaskAerosolProduct;
    @SourceProduct(alias = "gascor")
    private Product gasCorProduct;
    @SourceProduct(alias = "ae_ray")
    private Product aeRayProduct;
    @SourceProduct(alias = "ae_aerosol")
    private Product aeAerosolProduct;

    @TargetProduct
    private Product targetProduct;

    @Parameter(defaultValue = "true")
    private boolean exportRhoToa;
    @Parameter(defaultValue = "true")
    private boolean exportRhoToaRayleigh;
    @Parameter(defaultValue = "true")
    private boolean exportRhoToaAerosol;
    @Parameter(defaultValue = "true")
    private boolean exportAeRayleigh;
    @Parameter(defaultValue = "true")
    private boolean exportAeAerosol;
    @Parameter(defaultValue = "true")
    private boolean exportAlphaAot;
    @Parameter(defaultValue = "false")
    private boolean icolAerosolCase2;
    @Parameter(defaultValue = "true")
    private boolean icolAerosolForWater;

    private List<Band> rhoToaRayBands;
    private List<Band> rhoToaAerBands;
    private Band aeFlagBand;

    @Override
    public void initialize() throws OperatorException {
        String productType = l1bProduct.getProductType();
        final int index = productType.indexOf("_1");
        if (index != -1) {
            productType = productType.substring(0, index) + "_1N";
        }
        targetProduct = OperatorUtils.createCompatibleProduct(l1bProduct, "reverseRhoToa", productType, true);
        Band[] allBands = rhoToaProduct.getBands();
        Band[] sourceBands = new Band[15];
        int count = 0;
        for (int i = 0; i < allBands.length; i++) {
            Band band = allBands[i];
            if (band.getName().startsWith("rho_toa")) {
                sourceBands[count++] = band;
            }
        }
        if (exportRhoToa) {
            copyBandGroup(rhoToaProduct, "rho_toa");
        }
        if (exportRhoToaRayleigh) {
            rhoToaRayBands = addBandGroup(sourceBands, "rho_toa_AERC");
        }
        if (exportRhoToaAerosol) {
            rhoToaAerBands = addBandGroup(sourceBands, "rho_toa_AEAC");
        }
        if (exportAeRayleigh) {
            copyBandGroup(aeRayProduct, "rho_aeRay");
        }
        if (exportAeAerosol) {
            copyBandGroup(aeAerosolProduct, "rho_aeAer");
        }
        if (exportAlphaAot) {
            ProductUtils.copyBand("alpha", aeAerosolProduct, targetProduct, true);
            ProductUtils.copyBand("aot", aeAerosolProduct, targetProduct, true);
        }

        if (icolAerosolCase2 && icolAerosolForWater) {
            ProductUtils.copyBand("rhoW9", aeAerosolProduct, targetProduct, true);
        }

        aeFlagBand = targetProduct.addBand("ae_flags", ProductData.TYPE_UINT8);
        aeFlagBand.setDescription("Adjacency-Effect flags");

        ProductUtils.copyBand("land_flag_ray_conv", aeRayProduct, targetProduct, true);
        ProductUtils.copyBand("cloud_flag_ray_conv", aeRayProduct, targetProduct, true);
        ProductUtils.copyBand("land_flag_aer_conv", aeAerosolProduct, targetProduct, true);
        ProductUtils.copyBand("cloud_flag_aer_conv", aeAerosolProduct, targetProduct, true);

        // create and add the flags coding
        FlagCoding flagCoding = createFlagCoding(aeFlagBand.getName());
        targetProduct.getFlagCodingGroup().add(flagCoding);
        aeFlagBand.setSampleCoding(flagCoding);

        ProductUtils.copyFlagBands(l1bProduct, targetProduct, true);
        ProductUtils.copyFlagBands(aeAerosolProduct, targetProduct, true);//needed ???
    }

    private FlagCoding createFlagCoding(String bandName) {
        MetadataAttribute cloudAttr;
        final FlagCoding flagCoding = new FlagCoding(bandName);
        flagCoding.setDescription("Adjacency-Effect - Flag Coding");

        cloudAttr = new MetadataAttribute("ae_mask_rayleigh", ProductData.TYPE_INT16);
        cloudAttr.getData().setElemInt(FLAG_AE_MASK_RAYLEIGH);
        cloudAttr.setDescription("Pixel is inside Rayleigh AE correction mask.");
        flagCoding.addAttribute(cloudAttr);

        cloudAttr = new MetadataAttribute("ae_mask_aerosol", ProductData.TYPE_INT16);
        cloudAttr.getData().setElemInt(FLAG_AE_MASK_AEROSOL);
        cloudAttr.setDescription("Pixel is inside aerosol AE correction mask.");
        flagCoding.addAttribute(cloudAttr);

        cloudAttr = new MetadataAttribute("landcons", ProductData.TYPE_INT16);
        cloudAttr.getData().setElemInt(FLAG_LANDCONS);
        cloudAttr.setDescription("Consolidated land pixel.");
        flagCoding.addAttribute(cloudAttr);

        cloudAttr = new MetadataAttribute("cloud", ProductData.TYPE_INT16);
        cloudAttr.getData().setElemInt(FLAG_CLOUD);
        cloudAttr.setDescription("Cloud pixel.");
        flagCoding.addAttribute(cloudAttr);

        cloudAttr = new MetadataAttribute("ae_applied_rayleigh", ProductData.TYPE_INT16);
        cloudAttr.getData().setElemInt(FLAG_AE_APPLIED_RAYLEIGH);
        cloudAttr.setDescription("Rayleigh AE correction was applied to this pixel.");
        flagCoding.addAttribute(cloudAttr);

        cloudAttr = new MetadataAttribute("ae_applied_aerosol", ProductData.TYPE_INT16);
        cloudAttr.getData().setElemInt(FLAG_AE_APPLIED_AEROSOL);
        cloudAttr.setDescription("Aerosol AE correction was applied to this pixel.");
        flagCoding.addAttribute(cloudAttr);

        cloudAttr = new MetadataAttribute("alpha_out_of_range", ProductData.TYPE_INT16);
        cloudAttr.getData().setElemInt(FLAG_ALPHA_OUT_OF_RANGE);
        cloudAttr.setDescription("Alpha value is out of range for this pixel.");
        flagCoding.addAttribute(cloudAttr);

        cloudAttr = new MetadataAttribute("aot_out_of_range", ProductData.TYPE_INT16);
        cloudAttr.getData().setElemInt(FLAG_AOT_OUT_OF_RANGE);
        cloudAttr.setDescription("AOT value is out of range for this pixel.");
        flagCoding.addAttribute(cloudAttr);

        cloudAttr = new MetadataAttribute("high_turbid_water", ProductData.TYPE_INT16);
        cloudAttr.getData().setElemInt(FLAG_HIGH_TURBID_WATER);
        cloudAttr.setDescription("Turbidity was identified as high for this pixel.");
        flagCoding.addAttribute(cloudAttr);

        cloudAttr = new MetadataAttribute("sunglint", ProductData.TYPE_INT16);
        cloudAttr.getData().setElemInt(FLAG_SUNGLINT);
        cloudAttr.setDescription("Sun glint present in this pixel.");
        flagCoding.addAttribute(cloudAttr);

        return flagCoding;
    }

    private void copyBandGroup(Product sourceProduct, String bandPrefix) {
        Band[] sourceBands = sourceProduct.getBands();
        for (Band srcBand : sourceBands) {
            String srcBandName = srcBand.getName();
            if (srcBandName.startsWith(bandPrefix)) {
                ProductUtils.copyBand(srcBandName, sourceProduct, targetProduct, true);
            }
        }
    }

    private List<Band> addBandGroup(Band[] sourceBands, String bandPrefix) {
        List<Band> bandList = new ArrayList<Band>(sourceBands.length);
        for (Band srcBand : sourceBands) {
            int bandNo = srcBand.getSpectralBandIndex() + 1;
            final String bandName = bandPrefix + "_" + bandNo;
            if (!targetProduct.containsRasterDataNode(bandName)) {
                Band targetBand = targetProduct.addBand(bandName, ProductData.TYPE_FLOAT32);
                ProductUtils.copySpectralBandProperties(srcBand, targetBand);
                targetBand.setNoDataValueUsed(srcBand.isNoDataValueUsed());
                targetBand.setNoDataValue(srcBand.getNoDataValue());
                if (bandNo == 11 || bandNo == 14 || bandNo == 15) {
                    targetBand.setSourceImage(srcBand.getSourceImage());
                } else {
                    bandList.add(targetBand);
                }
            }
        }
        return bandList;
    }

    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        final int bandId = band.getSpectralBandIndex();
        final int bandNumber = bandId + 1;

        if (rhoToaRayBands != null && rhoToaRayBands.contains(band)) {
            correctForRayleigh(targetTile, bandNumber, pm);
        } else if (rhoToaAerBands != null && rhoToaAerBands.contains(band)) {
            correctForRayleighAndAerosol(targetTile, bandNumber, pm);
        } else if (band == aeFlagBand) {
            computeAeFlags(targetTile, pm);
        }
    }

    private void computeAeFlags(Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Rectangle rect = targetTile.getRectangle();
        pm.beginTask("Processing frame...", rect.height + 6);
        try {
            Tile land = getSourceTile(landProduct.getBand(MerisLandClassificationOp.LAND_FLAGS), rect);
            Tile cloud = getSourceTile(cloudProduct.getBand(CloudClassificationOp.CLOUD_FLAGS), rect);
            Tile aemaskRayleigh = getSourceTile(aemaskRayleighProduct.getBand(AdjacencyEffectMaskOp.AE_MASK_RAYLEIGH),
                                                rect);
            Tile aemaskAerosol = getSourceTile(aemaskAerosolProduct.getBand(AdjacencyEffectMaskOp.AE_MASK_AEROSOL),
                                               rect);
            Tile gasCor0 = getSourceTile(gasCorProduct.getBand(GaseousCorrectionOp.RHO_NG_BAND_PREFIX + "_1"), rect);
            Tile aerosol = getSourceTile(aeAerosolProduct.getBand(MerisAdjacencyEffectAerosolOp.AOT_FLAGS), rect);

            for (int y = rect.y; y < rect.y + rect.height; y++) {
                for (int x = rect.x; x < rect.x + rect.width; x++) {
                    int result = 0;
                    if (aemaskRayleigh.getSampleInt(x, y) == 1) {
                        result += FLAG_AE_MASK_RAYLEIGH;
                    }
                    if (aemaskAerosol.getSampleInt(x, y) == 1) {
                        result += FLAG_AE_MASK_AEROSOL;
                    }
                    if (land.getSampleBit(x, y, 3)) {
                        result += FLAG_LANDCONS;
                    }
                    if (cloud.getSampleBit(x, y, 0)) {
                        result += FLAG_CLOUD;
                    }
                    if (aemaskRayleigh.getSampleInt(x, y) == 1 && gasCor0.getSampleFloat(x, y) != -1) {
                        result += FLAG_AE_APPLIED_RAYLEIGH;
                    }
                    boolean aotError = aerosol.getSampleBit(x, y, 1);
                    if (aemaskAerosol.getSampleInt(x, y) == 1 && gasCor0.getSampleFloat(x, y) != -1 && !aotError) {
                        result += FLAG_AE_APPLIED_AEROSOL;
                    }
                    if (aerosol.getSampleBit(x, y, 0)) {
                        result += FLAG_ALPHA_OUT_OF_RANGE;
                    }
                    if (aotError) {
                        result += FLAG_AOT_OUT_OF_RANGE;
                    }
                    if (aerosol.getSampleBit(x, y, 3)) {
                        result += FLAG_HIGH_TURBID_WATER;
                    }
                    if (aerosol.getSampleBit(x, y, 4)) {
                        result += FLAG_SUNGLINT;
                    }
                    targetTile.setSample(x, y, result);
                }
                checkForCancellation();
                pm.worked(1);
            }
        } finally {
            pm.done();
        }
    }

    private void correctForRayleigh(Tile targetTile, int bandNumber, ProgressMonitor pm) throws OperatorException {
        Rectangle rectangle = targetTile.getRectangle();
        pm.beginTask("Processing frame...", rectangle.height + 5);
        try {
            Tile gasCor = getSourceTile(
                    gasCorProduct.getBand(GaseousCorrectionOp.RHO_NG_BAND_PREFIX + "_" + bandNumber), rectangle);
            Tile tg = getSourceTile(gasCorProduct.getBand(GaseousCorrectionOp.TG_BAND_PREFIX + "_" + bandNumber),
                                    rectangle);
            Tile aep = getSourceTile(aemaskRayleighProduct.getBand(AdjacencyEffectMaskOp.AE_MASK_RAYLEIGH), rectangle);
            Tile rhoToaR = getSourceTile(rhoToaProduct.getBand("rho_toa_" + bandNumber), rectangle);
            Tile aeRayleigh = null;

            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                    double rhoToa = 0;
                    double gasCorValue = gasCor.getSampleDouble(x, y);
                    if (aep.getSampleInt(x, y) == 1 && gasCorValue != -1) {
                        if (aeRayleigh == null) {
                            aeRayleigh = getSourceTile(aeRayProduct.getBand("rho_aeRay_" + bandNumber), rectangle);
                        }
                        double aeRayleighValue = aeRayleigh.getSampleDouble(x, y);
                        double corrected = gasCorValue - aeRayleighValue;
                        if (corrected != 0) {
                            rhoToa = corrected * tg.getSampleDouble(x, y);
                        }
                    }
                    if (rhoToa == 0) {
                        rhoToa = rhoToaR.getSampleDouble(x, y);
                    }
                    targetTile.setSample(x, y, rhoToa);
                }
                checkForCancellation();
                pm.worked(1);
            }
        } finally {
            pm.done();
        }
    }

    private void correctForRayleighAndAerosol(Tile targetTile, int bandNumber, ProgressMonitor pm) throws
                                                                                                   OperatorException {
        Rectangle rectangle = targetTile.getRectangle();
        pm.beginTask("Processing frame...", rectangle.height + 7);
        try {
            Tile gasCor = getSourceTile(
                    gasCorProduct.getBand(GaseousCorrectionOp.RHO_NG_BAND_PREFIX + "_" + bandNumber), rectangle);
            Tile tg = getSourceTile(gasCorProduct.getBand(GaseousCorrectionOp.TG_BAND_PREFIX + "_" + bandNumber),
                                    rectangle);
            Tile aepRayleigh = getSourceTile(aemaskRayleighProduct.getBand(AdjacencyEffectMaskOp.AE_MASK_RAYLEIGH),
                                             rectangle);
            Tile aepAerosol = getSourceTile(aemaskAerosolProduct.getBand(AdjacencyEffectMaskOp.AE_MASK_AEROSOL),
                                            rectangle);
            Tile rhoToaR = getSourceTile(rhoToaProduct.getBand("rho_toa_" + bandNumber), rectangle);
            Tile aeRayleigh = null;
            Tile aeAerosol = null;

            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                    double gasCorValue = gasCor.getSampleDouble(x, y);
                    int aepRayleighSample = aepRayleigh.getSampleInt(x, y);
                    int aepAerosolSample = aepAerosol.getSampleInt(x, y);
                    double corrected = 0.0;
                    if (aepRayleighSample == 1 && aepAerosolSample == 1 && gasCorValue != -1) {
                        if (aeRayleigh == null) {
                            aeRayleigh = getSourceTile(aeRayProduct.getBand("rho_aeRay_" + bandNumber), rectangle);
                        }
                        if (aeAerosol == null) {
                            aeAerosol = getSourceTile(aeAerosolProduct.getBand("rho_aeAer_" + bandNumber), rectangle);
                        }
                        double aeRayleighValue = aeRayleigh.getSampleDouble(x, y);
                        double aeAerosolValue = aeAerosol.getSampleDouble(x, y);
                        corrected = gasCorValue - aeRayleighValue;
                        corrected -= aeAerosolValue;
                    }

                    double rhoToa = 0;
                    if (corrected != 0.0) {
                        rhoToa = corrected * tg.getSampleDouble(x, y);
                    } else {
                        rhoToa = rhoToaR.getSampleDouble(x, y);
                    }


                    targetTile.setSample(x, y, rhoToa);
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
            super(MerisReflectanceCorrectionOp.class);
        }
    }
}
