package org.esa.beam.meris.icol;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.Tile;

import java.awt.Rectangle;

/**
 * Computes a <code>&lt;rho&gt;</code> pixel by convolution of some <code>rho</code> pixel.
 * @author Norman Fomferra
 * @author Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
public interface RhoBracketAlgo {
    Rectangle mapTargetRect(Rectangle targetRect);

    Convolver createConvolver(Operator op, Tile[] rhoTiles, Rectangle targetRect, ProgressMonitor pm);

    interface Convolver {
        double convolveSample(int x, int y, int iaer, int b);
        double[] convolvePixel(int x, int y, int iaer);
    }
}
