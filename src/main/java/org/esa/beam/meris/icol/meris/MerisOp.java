/*
 * $Id: $
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

import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.meris.N1PatcherOp;
import org.esa.beam.meris.brr.CloudClassificationOp;
import org.esa.beam.meris.brr.GaseousCorrectionOp;
import org.esa.beam.meris.brr.LandClassificationOp;
import org.esa.beam.meris.brr.Rad2ReflOp;
import org.esa.beam.meris.brr.RayleighCorrectionOp;
import org.esa.beam.meris.icol.AeArea;
import org.esa.beam.meris.icol.FresnelCoefficientOp;
import org.esa.beam.meris.icol.IcolConstants;
import org.esa.beam.meris.icol.Instrument;
import org.esa.beam.meris.icol.common.AdjacencyEffectMaskOp;
import org.esa.beam.meris.icol.common.AdjacencyEffectRayleighOp;
import org.esa.beam.meris.icol.common.CloudDistanceOp;
import org.esa.beam.meris.icol.common.CoastDistanceOp;
import org.esa.beam.meris.icol.common.ZmaxOp;
import org.esa.beam.meris.icol.utils.DebugUtils;
import org.esa.beam.meris.icol.utils.IcolUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;


/**
 * Main operator for MERIS AE correction.
 *
 * @author Marco Zuehlke, Olaf Danne
 * @version $Revision: 8083 $ $Date: 2010-01-25 19:08:29 +0100 (Mo, 25 Jan 2010) $
 */
@SuppressWarnings({"FieldCanBeLocal"})
@OperatorMetadata(alias = "IcolMeris",
                  version = "1.1",
                  authors = "Marco Zuehlke, Olaf Danne",
                  copyright = "(c) 2007-2009 by Brockmann Consult",
                  description = "Performs a correction of the adjacency effect, computes radiances and writes a Envisat N1 file.")
public class MerisOp extends Operator {

    @SourceProduct(description = "The source product.")
    Product sourceProduct;
    @SourceProduct(optional = true, description = "The cloud product.")
    Product cloudMaskProduct;

    @TargetProduct(description = "The target product.")
    Product targetProduct;

    // Cloud
    @Parameter(description = "An expression for the cloud mask.")
    private String cloudMaskExpression;

    // CTP
    @Parameter(defaultValue = "false")
    private boolean useUserCtp = false;
    @Parameter(interval = "[0.0, 1013.0]", defaultValue = "1013.0")
    private double userCtp = 1013.0;

    // MerisAeAerosolOp
    @Parameter(defaultValue = "false", description = "export the aerosol and fresnel correction term as bands")
    private boolean exportSeparateDebugBands = false;
    @Parameter(defaultValue = "false")
    private boolean icolAerosolForWater = false;
    @Parameter(defaultValue = "false")
    private boolean icolAerosolCase2 = false;
    @Parameter(interval = "[440.0, 2225.0]", defaultValue = "550.0")
    private double userAerosolReferenceWavelength;
    @Parameter(interval = "[-2.1, -0.4]", defaultValue = "-1")
    private double userAlpha;
    @Parameter(interval = "[0, 1.5]", defaultValue = "0.2", description = "The aerosol optical thickness at 550nm")
    private double userAot;

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

    // general
    @Parameter(defaultValue = "0", valueSet = {"0", "1"})
    private int productType = 0;
    @Parameter(defaultValue = "true")
    private boolean reshapedConvolution = true;
    @Parameter(defaultValue = "false")
    private boolean openclConvolution = false;
    @Parameter(defaultValue = "64")
    private int tileSize = 64;
    @Parameter(defaultValue = "COASTAL_OCEAN", valueSet = {"COASTAL_OCEAN", "OCEAN", "COASTAL_ZONE", "EVERYWHERE"})
    private AeArea aeArea;

    // N1PatcherOp
    @Parameter(description = "The file to which the patched L1b product is written.")
    private File patchedFile;

