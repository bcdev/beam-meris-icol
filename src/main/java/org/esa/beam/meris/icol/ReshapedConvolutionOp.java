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
import javax.media.jai.Interpolation;
import javax.media.jai.JAI;
import javax.media.jai.KernelJAI;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.ConvolveDescriptor;
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

//    private static final Interpolation BILIN = Interpolation.getInstance(Interpolation.INTERP_NEAREST);
    private static final Interpolation BILIN = Interpolation.getInstance(Interpolation.INTERP_BILINEAR);
    private static final long MB = 1024 * 1024;

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
        RenderedImage kernelFT = null;
        for (String name : sourceNames) {

            if (name.startsWith(namePrefix)) {
                    Band targetBand = ProductUtils.copyBand(name, sourceProduct, targetProduct);
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
        RenderingHints renderingHints = new RenderingHints(JAI.KEY_BORDER_EXTENDER,
                                                           borderExtender);
        // The ConvolveDescriptor performs a kernel-based convolution in SPATIAL domain.
        final RenderedOp image = ConvolveDescriptor.create(src, kernel, renderingHints);
//        System.out.printf("Convolved, size: %d x %d x %d\n", image.getWidth(), image.getHeight(), image.getNumBands());

        return image;
   }

   private static RenderedOp convolveDownscaled(RenderedImage src,
                                                KernelJAI kernel,
                                                Interpolation interpolation,
                                                float downscalingFactor) {
       RenderedOp image;
       if (downscalingFactor != 1.0) {
           image = ScaleDescriptor.create(src,
                                          1.0f / downscalingFactor,
                                          1.0f / downscalingFactor,
                                          0.0f, 0.0f, interpolation, null);
//           System.out.printf("Downscaled 1, size: %d x %d x %d\n", image.getWidth(), image.getHeight(), image.getNumBands());
           image = convolve(image, kernel);
       } else {
           image = convolve(src, kernel);
       }
//       System.out.printf("Downscaled 2, size: %d x %d x %d\n", image.getWidth(), image.getHeight(), image.getNumBands());
       if (downscalingFactor != 1.0) {
           image = ScaleDescriptor.create(image,
                                          downscalingFactor,
                                          downscalingFactor,
                                          0.0f, 0.0f, interpolation, null);
       }
//       System.out.printf("Downscaled 3, size: %d x %d x %d\n", image.getWidth(), image.getHeight(), image.getNumBands());
       return image;
   }

    // CURRENTLY NOT USED. MAY BE STILL NEEDED LATER?
//   private static RenderedOp combine(RenderedImage src1, double w1, RenderedImage src2, double w2) {
//       RenderedOp wimg1 = MultiplyConstDescriptor.create(src1, new double[]{w1}, null);
//       RenderedOp wimg2 = MultiplyConstDescriptor.create(src2, new double[]{w2}, null);
//       System.out.printf("Combined 1, size: %d x %d x %d\n", wimg1.getWidth(), wimg1.getHeight(), wimg1.getNumBands());
//       System.out.printf("Combined 2, size: %d x %d x %d\n", wimg2.getWidth(), wimg2.getHeight(), wimg2.getNumBands());
//
//       return AddDescriptor.create(wimg1, wimg2, null);
//   }



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