package org.esa.beam.meris.icol;

import com.bc.ceres.binding.ConversionException;
import com.bc.ceres.binding.Converter;
import com.bc.ceres.jai.operator.DFTConvolveDescriptor;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.StringUtils;

import javax.media.jai.BorderExtender;
import javax.media.jai.JAI;
import javax.media.jai.KernelJAI;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.ConvolveDescriptor;
import java.awt.RenderingHints;
import java.awt.image.RenderedImage;


/**
 * Operator performing the convolution step with JAI image convolution in Fourier space.
 *
 * CURRENTLY NOT USED! TO BE REMOVED FROM PROJECT IF RESHAPED CONVOLUTION IS AGREED TO BE USED
 *
 * @author Marco Zuehlke, Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
@OperatorMetadata(alias = "Meris.DftConvolve",
        version = "1.0",
        internal = true,
        authors = "OD,NF",
        copyright = "(c) 2009 by Brockmann Consult",
        description = "Convolves reflectances using a given kernel.")
public class DFTConvolutionOp extends Operator {


    @SourceProduct
    private Product sourceProduct;

    @TargetProduct
    private Product targetProduct;

    @Parameter(notNull = true, notEmpty = true)
    private String namePrefix;

    @Parameter(converter = KernelConverter.class, notNull = true)
    private KernelJAI kernel;

    @Parameter(defaultValue = "false")
    private boolean useFT;

    @Override
    public void initialize() throws OperatorException {

        BorderExtender borderExtender = BorderExtender.createInstance(BorderExtender.BORDER_COPY);
        RenderingHints renderingHints = new RenderingHints(JAI.KEY_BORDER_EXTENDER, borderExtender);
        targetProduct = new Product("P", "PT", sourceProduct.getSceneRasterWidth(), sourceProduct.getSceneRasterHeight());
        String[] sourceNames = sourceProduct.getBandNames();
        RenderedImage kernelFT = null;
        for (String name : sourceNames) {
            if (name.startsWith(namePrefix)) {
                Band targetBand = ProductUtils.copyBand(name, sourceProduct, targetProduct);
                RenderedImage sourceImage = sourceProduct.getBand(name).getSourceImage();
                RenderedOp targetOp;
                if (useFT) {
                    targetOp = DFTConvolveDescriptor.create(sourceImage, kernel, kernelFT, renderingHints);
                    kernelFT = (RenderedImage) targetOp.getProperty("kernelFT");
                }else {
                    targetOp = ConvolveDescriptor.create(sourceImage, kernel, renderingHints);
                }
                targetBand.setSourceImage(targetOp.getRendering());
            }
        }
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(DFTConvolutionOp.class);
        }
    }

    public static class KernelConverter implements Converter {
        public Class<?> getValueType() {
            return KernelJAI.class;
        }

        public Object parse(String text) throws ConversionException {
            throw new ConversionException("Not implemented!");
        }

        public String format(Object value) {
            KernelJAI kernel = (KernelJAI) value;
            String data = StringUtils.arrayToCsv(kernel.getKernelData());
            return kernel.getWidth() + "," + kernel.getHeight() + ",{" + data + "}";
        }
    }
}