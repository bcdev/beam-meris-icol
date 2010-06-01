package org.esa.beam.meris.icol.utils;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.util.ProductUtils;


public class OperatorUtils {
    private OperatorUtils() {
    }

    /**
     * creates a new product with the same size
     *
     * @param sourceProduct
     * @param name
     * @param type
     * @return targetProduct
     */
    public static Product createCompatibleProduct(Product sourceProduct, String name, String type) {
        final int sceneWidth = sourceProduct.getSceneRasterWidth();
        final int sceneHeight = sourceProduct.getSceneRasterHeight();

        Product targetProduct = new Product(name, type, sceneWidth, sceneHeight);
        copyProductBase(sourceProduct, targetProduct);
        return targetProduct;
    }

    /**
     * Copies geocoding and the start and stop time.
     *
     * @param sourceProduct
     * @param targetProduct
     */
    public static void copyProductBase(Product sourceProduct,
                                 Product targetProduct) {
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
    }
}
