package org.esa.beam.meris.icol.tm;

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
import org.esa.beam.meris.brr.GaseousCorrectionOp;
import org.esa.beam.meris.icol.meris.MerisAeAerosolOp;
import org.esa.beam.meris.icol.meris.MerisAeMaskOp;
import org.esa.beam.meris.icol.utils.IcolUtils;
import org.esa.beam.util.ProductUtils;

import java.awt.Rectangle;
import java.awt.image.RenderedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
@OperatorMetadata(alias = "Landsat.ReflectanceCorrection",
        version = "1.0",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2009 by Brockmann Consult",
        description = "Overall AE correction for the Landsat TM reflectances.")
public class TmReflectanceCorrectionOp extends TmBasisOp {

    private static final int FLAG_AE_MASK_RAYLEIGH = 1;
    private static final int FLAG_AE_MASK_AEROSOL = 2;
    private static final int FLAG_LANDCONS = 4;
    private static final int FLAG_CLOUD = 8;
    private static final int FLAG_AE_APPLIED_RAYLEIGH = 16;
    private static final int FLAG_AE_APPLIED_AEROSOL = 32;
    private static final int FLAG_ALPHA_ERROR = 64;
    private static final int FLAG_AOT_ERROR = 128;

    @SourceProduct(alias="refl")
    private Product sourceProduct;
    @SourceProduct(alias="land")
    private Product landProduct;
    @SourceProduct(alias="cloud")
    private Product cloudProduct;
    @SourceProduct(alias="gascor")
    private Product gasCorProduct;
    @SourceProduct(alias="ae_ray")
    private Product aeRayProduct;
    @SourceProduct(alias="ae_aerosol", optional=true)
    private Product aeAerosolProduct;
    @SourceProduct(alias="aemaskRayleigh")
    private Product aemaskRayleighProduct;
    @SourceProduct(alias="aemaskAerosol")
    private Product aemaskAerosolProduct;

    @TargetProduct
    private Product targetProduct;

    @Parameter(defaultValue = "true")
    private boolean exportRhoToa = true;
    @Parameter(defaultValue = "true")
    private boolean exportRhoToaRayleigh = true;
    @Parameter(defaultValue = "true")
    private boolean exportRhoToaAerosol = true;
    @Parameter(defaultValue = "true")
    private boolean exportAeRayleigh = true;
    @Parameter(defaultValue = "true")
    private boolean exportAeAerosol = true;
    @Parameter(defaultValue = "true")
    private boolean exportAlphaAot = true;

    @Parameter(defaultValue="true")
    private boolean correctForBoth = true;

    private List<Band> rhoToaBands;
    private List<Band> rhoToaRayBands;
    private List<Band> rhoToaAerBands;
    private Band l1FlagBand;
    private Band aeFlagBand;
    private Map<Band, Band> copySource;

    @Override
    public void initialize() throws OperatorException {

        targetProduct = createCompatibleProduct(sourceProduct, sourceProduct.getName() + "_ICOL", sourceProduct.getProductType());
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());

        ProductUtils.copyFlagBands(sourceProduct, targetProduct);
        if (sourceProduct.getPreferredTileSize() != null) {
            targetProduct.setPreferredTileSize(sourceProduct.getPreferredTileSize());
        }
        Band[] allBands = sourceProduct.getBands();
        Band[] sourceBands = new Band[TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS];
        int i = 0;
        for (Band band : allBands) {
            if (band.getName().startsWith(TmConstants.LANDSAT5_REFLECTANCE_BAND_PREFIX)) {
                sourceBands[i] = band;
                i++;
            }
        }

        copySource = new HashMap<Band, Band>();
        if (exportRhoToa) {
            copyBandGroup(sourceProduct, TmConstants.LANDSAT5_REFLECTANCE_BAND_PREFIX);
        }
        if (exportRhoToaRayleigh) {
            rhoToaRayBands = addBandGroup(sourceBands, TmConstants.LANDSAT5_REFLECTANCE_BAND_PREFIX + "_AERC");
        }
        if (correctForBoth && exportRhoToaAerosol) {
            rhoToaAerBands = addBandGroup(sourceBands, TmConstants.LANDSAT5_REFLECTANCE_BAND_PREFIX + "_AEAC");
        }
        if (exportAeRayleigh) {
            copyBandGroup(aeRayProduct, "rho_aeRay");
        }
        if (exportAeAerosol) {
            copyBandGroup(aeAerosolProduct, "rho_aeAer");
        }
        if (exportAlphaAot) {
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
        List<Band> bandList = new ArrayList<Band>(TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS);
        Band[] sourceBands = sourceProduct.getBands();
        for (Band srcBand : sourceBands) {
            if (srcBand.getName().startsWith(bandPrefix)) {
                int bandNo = srcBand.getSpectralBandIndex() + 1;
                if (!IcolUtils.isIndexToSkip(bandNo-1,
                                             new int[]{TmConstants.LANDSAT5_RADIANCE_6_BAND_INDEX})) {
                    Band targetBand = targetProduct.addBand(bandPrefix + "_" + bandNo, ProductData.TYPE_FLOAT32);

//                    ProductUtils.copySpectralAttributes(srcBand, targetBand);
                    ProductUtils.copySpectralBandProperties(srcBand, targetBand);
                    targetBand.setNoDataValueUsed(srcBand.isNoDataValueUsed());
                    targetBand.setNoDataValue(srcBand.getNoDataValue());
                    bandList.add(targetBand);

                    prepareBandForCopy(srcBand, targetBand);
                }
            }
        }
        return bandList;
    }

