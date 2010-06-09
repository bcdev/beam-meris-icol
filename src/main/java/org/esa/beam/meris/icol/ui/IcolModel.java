package org.esa.beam.meris.icol.ui;

import com.bc.ceres.binding.PropertyContainer;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.ParameterDescriptorFactory;
import org.esa.beam.meris.icol.AeArea;
import org.esa.beam.meris.icol.tm.TmConstants;

import java.util.HashMap;
import java.util.Map;

public class IcolModel {
    // MerisReflectanceCorrectionOp
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

    // CTP
    @Parameter(defaultValue = "false")
    private boolean useUserCtp = false;
    @Parameter(interval = "[0.0, 1013.0]", defaultValue = "1013.0")
    private double userCtp = 1013.0;

    // MerisAeAerosolOp
    @Parameter(defaultValue = "false")
    boolean icolAerosolForWater = false;
    @Parameter(defaultValue = "false")
    boolean icolAerosolCase2 = false;
    @Parameter(interval = "[-2.1, -0.4]", defaultValue = "-1")
    private double userAlpha = -1.0;
    @Parameter(interval = "[0, 1.5]", defaultValue = "0.2")
    private double userAot550 = 0.2;

    // MerisRadianceCorrectionOp
    @Parameter(defaultValue = "true")
    private boolean correctForBoth = true;

    // General
    @Parameter(interval = "[1, 3]", defaultValue = "3")
    private int convolveMode = 1;
    @Parameter(defaultValue = "true")
    private boolean reshapedConvolution = true;
    @Parameter(defaultValue = "true")
    private boolean openclConvolution = true;
    @Parameter(defaultValue = "64")
    private int tileSize = 64;
    @Parameter(defaultValue = "COSTAL_OCEAN", valueSet = {"COSTAL_OCEAN", "OCEAN", "COSTAL_ZONE", "EVERYWHERE"})
    private AeArea aeArea;

    // Landsat
    @Parameter(defaultValue = "0", valueSet = {"0", "1"})
    private int landsatTargetResolution = 0; // 300m
    @Parameter(defaultValue = "")
    // test values:
    private String landsatStartTime = "06-AUG-2007 09:30:00";
    @Parameter(defaultValue = "")
    private String landsatStopTime = "06-AUG-2007 09:40:00";
    @Parameter(interval = "[0.0, 1.0]", defaultValue = "0.32")
    private double landsatUserOzoneContent = TmConstants.DEFAULT_OZONE_CONTENT;
    @Parameter(interval = "[300.0, 1060.0]", defaultValue = "1013.0")
    private double landsatUserPSurf = TmConstants.DEFAULT_SURFACE_PRESSURE;
    @Parameter(interval = "[200.0, 320.0]", defaultValue = "288.0")
    private double landsatUserTm60 = TmConstants.DEFAULT_SURFACE_TM_APPARENT_TEMPERATURE;
    @Parameter(defaultValue = "false")
    private boolean landsatComputeFlagSettingsOnly = false;
    @Parameter(defaultValue = "false")
    private boolean landsatComputeToTargetGridOnly = false;
    @Parameter(defaultValue = "false")
    private boolean upscaleToTMFR = false;


    @Parameter(defaultValue = "true")
    private boolean landsatCloudFlagApplyBrightnessFilter = true;
    @Parameter(defaultValue = "true")
    private boolean landsatCloudFlagApplyNdviFilter = true;
    @Parameter(defaultValue = "true")
    private boolean landsatCloudFlagApplyNdsiFilter = true;
    @Parameter(defaultValue = "true")
    private boolean landsatCloudFlagApplyTemperatureFilter = true;
    @Parameter(interval = "[0.0, 1.0]", defaultValue = "0.08")
    private double cloudBrightnessThreshold = TmConstants.DEFAULT_BRIGHTNESS_THRESHOLD;
    @Parameter(interval = "[0.0, 1.0]", defaultValue = "0.2")
    private double cloudNdviThreshold = TmConstants.DEFAULT_NDVI_CLOUD_THRESHOLD;
    @Parameter(interval = "[0.0, 10.0]", defaultValue = "3.0")
    private double cloudNdsiThreshold = TmConstants.DEFAULT_NDSI_THRESHOLD;
    @Parameter(interval = "[200.0, 320.0]", defaultValue = "300.0")
    private double cloudTM6Threshold = TmConstants.DEFAULT_TM6_CLOUD_THRESHOLD;

    @Parameter(defaultValue = "true")
    private boolean landsatLandFlagApplyNdviFilter = true;
    @Parameter(defaultValue = "true")
    private boolean landsatLandFlagApplyTemperatureFilter = true;
    @Parameter(interval = "[0.0, 1.0]", defaultValue = "0.2")
    private double landNdviThreshold = TmConstants.DEFAULT_NDVI_LAND_THRESHOLD;
    @Parameter(interval = "[200.0, 320.0]", defaultValue = "300.0")
    // TBD!!
    private double landTM6Threshold = TmConstants.DEFAULT_TM6_LAND_THRESHOLD;
    @Parameter(defaultValue = "", valueSet = {TmConstants.LAND_FLAGS_SUMMER,
            TmConstants.LAND_FLAGS_WINTER})
    private String landsatSeason = TmConstants.LAND_FLAGS_SUMMER;


