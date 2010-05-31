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
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.meris.brr.CloudClassificationOp;
import org.esa.beam.meris.brr.GaseousCorrectionOp;
import org.esa.beam.meris.brr.LandClassificationOp;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;

import java.awt.Rectangle;
import java.awt.image.RenderedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Operator providing an output product with AE corrected MERIS TOA reflectances.
 *
 * @author Marco Zuehlke, Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
@OperatorMetadata(alias = "Meris.IcolCorrectedReflectances",
        version = "1.0",
        internal = true,
        authors = "Marco Zühlke",
        copyright = "(c) 2007 by Brockmann Consult",
        description = "Corrects for the adjacency effect and computes rho TOA.")
public class MerisReflectanceCorrectionOp extends MerisBasisOp {

    private static final int FLAG_AE_MASK_RAYLEIGH = 1;
    private static final int FLAG_AE_MASK_AEROSOL = 2;
    private static final int FLAG_LANDCONS = 4;
    private static final int FLAG_CLOUD = 8;
    private static final int FLAG_AE_APPLIED_RAYLEIGH = 16;
    private static final int FLAG_AE_APPLIED_AEROSOL = 32;
    private static final int FLAG_ALPHA_ERROR = 64;
    private static final int FLAG_AOT_ERROR = 128;
    
    @SourceProduct(alias="l1b")
    private Product l1bProduct;
    @SourceProduct(alias="rhotoa")
    private Product rhoToaProduct;
    @SourceProduct(alias="land")
    private Product landProduct;
    @SourceProduct(alias="cloud")
    private Product cloudProduct;
    @SourceProduct(alias="aemaskRayleigh")
    private Product aemaskRayleighProduct;
    @SourceProduct(alias="aemaskAerosol")
    private Product aemaskAerosolProduct;
    @SourceProduct(alias="gascor")
    private Product gasCorProduct;
    @SourceProduct(alias="ae_ray")
    private Product aeRayProduct;
    @SourceProduct(alias="ae_aerosol", optional=true)
    private Product aeAerosolProduct;
   
    
    @TargetProduct
    private Product targetProduct;
    
    @Parameter(defaultValue="true")
    private boolean exportRhoToa = true;
    @Parameter(defaultValue="true")
    private boolean exportRhoToaRayleigh = true;
    @Parameter(defaultValue="true")
    private boolean exportRhoToaAerosol = true;
    @Parameter(defaultValue="true")
    private boolean exportAeRayleigh = true;
    @Parameter(defaultValue="true")
    private boolean exportAeAerosol = true;
    @Parameter(defaultValue="true")
    private boolean exportAlphaAot = true;

    @Parameter(defaultValue="true")
    private boolean correctForBoth = true;

    private List<Band> rhoToaRayBands;
    private List<Band> rhoToaAerBands;
    private Band l1FlagBand;
    private Band aeFlagBand;
    private Map<Band, Band> copySource;

