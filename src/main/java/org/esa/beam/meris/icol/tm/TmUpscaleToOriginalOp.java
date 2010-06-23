package org.esa.beam.meris.icol.tm;

import com.bc.ceres.glevel.MultiLevelImage;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.meris.icol.utils.OperatorUtils;
import org.esa.beam.util.ProductUtils;

import javax.media.jai.Interpolation;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.AddDescriptor;
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
    @SourceProduct(alias = "geometry")
    private Product geometryProduct;
    @SourceProduct(alias = "corrected")
    private Product correctedProduct;
    @TargetProduct
    private Product targetProduct;

    @Override
    public void initialize() throws OperatorException {
        float xScale = (float) sourceProduct.getSceneRasterWidth() / correctedProduct.getSceneRasterWidth();
        float yScale = (float) sourceProduct.getSceneRasterHeight() / correctedProduct.getSceneRasterHeight();
        targetProduct = OperatorUtils.createCompatibleProduct(sourceProduct, "upscale_" + correctedProduct.getName(), "UPSCALE");

        for (int i=0; i<sourceProduct.getNumBands(); i++) {
            Band sourceBand = sourceProduct.getBandAt(i);

            Band targetBand;
            int dataType = sourceBand.getDataType();
            final String srcBandName = sourceBand.getName();
            if (srcBandName.startsWith("radiance")) {
                final int length = srcBandName.length();
                final String radianceBandSuffix = srcBandName.substring(length - 1, length);
                final int radianceBandIndex = Integer.parseInt(radianceBandSuffix);

                Band diffBand = null;
                Band origBand = null;
                if (radianceBandIndex != 6) {
                    dataType = ProductData.TYPE_FLOAT32;
                    diffBand = targetProduct.addBand(srcBandName + "_diff", dataType);
                    origBand = targetProduct.addBand(srcBandName + "_orig", sourceBand.getDataType());
                    ProductUtils.copyRasterDataNodeProperties(sourceBand, origBand);
                }
                targetBand = targetProduct.addBand(srcBandName, dataType);

                MultiLevelImage sourceImage = sourceBand.getSourceImage();
                if (radianceBandIndex != 6) {
                    Band correctedBand = correctedProduct.getBand(sourceBand.getName());
                    Band geometryBand = geometryProduct.getBand(sourceBand.getName());

                    RenderedImage geometryImage = geometryBand.getSourceImage();
                    RenderedImage correctedImage = correctedBand.getSourceImage();
                    RenderedOp diffImage = SubtractDescriptor.create(geometryImage, correctedImage, null);

                    RenderedOp upscaledDiffImage = ScaleDescriptor.create(diffImage,
                                                                             xScale,
                                                                             yScale,
                                                                             0.0f, 0.0f,
                                                                             Interpolation.getInstance(
                                                                                     Interpolation.INTERP_BILINEAR),
                                                                             null);
                    origBand.setSourceImage(sourceImage);
                    diffBand.setSourceImage(upscaledDiffImage);
                    RenderedOp finalAeCorrectedImage = SubtractDescriptor.create(sourceBand.getGeophysicalImage(), upscaledDiffImage, null);
                    targetBand.setSourceImage(finalAeCorrectedImage);
                } else {
                    targetBand.setSourceImage(sourceImage);
                }
            }
        }
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(TmUpscaleToOriginalOp.class);
        }
    }
}