package org.esa.beam.meris.icol;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.util.ProductUtils;

import javax.media.jai.JAI;
import javax.media.jai.RenderedOp;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;

/**
 * Test operator: sets values in all input bands (except flag bands) to constant value.
 *
 * @author Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
public class ConstantValueOp extends MerisBasisOp {

    @SourceProduct(alias="source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    public static double CONSTANT_VALUE = 0.2;

    public void initialize() throws OperatorException {

        targetProduct = createTargetProduct(sourceProduct, sourceProduct.getName() + "_const", sourceProduct.getProductType());

        String[] sourceNames = sourceProduct.getBandNames();
        for (String name : sourceNames) {
            Band sourceBand = sourceProduct.getBand(name);
            RenderedImage sourceImage = sourceProduct.getBand(name).getSourceImage();
            if (!sourceBand.isFlagBand()) {
                Band targetBand = targetProduct.addBand(name, sourceBand.getDataType());
                targetBand.setSpectralBandIndex(sourceBand.getSpectralBandIndex());
                targetBand.setNoDataValue(sourceBand.getNoDataValue());
                RenderedImage image1 = createConstantImage(sourceImage);
                targetBand.setSourceImage(image1);
            } 
        }
    }

    public Product createTargetProduct(Product sourceProduct, String name, String type) {
        final int sceneWidth = sourceProduct.getSceneRasterWidth();
        final int sceneHeight = sourceProduct.getSceneRasterHeight();

        Product targetProduct = new Product(name, type, sceneWidth, sceneHeight);

        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        ProductUtils.copyFlagBands(sourceProduct, targetProduct);

        return targetProduct;
    }

    private static RenderedOp createConstantImage(RenderedImage src) {

        double[] low, high, map;

        low = new double[1];
        high = new double[1];
        map = new double[1];

        low[0] = Double.MIN_VALUE;
        high[0] = Double.MAX_VALUE;
        map[0] = CONSTANT_VALUE;

        // use JAI threshold operation.
        ParameterBlock pb = new ParameterBlock();
        pb.addSource(src);
        pb.add(low);
        pb.add(high);
        pb.add(map);
        RenderedOp dest = JAI.create("threshold", pb);

        return dest;

    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ConstantValueOp.class);
        }
    }
}