    @Override
    public void initialize() throws OperatorException {
        String productType = l1bProduct.getProductType();
        int index = productType.indexOf("_1");
        productType = productType.substring(0, index) + "_1N";
        targetProduct = createCompatibleProduct(rhoToaProduct, "reverseRhoToa", productType);
        Band[] allBands = rhoToaProduct.getBands();
        Band[] sourceBands = new Band[15];
        int i = 0;
        for (Band band : allBands) {
            if (band.getName().startsWith("rho_toa")) {
                sourceBands[i] = band;
                i++;
            }
        }
        copySource = new HashMap<Band, Band>();
        if (exportRhoToa) {
            copyBandGroup(rhoToaProduct, "rho_toa");
        }
        if (exportRhoToaRayleigh) {
            rhoToaRayBands = addBandGroup(sourceBands, "rho_toa_AERC");
        }
        if (correctForBoth && exportRhoToaAerosol) {
            rhoToaAerBands = addBandGroup(sourceBands, "rho_toa_AEAC");
        }
        if (exportAeRayleigh) {
            copyBandGroup(aeRayProduct, "rho_aeRay");
        }
        if (aeAerosolProduct != null && exportAeAerosol) {
            copyBandGroup(aeAerosolProduct, "rho_aeAer");
        }
        if (aeAerosolProduct != null && exportAlphaAot) {
            Band copyAlphaBand = ProductUtils.copyBand("alpha", aeAerosolProduct, targetProduct);
            copyAlphaBand.setSourceImage(aeAerosolProduct.getBand("alpha").getSourceImage());
            copySource.put(copyAlphaBand, aeAerosolProduct.getBand("alpha"));
            Band copyAotBand = ProductUtils.copyBand("aot", aeAerosolProduct, targetProduct);
            copyAotBand.setSourceImage(aeAerosolProduct.getBand("aot").getSourceImage());
            copySource.put(copyAotBand, aeAerosolProduct.getBand("aot"));
        }
        aeFlagBand = targetProduct.addBand("ae_flags", ProductData.TYPE_UINT8);
        aeFlagBand.setDescription("Adjacency-Effect flags");
        
        // create and add the flags coding
        FlagCoding flagCoding = createFlagCoding(aeFlagBand.getName());
        targetProduct.addFlagCoding(flagCoding);
        aeFlagBand.setFlagCoding(flagCoding);
        
        ProductUtils.copyFlagBands(l1bProduct, targetProduct);
        l1FlagBand = targetProduct.getBand("l1_flags");
        prepareBandForCopy(l1bProduct.getBand("l1_flags"), l1FlagBand);
        if (l1bProduct.getPreferredTileSize() != null) {
            targetProduct.setPreferredTileSize(l1bProduct.getPreferredTileSize());
        }
    }
    
    private void prepareBandForCopy(Band srcBand, Band targetBand) {
        copySource.put(targetBand, srcBand);

        RenderedImage image = srcBand.getSourceImage();
        if (image != null) {
            targetBand.setSourceImage(image);
        }
    }
    
    private FlagCoding createFlagCoding(String bandName) {
        MetadataAttribute cloudAttr;
        final FlagCoding flagCoding = new FlagCoding(bandName);
        flagCoding.setDescription("Adjacency-Effect - Flag Coding");

        cloudAttr = new MetadataAttribute("ae_mask_rayleigh", ProductData.TYPE_UINT8);
        cloudAttr.getData().setElemInt(FLAG_AE_MASK_RAYLEIGH);
        flagCoding.addAttribute(cloudAttr);

        cloudAttr = new MetadataAttribute("ae_mask_aerosol", ProductData.TYPE_UINT8);
        cloudAttr.getData().setElemInt(FLAG_AE_MASK_AEROSOL);
        flagCoding.addAttribute(cloudAttr);

        cloudAttr = new MetadataAttribute("landcons", ProductData.TYPE_UINT8);
        cloudAttr.getData().setElemInt(FLAG_LANDCONS);
        flagCoding.addAttribute(cloudAttr);

        cloudAttr = new MetadataAttribute("cloud", ProductData.TYPE_UINT8);
        cloudAttr.getData().setElemInt(FLAG_CLOUD);
        flagCoding.addAttribute(cloudAttr);

        cloudAttr = new MetadataAttribute("ae_applied_rayleigh", ProductData.TYPE_UINT8);
        cloudAttr.getData().setElemInt(FLAG_AE_APPLIED_RAYLEIGH);
        flagCoding.addAttribute(cloudAttr);

        cloudAttr = new MetadataAttribute("ae_applied_aerosol", ProductData.TYPE_UINT8);
        cloudAttr.getData().setElemInt(FLAG_AE_APPLIED_AEROSOL);
        flagCoding.addAttribute(cloudAttr);

        cloudAttr = new MetadataAttribute("alpha_out_of_range", ProductData.TYPE_UINT8);
        cloudAttr.getData().setElemInt(FLAG_ALPHA_ERROR);
        flagCoding.addAttribute(cloudAttr);
        
        cloudAttr = new MetadataAttribute("aot_out_of_range", ProductData.TYPE_UINT8);
        cloudAttr.getData().setElemInt(FLAG_AOT_ERROR);
        flagCoding.addAttribute(cloudAttr);
        
        return flagCoding;
    }