    @Override
    public void initialize() throws OperatorException {
        System.out.println("Tile size: " + tileSize);
        System.out.println("Apply AE over: " + aeArea);

        // todo: why this??
        if (tileSize > 0) {
            sourceProduct.setPreferredTileSize(tileSize, tileSize);
        }

        // todo: we need to think about how to handle the left and right stripes in Meris N1 files which
        // consist of invalid pixels (detector_index = -1). It seems that they are somehow introduced in the
        // AE correction (see email from MB, 10.09.2010). Do a kind of a priori "edge correction"?

        Product rad2reflProduct = createRad2ReflProduct();
        Product ctpProduct = createCtpProduct();
        Product cloudClassificationProduct = createCloudClassificationProduct(rad2reflProduct, ctpProduct);
        cloudClassificationProduct = updateCloudClassificationProduct(cloudClassificationProduct);
        Product gasProduct = createGasProduct(rad2reflProduct, cloudClassificationProduct);
        Product landProduct = createLandProduct(rad2reflProduct, gasProduct);
        Product fresnelProduct = createFresnelProduct(gasProduct, landProduct);
        Product rayleighProduct = createRayleighProduct(cloudClassificationProduct, landProduct, fresnelProduct);
        Product aemaskRayleighProduct = createAeMaskRayleighProduct(landProduct);
        Product aemaskAerosolProduct = createAeMaskProduct(landProduct);
        Product coastDistanceProduct = createCoastDistanceProduct(landProduct);
        Product cloudDistanceProduct = createCloudDistanceProduct(cloudClassificationProduct);
        Product zmaxProduct = createZMaxProduct(aemaskRayleighProduct, coastDistanceProduct);
        Product zmaxCloudProduct = createZMaxCloudProduct(aemaskRayleighProduct, cloudDistanceProduct);

        // test: create constant reflectance input
//        Map<String, Product> constInput = new HashMap<String, Product>(1);
//        constInput.put("source", sourceProduct);
//        Product constProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(ConstantValueOp.class), GPF.NO_PARAMS, rayleighProduct);
//        Product constProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(TestImageOp.class), GPF.NO_PARAMS, rayleighProduct);
        // end test

        Product brrCloudProduct = createBrrCloudProduct(rad2reflProduct, cloudClassificationProduct, landProduct,
                                                        rayleighProduct);

        Product brrConvolveProduct = createBrrConvolveProduct(brrCloudProduct);

        Product aeRayProduct = createAeRayProduct(rad2reflProduct, cloudClassificationProduct, gasProduct, landProduct,
                                                  aemaskRayleighProduct, zmaxProduct, zmaxCloudProduct, brrCloudProduct,
                                                  brrConvolveProduct);

        // test: create constant reflectance input
//            Map<String, Product> compareConvolutionInput = new HashMap<String, Product>(1);
//            compareConvolutionInput.put("l1b", sourceProduct);
//            compareConvolutionInput.put("ae_ray", aeRayProduct);
//            Product compareConvolutionProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(CompareConvolutionOp.class), GPF.NO_PARAMS, compareConvolutionInput);
//            targetProduct = compareConvolutionProduct;
//            return;


        // test: create constant reflectance input
//            Map<String, Product> constInputAer = new HashMap<String, Product>(1);
//            constInputAer.put("source", sourceProduct);
//            Product constProductAer = GPF.createProduct(OperatorSpi.getOperatorAlias(TestImageOp.class), GPF.NO_PARAMS, constInputAer);
        // end test

        Product rayAercConvolveProduct = createRayAercConvolveProduct(aeRayProduct);
        Product aeAerProduct = createAeAerProduct(cloudClassificationProduct, landProduct, aemaskAerosolProduct,
                                                  zmaxProduct, zmaxCloudProduct, aeRayProduct, rayAercConvolveProduct);
        Product reverseRhoToaProduct = createReverseRhoToaProduct(rad2reflProduct, cloudClassificationProduct,
                                                                  gasProduct, landProduct, aemaskRayleighProduct,
                                                                  aemaskAerosolProduct, aeRayProduct, aeAerProduct);
        Product finalRhoToaProduct = createFinalRhoToaProduct(rad2reflProduct, reverseRhoToaProduct);

        if (productType == 0) {
            // radiance output product
            Product reverseRadianceProduct = createReverseRadianceProduct(gasProduct, aemaskAerosolProduct,
                                                                          aeAerProduct, finalRhoToaProduct);

            // additional output bands for RS
            if (System.getProperty("additionalOutputBands") != null && System.getProperty(
                    "additionalOutputBands").equals("RS")) {

                addDebugBands(rad2reflProduct, ctpProduct, cloudClassificationProduct, landProduct,
                              aemaskRayleighProduct, aemaskAerosolProduct, coastDistanceProduct, zmaxProduct,
                              brrCloudProduct, brrConvolveProduct, aeRayProduct, aeAerProduct, reverseRadianceProduct);
            } else {
                // test:
                // Rayleigh correction
//                DebugUtils.addRayleighCorrDebugBands(reverseRadianceProduct, brrCloudProduct);

                // brr convolution (test)
//                if (openclConvolution)
//                    DebugUtils.addRayleighCorrDebugBands(reverseRadianceProduct, brrConvolveProduct);
                // end test
            }
            if (patchedFile != null) {
                targetProduct = createN1Product(reverseRadianceProduct);
            } else {
                targetProduct = reverseRadianceProduct;
//                targetProduct = constProduct;
            }
        } else if (productType == 1) {
            targetProduct = finalRhoToaProduct;
        }
    }