    @Parameter(defaultValue = "0", valueSet = {"0", "1"})
    private int productType = 0;
    private Product sourceProduct;
    private PropertyContainer propertyContainer;


    public IcolModel() {
        propertyContainer = PropertyContainer.createObjectBacked(this, new ParameterDescriptorFactory());
        aeArea = AeArea.COSTAL_OCEAN;
    }

    public Product getSourceProduct() {
        return sourceProduct;
    }

//    public boolean isComputeRhoToa() {
//        return (productType == 1);
//    }

    private int getLandsatTargetResolution() {
        int landsatTargetResolutionValue = -1;
        if (landsatTargetResolution == 0) {
            landsatTargetResolutionValue = 300;
        } else if (landsatTargetResolution == 1) {
            landsatTargetResolutionValue = 1200;
        }
        return landsatTargetResolutionValue;
    }

    public PropertyContainer getPropertyContainer() {
        return propertyContainer;
    }

    public Map<String, Object> getRhoToaParameters() {
        HashMap<String, Object> params = new HashMap<String, Object>();
        configCtp(params);
        configAeAerosolOp(params);
        configReverseRhoToaOp(params);
        configGeneral(params);
        return params;
    }

    public Map<String, Object> getN1Parameters() {
        HashMap<String, Object> params = new HashMap<String, Object>();
        configCtp(params);
        configAeAerosolOp(params);
        configReverseRhoToaOp(params);
        configGeneral(params);
        return params;
    }

    public Map<String, Object> getLandsat5Parameters() {
        HashMap<String, Object> params = new HashMap<String, Object>();
        configAeAerosolOp(params);
        configReverseRhoToaOp(params);
        configGeneral(params);
        configLandsatOp(params);
        return params;
    }

    private void configReverseRhoToaOp(HashMap<String, Object> params) {
        params.put("exportRhoToa", exportRhoToa);
        params.put("exportRhoToaRayleigh", exportRhoToaRayleigh);
        params.put("exportRhoToaAerosol", exportRhoToaAerosol);
        params.put("exportAeRayleigh", exportAeRayleigh);
        params.put("exportAeAerosol", exportAeAerosol);
        params.put("exportAlphaAot", exportAlphaAot);
    }

    private void configCtp(HashMap<String, Object> params) {
        params.put("useUserCtp", useUserCtp);
        params.put("userCtp", userCtp);
    }

    private void configAeAerosolOp(HashMap<String, Object> params) {
        params.put("icolAerosolForWater", icolAerosolForWater);
        params.put("icolAerosolCase2", icolAerosolCase2);
        params.put("userAlpha", userAlpha);
        params.put("userAot550", userAot550);
    }

    private void configLandsatOp(HashMap<String, Object> params) {
        params.put("landsatTargetResolution", getLandsatTargetResolution());
        params.put("landsatStartTime", landsatStartTime);
        params.put("landsatStopTime", landsatStopTime);
        params.put("landsatUserOzoneContent", landsatUserOzoneContent);
        params.put("landsatUserPSurf", landsatUserPSurf);
        params.put("landsatUserTm60", landsatUserTm60);
        params.put("landsatComputeFlagSettingsOnly", landsatComputeFlagSettingsOnly);
        params.put("landsatComputeToTargetGridOnly", landsatComputeToTargetGridOnly);
        params.put("upscaleToTMFR", upscaleToTMFR);
        params.put("landsatCloudFlagApplyBrightnessFilter", landsatCloudFlagApplyBrightnessFilter);
        params.put("landsatCloudFlagApplyNdviFilter", landsatCloudFlagApplyNdviFilter);
        params.put("landsatCloudFlagApplyNdsiFilter", landsatCloudFlagApplyNdsiFilter);
        params.put("landsatCloudFlagApplyTemperatureFilter", landsatCloudFlagApplyTemperatureFilter);
        params.put("cloudBrightnessThreshold", cloudBrightnessThreshold);
        params.put("cloudNdviThreshold", cloudNdviThreshold);
        params.put("cloudNdsiThreshold", cloudNdsiThreshold);
        params.put("cloudTM6Threshold", cloudTM6Threshold);
        params.put("landsatLandFlagApplyNdviFilter", landsatLandFlagApplyNdviFilter);
        params.put("landsatLandFlagApplyTemperatureFilter", landsatLandFlagApplyTemperatureFilter);
        params.put("landNdviThreshold", landNdviThreshold);
        params.put("landTM6Threshold", landTM6Threshold);
        params.put("landsatSeason", landsatSeason);
    }

    private void configGeneral(HashMap<String, Object> params) {
        params.put("productType", productType);
        params.put("convolveMode", convolveMode);
        params.put("tileSize", tileSize);
        params.put("reshapedConvolution", reshapedConvolution);
        params.put("openclConvolution", openclConvolution);
        params.put("aeArea", aeArea);
        params.put("correctForBoth", correctForBoth);
    }
}
