package org.esa.beam.meris.icol;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.meris.icol.utils.IcolUtils;

import javax.media.jai.KernelJAI;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Norman Fomferra
 * @author Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
public class RhoBracketJaiConvolve implements RhoBracketAlgo {
    private double[/*26*/][/*RR=26|FR=101*/] w;
    private final Product rhoBracketProduct;
    private final String namePrefix;
    private int numBands;
    private int[] bandsToSkip;

    public RhoBracketJaiConvolve(Product l1bProduct, String productType, CoeffW coeffW, String namePrefix, int iaerConv,
                                 int numBands, int[] bandsToSkip) {
        KernelJAI convolveKernel = null;
        double reshapedScalingFactor = 1.0;
        if (productType.indexOf("_RR") > -1) {
            convolveKernel = coeffW.getReshapedConvolutionKernelForRR(iaerConv);
            reshapedScalingFactor = 2.0 * (CoeffW.RR_KERNEL_SIZE ) / (convolveKernel.getWidth() - 1);
        } else {
            convolveKernel = coeffW.getReshapedConvolutionKernelForFR(iaerConv);
            reshapedScalingFactor = 2.0 * (CoeffW.FR_KERNEL_SIZE ) / (convolveKernel.getWidth() - 1);
        }


        this.namePrefix = namePrefix;
        this.numBands = numBands;
        this.bandsToSkip = bandsToSkip;
        Map<String, Object> convolveParams = new HashMap<String, Object>();
        convolveParams.put("kernel", convolveKernel);
        convolveParams.put("namePrefix", namePrefix);
        convolveParams.put("correctionMode", coeffW.getCorrectionMode());
        convolveParams.put("reshapedScalingFactor", reshapedScalingFactor);
        rhoBracketProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(ReshapedConvolutionOp.class), convolveParams, l1bProduct);
    }

    public Rectangle mapTargetRect(Rectangle targetRect) {
        return targetRect;
    }

    public Convolver createConvolver(Operator op, Tile[] rhoTiles, Rectangle targetRect, ProgressMonitor pm) {
        Tile[] rhoBracket = new Tile[numBands];
        for (int i = 0; i < numBands; i++) {
            if (IcolUtils.isIndexToSkip(i, bandsToSkip)) {
                continue;
            }
            final Band rhoBracketProductBand = rhoBracketProduct.getBand(namePrefix + (i + 1));
            rhoBracket[i] = op.getSourceTile(rhoBracketProductBand, targetRect, pm);
        }
        return new ConvolverImpl(rhoBracket);
    }

    public class ConvolverImpl implements Convolver {
        private final Tile[] rhoBracket;

        public ConvolverImpl(Tile[] rhoBracket) {
            this.rhoBracket = rhoBracket;
        }

        public double[] convolvePixel(int x, int y, int iaer) {
            final double[] pixel = new double[rhoBracket.length];
            for (int b = 0; b < pixel.length; b++) {
                if (IcolUtils.isIndexToSkip(b, bandsToSkip)) {
                    continue;
                }
                pixel[b] = rhoBracket[b].getSampleDouble(x, y);
            }
            return pixel;
        }

        public double convolveSample(int x, int y, int iaer, int b) {
            return rhoBracket[b].getSampleDouble(x, y);
        }
    }
}