    private List<Band> copyBandGroup(Product sourceProduct, String bandPrefix) {
        List<Band> bandList = new ArrayList<Band>(15);
        Band[] sourceBands = sourceProduct.getBands();
        for (Band srcBand : sourceBands) {
            if (srcBand.getName().startsWith(bandPrefix)) {
                int bandNo = srcBand.getSpectralBandIndex()+1;
                Band targetBand = targetProduct.addBand(bandPrefix + "_" + bandNo, ProductData.TYPE_FLOAT32);
                
//                ProductUtils.copySpectralAttributes(srcBand, targetBand);
                ProductUtils.copySpectralBandProperties(srcBand, targetBand);
                targetBand.setNoDataValueUsed(srcBand.isNoDataValueUsed());
                targetBand.setNoDataValue(srcBand.getNoDataValue());
                bandList.add(targetBand);

                prepareBandForCopy(srcBand, targetBand);
            }
        }
        return bandList;
    }
    
    private List<Band> addBandGroup(Band[] sourceBands, String bandPrefix) {
        List<Band> bandList = new ArrayList<Band>(sourceBands.length);
        for (Band srcBand : sourceBands) {
            int bandNo = srcBand.getSpectralBandIndex()+1;
            Band targetBand = targetProduct.addBand(bandPrefix + "_" + bandNo, ProductData.TYPE_FLOAT32);
//            ProductUtils.copySpectralAttributes(srcBand, targetBand);
            ProductUtils.copySpectralBandProperties(srcBand, targetBand);
            targetBand.setNoDataValueUsed(srcBand.isNoDataValueUsed());
            targetBand.setNoDataValue(srcBand.getNoDataValue());
            if (bandNo == 11 || bandNo== 14 || bandNo== 15) {
                prepareBandForCopy(srcBand, targetBand);
            } else {
                bandList.add(targetBand);
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
        } else if (copySource.containsKey(band)) {
            Rectangle rectangle = targetTile.getRectangle();
            Tile srcTile = getSourceTile(copySource.get(band), rectangle, pm);
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                    targetTile.setSample(x, y, srcTile.getSampleDouble(x, y));
                }
            }
        } else if (band == aeFlagBand) {
            computeAeFlags(targetTile, pm);
        }
    }

    private void computeAeFlags(Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Rectangle rectangle = targetTile.getRectangle();
        Tile land = getSourceTile(landProduct.getBand(LandClassificationOp.LAND_FLAGS), rectangle, pm);
        Tile cloud = getSourceTile(cloudProduct.getBand(CloudClassificationOp.CLOUD_FLAGS), rectangle, pm);
        Tile aemaskRayleigh = getSourceTile(aemaskRayleighProduct.getBand(MerisAeMaskOp.AE_MASK_RAYLEIGH), rectangle, pm);
        Tile aemaskAerosol = getSourceTile(aemaskAerosolProduct.getBand(MerisAeMaskOp.AE_MASK_AEROSOL), rectangle, pm);
        Tile gasCor0 = getSourceTile(gasCorProduct.getBand(GaseousCorrectionOp.RHO_NG_BAND_PREFIX + "_1"), rectangle, pm);
        Tile aerosol = null;
        if (aeAerosolProduct != null) {
            aerosol = getSourceTile(aeAerosolProduct.getBand(MerisAeAerosolOp.AOT_FLAGS), rectangle, pm);
        }
        for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
            for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
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
                    result += FLAG_LANDCONS;
                }
                boolean aotError = aerosol.getSampleBit(x, y, 1);
                if (aemaskRayleigh.getSampleInt(x, y) == 1 && gasCor0.getSampleFloat(x, y) != -1) {
                    result += FLAG_AE_APPLIED_RAYLEIGH;
                }
                if (aemaskAerosol.getSampleInt(x, y) == 1 && gasCor0.getSampleFloat(x, y) != -1 && !aotError) {
                    result += FLAG_AE_APPLIED_AEROSOL;
                }
                if (aerosol.getSampleBit(x, y, 0)) {
                    result += FLAG_ALPHA_ERROR;
                }
                if (aotError) {
                    result += FLAG_AOT_ERROR;
                }
                targetTile.setSample(x, y, result);
            }
        }
    }
    
    private void correctForRayleigh(Tile targetTile, int bandNumber, ProgressMonitor pm) throws OperatorException {
        Rectangle rectangle = targetTile.getRectangle();
        Tile gasCor = getSourceTile(gasCorProduct.getBand(GaseousCorrectionOp.RHO_NG_BAND_PREFIX + "_" + bandNumber), rectangle, pm);
        Tile tg = getSourceTile(gasCorProduct.getBand(GaseousCorrectionOp.TG_BAND_PREFIX + "_" + bandNumber), rectangle, pm);
        Tile aep = getSourceTile(aemaskRayleighProduct.getBand(MerisAeMaskOp.AE_MASK_RAYLEIGH), rectangle, pm);
        Tile rhoToaR = getSourceTile(rhoToaProduct.getBand("rho_toa_" +  bandNumber), rectangle, pm);
        Tile aeRayleigh = null;

        for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
            for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                double rhoToa = 0;
                double gasCorValue = gasCor.getSampleDouble(x, y);
                if (aep.getSampleInt(x, y) == 1 && gasCorValue != -1) {
                    if (aeRayleigh == null) {
                        aeRayleigh = getSourceTile(aeRayProduct.getBand("rho_aeRay_"+bandNumber), rectangle, pm);                
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
        }
    }

    private void correctForRayleighAndAerosol(Tile targetTile, int bandNumber, ProgressMonitor pm) throws OperatorException {
        Rectangle rectangle = targetTile.getRectangle();
        Tile gasCor = getSourceTile(gasCorProduct.getBand(GaseousCorrectionOp.RHO_NG_BAND_PREFIX + "_" + bandNumber), rectangle, pm);
        Tile tg = getSourceTile(gasCorProduct.getBand(GaseousCorrectionOp.TG_BAND_PREFIX + "_" + bandNumber), rectangle, pm);
        Tile aepRayleigh = getSourceTile(aemaskRayleighProduct.getBand(MerisAeMaskOp.AE_MASK_RAYLEIGH), rectangle, pm);
        Tile aepAerosol = getSourceTile(aemaskAerosolProduct.getBand(MerisAeMaskOp.AE_MASK_AEROSOL), rectangle, pm);
        Tile rhoToaR = getSourceTile(rhoToaProduct.getBand("rho_toa_" +  bandNumber), rectangle, pm);
        Tile aeRayleigh = null;
        Tile aeAerosol = null;

        for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
            for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                double rhoToa = 0;
                double gasCorValue = gasCor.getSampleDouble(x, y);
                double corrected = 0.0;
                if (aepRayleigh.getSampleInt(x, y) == 1 && gasCorValue != -1) {
                    if (aeRayleigh == null) {
                        aeRayleigh = getSourceTile(aeRayProduct.getBand("rho_aeRay_"+bandNumber), rectangle, pm);
                    }
                    double aeRayleighValue = aeRayleigh.getSampleDouble(x, y);
                    corrected = gasCorValue - aeRayleighValue;
                }
                if (aepAerosol.getSampleInt(x, y) == 1) {
                    if (aeAerosol == null) {
                        aeAerosol = getSourceTile(aeAerosolProduct.getBand("rho_aeAer_"+bandNumber), rectangle, pm);
                    }
                    double aeAerosolValue = aeAerosol.getSampleDouble(x, y);
                    corrected -= aeAerosolValue;
                }

                if (corrected != 0.0) {
                    rhoToa = corrected * tg.getSampleDouble(x, y);
                }


                if (rhoToa == 0) {
                    rhoToa = rhoToaR.getSampleDouble(x, y);
                }
                targetTile.setSample(x, y, rhoToa);
            }
        }
    }

    
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(MerisReflectanceCorrectionOp.class);
        }
    }
}
