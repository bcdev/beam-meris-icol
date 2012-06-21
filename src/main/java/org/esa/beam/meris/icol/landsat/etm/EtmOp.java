package org.esa.beam.meris.icol.landsat.etm;

import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.meris.icol.AeArea;
import org.esa.beam.meris.icol.FresnelCoefficientOp;
import org.esa.beam.meris.icol.IcolConstants;
import org.esa.beam.meris.icol.Instrument;
import org.esa.beam.meris.icol.common.*;
import org.esa.beam.meris.icol.landsat.common.*;
import org.esa.beam.meris.icol.landsat.tm.TmBasisOp;
import org.esa.beam.meris.icol.meris.CloudLandMaskOp;
import org.esa.beam.meris.icol.utils.DebugUtils;
import org.esa.beam.meris.icol.utils.LandsatUtils;
import org.esa.beam.meris.icol.utils.OperatorUtils;
import org.esa.beam.util.ProductUtils;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * Class providing the correction of the adjacency effect for LANDSAT 7 ETM+ data.
 *
 * @author Olaf Danne
 */
@OperatorMetadata(alias = "icol.EnhancedThematicMapper",
                  version = "1.1",
                  authors = "Marco Zuehlke, Olaf Danne",
                  copyright = "(c) 2007-2009 by Brockmann Consult",
                  description = "Performs a correction of the adjacency effect for LANDSAT ETM+ L1b data.")
public class EtmOp extends TmBasisOp {

    @SourceProduct(description = "The source product.")
    Product sourceProduct;

    @TargetProduct(description = "The target product.")
    Product targetProduct;

    @Parameter(interval = "[440.0, 2225.0]", defaultValue = "550.0", description = "The Aerosol optical thickness reference wavelength.")
    private double userAerosolReferenceWavelength;
    @Parameter(interval = "[-2.1, -0.4]", defaultValue = "-1", description = "The Angstrom coefficient.")
    private double userAlpha;
    @Parameter(interval = "[0, 1.5]", defaultValue = "0.2", description = "The aerosol optical thickness at reference wavelength.")
    private double userAot;
    @Parameter(interval = "[300.0, 1060.0]", defaultValue = "1013.25", description = "The surface pressure to be used by AE correction algorithm.")
    private double landsatUserPSurf;
    @Parameter(interval = "[200.0, 320.0]", defaultValue = "300.0", description = "The TM band 6 temperature to be used by AE correction algorithm.")
    private double landsatUserTm60;
    @Parameter(interval = "[0.01, 1.0]", defaultValue = "0.32", description = "The ozone content to be used by AE correction algorithm.")
    private double landsatUserOzoneContent;
    @Parameter(defaultValue = "1200", valueSet = {"300", "1200"}, description = "The AE correction grid resolution to be used by AE correction algorithm.")
    private int landsatTargetResolution;
    @Parameter(defaultValue = "0", valueSet = {"0", "1", "2"}, description =
            "The output product: 0 = the source bands will only be downscaled to AE correction grid resolution; 1 = compute an AE corrected product; 2 = upscale an AE corrected product to original resolution; 3 = only the cloud and land flag bands will be computed; .")
    private int landsatOutputProductType;

    @Parameter(defaultValue = "true")
    private boolean landsatCloudFlagApplyBrightnessFilter = true;
    @Parameter(defaultValue = "true")
    private boolean landsatCloudFlagApplyNdviFilter = true;
    @Parameter(defaultValue = "true")
    private boolean landsatCloudFlagApplyNdsiFilter = true;
    @Parameter(defaultValue = "true")
    private boolean landsatCloudFlagApplyTemperatureFilter = true;
    @Parameter(interval = "[0.0, 1.0]", defaultValue = "0.3", description = "The cloud brightness threshold.")
    private double cloudBrightnessThreshold;
    @Parameter(interval = "[0.0, 1.0]", defaultValue = "0.2", description = "The cloud NDVI threshold.")
    private double cloudNdviThreshold;
    @Parameter(interval = "[0.0, 10.0]", defaultValue = "3.0", description = "The cloud NDSI threshold.")
    private double cloudNdsiThreshold;
    @Parameter(interval = "[200.0, 320.0]", defaultValue = "300.0", description = "The cloud TM band 6 temperature threshold.")
    private double cloudTM6Threshold;

