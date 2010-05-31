package org.esa.beam.meris.icol.ui;

import java.util.HashMap;
import java.util.Map;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.ParameterDescriptorFactory;

import com.bc.ceres.binding.ValueContainer;

public class IcolModel {
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
    
    // AeAerosolOp
    @Parameter(defaultValue="false")
    boolean useUserAlphaAndAot = false;
    @Parameter(interval="[-2.1, -0.4]", defaultValue="-1")
    private double userAlpha = -1.0;
    @Parameter(interval="[0, 1.5]", defaultValue="0")
    private double userAot = 0.0;
   
    // ReverseRadianceOp
    @Parameter(defaultValue="true")
    private boolean correctForBoth = true;

    @Parameter(defaultValue="0", valueSet= {"0","1"})
    private int productType = 0;
    private Product sourceProduct;
    private ValueContainer valueContainer;
    

	public IcolModel() {
	    valueContainer = ValueContainer.createObjectBacked(this, new ParameterDescriptorFactory());
	}
	
    public Product getSourceProduct() {
        return sourceProduct;
    }
    
    public boolean isComputeRhoToa() {
        return (productType == 0);
    }
	
    public ValueContainer getValueContainer() {
        return valueContainer;
    }
    
    public Map<String, Object> getRhoToaParameters() {
        HashMap<String, Object> params = new HashMap<String, Object>();
        configAeAerosolOp(params);
        configReverseRhoToaOp(params);
        return params;
    }
    
    public Map<String, Object> getN1Parameters() {
        HashMap<String, Object> params = new HashMap<String, Object>();
        configAeAerosolOp(params);
        configN1(params);
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
    
    private void configAeAerosolOp(HashMap<String, Object> params) {
        params.put("useUserAlphaAndAot", useUserAlphaAndAot);
        params.put("userAlpha", userAlpha);
        params.put("userAot", userAot);
    }
    
    private void configN1(HashMap<String, Object> params) {
        params.put("correctForBoth", correctForBoth);
    }
}
