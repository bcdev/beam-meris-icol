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
package org.esa.beam.meris.icol.ui;


import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.ui.SingleTargetProductDialog;
import org.esa.beam.framework.gpf.ui.TargetProductSelector;
import org.esa.beam.framework.gpf.ui.TargetProductSelectorModel;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.meris.icol.IcolN1Op;
import org.esa.beam.meris.icol.IcolRhoToaOp;

/**
 * Created by marcoz.
 *
 * @author marcoz
 * @version $Revision: $ $Date: $
 */
public class IcolDialog extends SingleTargetProductDialog {
    
    public static final String TITLE = "ICOL Processor";
    private IcolForm form;
    private IcolModel model;

    public IcolDialog(AppContext appContext)  {
        super(appContext, TITLE, "icolProcessor");
        
        model = new IcolModel();
        form = new IcolForm(appContext, model, getTargetProductSelector());
    }
    
    @Override
    public int show() {
        form.prepareShow();
        setContent(form);
        return super.show();
    }

    @Override
    public void hide() {
        form.prepareHide();
        super.hide();
    }
    
    @Override
    protected boolean verifyUserInput() {
        Product sourceProduct = model.getSourceProduct();
        if (sourceProduct == null) {
            showErrorDialog("Please specify a MERIS L1b source product.");
            return false;
        }
        final String productType = sourceProduct.getProductType();
        if (!EnvisatConstants.MERIS_L1_TYPE_PATTERN.matcher(productType).matches()) {
            showErrorDialog("Please specify a MERIS L1b source product.");
            return false;
        }
        String[] tiePointGridNames = sourceProduct.getTiePointGridNames();
        List<String> gridNames = Arrays.asList(tiePointGridNames);
        if (!gridNames.contains(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME) ||
                !gridNames.contains(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME) ||
                !gridNames.contains(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME) ||
                !gridNames.contains(EnvisatConstants.MERIS_VIEW_AZIMUTH_DS_NAME)) {
            showErrorDialog("The specify MERIS L1b source product doesn't contain tiepoints");
            return false;
        }
        return true;
    }

    @Override
    protected Product createTargetProduct() throws Exception {
        Product outputProduct = null;
        if(model.isComputeRhoToa()) {
            outputProduct = ceateRhotoaProduct();
        } else {
            outputProduct = ceateN1Product();
        }
        return outputProduct;
    }
    
    private Product ceateRhotoaProduct() throws OperatorException {
        final Product sourceProduct = model.getSourceProduct();
        Map<String, Object> parameters = model.getRhoToaParameters();
        Product targetProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(IcolRhoToaOp.class)
              ,parameters, sourceProduct);
        return targetProduct;
    }
    
    private Product ceateN1Product() throws Exception {
        final Product sourceProduct = model.getSourceProduct();
        Map<String, Object> parameters = model.getN1Parameters();
        addN1PathParamters(parameters);
        Product targetProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(IcolN1Op.class)
              ,parameters, sourceProduct);
        return targetProduct;
    }
    
    public void addN1PathParamters(Map<String, Object> parameter) throws Exception {
        TargetProductSelectorModel targetProductSelectorModel = getTargetProductSelector().getModel();
        TargetProductSelector targetProductSelector = getTargetProductSelector();
        String formatName = (String) targetProductSelector.getFormatNameComboBox().getSelectedItem();
        if (formatName.equals("N1")) {
            String productName = targetProductSelectorModel.getProductName();
            String n1Productname;
            if (productName.endsWith(".N1")) {
                n1Productname = productName;
                productName = productName.substring(0, productName.length() - ".N1".length());
                targetProductSelectorModel.getValueContainer().getModel("productName").setValueFromText(productName);
            } else {
                n1Productname = productName + ".N1";
            }
            File patchedN1File = new File(targetProductSelectorModel.getProductDir(), n1Productname);
            parameter.put("patchedFile", patchedN1File);
            targetProductSelectorModel.setFormatName("BEAM-DIMAP");
        }
    }
}
