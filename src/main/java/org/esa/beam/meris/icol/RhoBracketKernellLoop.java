package org.esa.beam.meris.icol;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.util.RectangleExtender;

import java.awt.Rectangle;


/**
 * @author Norman Fomferra
 * @author Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
public class RhoBracketKernellLoop implements RhoBracketAlgo {
    private final int sourceExtend;
    private final RectangleExtender rectCalculator;
    private final double[][] w;

    public RhoBracketKernellLoop(Product l1bProduct, CoeffW coeffW, int correctionMode) {
        final String productType = l1bProduct.getProductType();
        double sourceExtendReduction = 1.0;
//        if (correctionMode == 0) {
//            // Rayleigh
//            sourceExtendReduction = IcolConstants.DEFAULT_AE_DISTANCE/IcolConstants.RAYLEIGH_AE_DISTANCE;
//        } else if (correctionMode == 1) {
//            // aerosol
//            sourceExtendReduction = IcolConstants.DEFAULT_AE_DISTANCE/IcolConstants.AEROSOL_AE_DISTANCE;
//        }

        if (productType.indexOf("_RR") > -1) {
//            w = coeffW.getCoeffForRRTest();
            w = coeffW.getCoeffForRR();
            this.sourceExtend = (int) (CoeffW.RR_KERNEL_SIZE/sourceExtendReduction);
        } else {
            w = coeffW.getCoeffForFR();
            this.sourceExtend = (int) (CoeffW.FR_KERNEL_SIZE/sourceExtendReduction);
        }
//        this.sourceExtend = w[0].length - 1;
        this.rectCalculator = new RectangleExtender(new Rectangle(l1bProduct.getSceneRasterWidth(), l1bProduct.getSceneRasterHeight()), sourceExtend, sourceExtend);
    }

    public Rectangle mapTargetRect(Rectangle targetRect) {
        return rectCalculator.extend(targetRect);
    }


    public Convolver createConvolver(Operator op, Tile[] srcTiles, Rectangle targetRect, ProgressMonitor pm) {
        return new ConvolverImpl(srcTiles);
    }

    public class ConvolverImpl implements Convolver {
        private final WeightedMeanCalculator meanCalculator;
        private final Tile[] srcTiles;

        public ConvolverImpl(Tile[] srcTiles) {
            this.meanCalculator = new WeightedMeanCalculator(sourceExtend);
            this.srcTiles = srcTiles;
        }

        public double convolveSample(int x, int y, int iaer, int b) {
            return meanCalculator.compute(x, y, srcTiles[b], w[iaer - 1]);
        }

        public double[] convolvePixel(int x, int y, int iaer) {
            return meanCalculator.computeAll(x, y, srcTiles, w[iaer - 1]);
        }
    }
}
