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
package org.esa.beam.meris.icol;

import com.bc.ceres.binding.ConversionException;
import com.bc.ceres.binding.Converter;
import com.bc.ceres.core.Assert;
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
import javax.media.jai.ImageLayout;
import javax.media.jai.Interpolation;
import javax.media.jai.JAI;
import javax.media.jai.KernelJAI;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.ConvolveDescriptor;
import javax.media.jai.operator.CropDescriptor;
import javax.media.jai.operator.ScaleDescriptor;
import java.awt.RenderingHints;
import java.awt.image.RenderedImage;


/**
 * Operator performing the 'reshaped' convolution step with JAI image convolution in spatial domain.
 *
 * @author Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
@OperatorMetadata(alias = "Meris.ReshapedConvolve",
        version = "1.0",
        internal = true,
        authors = "OD,NF",
        copyright = "(c) 2009 by Brockmann Consult",
        description = "Convolves reflectances using a given kernel.")
public class ReshapedConvolutionOp extends Operator {

    private static final Interpolation BILIN = Interpolation.getInstance(Interpolation.INTERP_BILINEAR);

    @SourceProduct
    private Product sourceProduct;

    @TargetProduct
    private Product targetProduct;

    @Parameter(notNull = true, notEmpty = true)
    private String namePrefix;
    @Parameter(converter = KernelConverter.class, notNull = true)
    private KernelJAI kernel;
    @Parameter(defaultValue = "-1")
    private int correctionMode;
    @Parameter(defaultValue = "1.0")
    private double reshapedScalingFactor;


    @Override
    public void initialize() throws OperatorException {
        targetProduct = new Product("P", "PT", sourceProduct.getSceneRasterWidth(), sourceProduct.getSceneRasterHeight());
        String[] sourceNames = sourceProduct.getBandNames();
        for (String name : sourceNames) {
            if (name.startsWith(namePrefix) && !targetProduct.containsRasterDataNode(name)) {
                Band targetBand = ProductUtils.copyBand(name, sourceProduct, targetProduct, false);
                RenderedImage sourceImage = sourceProduct.getBand(name).getSourceImage();
                if (correctionMode == IcolConstants.AE_CORRECTION_MODE_RAYLEIGH) {
                    // Rayleigh
                    RenderedImage image1 = convolveDownscaled(sourceImage, kernel, BILIN,
                            (float) reshapedScalingFactor);
                    targetBand.setSourceImage(image1);
                } else if (correctionMode == IcolConstants.AE_CORRECTION_MODE_AEROSOL) {
                    // aerosol
                    RenderedImage image1 = convolve(sourceImage, kernel);
                    targetBand.setSourceImage(image1);
                }
            }
        }
    }

    public static RenderedOp convolve(RenderedImage src, KernelJAI kernel) {
        final BorderExtender borderExtender = BorderExtender.createInstance(BorderExtender.BORDER_COPY);
//        final BorderExtender borderExtender = BorderExtender.createInstance(BorderExtender.BORDER_REFLECT);
//        final BorderExtender borderExtender = BorderExtender.createInstance(BorderExtender.BORDER_WRAP);
//        final BorderExtender borderExtender = BorderExtender.createInstance(BorderExtender.BORDER_ZERO);
        RenderingHints renderingHints = new RenderingHints(JAI.KEY_BORDER_EXTENDER, borderExtender);
        // The ConvolveDescriptor performs a kernel-based convolution in SPATIAL domain.
        //        System.out.printf("Convolved, size: %d x %d x %d\n", image.getWidth(), image.getHeight(), image.getNumBands());
        return ConvolveDescriptor.create(src, kernel, renderingHints);
   }

   private static RenderedOp convolveDownscaled(RenderedImage src,
                                                KernelJAI kernel,
                                                Interpolation interpolation,
                                                float downscalingFactor) {
       RenderedOp image;
       int width = src.getWidth();
       int height = src.getHeight();
       if (downscalingFactor != 1.0) {

           ImageLayout targetImageLayout = new ImageLayout();
           targetImageLayout.setTileWidth((int) (src.getTileWidth() / downscalingFactor));
           targetImageLayout.setTileHeight((int) (src.getTileHeight() / downscalingFactor));
           final RenderingHints renderingHints = new RenderingHints(JAI.KEY_IMAGE_LAYOUT, targetImageLayout);

           image = ScaleDescriptor.create(src,
                                          1.0f / downscalingFactor,
                                          1.0f / downscalingFactor,
                                          0.0f, 0.0f, interpolation, renderingHints);
//           System.out.printf("Downscaled 1, size: %d x %d x %d\n", image.getWidth(), image.getHeight(), image.getNumBands());
           image = convolve(image, kernel);
       } else {
           image = convolve(src, kernel);
       }
//       System.out.printf("Downscaled 2, size: %d x %d x %d\n", image.getWidth(), image.getHeight(), image.getNumBands());
       if (downscalingFactor != 1.0) {
           ImageLayout targetImageLayout = new ImageLayout();
           targetImageLayout.setTileWidth(src.getTileWidth());
           targetImageLayout.setTileHeight(src.getTileHeight());
           final RenderingHints renderingHints = new RenderingHints(JAI.KEY_IMAGE_LAYOUT, targetImageLayout);
           renderingHints.put(JAI.KEY_IMAGE_LAYOUT, targetImageLayout);
           renderingHints.put(JAI.KEY_BORDER_EXTENDER, BorderExtender.createInstance(BorderExtender.BORDER_COPY));
           float xScale = (float) width / (float) image.getWidth();
           float yScale = (float) height / (float) image.getHeight();
           image = ScaleDescriptor.create(image,
                                          xScale,
                                          yScale,
                                          0.0f, 0.0f, interpolation, renderingHints);
           if (image.getWidth() > width || image.getHeight() > height) {
               image = CropDescriptor.create(image, 0f, 0f, (float) width, (float) height, renderingHints);
           }
           Assert.state(image.getWidth() == width, "image.getWidth() == width");
           Assert.state(image.getHeight() == height, "image.getHeight() == height");
       }
//       System.out.printf("Downscaled 3, size: %d x %d x %d\n", image.getWidth(), image.getHeight(), image.getNumBands());
       return image;
   }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ReshapedConvolutionOp.class);
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