    @Parameter(defaultValue = "true")
    private boolean landsatLandFlagApplyNdviFilter = true;
    @Parameter(defaultValue = "true")
    private boolean landsatLandFlagApplyTemperatureFilter = true;
    @Parameter(interval = "[0.0, 1.0]", defaultValue = "0.2", description = "The land NDVIthreshold.")
    private double landNdviThreshold;
    @Parameter(interval = "[200.0, 320.0]", defaultValue = "300.0", description = "The land TM band 6 temperature threshold.")
    private double landTM6Threshold;
    @Parameter(defaultValue = LandsatConstants.LAND_FLAGS_SUMMER, valueSet = {LandsatConstants.LAND_FLAGS_SUMMER,
            LandsatConstants.LAND_FLAGS_WINTER}, description = "The summer/winter option for TM band 6 temperature test.")
    private String landsatSeason = LandsatConstants.LAND_FLAGS_SUMMER;

    @Parameter
    private String landsatOutputProductsDir;

    // general
    private static final int productType = 0;

    private boolean reshapedConvolution = true;     // currently no user option

//    @Parameter(defaultValue = "false", description = "If set to 'true', the convolution shall be computed on GPU device if available.")
    private boolean openclConvolution = false;      // currently not used as parameter in TM
//    @Parameter(defaultValue = "64")
    // currently no user option
    private int tileSize = 64;
    @Parameter(defaultValue = "EVERYWHERE", valueSet = {"EVERYWHERE", "COASTAL_ZONE", "COASTAL_OCEAN", "OCEAN"},
               description = "The area where the AE correction will be applied.")
    private AeArea aeArea = AeArea.EVERYWHERE;
//    @Parameter(defaultValue = "false", description = "If set to 'true', the aerosol and fresnel correction term are exported as bands.")
    // currently no user option here
    private boolean exportSeparateDebugBands = false;

    private String landsatStartTime;
    private String landsatStopTime;

