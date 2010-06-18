package org.esa.beam.meris.icol.tm;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.meris.icol.utils.OperatorUtils;
import org.esa.beam.util.ProductUtils;

import javax.media.jai.Interpolation;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.ScaleDescriptor;
import javax.media.jai.operator.SubtractDescriptor;
import java.awt.image.RenderedImage;

/**
 * @author Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
public class TmUpscaleToOriginalOp extends TmBasisOp {

    @SourceProduct(alias = "l1b")
    private Product sourceProduct;
    @SourceProduct(alias = "aeTotal")
    private Product aeTotalProduct;
    @TargetProduct
    private Product targetProduct;

    @Override
    public void initialize() throws OperatorException {
        float xScale = (float) sourceProduct.getSceneRasterWidth() / aeTotalProduct.getSceneRasterWidth();
        float yScale = (float) sourceProduct.getSceneRasterHeight() / aeTotalProduct.getSceneRasterHeight();
        targetProduct = OperatorUtils.createCompatibleProduct(sourceProduct, "upscale_" + aeTotalProduct.getName(), "UPSCALE");

        for (int i=0; i<sourceProduct.getNumBands(); i++) {
            Band sourceBand = sourceProduct.getBandAt(i);

            Band targetBand;
            int dataType = sourceBand.getDataType();
            final String srcBandName = sourceBand.getName();
            final int length = srcBandName.length();
            final String radianceBandSuffix = srcBandName.substring(length - 1, length);
            final int radianceBandIndex = Integer.parseInt(radianceBandSuffix);

            if (radianceBandIndex != 6) {
                dataType = ProductData.TYPE_FLOAT32;
            }
            targetBand = targetProduct.addBand(srcBandName, dataType);

            if (radianceBandIndex != 6) {
                Band aeTotalBand = aeTotalProduct.getBand(TmAeMergeOp.AE_TOTAL + "_" + radianceBandIndex);

                RenderedImage sourceImage = sourceBand.getSourceImage();
                RenderedImage aeTotalImage = aeTotalBand.getSourceImage();

                RenderedOp upscaledAeTotalImage = ScaleDescriptor.create(aeTotalImage,
                                                                         xScale,
                                                                         yScale,
                                                                         0.0f, 0.0f,
                                                                         Interpolation.getInstance(
                                                                                 Interpolation.INTERP_BILINEAR),
                                                                         null);

                RenderedOp finalAeCorrectedImage = SubtractDescriptor.create(sourceImage, upscaledAeTotalImage, null);
                targetBand.setSourceImage(finalAeCorrectedImage);
//                targetBand.setSourceImage(upscaledAeTotalImage);
            } else {
                targetBand.setSourceImage(sourceBand.getSourceImage());
            }
        }
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(TmUpscaleToOriginalOp.class);
        }
    }
}