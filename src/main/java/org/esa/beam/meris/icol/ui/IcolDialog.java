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
import org.esa.beam.framework.gpf.ui.OperatorMenu;
import org.esa.beam.framework.gpf.ui.OperatorParameterSupport;
import org.esa.beam.framework.gpf.ui.SingleTargetProductDialog;
import org.esa.beam.framework.gpf.ui.TargetProductSelectorModel;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.gpf.operators.meris.N1PatcherOp;
import org.esa.beam.meris.icol.meris.MerisOp;
import org.esa.beam.meris.icol.tm.TmConstants;
import org.esa.beam.meris.icol.tm.TmOp;

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

    public static final String TITLE = "ICOL Processor - v2.7.3";
    private IcolForm form;
    private IcolModel model;

    public IcolDialog(AppContext appContext) {
        super(appContext, TITLE, ID_APPLY_CLOSE_HELP, "icolProcessor",
              TargetProductSelectorModel.createEnvisatTargetProductSelectorModel());

        model = new IcolModel();
        form = new IcolForm(appContext, model, getTargetProductSelector());

        OperatorSpi operatorSpi = GPF.getDefaultInstance().getOperatorSpiRegistry().getOperatorSpi("icol.Meris");
        final OperatorParameterSupport parameterSupport = new OperatorParameterSupport(operatorSpi.getOperatorClass(),
                                                                                       null,
                                                                                       null,
                                                                                       null);
        OperatorMenu menuSupport = new OperatorMenu(this.getJDialog(), operatorSpi.getOperatorClass(),
                                                    parameterSupport, null);
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
            showErrorDialog("Please specify either a MERIS L1b or a Landsat5 TM source product.");
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
        //    - Landsat TM5 GeoTIFF  (L1T)
        //    - Landsat TM5 Icol 'Geometry' product (L1G)
        if (!(EnvisatConstants.MERIS_L1_TYPE_PATTERN.matcher(productType).matches()) &&
            !(productType.equals(TmConstants.LANDSAT_GEOTIFF_PRODUCT_TYPE_PREFIX)) &&
            !(productType.startsWith(TmConstants.LANDSAT_GEOMETRY_PRODUCT_TYPE_PREFIX))) {
            showErrorDialog("Please specify either a MERIS L1b or a Landsat5 TM GeoTIFF or Geometry source product.");
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
        if ((productType.equals(TmConstants.LANDSAT_GEOTIFF_PRODUCT_TYPE_PREFIX)) ||
            (productType.equals(TmConstants.LANDSAT_DIMAP_SUBSET_PRODUCT_TYPE)) ||
            (productType.startsWith(TmConstants.LANDSAT_GEOMETRY_PRODUCT_TYPE_PREFIX))) {
            outputProduct = createLandsat5Product();
        } else if (EnvisatConstants.MERIS_L1_TYPE_PATTERN.matcher(productType).matches()) {
            outputProduct = createMerisOp();
        }
        return outputProduct;
    }

    private Product createLandsat5Product() throws OperatorException {
        final Product sourceProduct = model.getSourceProduct();
        Map<String, Object> parameters = model.getLandsat5Parameters();
        return GPF.createProduct(OperatorSpi.getOperatorAlias(TmOp.class), parameters, sourceProduct);
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

}