    @Override
    public void initialize() throws OperatorException {
//        JAI.getDefaultInstance().getTileScheduler().setParallelism(1); // todo: only for debugging purpose!!
        setStartStopTime();

        if (landsatOutputProductType == LandsatConstants.OUTPUT_PRODUCT_TYPE_DOWNSCALE) {
            targetProduct = createDownscaledProduct();
            return;
        }

        Product downscaledSourceProduct = null;

        final File downscaledSourceProductFile = new File(landsatOutputProductsDir + File.separator +
                                                                  "L1N_" + sourceProduct.getName() +
                                                                  LandsatConstants.LANDSAT_DOWNSCALED_PRODUCT_SUFFIX + ".dim");

        try {
            downscaledSourceProduct = ProductIO.readProduct(downscaledSourceProductFile.getAbsolutePath());
        } catch (IOException e) {
            throw new OperatorException("Cannot read downscaled source product for AE correction: " + e.getMessage());
        }

        if (landsatOutputProductType == LandsatConstants.OUTPUT_PRODUCT_TYPE_UPSCALE) {
            // check if both original and AE corrected product exists on AE grid, in parent directory...

            final File aeCorrProductFile = new File(landsatOutputProductsDir + File.separator +
                                                            "L1N_" + sourceProduct.getName() +
                                                            LandsatConstants.LANDSAT_DOWNSCALED_CORRECTED_PRODUCT_SUFFIX + ".dim");
            Product aeCorrProduct;
            try {
                aeCorrProduct = ProductIO.readProduct(aeCorrProductFile.getAbsolutePath());
            } catch (IOException e) {
                throw new OperatorException("Cannot read AE corrected product for upscaling: " + e.getMessage());
            }
            Product upscaledProduct = createUpscaledToOriginalProduct(downscaledSourceProduct, aeCorrProduct);
            targetProduct = upscaledProduct;
            return;
        }

        // for the remaining option, we now have to go for the full AE correction chain...

//        if (landsatOutputProductType == LandsatConstants.OUTPUT_PRODUCT_TYPE_AECORR) {
//            compute downscaled product (ATBD D4, section 5.3.1) if not already done...
//            downscaledSourceProduct = sourceProduct;
//        }

        // compute conversion to reflectance and temperature (ATBD D4, section 5.3.2)
        Product conversionProduct = createConversionProduct(downscaledSourceProduct);

        // compute gaseous transmittance (ATBD D4, section 5.3.3)
        Product gaseousTransmittanceProduct = createGaseousTransmittanceProduct(downscaledSourceProduct);

        // now we need to call the operator sequence as for MERIS, but adjusted to Landsat if needed...

        // Cloud mask
        // MERIS: Product cloudProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(CloudClassificationOp.class), ...
        // Landsat: provide new CloudClassificationOp (cloud mask product as described in ATBD section 5.4.1)
        Product cloudProduct = createCloudProduct(conversionProduct);

        // Cloud Top Pressure
        // MERIS: Product ctpProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(MerisCloudTopPressureOp.class),...
        // Landsat: provide new CloudTopPressureOp (CTP product as described in ATBD section 5.4.4)
        Product ctpProduct = createCtpProduct(conversionProduct, cloudProduct);

        // gas product
        // MERIS: Product gasProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(GaseousCorrectionOp.class), ...
        // Landsat: provide adjusted TmGaseousCorrectionOp (gaseous transmittance as from ATBD section 5.3.3)
        Product gasProduct = createGasProduct(conversionProduct, gaseousTransmittanceProduct);

        // Land classification:
        // MERIS: Product landProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(LandClassificationOp.class), ...
        // Landsat: provide new LandClassificationOp (land mask product as described in ATBD section 5.4.1)
        Product landProduct = createLandProduct(conversionProduct);
        Product cloudLandMaskProduct = createCloudLandMaskProduct(cloudProduct, landProduct);

        FlagCoding landFlagCoding = LandClassificationOp.createFlagCoding();
        FlagCoding cloudFlagCoding = CloudClassificationOp.createFlagCoding();

        // Fresnel product:
        // MERIS: Product fresnelProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(FresnelCoefficientOp.class), ...
        // Landsat: provide adjusted LandsatFresnelCoefficientOp
        Product fresnelProduct = createFresnelProduct(conversionProduct, gasProduct, landProduct);

        // Rayleigh correction:
        // MERIS: Product rayleighProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(RayleighCorrectionOp.class),
        // Landsat: provide adjusted TmRayleighCorrectionOp
        Product rayleighProduct = createRayleighProduct(downscaledSourceProduct, conversionProduct, cloudProduct, ctpProduct, landProduct, fresnelProduct);

        // AE mask:
        // --> same operator as for MERIS
        Product aemaskRayleighProduct = createAeMaskRayleighProduct(conversionProduct, landProduct);
        Product aemaskAerosolProduct = createAeMaskAerosolProduct(conversionProduct, landProduct);

        // Coast distance:
        Product coastDistanceProduct = createCoastDistanceProduct(conversionProduct, landProduct);

        // Cloud distance:
        Product cloudDistanceProduct = createCloudDistanceProduct(conversionProduct, cloudProduct);

        // zMax:
        // --> same operator as for MERIS, provide necessary TPGs
        Product zmaxProduct = createZmaxProduct(conversionProduct, aemaskRayleighProduct, coastDistanceProduct);

        // zMaxCloud:
        // --> same operator as for MERIS, provide necessary TPGs
        Product zmaxCloudProduct = createZmaxCloudProduct(conversionProduct, aemaskRayleighProduct, cloudDistanceProduct);


        // AE Rayleigh:
        // --> same operator as for MERIS, distinguish Meris/Landsat case
        Product aeRayProduct = createAeRayleighProduct(conversionProduct, cloudProduct, gasProduct, landProduct, cloudLandMaskProduct, rayleighProduct, aemaskRayleighProduct, zmaxProduct, zmaxCloudProduct);

        // AE Aerosol:
        // --> same operator as for MERIS, distinguish Meris/Landsat case
        Product aeAerProduct = createAeAerosolProduct(conversionProduct, cloudProduct, ctpProduct, landProduct, aemaskAerosolProduct, zmaxProduct, zmaxCloudProduct, aeRayProduct);

        // Reverse radiance:
        // MERIS: correctionProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(MerisRadianceCorrectionOp.class), ...
        // Landsat: provide adjusted TmReflectanceCorrectionOp
        Product correctionProduct = createRadianceCorrectionProduct(conversionProduct, gasProduct, aemaskRayleighProduct, aemaskAerosolProduct, aeRayProduct, aeAerProduct);

        // output:
        correctionProduct.getFlagCodingGroup().add(cloudFlagCoding);
        DebugUtils.addSingleDebugFlagBand(correctionProduct, cloudProduct, cloudFlagCoding, CloudClassificationOp.CLOUD_FLAGS);
        correctionProduct.getFlagCodingGroup().add(landFlagCoding);
        DebugUtils.addSingleDebugFlagBand(correctionProduct, landProduct, landFlagCoding, LandClassificationOp.LAND_FLAGS);

        // now we have the final product on the AE correction grid
        targetProduct = correctionProduct;
        ProductUtils.copyMetadata(sourceProduct, targetProduct);
    }