    private void addDebugBands(Product rad2reflProduct, Product ctpProduct, Product cloudClassificationProduct,
                               Product landProduct, Product aemaskRayleighProduct, Product aemaskAerosolProduct,
                               Product coastDistanceProduct, Product zmaxProduct, Product brrCloudProduct,
                               Product brrConvolveProduct, Product aeRayProduct, Product aeAerProduct,
                               Product reverseRadianceProduct) {
        // rad2refl
        DebugUtils.addRad2ReflDebugBands(reverseRadianceProduct, rad2reflProduct);

        // cloud classif flags
        FlagCoding flagCodingCloud = CloudClassificationOp.createFlagCoding();
        reverseRadianceProduct.getFlagCodingGroup().add(flagCodingCloud);
        DebugUtils.addSingleDebugFlagBand(reverseRadianceProduct, cloudClassificationProduct, flagCodingCloud,
                                          CloudClassificationOp.CLOUD_FLAGS);

        // land classif flags
        FlagCoding flagCodingLand = LandClassificationOp.createFlagCoding();
        reverseRadianceProduct.getFlagCodingGroup().add(flagCodingLand);
        DebugUtils.addSingleDebugFlagBand(reverseRadianceProduct, landProduct, flagCodingLand,
                                          LandClassificationOp.LAND_FLAGS);

        // Rayleigh correction
        DebugUtils.addRayleighCorrDebugBands(reverseRadianceProduct, brrCloudProduct);

        // ctp
        DebugUtils.addCtpProductDebugBand(reverseRadianceProduct, ctpProduct, "cloud_top_press");

        // brr convolution (test)
        if (openclConvolution) {
            DebugUtils.addRayleighCorrDebugBands(reverseRadianceProduct, brrConvolveProduct);
        }

        // (i) AE mask
        DebugUtils.addSingleDebugBand(reverseRadianceProduct, aemaskRayleighProduct, AdjacencyEffectMaskOp.AE_MASK_RAYLEIGH);
        DebugUtils.addSingleDebugBand(reverseRadianceProduct, aemaskAerosolProduct, AdjacencyEffectMaskOp.AE_MASK_AEROSOL);

        // (iv) zMax
        DebugUtils.addSingleDebugBand(reverseRadianceProduct, zmaxProduct, ZmaxOp.ZMAX + "_1");
        DebugUtils.addSingleDebugBand(reverseRadianceProduct, zmaxProduct, ZmaxOp.ZMAX + "_2");

        // (iv a) coastDistance
        DebugUtils.addSingleDebugBand(reverseRadianceProduct, coastDistanceProduct,
                                      CoastDistanceOp.COAST_DISTANCE + "_1");
        DebugUtils.addSingleDebugBand(reverseRadianceProduct, coastDistanceProduct,
                                      CoastDistanceOp.COAST_DISTANCE + "_2");

        DebugUtils.addAeRayleighProductDebugBands(reverseRadianceProduct, aeRayProduct);
        DebugUtils.addAeAerosolProductDebugBands(reverseRadianceProduct, aeAerProduct);
    }

