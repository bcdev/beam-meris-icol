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
package org.esa.beam.meris.icol;

import java.util.HashMap;
import java.util.Map;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.meris.brr.CloudClassificationOp;
import org.esa.beam.meris.brr.GaseousCorrectionOp;
import org.esa.beam.meris.brr.LandClassificationOp;
import org.esa.beam.meris.brr.Rad2ReflOp;
import org.esa.beam.meris.brr.RayleighCorrectionOp;


@OperatorMetadata(alias = "IcolRhoToa",
        version = "1.0",
        authors = "Marco Zühlke",
        copyright = "(c) 2007 by Brockmann Consult",
        description = "Performs a correction of the adjacency effect and computes rho TOA.")
public class IcolRhoToaOp extends Operator {

    @SourceProduct(description = "The source product.")
    Product sourceProduct;

    @TargetProduct(description = "The target product.")
    Product targetProduct;
    
    // AeAerosolOp
    @Parameter
    private boolean exportSeparateDebugBands = false;
    @Parameter
    private boolean useUserAlphaAndAot = false;
    @Parameter(interval="[-2.1, -0.4]", defaultValue="-1")
    private double userAlpha;
    @Parameter(interval="[0, 1.5]")
    private double userAot;
    
    // ReverseRhoToaOp
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
    
    @Override
    public void initialize() throws OperatorException {
        sourceProduct.setPreferredTileSize(16, 16);
        
        Map<String, Object> emptyParams = new HashMap<String, Object>();
        Product rad2reflProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(Rad2ReflOp.class), emptyParams, sourceProduct);
 
        Map<String, Product> cloudInput = new HashMap<String, Product>(2);
        cloudInput.put("l1b", sourceProduct);
        cloudInput.put("rhotoa", rad2reflProduct);
        Product cloudProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(CloudClassificationOp.class), emptyParams, cloudInput);
        
        Map<String, Product> gasInput = new HashMap<String, Product>(3);
        gasInput.put("l1b", sourceProduct);
        gasInput.put("rhotoa", rad2reflProduct);
        gasInput.put("cloud", cloudProduct);
        Map<String, Object> gasParameters = new HashMap<String, Object>(2);
        gasParameters.put("correctWater", true);
        gasParameters.put("exportTg", true);
        Product gasProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(GaseousCorrectionOp.class), gasParameters, gasInput);
        
        Map<String, Product> landInput = new HashMap<String, Product>(2);
        landInput.put("l1b", sourceProduct);
        landInput.put("gascor", gasProduct);
        Product landProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(LandClassificationOp.class), emptyParams, landInput);
        
        Map<String, Product> fresnelInput = new HashMap<String, Product>(3);
        fresnelInput.put("l1b", sourceProduct);
        fresnelInput.put("land", landProduct);
        fresnelInput.put("input", gasProduct);
        Product fresnelProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(FresnelCoefficientOp.class), emptyParams, fresnelInput);
        
        Map<String, Product> rayleighInput = new HashMap<String, Product>(3);
        rayleighInput.put("l1b", sourceProduct);
        rayleighInput.put("land", landProduct);
        rayleighInput.put("input", fresnelProduct);
        Map<String, Object> rayleighParameters = new HashMap<String, Object>(2);
        rayleighParameters.put("correctWater", true);
        rayleighParameters.put("exportRayCoeffs", true);
        Product rayleighProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(RayleighCorrectionOp.class), rayleighParameters, rayleighInput);
        
        Map<String, Product> aemaskInput = new HashMap<String, Product>(2);
        aemaskInput.put("l1b", sourceProduct);
        aemaskInput.put("land", landProduct);
        Map<String, Object> aemaskParameters = new HashMap<String, Object>(1);
        aemaskParameters.put("landExpression", "land_classif_flags.F_LANDCONS");
        Product aemaskProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(AEMaskOp.class), aemaskParameters, aemaskInput);
        
        Product landDistanceProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(LandDistanceOp.class), aemaskParameters, aemaskInput);
        
        Map<String, Product> zmaxInput = new HashMap<String, Product>(3);
        zmaxInput.put("l1b", sourceProduct);
        zmaxInput.put("landDistance", landDistanceProduct);
        zmaxInput.put("ae_mask", aemaskProduct);
        Product zmaxProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(ZmaxOp.class), emptyParams, zmaxInput);
        
        Map<String, Product> aeRayInput = new HashMap<String, Product>(5);
        aeRayInput.put("l1b", sourceProduct);
        aeRayInput.put("aemask", aemaskProduct);
        aeRayInput.put("ray1b", rayleighProduct);
        aeRayInput.put("rhoNg", gasProduct);
        aeRayInput.put("zmax", zmaxProduct);
        Product aeRayProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(AeRayleighOp.class), emptyParams, aeRayInput);
        
        Map<String, Product> aeAerInput = new HashMap<String, Product>(4);
        aeAerInput.put("l1b", sourceProduct);
        aeAerInput.put("aemask", aemaskProduct);
        aeAerInput.put("zmax", zmaxProduct);
        aeAerInput.put("input", aeRayProduct);
        Map<String, Object> aeAerosolParams = new HashMap<String, Object>(1);
        aeAerosolParams.put("exportSeparateDebugBands", exportSeparateDebugBands);
        aeAerosolParams.put("useUserAlphaAndAot", useUserAlphaAndAot);
        aeAerosolParams.put("userAlpha", userAlpha);
        aeAerosolParams.put("userAot", userAot);
        Product aeAerProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(AeAerosolOp.class), aeAerosolParams, aeAerInput);
        
        Map<String, Product> reverseRhoToaInput = new HashMap<String, Product>(8);
        reverseRhoToaInput.put("l1b", sourceProduct);
        reverseRhoToaInput.put("rhotoa", rad2reflProduct);
        reverseRhoToaInput.put("land", landProduct);
        reverseRhoToaInput.put("cloud", cloudProduct);
        reverseRhoToaInput.put("aemask", aemaskProduct);
        reverseRhoToaInput.put("gascor", gasProduct);
        reverseRhoToaInput.put("ae_ray", aeRayProduct);
        reverseRhoToaInput.put("ae_aerosol", aeAerProduct);
        Map<String, Object> reverseRhoToaParams = new HashMap<String, Object>(1);
        reverseRhoToaParams.put("exportRhoToa", exportRhoToa);
        reverseRhoToaParams.put("exportRhoToaRayleigh", exportRhoToaRayleigh);
        reverseRhoToaParams.put("exportRhoToaAerosol", exportRhoToaAerosol);
        reverseRhoToaParams.put("exportAeRayleigh", exportAeRayleigh);
        reverseRhoToaParams.put("exportAeAerosol", exportAeAerosol);
        reverseRhoToaParams.put("exportAlphaAot", exportAlphaAot);
        Product reverseRhoToaProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(ReverseRhoToaOp.class), reverseRhoToaParams, reverseRhoToaInput);
        
        targetProduct = reverseRhoToaProduct;
    }
    
    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(IcolRhoToaOp.class);
        }
    }

}
