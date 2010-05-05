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


import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.ui.SingleTargetProductDialog;
import org.esa.beam.framework.gpf.ui.TargetProductSelector;
import org.esa.beam.framework.gpf.ui.TargetProductSelectorModel;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.meris.icol.meris.MerisOp;
import org.esa.beam.meris.icol.tm.TmOp;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by marcoz.
 *
 * @author marcoz
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
public class IcolDialog extends SingleTargetProductDialog {
    
    public static final String TITLE = "ICOL Processor - v2.0";
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
            showErrorDialog("Please specify either a MERIS L1b or a Landsat5 TM source product.");
            return false;
        }
        final String productType = sourceProduct.getProductType();
        final String productName = sourceProduct.getName();
        final int productNumBands = sourceProduct.getBandGroup().getNodeCount();
        if (!EnvisatConstants.MERIS_L1_TYPE_PATTERN.matcher(productType).matches() &&
                !(productType.equals("L1T") && productName.startsWith("L5") && productNumBands == 7)) {
            showErrorDialog("Please specify either a MERIS L1b or a Landsat5 TM GeoTIFF source product.");
            return false;
        }
        if (EnvisatConstants.MERIS_L1_TYPE_PATTERN.matcher(productType).matches()) {
            String[] tiePointGridNames = sourceProduct.getTiePointGridNames();
            List<String> gridNames = Arrays.asList(tiePointGridNames);
            if (!gridNames.contains(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME) ||
                    !gridNames.contains(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME) ||
                    !gridNames.contains(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME) ||
                    !gridNames.contains(EnvisatConstants.MERIS_VIEW_AZIMUTH_DS_NAME)) {
                showErrorDialog("The specify MERIS L1b source product doesn't contain tiepoints");
                return false;
            }
        }
        return true;
    }

    @Override
    protected Product createTargetProduct() throws Exception {
        Product outputProduct = null;
        final Product sourceProduct = model.getSourceProduct();
        String productType = sourceProduct.getProductType();
        final String productName = sourceProduct.getName();
        if ((productType.equals("L1T") && productName.startsWith("L5"))) {
            outputProduct = createLandsat5Product();
        } else if (EnvisatConstants.MERIS_L1_TYPE_PATTERN.matcher(productType).matches()) {
            outputProduct = createMerisOp();
        }
        return outputProduct;
    }
    
    private Product createLandsat5Product() throws OperatorException {
        final Product sourceProduct = model.getSourceProduct();
        Map<String, Object> parameters = model.getLandsat5Parameters();
        Product targetProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(TmOp.class)
              ,parameters, sourceProduct);
        return targetProduct;
    }
    
    private Product createMerisOp() throws Exception {
        final Product sourceProduct = model.getSourceProduct();
        Map<String, Object> parameters = model.getN1Parameters();
        addN1PathParamters(parameters);
        Product targetProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(MerisOp.class)
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
                targetProductSelectorModel.getValueContainer().setValue("productName", productName);
            } else {
                n1Productname = productName + ".N1";
            }
            File patchedN1File = new File(targetProductSelectorModel.getProductDir(), n1Productname);
            parameter.put("patchedFile", patchedN1File);
            targetProductSelectorModel.setFormatName("BEAM-DIMAP");
        }
    }
}
