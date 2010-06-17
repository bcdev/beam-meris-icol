package org.esa.beam.meris.icol.tm;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.util.ProductUtils;

import javax.media.jai.Interpolation;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.ScaleDescriptor;
import java.awt.image.RenderedImage;

/**
 * @author Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
public class TmUpscaleToOriginalOp extends TmBasisOp {

    // todo: provide the original source product
//    @SourceProduct(alias = "original")
//    private Product originalProduct;
    @SourceProduct(alias = "aeTotal")
    private Product aeTotalProduct;
    @TargetProduct
    private Product targetProduct;
    @Parameter
    private int tmOrigProductWidth;
    @Parameter
    private int tmOrigProductHeight;

    @Override
    public void initialize() throws OperatorException {
        float xScale = (float) tmOrigProductWidth / aeTotalProduct.getSceneRasterWidth();
        float yScale = (float) tmOrigProductHeight / aeTotalProduct.getSceneRasterHeight();
        targetProduct = createUpscaledProduct(aeTotalProduct, "upscale_" + aeTotalProduct.getName(), "UPSCALE", xScale, yScale);

        for (Band band:aeTotalProduct.getBands()) {
            Band targetBand;
            if (band.isFlagBand()) {
                targetBand = targetProduct.addBand(band.getFlagCoding().getName(), band.getDataType());
            } else {
                targetBand = targetProduct.addBand(band.getName(), band.getDataType());
            }

            if (band.isFlagBand()) {
                targetBand.setSampleCoding(band.getFlagCoding());
            }

            RenderedImage sourceImage = band.getSourceImage();
            System.out.printf("Source, size: %d x %d\n", sourceImage.getWidth(), sourceImage.getHeight());
            RenderedOp upscaledImage = ScaleDescriptor.create(sourceImage,
                                          xScale,
                                          yScale,
                                          0.0f, 0.0f,
                                          Interpolation.getInstance(Interpolation.INTERP_BILINEAR),
                                          null);
            System.out.printf("Upscaled, size: %d x %d\n", upscaledImage.getWidth(), upscaledImage.getHeight());

            // todo: add (subtract) correction term in original resolution. write result to final target product.
//            SubtractDescriptor.create(...)

            targetBand.setSourceImage(upscaledImage);
        }
    }

    private Product createUpscaledProduct(Product sourceProduct, String name, String type, float xScale, float yScale) {
        final int sceneWidth = Math.round(xScale * sourceProduct.getSceneRasterWidth());
        final int sceneHeight = Math.round(yScale * sourceProduct.getSceneRasterHeight());

        Product targetProduct = new Product(name, type, sceneWidth, sceneHeight);
        copyProductTrunk(sourceProduct, targetProduct);
        ProductUtils.copyFlagCodings(sourceProduct, targetProduct);

        return targetProduct;
    }


    public static class Spi extends OperatorSpi {

        public Spi() {
            super(TmUpscaleToOriginalOp.class);
        }
    }
}