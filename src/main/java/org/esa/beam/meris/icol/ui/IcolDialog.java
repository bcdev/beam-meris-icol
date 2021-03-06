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


import com.bc.ceres.binding.ConversionException;
import com.bc.ceres.binding.ValidationException;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.ui.*;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.gpf.operators.meris.N1PatcherOp;
import org.esa.beam.meris.icol.IcolConstants;
import org.esa.beam.meris.icol.landsat.common.LandsatConstants;
import org.esa.beam.meris.icol.landsat.etm.EtmOp;
import org.esa.beam.meris.icol.landsat.tm.TmOp;
import org.esa.beam.meris.icol.meris.MerisOp;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by marcoz.
 *
 * @author marcoz
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
public class IcolDialog extends SingleTargetProductDialog {

    public static final String TITLE = "ICOL Processor - v2.10-SNAPSHOT";
    private IcolForm form;
    private IcolModel model;

    public IcolDialog(AppContext appContext, final String helpID) {
        super(appContext, TITLE, ID_APPLY_CLOSE_HELP, helpID,
              TargetProductSelectorModel.createEnvisatTargetProductSelectorModel());

        model = new IcolModel();
        form = new IcolForm(appContext, model, getTargetProductSelector());

        OperatorSpi operatorSpi = GPF.getDefaultInstance().getOperatorSpiRegistry().getOperatorSpi("icol.Meris");
        ParameterUpdater parameterUpdater = new IcolParameterUpdater();
        final OperatorParameterSupport parameterSupport = new OperatorParameterSupport(operatorSpi.getOperatorClass(),
                                                                                       null,
                                                                                       null,
                                                                                       parameterUpdater);
        OperatorMenu menuSupport = new OperatorMenu(this.getJDialog(), operatorSpi.getOperatorClass(),
                                                    parameterSupport, helpID);
        getJDialog().setJMenuBar(menuSupport.createDefaultMenu());
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
            showErrorDialog("Please specify either a MERIS L1b, a Landsat5 TM or a Landsat7 ETM+ source product.");
            return false;
        }
        if (form.isEnvisatOutputFormatSelected() && !form.isEnvisatSourceProduct(sourceProduct)) {
            showErrorDialog("If " + EnvisatConstants.ENVISAT_FORMAT_NAME + " is selected as output format the " +
                                    "source product must be in the same format.");
            return false;
        }

        final String productType = sourceProduct.getProductType();
        // input product must be either:
        //    - MERIS L1b
        //    - MERIS L1 AMORGOS corrected (L1N)
        //    - Landsat5 TM GeoTIFF
        //    - Landsat7 ETM+ GeoTIFF
        if (!(EnvisatConstants.MERIS_L1_TYPE_PATTERN.matcher(productType).matches()) &&
                !(IcolConstants.MERIS_L1_AMORGOS_TYPE_PATTERN.matcher(productType).matches()) &&
                !(IcolConstants.MERIS_L1_CC_L1P_TYPE_PATTERN.matcher(productType).matches()) &&
                !(isValidLandsat5ProductType(productType)) &&
                !(isValidLandsat7ProductType(productType)) &&
                !(productType.startsWith(LandsatConstants.LANDSAT_DOWNSCALED_PRODUCT_TYPE_PREFIX))) {
            showErrorDialog("Please specify either a MERIS L1b, a Landsat5 TM or a Landsat7 ETM+ source product.");
            return false;
        }
        if (EnvisatConstants.MERIS_L1_TYPE_PATTERN.matcher(productType).matches()) {
            String[] tiePointGridNames = sourceProduct.getTiePointGridNames();
            List<String> gridNames = Arrays.asList(tiePointGridNames);
            if (!gridNames.contains(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME) ||
                    !gridNames.contains(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME) ||
                    !gridNames.contains(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME) ||
                    !gridNames.contains(EnvisatConstants.MERIS_VIEW_AZIMUTH_DS_NAME)) {
                showErrorDialog("The specify MERIS L1b source product doesn't contain tie-points");
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
        if (isValidLandsat5ProductType(productType)) {
            outputProduct = createLandsat5Product();
        } else if (isValidLandsat7ProductType(productType)) {
            outputProduct = createLandsat7Product();
        }  else if (EnvisatConstants.MERIS_L1_TYPE_PATTERN.matcher(productType).matches() ||
                IcolConstants.MERIS_L1_CC_L1P_TYPE_PATTERN.matcher(productType).matches() ||
                IcolConstants.MERIS_L1_AMORGOS_TYPE_PATTERN.matcher(productType).matches()) {
            outputProduct = createMerisOp();
        }
        return outputProduct;
    }

    private boolean isValidLandsat5ProductType(String productType) {
        return (productType.toUpperCase().startsWith(LandsatConstants.LANDSAT5_PRODUCT_TYPE_PREFIX));
    }

    private boolean isValidLandsat7ProductType(String productType) {
        return (productType.toUpperCase().startsWith(LandsatConstants.LANDSAT7_PRODUCT_TYPE_PREFIX));
    }

    private Product createLandsat5Product() throws OperatorException {
        final Product sourceProduct = model.getSourceProduct();
        Map<String, Object> parameters = model.getLandsatParameters();
        return GPF.createProduct(OperatorSpi.getOperatorAlias(TmOp.class), parameters, sourceProduct);
    }

    private Product createLandsat7Product() throws OperatorException {
        final Product sourceProduct = model.getSourceProduct();
        Map<String, Object> parameters = model.getLandsatParameters();
        return GPF.createProduct(OperatorSpi.getOperatorAlias(EtmOp.class), parameters, sourceProduct);
    }


    private Product createMerisOp() throws Exception {
        Map<String, Product> sourceProducts = new HashMap<String, Product>(2);
        Map<String, Object> parameters = model.getMerisParameters();
        sourceProducts.put("sourceProduct", model.getSourceProduct());
        final Product cloudProduct = model.getCloudMaskProduct();
        if (cloudProduct != null && parameters.get("cloudMaskExpression") != null) {
            sourceProducts.put("cloudMaskProduct", cloudProduct);
        }
        final Product merisProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(MerisOp.class), parameters,
                                                       sourceProducts);
        if (form.isEnvisatOutputFormatSelected()) {
            Map<String, Product> n1PatcherInput = new HashMap<String, Product>(2);
            n1PatcherInput.put("n1", model.getSourceProduct());
            n1PatcherInput.put("input", merisProduct);
            Map<String, Object> n1Params = new HashMap<String, Object>(1);
            n1Params.put("patchedFile", getTargetProductSelector().getModel().getProductFile());
            return GPF.createProduct(OperatorSpi.getOperatorAlias(N1PatcherOp.class), n1Params,
                                     n1PatcherInput);
        } else {
            return merisProduct;

        }

    }

    private class IcolParameterUpdater implements ParameterUpdater {

        @Override
        public void handleParameterSaveRequest(Map<String, Object> parameterMap) {
            form.updateParameterMap(parameterMap);
        }

        @Override
        public void handleParameterLoadRequest(Map<String, Object> parameterMap) throws ValidationException, ConversionException {
            form.updateFormModel(parameterMap);
        }
    }

}