    private Product createN1Product(Product reverseRadianceProduct) {
        Map<String, Product> n1PatcherInput = new HashMap<String, Product>(2);
        n1PatcherInput.put("n1", sourceProduct);
        n1PatcherInput.put("input", reverseRadianceProduct);
        Map<String, Object> n1Params = new HashMap<String, Object>(1);
        n1Params.put("patchedFile", patchedFile);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(N1PatcherOp.class), n1Params,
                                 n1PatcherInput);
    }

    private Product createReverseRadianceProduct(Product gasProduct, Product aemaskAerosolProduct, Product aeAerProduct,
                                                 Product finalRhoToaProduct) {
        Map<String, Product> reverseRadianceInput = new HashMap<String, Product>(5);
        reverseRadianceInput.put("l1b", sourceProduct);
        reverseRadianceInput.put("refl", finalRhoToaProduct);
        reverseRadianceInput.put("gascor", gasProduct);
        reverseRadianceInput.put("ae_aerosol", aeAerProduct);
        reverseRadianceInput.put("aemaskAerosol", aemaskAerosolProduct);
        return GPF.createProduct(
                OperatorSpi.getOperatorAlias(MerisRadianceCorrectionOp.class), GPF.NO_PARAMS,
                reverseRadianceInput);
    }

    private Product createFinalRhoToaProduct(Product rad2reflProduct, Product reverseRhoToaProduct) {
        // band 11 and 15 correction (new scheme, RS 09/12/2009)
        Map<String, Product> band11And15Input = new HashMap<String, Product>(3);
        band11And15Input.put("l1b", sourceProduct);
        band11And15Input.put("refl", rad2reflProduct);
        band11And15Input.put("corrRad", reverseRhoToaProduct);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(MerisBand11And15Op.class), GPF.NO_PARAMS,
                                 band11And15Input);
    }

    private Product createReverseRhoToaProduct(Product rad2reflProduct, Product cloudClassificationProduct,
                                               Product gasProduct, Product landProduct, Product aemaskRayleighProduct,
                                               Product aemaskAerosolProduct, Product aeRayProduct,
                                               Product aeAerProduct) {
        // rho_TOA product
        Map<String, Product> reverseRhoToaInput = new HashMap<String, Product>(9);
        reverseRhoToaInput.put("l1b", sourceProduct);
        reverseRhoToaInput.put("rhotoa", rad2reflProduct);
        reverseRhoToaInput.put("land", landProduct);
        reverseRhoToaInput.put("cloud", cloudClassificationProduct);
        reverseRhoToaInput.put("aemaskRayleigh", aemaskRayleighProduct);
        reverseRhoToaInput.put("aemaskAerosol", aemaskAerosolProduct);
        reverseRhoToaInput.put("gascor", gasProduct);
        reverseRhoToaInput.put("ae_ray", aeRayProduct);
        reverseRhoToaInput.put("ae_aerosol", aeAerProduct);
        Map<String, Object> reverseRhoToaParams = new HashMap<String, Object>(7);
        reverseRhoToaParams.put("exportRhoToa", exportRhoToa);
        reverseRhoToaParams.put("exportRhoToaRayleigh", exportRhoToaRayleigh);
        reverseRhoToaParams.put("exportRhoToaAerosol", exportRhoToaAerosol);
        if (productType == 0 && System.getProperty("additionalOutputBands") != null && System.getProperty(
                "additionalOutputBands").equals("RS")) {
            // they already exist in this case
            exportAeRayleigh = false;
            exportAeAerosol = false;
        }
        reverseRhoToaParams.put("exportAeRayleigh", exportAeRayleigh);
        reverseRhoToaParams.put("exportAeAerosol", exportAeAerosol);
        reverseRhoToaParams.put("exportAlphaAot", exportAlphaAot);
        return GPF.createProduct(
                OperatorSpi.getOperatorAlias(MerisReflectanceCorrectionOp.class), reverseRhoToaParams,
                reverseRhoToaInput);
    }

    private Product createAeAerProduct(Product cloudClassificationProduct, Product landProduct,
                                       Product aemaskAerosolProduct, Product zmaxProduct, Product zmaxCloudProduct,
                                       Product aeRayProduct, Product rayAercConvolveProduct) {
        Map<String, Product> aeAerInput = new HashMap<String, Product>(8);
        aeAerInput.put("l1b", sourceProduct);
        aeAerInput.put("land", landProduct);
        aeAerInput.put("aemask", aemaskAerosolProduct);
        aeAerInput.put("zmax", zmaxProduct);
        aeAerInput.put("ae_ray", aeRayProduct);
        if (openclConvolution && !icolAerosolForWater) {
            aeAerInput.put("rayaercconv", rayAercConvolveProduct);  // use brr pre-convolved with JavaCL
        }
//      aeAerInput.put("ae_ray", constProductAer);  // test!!
        aeAerInput.put("cloud", cloudClassificationProduct);
        aeAerInput.put("zmaxCloud", zmaxCloudProduct);
        Map<String, Object> aeAerosolParams = new HashMap<String, Object>(8);
        if (productType == 0 && System.getProperty("additionalOutputBands") != null && System.getProperty(
                "additionalOutputBands").equals("RS")) {
            exportSeparateDebugBands = true;
        }
        aeAerosolParams.put("exportSeparateDebugBands", exportSeparateDebugBands);
        aeAerosolParams.put("icolAerosolForWater", icolAerosolForWater);
        aeAerosolParams.put("userAerosolReferenceWavelength", userAerosolReferenceWavelength);
        aeAerosolParams.put("userAlpha", userAlpha);
        aeAerosolParams.put("userAot", userAot);
        aeAerosolParams.put("reshapedConvolution", reshapedConvolution);
        aeAerosolParams.put("landExpression", "land_classif_flags.F_LANDCONS || land_classif_flags.F_ICE");
        Product aeAerProduct;
        if (icolAerosolCase2 && icolAerosolForWater) {
            aeAerProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(MerisAdjacencyEffectAerosolCase2Op.class), aeAerosolParams,
                                             aeAerInput);
        } else {
            aeAerosolParams.put("openclConvolution", openclConvolution);
            aeAerProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(MerisAdjacencyEffectAerosolOp.class), aeAerosolParams,
                                             aeAerInput);
        }
        return aeAerProduct;
    }

    private Product createRayAercConvolveProduct(Product aeRayProduct) {
        // begin TEST JavaCL
        Product rayAercConvolveProduct = null;
        if (openclConvolution && !icolAerosolForWater) {
            Map<String, Product> rayAercConvolveInput = new HashMap<String, Product>(2);
            rayAercConvolveInput.put("l1b", sourceProduct);
            rayAercConvolveInput.put("brr", aeRayProduct);
            Map<String, Object> rayAercConvolveParams = new HashMap<String, Object>(3);
            rayAercConvolveParams.put("openclConvolution", openclConvolution);
            rayAercConvolveParams.put("bandPrefix", "rho_ray_aerc");
//           int aerosolModelIndex = 10;
            int aerosolModelIndex = IcolUtils.determineAerosolModelIndex(userAlpha);
            rayAercConvolveParams.put("filterWeightsIndex", aerosolModelIndex - 1);
            rayAercConvolveProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(MerisBrrConvolveOp.class),
                                                       rayAercConvolveParams, rayAercConvolveInput);
        }
        return rayAercConvolveProduct;
        // end TEST JavaCL
    }

    private Product createAeRayProduct(Product rad2reflProduct, Product cloudClassificationProduct, Product gasProduct,
                                       Product landProduct, Product aemaskRayleighProduct, Product zmaxProduct,
                                       Product zmaxCloudProduct, Product brrCloudProduct, Product brrConvolveProduct) {
        Map<String, Product> aeRayInput = new HashMap<String, Product>(10);
        aeRayInput.put("l1b", sourceProduct);
        aeRayInput.put("refl", rad2reflProduct);
        aeRayInput.put("land", landProduct);
        aeRayInput.put("aemask", aemaskRayleighProduct);
        aeRayInput.put("ray1b", brrCloudProduct);
        if (openclConvolution) {
            aeRayInput.put("ray1bconv", brrConvolveProduct);  // use brr pre-convolved with JavaCL
        }
//        aeRayInput.put("ray1b", constProduct);  // test: use constant reflectance input
        aeRayInput.put("rhoNg", gasProduct);
        aeRayInput.put("zmax", zmaxProduct);
        aeRayInput.put("cloud", cloudClassificationProduct);
        aeRayInput.put("zmaxCloud", zmaxCloudProduct);
        Map<String, Object> aeRayParams = new HashMap<String, Object>(5);
        aeRayParams.put("landExpression", "land_classif_flags.F_LANDCONS || land_classif_flags.F_ICE");
        // todo simplify expression
        final String debugProperty = System.getProperty("additionalOutputBands");
        if (productType == 0 && debugProperty != null && debugProperty.equals("RS")) {
            exportSeparateDebugBands = true;
        }
        aeRayParams.put("exportSeparateDebugBands", exportSeparateDebugBands);
        aeRayParams.put("reshapedConvolution", reshapedConvolution);
        aeRayParams.put("openclConvolution", openclConvolution);
        aeRayParams.put("instrument", Instrument.MERIS);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(AdjacencyEffectRayleighOp.class), aeRayParams, aeRayInput);
    }

    private Product createBrrConvolveProduct(Product brrCloudProduct) {
        // begin TEST JavaCL
        Product brrConvolveProduct = null;
        if (openclConvolution) {
            Map<String, Product> brrConvolveInput = new HashMap<String, Product>(2);
            brrConvolveInput.put("l1b", sourceProduct);
            brrConvolveInput.put("brr", brrCloudProduct);
            Map<String, Object> brrConvolveParams = new HashMap<String, Object>(3);
            brrConvolveParams.put("openclConvolution", openclConvolution);
            brrConvolveParams.put("bandPrefix", "brr");
            brrConvolveParams.put("filterWeightsIndex", 0);
            brrConvolveProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(MerisBrrConvolveOp.class),
                                                   brrConvolveParams, brrConvolveInput);
            // end TEST JavaCL
        }
        return brrConvolveProduct;
    }

    private Product createBrrCloudProduct(Product rad2reflProduct, Product cloudClassificationProduct,
                                          Product landProduct, Product rayleighProduct) {
        Map<String, Product> brrCloudInput = new HashMap<String, Product>(5);
        brrCloudInput.put("l1b", sourceProduct);
        brrCloudInput.put("brr", rayleighProduct);
        brrCloudInput.put("refl", rad2reflProduct);
        brrCloudInput.put("cloud", cloudClassificationProduct);
        brrCloudInput.put("land", landProduct);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(MerisBrrCloudOp.class), GPF.NO_PARAMS,
                                 brrCloudInput);
    }

    private Product createZMaxCloudProduct(Product aemaskRayleighProduct, Product cloudDistanceProduct) {
        Map<String, Product> zmaxCloudInput = new HashMap<String, Product>(3);
        zmaxCloudInput.put("source", sourceProduct);
        zmaxCloudInput.put("ae_mask", aemaskRayleighProduct);
        zmaxCloudInput.put("distance", cloudDistanceProduct);
        Map<String, Object> zmaxCloudParameters = new HashMap<String, Object>(2);
        zmaxCloudParameters.put("aeMaskExpression", AdjacencyEffectMaskOp.AE_MASK_RAYLEIGH + ".aep");
        zmaxCloudParameters.put("distanceBandName", CloudDistanceOp.CLOUD_DISTANCE);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(ZmaxOp.class), zmaxCloudParameters,
                                 zmaxCloudInput);
    }

    private Product createZMaxProduct(Product aemaskRayleighProduct, Product coastDistanceProduct) {
        Map<String, Product> zmaxInput = new HashMap<String, Product>(3);
        zmaxInput.put("source", sourceProduct);
        zmaxInput.put("distance", coastDistanceProduct);
        zmaxInput.put("ae_mask", aemaskRayleighProduct);   // use the more extended mask here
        String aeMaskExpression = AdjacencyEffectMaskOp.AE_MASK_RAYLEIGH + ".aep";
        if (aeArea.correctOverLand()) {
            aeMaskExpression = "true";
        }
        Map<String, Object> zmaxParameters = new HashMap<String, Object>(2);
        zmaxParameters.put("aeMaskExpression", aeMaskExpression);
        zmaxParameters.put("distanceBandName", CoastDistanceOp.COAST_DISTANCE);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(ZmaxOp.class), zmaxParameters, zmaxInput);
    }

    private Product createCloudDistanceProduct(Product cloudClassificationProduct) {
        Map<String, Product> cloudDistanceInput = new HashMap<String, Product>(2);
        cloudDistanceInput.put("source", sourceProduct);
        cloudDistanceInput.put("cloud", cloudClassificationProduct);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(CloudDistanceOp.class), GPF.NO_PARAMS,
                                 cloudDistanceInput);
    }

    private Product createCoastDistanceProduct(Product landProduct) {
        Map<String, Product> coastDistanceInput = new HashMap<String, Product>(2);
        coastDistanceInput.put("source", sourceProduct);
        coastDistanceInput.put("land", landProduct);
        Map<String, Object> distanceParameters = new HashMap<String, Object>(4);
        distanceParameters.put("landExpression", "land_classif_flags.F_LANDCONS || land_classif_flags.F_ICE");
        distanceParameters.put("waterExpression", "land_classif_flags.F_LOINLD");
        distanceParameters.put("correctOverLand", aeArea.correctOverLand());
        distanceParameters.put("numDistances", 2);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(CoastDistanceOp.class), distanceParameters,
                                 coastDistanceInput);
    }

    private Product createAeMaskProduct(Product landProduct) {
        Map<String, Product> aemaskAerosolInput = new HashMap<String, Product>(2);
        aemaskAerosolInput.put("source", sourceProduct);
        aemaskAerosolInput.put("land", landProduct);
        Map<String, Object> aemaskAerosolParameters = new HashMap<String, Object>(5);
        aemaskAerosolParameters.put("landExpression", "land_classif_flags.F_LANDCONS || land_classif_flags.F_ICE");
        aemaskAerosolParameters.put("coastlineExpression", "l1_flags.COASTLINE");
        aemaskAerosolParameters.put("aeArea", aeArea);
        aemaskAerosolParameters.put("correctionMode", IcolConstants.AE_CORRECTION_MODE_AEROSOL);
        aemaskAerosolParameters.put("reshapedConvolution", reshapedConvolution);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(AdjacencyEffectMaskOp.class), aemaskAerosolParameters,
                                 aemaskAerosolInput);
    }

    private Product createAeMaskRayleighProduct(Product landProduct) {
        Map<String, Product> aemaskRayleighInput = new HashMap<String, Product>(2);
        aemaskRayleighInput.put("source", sourceProduct);
        aemaskRayleighInput.put("land", landProduct);
        Map<String, Object> aemaskRayleighParameters = new HashMap<String, Object>(5);
        aemaskRayleighParameters.put("landExpression", "land_classif_flags.F_LANDCONS || land_classif_flags.F_ICE");
        aemaskRayleighParameters.put("coastlineExpression", "l1_flags.COASTLINE");
        aemaskRayleighParameters.put("aeArea", aeArea);
        aemaskRayleighParameters.put("correctionMode", IcolConstants.AE_CORRECTION_MODE_RAYLEIGH);
        aemaskRayleighParameters.put("reshapedConvolution", reshapedConvolution);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(AdjacencyEffectMaskOp.class),
                                 aemaskRayleighParameters, aemaskRayleighInput);
    }

    private Product createRayleighProduct(Product cloudClassificationProduct, Product landProduct,
                                          Product fresnelProduct) {
        Map<String, Product> rayleighInput = new HashMap<String, Product>(4);
        rayleighInput.put("l1b", sourceProduct);
        rayleighInput.put("land", landProduct);
        rayleighInput.put("input", fresnelProduct);
        rayleighInput.put("cloud", cloudClassificationProduct);
        Map<String, Object> rayleighParameters = new HashMap<String, Object>(2);
        rayleighParameters.put("correctWater", true);
        rayleighParameters.put("exportRayCoeffs", true);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(RayleighCorrectionOp.class),
                                 rayleighParameters, rayleighInput);
    }

    private Product createFresnelProduct(Product gasProduct, Product landProduct) {
        Map<String, Product> fresnelInput = new HashMap<String, Product>(3);
        fresnelInput.put("l1b", sourceProduct);
        fresnelInput.put("land", landProduct);
        fresnelInput.put("input", gasProduct);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(FresnelCoefficientOp.class), GPF.NO_PARAMS, fresnelInput);
    }

    private Product createLandProduct(Product rad2reflProduct, Product gasProduct) {
        Map<String, Product> landInput = new HashMap<String, Product>(3);
        landInput.put("l1b", sourceProduct);
        landInput.put("rhotoa", rad2reflProduct);
        landInput.put("gascor", gasProduct);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(LandClassificationOp.class), GPF.NO_PARAMS,
                                 landInput);
    }

    private Product createGasProduct(Product rad2reflProduct, Product cloudClassificationProduct) {
        Map<String, Product> gasInput = new HashMap<String, Product>(3);
        gasInput.put("l1b", sourceProduct);
        gasInput.put("rhotoa", rad2reflProduct);
        gasInput.put("cloud", cloudClassificationProduct);
        Map<String, Object> gasParameters = new HashMap<String, Object>(2);
        gasParameters.put("correctWater", true);
        gasParameters.put("exportTg", true);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(GaseousCorrectionOp.class), gasParameters,
                                 gasInput);
    }

    private Product updateCloudClassificationProduct(Product cloudClassificationProduct) {
        if (cloudMaskProduct != null && cloudMaskExpression != null && !cloudMaskExpression.isEmpty()) {
            Map<String, Object> userCloudParameters = new HashMap<String, Object>(1);
            userCloudParameters.put("cloudMaskExpression", cloudMaskExpression);
            Map<String, Product> userCloudInput = new HashMap<String, Product>(2);
            userCloudInput.put("cloudClassification", cloudClassificationProduct);
            userCloudInput.put("cloudMask", cloudMaskProduct);
            cloudClassificationProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(MerisUserCloudOp.class),
                                                           userCloudParameters, userCloudInput);
        }
        return cloudClassificationProduct;
    }

    private Product createCloudClassificationProduct(Product rad2reflProduct, Product ctpProduct) {
        Map<String, Product> cloudInput = new HashMap<String, Product>(3);
        cloudInput.put("l1b", sourceProduct);
        cloudInput.put("rhotoa", rad2reflProduct);
        cloudInput.put("ctp", ctpProduct);
        return GPF.createProduct(
                OperatorSpi.getOperatorAlias(CloudClassificationOp.class), GPF.NO_PARAMS, cloudInput);
    }

    private Product createCtpProduct() {
        Map<String, Object> ctpParameters = new HashMap<String, Object>(2);
        ctpParameters.put("useUserCtp", useUserCtp);
        ctpParameters.put("userCtp", userCtp);
        return GPF.createProduct(OperatorSpi.getOperatorAlias(MerisCloudTopPressureOp.class), ctpParameters,
                                 sourceProduct);
    }

    private Product createRad2ReflProduct() {
        return GPF.createProduct(OperatorSpi.getOperatorAlias(Rad2ReflOp.class), GPF.NO_PARAMS, sourceProduct);
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(MerisOp.class);
        }
    }

}
