package org.esa.beam.meris.icol.utils;

import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.util.ProductUtils;

import java.util.ArrayList;
import java.util.List;


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

    
    public static Band[] addBandGroup(Product srcProduct, int numSrcBands, int[] bandsToSkip, Product targetProduct, String targetPrefix, double noDataValue, boolean compactTargetArray) {
        int numTargetBands = numSrcBands;
        if (compactTargetArray && bandsToSkip.length > 0) {
            numTargetBands -= bandsToSkip.length;
        }
        Band[] targetBands = new Band[numTargetBands];
        int targetIndex = 0;
        for (int srcIndex = 0; srcIndex < numSrcBands; srcIndex++) {
            if (!IcolUtils.isIndexToSkip(srcIndex, bandsToSkip)) {
                Band srcBand = srcProduct.getBandAt(srcIndex);
                Band targetBand = targetProduct.addBand(targetPrefix + "_" + (srcIndex + 1), ProductData.TYPE_FLOAT32);
                ProductUtils.copySpectralBandProperties(srcBand, targetBand);
                targetBand.setNoDataValueUsed(true);
                targetBand.setNoDataValue(noDataValue);
                targetBands[targetIndex] = targetBand;
                targetIndex++;
            } else {
                // skip this src index
                if (!compactTargetArray) {
                    targetIndex++;
                }
            }
        }
        return targetBands;
    }
}
