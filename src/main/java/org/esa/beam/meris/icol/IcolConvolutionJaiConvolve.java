package org.esa.beam.meris.icol;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.meris.icol.utils.IcolUtils;

import javax.media.jai.BorderExtender;
import javax.media.jai.KernelJAI;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Norman Fomferra
 * @author Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
public class IcolConvolutionJaiConvolve implements IcolConvolutionAlgo {

    private final Product convolveSourceProduct;
    private final String namePrefix;
    private int numBands;
    private int[] bandsToSkip;

    public IcolConvolutionJaiConvolve(Product l1bProduct, String productType, CoeffW coeffW, String namePrefix,
                                      int iaerConv,
                                      int numBands, int[] bandsToSkip) {
        KernelJAI convolveKernel;
        double reshapedScalingFactor;
        if (productType.contains("_RR")) {
            convolveKernel = coeffW.getReshapedConvolutionKernelForRR(iaerConv);
            reshapedScalingFactor = 2.0 * (CoeffW.RR_KERNEL_SIZE) / (convolveKernel.getWidth() - 1);
        } else {
            convolveKernel = coeffW.getReshapedConvolutionKernelForFR(iaerConv);
            reshapedScalingFactor = 2.0 * (CoeffW.FR_KERNEL_SIZE) / (convolveKernel.getWidth() - 1);
        }


        this.namePrefix = namePrefix;
        this.numBands = numBands;
        this.bandsToSkip = bandsToSkip;
        Map<String, Object> convolveParams = new HashMap<String, Object>();
        convolveParams.put("kernel", convolveKernel);
        convolveParams.put("namePrefix", namePrefix);
        convolveParams.put("correctionMode", coeffW.getCorrectionMode());
        convolveParams.put("reshapedScalingFactor", reshapedScalingFactor);
        convolveSourceProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(ReshapedConvolutionOp.class),
                                                  convolveParams, l1bProduct);
    }

    @Override
    public Rectangle mapTargetRect(Rectangle targetRect) {
        return targetRect;
    }

    @Override
    public Convolver createConvolver(Operator op, Tile[] rhoTiles, Rectangle targetRect, ProgressMonitor pm) {
        Tile[] sourceTiles = new Tile[numBands];
        for (int i = 0; i < numBands; i++) {
            if (bandsToSkip != null && IcolUtils.isIndexToSkip(i, bandsToSkip)) {
                continue;
            }
            // todo: this only handles band names of the form <name>_n - make more general
            final Band sourceBand = convolveSourceProduct.getBand(namePrefix + (i + 1));
            sourceTiles[i] = op.getSourceTile(sourceBand, targetRect,
                                              BorderExtender.createInstance(BorderExtender.BORDER_COPY));
        }
        return new ConvolverImpl(sourceTiles);
    }

    public class ConvolverImpl implements Convolver {

        private final Tile[] rhoBracket;

        public ConvolverImpl(Tile[] rhoBracket) {
            this.rhoBracket = rhoBracket;
        }

        @Override
        public double[] convolvePixel(int x, int y, int iaer) {
            final double[] pixel = new double[rhoBracket.length];
            for (int b = 0; b < pixel.length; b++) {
                if (bandsToSkip != null && IcolUtils.isIndexToSkip(b, bandsToSkip)) {
                    continue;
                }
                pixel[b] = rhoBracket[b].getSampleDouble(x, y);
            }
            return pixel;
        }

        @Override
        public double convolveSample(int x, int y, int iaer, int b) {
            return rhoBracket[b].getSampleDouble(x, y);
        }

        @Override
        public double convolveSampleBoolean(int x, int y, int iaer, int b) {
            return 0.0;  // not used
        }
    }
}
