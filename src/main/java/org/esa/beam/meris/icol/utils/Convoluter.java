package org.esa.beam.meris.icol.utils;

import com.nativelibs4java.opencl.CLBuildException;
import com.nativelibs4java.opencl.CLContext;
import com.nativelibs4java.opencl.CLDevice;
import com.nativelibs4java.opencl.CLEvent;
//import com.nativelibs4java.opencl.CLFloatBuffer;
import com.nativelibs4java.opencl.CLKernel;
import com.nativelibs4java.opencl.CLMem;
import com.nativelibs4java.opencl.CLProgram;
import com.nativelibs4java.opencl.CLQueue;
import com.nativelibs4java.opencl.JavaCL;
import com.nativelibs4java.util.IOUtils;
import org.esa.beam.meris.icol.ReshapedConvolutionOp;

import javax.media.jai.KernelJAI;
import javax.media.jai.PlanarImage;
import javax.swing.JOptionPane;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferFloat;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.Map;

/**
* @author Olaf Danne
* @version $Revision: $ $Date:  $
*/

public class Convoluter {
    private KernelJAI kernelJAI;
    private boolean openCL;
    private CLContext context;
    private CLProgram program;
    private CLKernel kernel;

    public Convoluter(KernelJAI kernelJAI, boolean openCL) {
        this.kernelJAI = kernelJAI;
        this.openCL = openCL;
    }

    public RenderedImage convolve(PlanarImage sourceImage) throws IOException {
        if (openCL) {
            try {
                if (context == null) {
                    context = JavaCL.createBestContext();
                    String src = IOUtils.readTextClose(Convoluter.class.getResourceAsStream("Convolution.cl"));
                    program = context.createProgram(src).build();
                    kernel = program.createKernel("Convolve");
                }
                return convolveOpenCL(sourceImage);
            } catch (Exception e) {
//                throw new IOException("OpenCL-Error: " + e.getMessage(), e);
                final String msg = "Warning: OpenCl cannot be used for convolution - going back to standard mode.\n\n" +
                                                 "Reason: " + e.getMessage();
                JOptionPane.showOptionDialog(null, msg, "ICOL - Info Message", JOptionPane.DEFAULT_OPTION,
                                             JOptionPane.INFORMATION_MESSAGE, null, null, null);
                return ReshapedConvolutionOp.convolve(sourceImage, kernelJAI);
            }
        } else {
            return ReshapedConvolutionOp.convolve(sourceImage, kernelJAI);
        }
    }

    public void dispose() {
        if (context != null) {
            kernel.release();
            kernel = null;
            program.release();
            program = null;
            context.release();
            context = null;
        }
    }

    private BufferedImage convolveOpenCL(PlanarImage inputImage) throws IOException, CLBuildException {
//                float[] filterData = kernelJAI.getKernelData();
//        int filterSize = kernelJAI.getWidth();
//
//        BufferedImage bufferedInputImage = inputImage.getAsBufferedImage();
//        DataBufferFloat inputDataBuffer = (DataBufferFloat) bufferedInputImage.getRaster().getDataBuffer();
//        float[] inputData = inputDataBuffer.getData();
//        int width = bufferedInputImage.getWidth();
//        int height = bufferedInputImage.getHeight();
//
//        long time = System.nanoTime();
//
//        FloatBuffer filter = FloatBuffer.wrap(filterData);
//        FloatBuffer input = FloatBuffer.wrap(inputData);
//
//        CLFloatBuffer clFilter = context.createFloatBuffer(CLMem.Usage.Input, filter, true);
//        CLFloatBuffer clInput = context.createFloatBuffer(CLMem.Usage.Input, input, true);
//        CLFloatBuffer clOutput = context.createFloatBuffer(CLMem.Usage.Output, width * height);
//
//        kernel.setArgs(clInput,
//                       width,
//                       height,
//                       clFilter,
//                       filterSize,
//                       clOutput);
//
//        CLQueue queue = context.createDefaultQueue();
//
////        CLEvent clEvent = kernel.enqueueNDRange(queue, new int[]{width, height}, new int[]{32, 4});
////        CLEvent clEvent = kernel.enqueueNDRange(queue, new int[]{width, height}, new int[]{width, 1});
////        CLEvent clEvent = kernel.enqueueNDRange(queue, new int[]{1024, 1024}, new int[]{16, 16});
////        CLEvent clEvent = kernel.enqueueNDRange(queue, new int[]{1024, 1024}, new int[]{width, 1});
//        CLEvent clEvent = kernel.enqueueNDRange(queue, new int[]{width, height}, null); // this works, but is it ok???
//
//        FloatBuffer floatBuffer = clOutput.read(queue, clEvent);
//        WritableRaster raster = WritableRaster.createWritableRaster(bufferedInputImage.getSampleModel(), null);
//        BufferedImage outputImage = new BufferedImage(bufferedInputImage.getColorModel(), raster, false, null);
//        floatBuffer.get(((DataBufferFloat) raster.getDataBuffer()).getData());
//
//        clEvent.release();
//        clInput.release();
//        clFilter.release();
//        clOutput.release();
//
//        queue.release();
//
//        time = System.nanoTime() - time;
//        System.out.println("time = " + time / 1000 + " ms");
//
//        return outputImage;
                return null;
    }
}