    private Product createRadianceCorrectionProduct(Product conversionProduct, Product gasProduct, Product aemaskRayleighProduct, Product aemaskAerosolProduct, Product aeRayProduct, Product aeAerProduct) {
        Map<String, Product> radianceCorrectionInput = new HashMap<String, Product>(6);
        radianceCorrectionInput.put("refl", conversionProduct);
        radianceCorrectionInput.put("gascor", gasProduct);
        radianceCorrectionInput.put("ae_ray", aeRayProduct);
        radianceCorrectionInput.put("ae_aerosol", aeAerProduct);
        radianceCorrectionInput.put("aemaskRayleigh", aemaskRayleighProduct);
        radianceCorrectionInput.put("aemaskAerosol", aemaskAerosolProduct);
        Map<String, Object> radianceCorrectionParams = new HashMap<String, Object>(1);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(EtmRadianceCorrectionOp.class), radianceCorrectionParams, radianceCorrectionInput);
    }

    private Product createAeAerosolProduct(Product conversionProduct, Product cloudProduct, Product ctpProduct, Product landProduct, Product aemaskAerosolProduct, Product zmaxProduct, Product zmaxCloudProduct, Product aeRayProduct) {
        Map<String, Product> aeAerInput = new HashMap<String, Product>(9);
        aeAerInput.put("l1b", conversionProduct);
        aeAerInput.put("land", landProduct);
        aeAerInput.put("aemask", aemaskAerosolProduct);
        aeAerInput.put("zmax", zmaxProduct);
        aeAerInput.put("ae_ray", aeRayProduct);
        aeAerInput.put("cloud", cloudProduct);
        aeAerInput.put("ctp", ctpProduct);
        aeAerInput.put("zmaxCloud", zmaxCloudProduct);
        Map<String, Object> aeAerosolParams = new HashMap<String, Object>(9);
        if (System.getProperty("additionalOutputBands") != null && System.getProperty("additionalOutputBands").equals("RS"))
            exportSeparateDebugBands = true;
        aeAerosolParams.put("exportSeparateDebugBands", exportSeparateDebugBands);
        aeAerosolParams.put("icolAerosolForWater", true);
        aeAerosolParams.put("userAerosolReferenceWavelength", userAerosolReferenceWavelength);
        aeAerosolParams.put("userAlpha", userAlpha);
        aeAerosolParams.put("userAot", userAot);
        aeAerosolParams.put("userPSurf", landsatUserPSurf);
        aeAerosolParams.put("reshapedConvolution", reshapedConvolution);
        aeAerosolParams.put("landExpression", "land_classif_flags.F_LANDCONS");
        aeAerosolParams.put("instrument", Instrument.ETM7);
        aeAerosolParams.put("numSpectralBands", LandsatConstants.LANDSAT7_NUM_SPECTRAL_BANDS);
        aeAerosolParams.put("effectiveWavelenghts", LandsatConstants.LANDSAT7_SPECTRAL_BAND_EFFECTIVE_WAVELENGTHS);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(AeAerosolOp.class), aeAerosolParams, aeAerInput);
    }

    private Product createAeRayleighProduct(Product conversionProduct, Product cloudProduct, Product gasProduct, Product landProduct, Product cloudLandMaskProduct, Product rayleighProduct, Product aemaskRayleighProduct, Product zmaxProduct, Product zmaxCloudProduct) {
        Map<String, Product> aeRayInput = new HashMap<String, Product>(8);
        aeRayInput.put("l1b", conversionProduct);
        aeRayInput.put("land", landProduct);
        aeRayInput.put("aemask", aemaskRayleighProduct);
        aeRayInput.put("ray1b", rayleighProduct);
        aeRayInput.put("rhoNg", gasProduct);
        aeRayInput.put("cloud", cloudProduct);
        aeRayInput.put("cloudLandMask", cloudLandMaskProduct);
        aeRayInput.put("zmax", zmaxProduct);
        aeRayInput.put("zmaxCloud", zmaxCloudProduct);
        Map<String, Object> aeRayParams = new HashMap<String, Object>(5);
        aeRayParams.put("landExpression", "land_classif_flags.F_LANDCONS");
        if (System.getProperty("additionalOutputBands") != null && System.getProperty("additionalOutputBands").equals("RS")) {
            exportSeparateDebugBands = true;
        }
        aeRayParams.put("exportSeparateDebugBands", exportSeparateDebugBands);
        aeRayParams.put("reshapedConvolution", reshapedConvolution);
        aeRayParams.put("instrument", Instrument.ETM7);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(AdjacencyEffectRayleighOp.class), aeRayParams, aeRayInput);
    }

    private Product createZmaxCloudProduct(Product conversionProduct, Product aemaskRayleighProduct, Product cloudDistanceProduct) {
        Map<String, Product> zmaxCloudInput = new HashMap<String, Product>(5);
        zmaxCloudInput.put("source", conversionProduct);
        zmaxCloudInput.put("ae_mask", aemaskRayleighProduct);
        zmaxCloudInput.put("distance", cloudDistanceProduct);
        Map<String, Object> zmaxCloudParameters = new HashMap<String, Object>();
        zmaxCloudParameters.put("aeMaskExpression", AdjacencyEffectMaskOp.AE_MASK_RAYLEIGH + ".aep");
        zmaxCloudParameters.put("distanceBandName", CloudDistanceOp.CLOUD_DISTANCE);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(ZmaxOp.class), zmaxCloudParameters, zmaxCloudInput);
    }

    private Product createZmaxProduct(Product conversionProduct, Product aemaskRayleighProduct, Product coastDistanceProduct) {
        Map<String, Product> zmaxInput = new HashMap<String, Product>(4);
        zmaxInput.put("source", conversionProduct);
        zmaxInput.put("distance", coastDistanceProduct);
        zmaxInput.put("ae_mask", aemaskRayleighProduct);
        Map<String, Object> zmaxParameters = new HashMap<String, Object>(2);
        String aeMaskExpression = AdjacencyEffectMaskOp.AE_MASK_RAYLEIGH + ".aep";
        if (aeArea.correctOverLand()) {
            aeMaskExpression = "true";
        }
        zmaxParameters.put("aeMaskExpression", aeMaskExpression);
        zmaxParameters.put("distanceBandName", CoastDistanceOp.COAST_DISTANCE);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(ZmaxOp.class), zmaxParameters, zmaxInput);
    }

    private Product createCloudDistanceProduct(Product conversionProduct, Product cloudProduct) {
        Map<String, Product> cloudDistanceInput = new HashMap<String, Product>(2);
        cloudDistanceInput.put("source", conversionProduct);
        cloudDistanceInput.put("cloud", cloudProduct);
        Map<String, Object> cloudDistanceParameters = new HashMap<String, Object>(0);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(CloudDistanceOp.class), cloudDistanceParameters, cloudDistanceInput);
    }

    private Product createCoastDistanceProduct(Product conversionProduct, Product landProduct) {
        Map<String, Product> coastDistanceInput = new HashMap<String, Product>(2);
        coastDistanceInput.put("source", conversionProduct);
        coastDistanceInput.put("land", landProduct);
        Map<String, Object> distanceParameters = new HashMap<String, Object>(4);
        distanceParameters.put("landExpression", "land_classif_flags.F_LANDCONS");
        distanceParameters.put("waterExpression", "land_classif_flags.F_LOINLD");
        distanceParameters.put("correctOverLand", aeArea.correctOverLand());
        distanceParameters.put("numDistances", 2);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(CoastDistanceOp.class), distanceParameters, coastDistanceInput);
    }

    private Product createAeMaskAerosolProduct(Product conversionProduct, Product landProduct) {
        Map<String, Product> aemaskAerosolInput = new HashMap<String, Product>(2);
        aemaskAerosolInput.put("source", conversionProduct);
        aemaskAerosolInput.put("land", landProduct);
        Map<String, Object> aemaskAerosolParameters = new HashMap<String, Object>(5);
        aemaskAerosolParameters.put("landExpression", "land_classif_flags.F_LANDCONS");
        aemaskAerosolParameters.put("aeArea", aeArea);
        aemaskAerosolParameters.put("reshapedConvolution", reshapedConvolution);
        aemaskAerosolParameters.put("correctionMode", IcolConstants.AE_CORRECTION_MODE_AEROSOL);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(AdjacencyEffectMaskOp.class), aemaskAerosolParameters, aemaskAerosolInput);
    }

    private Product createAeMaskRayleighProduct(Product conversionProduct, Product landProduct) {
        Map<String, Product> aemaskRayleighInput = new HashMap<String, Product>(2);
        aemaskRayleighInput.put("source", conversionProduct);
        aemaskRayleighInput.put("land", landProduct);
        Map<String, Object> aemaskRayleighParameters = new HashMap<String, Object>(5);
        aemaskRayleighParameters.put("landExpression", "land_classif_flags.F_LANDCONS");
        aemaskRayleighParameters.put("aeArea", aeArea);
        aemaskRayleighParameters.put("reshapedConvolution", reshapedConvolution);
        aemaskRayleighParameters.put("correctionMode", IcolConstants.AE_CORRECTION_MODE_RAYLEIGH);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(AdjacencyEffectMaskOp.class), aemaskRayleighParameters, aemaskRayleighInput);
    }

    private Product createRayleighProduct(Product downscaledSourceProduct, Product conversionProduct, Product cloudProduct, Product ctpProduct, Product landProduct, Product fresnelProduct) {
        Map<String, Product> rayleighInput = new HashMap<String, Product>(6);
        rayleighInput.put("refl", conversionProduct);
        rayleighInput.put("land", landProduct);
        rayleighInput.put("downscaled", downscaledSourceProduct);
        rayleighInput.put("fresnel", fresnelProduct);
        rayleighInput.put("cloud", cloudProduct);
        rayleighInput.put("ctp", ctpProduct);
        Map<String, Object> rayleighParameters = new HashMap<String, Object>(3);
        rayleighParameters.put("correctWater", true);
        rayleighParameters.put("exportRayCoeffs", true);
        rayleighParameters.put("userPSurf", landsatUserPSurf);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(EtmRayleighCorrectionOp.class), rayleighParameters, rayleighInput);
    }

    private Product createFresnelProduct(Product conversionProduct, Product gasProduct, Product landProduct) {
        Map<String, Product> fresnelInput = new HashMap<String, Product>(3);
        fresnelInput.put("l1b", conversionProduct);
        fresnelInput.put("land", landProduct);
        fresnelInput.put("input", gasProduct);
        Map<String, Object> fresnelParameters = new HashMap<String, Object>();
        return GPF.createProduct(OperatorSpi.getOperatorAlias(FresnelCoefficientOp.class), fresnelParameters, fresnelInput);
    }

    private Product createCloudLandMaskProduct(Product cloudProduct, Product landProduct) {
        Map<String, Product> cloudLandMaskInput = new HashMap<String, Product>(2);
        cloudLandMaskInput.put("cloud", cloudProduct);
        cloudLandMaskInput.put("land", landProduct);
        Map<String, Object> cloudLandMaskParameters = new HashMap<String, Object>(2);
        cloudLandMaskParameters.put("landExpression", "land_classif_flags.F_LANDCONS || land_classif_flags.F_ICE");
        cloudLandMaskParameters.put("cloudExpression", "cloud_classif_flags.F_CLOUD");
        return GPF.createProduct(OperatorSpi.getOperatorAlias(CloudLandMaskOp.class), cloudLandMaskParameters,
                                 cloudLandMaskInput);
    }

    private Product createLandProduct(Product conversionProduct) {
        Map<String, Product> landInput = new HashMap<String, Product>(2);
        landInput.put("refl", conversionProduct);
        Map<String, Object> landParameters = new HashMap<String, Object>(6);
        landParameters.put("landsatLandFlagApplyNdviFilter", landsatLandFlagApplyNdviFilter);
        landParameters.put("landsatLandFlagApplyTemperatureFilter", landsatLandFlagApplyTemperatureFilter);
        landParameters.put("landNdviThreshold", landNdviThreshold);
        landParameters.put("landTM6Threshold", landTM6Threshold);
        landParameters.put("landsatSeason", landsatSeason);
        landParameters.put("reflectanceBandNames", LandsatConstants.LANDSAT7_REFLECTANCE_BAND_NAMES);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(LandClassificationOp.class), landParameters, landInput);
    }

    private Product createGasProduct(Product conversionProduct, Product gaseousTransmittanceProduct) {
        Map<String, Product> gasInput = new HashMap<String, Product>(3);
        gasInput.put("refl", conversionProduct);
        gasInput.put("atmFunctions", gaseousTransmittanceProduct);
        Map<String, Object> gasParameters = new HashMap<String, Object>(2);
        gasParameters.put("exportTg", true);
        Product gasProduct = null;
        if (sourceProduct.getProductType().startsWith("Landsat5")) {
            gasProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(EtmGaseousCorrectionOp.class), gasParameters, gasInput);
        } else if (sourceProduct.getProductType().startsWith("Landsat7")) {
            gasProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(EtmGaseousCorrectionOp.class), gasParameters, gasInput);
        }
        return gasProduct;
    }

    private Product createCtpProduct(Product conversionProduct, Product cloudProduct) {
        Map<String, Product> ctpInput = new HashMap<String, Product>(2);
        ctpInput.put("refl", conversionProduct);
        ctpInput.put("cloud", cloudProduct);
        Map<String, Object> ctpParameters = new HashMap<String, Object>(3);
        ctpParameters.put("userPSurf", landsatUserPSurf);
        ctpParameters.put("userTm60", landsatUserTm60);
        ctpParameters.put("thermalBandName", LandsatConstants.LANDSAT7_REFLECTANCE_6a_BAND_NAME);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(CloudTopPressureOp.class), ctpParameters, ctpInput);
    }

    private Product createCloudProduct(Product conversionProduct) {
        Map<String, Product> cloudInput = new HashMap<String, Product>(2);
        cloudInput.put("refl", conversionProduct);
        Map<String, Object> cloudParameters = new HashMap<String, Object>(8);
        cloudParameters.put("landsatCloudFlagApplyBrightnessFilter", landsatCloudFlagApplyBrightnessFilter);
        cloudParameters.put("landsatCloudFlagApplyNdviFilter", landsatCloudFlagApplyNdviFilter);
        cloudParameters.put("landsatCloudFlagApplyNdsiFilter", landsatCloudFlagApplyNdsiFilter);
        cloudParameters.put("landsatCloudFlagApplyTemperatureFilter", landsatCloudFlagApplyTemperatureFilter);
        cloudParameters.put("cloudBrightnessThreshold", cloudBrightnessThreshold);
        cloudParameters.put("cloudNdviThreshold", cloudNdviThreshold);
        cloudParameters.put("cloudNdsiThreshold", cloudNdsiThreshold);
        cloudParameters.put("cloudTM6Threshold", cloudTM6Threshold);
        cloudParameters.put("reflectanceBandNames", LandsatConstants.LANDSAT7_REFLECTANCE_BAND_NAMES);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(CloudClassificationOp.class), cloudParameters, cloudInput);
    }

    private Product createGaseousTransmittanceProduct(Product downscaledSourceProduct) {
        Map<String, Product> gaseousTransmittanceInput = new HashMap<String, Product>(3);
        gaseousTransmittanceInput.put("l1g", sourceProduct);
        gaseousTransmittanceInput.put("downscaled", downscaledSourceProduct);
        Map<String, Object> gaseousTransmittanceParameters = new HashMap<String, Object>(1);
        gaseousTransmittanceParameters.put("ozoneContent", landsatUserOzoneContent);
        Product gaseousTransmittanceProduct = null;
        if (sourceProduct.getProductType().startsWith("Landsat5")) {
            gaseousTransmittanceProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(EtmGaseousTransmittanceOp.class), gaseousTransmittanceParameters, gaseousTransmittanceInput);
        } else if (sourceProduct.getProductType().startsWith("Landsat7")) {
            gaseousTransmittanceProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(EtmGaseousTransmittanceOp.class), gaseousTransmittanceParameters, gaseousTransmittanceInput);
        }
        return gaseousTransmittanceProduct;
    }

    private Product createConversionProduct(Product downscaledSourceProduct) {
        Map<String, Product> radianceConversionInput = new HashMap<String, Product>(2);
        radianceConversionInput.put("l1g", sourceProduct);
        radianceConversionInput.put("downscaled", downscaledSourceProduct);
        Map<String, Object> conversionParameters = new HashMap<String, Object>(4);
        conversionParameters.put("startTime", landsatStartTime);
        conversionParameters.put("stopTime", landsatStopTime);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(EtmRadConversionOp.class), conversionParameters, radianceConversionInput);
    }

    private Product createDownscaledProduct() {
        Product downscaledProduct;
        Map<String, Product> downscaledInput = new HashMap<String, Product>(1);
        downscaledInput.put("l1g", sourceProduct);
        Map<String, Object> downscaledParameters = new HashMap<String, Object>(3);
        downscaledParameters.put("landsatTargetResolution", landsatTargetResolution);
        downscaledParameters.put("radianceBandNames", LandsatConstants.LANDSAT7_RADIANCE_BAND_NAMES);
        downscaledParameters.put("startTime", landsatStartTime);
        downscaledParameters.put("stopTime", landsatStopTime);
        downscaledProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(DownscaleOp.class), downscaledParameters, downscaledInput);
        return downscaledProduct;
    }

    private Product createUpscaledToOriginalProduct(Product downscaledProduct, Product correctionProduct) {
        Map<String, Product> aeUpscaleInput = new HashMap<String, Product>(9);
        aeUpscaleInput.put("l1b", sourceProduct);
        aeUpscaleInput.put("downscaled", downscaledProduct);
        aeUpscaleInput.put("corrected", correctionProduct);
        Map<String, Object> aeUpscaleParams = new HashMap<String, Object>(9);
        aeUpscaleParams.put("instrument", Instrument.ETM7);

        return GPF.createProduct(OperatorSpi.getOperatorAlias(UpscaleToOriginalOp.class), aeUpscaleParams, aeUpscaleInput);
    }

    /**
     * creates a new product with the same size
     *
     * @param sourceProduct
     * @param name
     * @param type
     * @return targetProduct
     */
    public Product createCompatibleProduct(Product sourceProduct, String name, String type) {
        final int sceneWidth = sourceProduct.getSceneRasterWidth();
        final int sceneHeight = sourceProduct.getSceneRasterHeight();

        Product targetProduct = new Product(name, type, sceneWidth, sceneHeight);
        copyBaseGeoInfo(sourceProduct, targetProduct);

        return targetProduct;
    }

    private void setStartStopTime() {
        try {
            // finally we need dd-MM-yyyy hh:mm:ss
            if (sourceProduct.getStartTime() != null && sourceProduct.getEndTime() != null) {
                // this is the case for downscaled product
                landsatStartTime = sourceProduct.getStartTime().toString().substring(0, 20);
                landsatStopTime = sourceProduct.getEndTime().toString().substring(0, 20);
            } else {
                // this is the case for original input product (GeoTIFF)
                // here we get yyyy-mm-dd:
                String acquisitionDate = sourceProduct.getMetadataRoot()
                        .getElement("L1_METADATA_FILE").getElement("PRODUCT_METADATA").getAttribute("ACQUISITION_DATE").getData().getElemString();

                String centerTime = sourceProduct.getMetadataRoot()
                        .getElement("L1_METADATA_FILE").getElement("PRODUCT_METADATA").getAttribute("SCENE_CENTER_SCAN_TIME").getData().getElemString().substring(0, 8);

                String landsatCenterTime = LandsatUtils.convertDate(acquisitionDate) + " " + centerTime;
                landsatStartTime = landsatCenterTime;
                landsatStopTime = landsatCenterTime;
                sourceProduct.setStartTime(ProductData.UTC.parse(landsatCenterTime));
                sourceProduct.setEndTime(ProductData.UTC.parse(landsatCenterTime));
            }

        } catch (ParseException e) {
            throw new OperatorException("Start or stop time invalid or has wrong format - must be 'yyyymmdd hh:mm:ss'.");
        }
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(EtmOp.class);
        }
    }
}