    private List<Band> addBandGroup(Band[] sourceBands, String bandPrefix) {
        List<Band> bandList = new ArrayList<Band>(sourceBands.length);
        for (Band srcBand : sourceBands) {
            int bandNo = srcBand.getSpectralBandIndex() + 1;
            if (!IcolUtils.isIndexToSkip(bandNo-1, new int[]{TmConstants.LANDSAT5_RADIANCE_6_BAND_INDEX})) {
                Band targetBand = targetProduct.addBand(bandPrefix + "_" + bandNo, ProductData.TYPE_FLOAT32);
//                ProductUtils.copySpectralAttributes(srcBand, targetBand);
                ProductUtils.copySpectralBandProperties(srcBand, targetBand);
                targetBand.setNoDataValueUsed(srcBand.isNoDataValueUsed());
                targetBand.setNoDataValue(srcBand.getNoDataValue());
                bandList.add(targetBand);
            }
        }
        return bandList;
    }

    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        final int bandNumber = band.getSpectralBandIndex() + 1;

        // AE  rayleigh and aerosol correction
        if (!band.isFlagBand() && !IcolUtils.isIndexToSkip(bandNumber-1, new int[]{TmConstants.LANDSAT5_RADIANCE_6_BAND_INDEX})) {
//         correctForRayleigh(targetTile, bandId+1, pm);
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
            }
        } else if (band == aeFlagBand) {
            computeAeFlags(targetTile, pm);
        }
    }

    private void computeAeFlags(Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Rectangle rectangle = targetTile.getRectangle();
        Tile land = getSourceTile(landProduct.getBand(TmLandClassificationOp.LAND_FLAGS), rectangle, pm);
        Tile cloud = getSourceTile(cloudProduct.getBand(TmCloudClassificationOp.CLOUD_FLAGS), rectangle, pm);
        Tile aemaskRayleigh = getSourceTile(aemaskRayleighProduct.getBand(MerisAeMaskOp.AE_MASK_RAYLEIGH), rectangle, pm);
        Tile aemaskAerosol = getSourceTile(aemaskAerosolProduct.getBand(MerisAeMaskOp.AE_MASK_AEROSOL), rectangle, pm);
        Tile gasCor0 = getSourceTile(gasCorProduct.getBand(GaseousCorrectionOp.RHO_NG_BAND_PREFIX + "_1"), rectangle, pm);
        Tile aerosol = getSourceTile(aeAerosolProduct.getBand(MerisAeAerosolOp.AOT_FLAGS), rectangle, pm);
        for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
            for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                int result = 0;
                if (aemaskRayleigh.getSampleInt(x, y) == 1) {
                    result += FLAG_AE_MASK_RAYLEIGH;
                }
                if (aemaskAerosol.getSampleInt(x, y) == 1) {
                    result += FLAG_AE_MASK_AEROSOL;
                }
                if (land.getSampleBit(x, y, 0)) {
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
        Tile rhoToaR = getSourceTile(sourceProduct.getBand(TmConstants.LANDSAT5_REFLECTANCE_BAND_PREFIX + "_" + bandNumber), rectangle, pm);
        Tile aeRayleigh = null;

        for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
            for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                double rhoToa = 0;
                double gasCorValue = gasCor.getSampleDouble(x, y);
                if (aep.getSampleInt(x, y) == 1 && gasCorValue != -1) {
                    if (aeRayleigh == null) {
                        aeRayleigh = getSourceTile(aeRayProduct.getBand("rho_aeRay_" + bandNumber), rectangle, pm);
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
        Tile aepAerosol= getSourceTile(aemaskAerosolProduct.getBand(MerisAeMaskOp.AE_MASK_AEROSOL), rectangle, pm);
        Tile rhoToaR = getSourceTile(sourceProduct.getBand(TmConstants.LANDSAT5_REFLECTANCE_BAND_PREFIX + "_" + bandNumber), rectangle, pm);
        Tile aeRayleigh = getSourceTile(aeRayProduct.getBand("rho_aeRay_" + bandNumber), rectangle, pm);;
        Tile aeAerosol = getSourceTile(aeAerosolProduct.getBand("rho_aeAer_" + bandNumber), rectangle, pm);;

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
            super(TmReflectanceCorrectionOp.class);
        }
    }
}
