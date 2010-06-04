package org.esa.beam.meris.icol.utils;

import org.esa.beam.framework.gpf.Tile;

/**
 * @author Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
public class IcolUtils {

    public static boolean isIndexToSkip(int index, int[] bandsToSkip) {
        for (int i = 0; i < bandsToSkip.length; i++) {
            if (index == bandsToSkip[i]) {
                return true;
            }
        }
        return false;
    }

    public static void computeMerisBands11And15(Tile[] l1bTile, Tile[] l1nTile, int x, int y) {
        final float l1b10 = l1bTile[9].getSampleFloat(x, y);
        final float l1b11 = l1bTile[10].getSampleFloat(x, y);
        final float l1b12 = l1bTile[11].getSampleFloat(x, y);
        final float l1b14 = l1bTile[13].getSampleFloat(x, y);
        final float l1b15 = l1bTile[14].getSampleFloat(x, y);
        final float l1n10 = l1nTile[9].getSampleFloat(x, y);
        final float l1n12 = l1nTile[10].getSampleFloat(x, y);
        final float l1n14 = l1nTile[12].getSampleFloat(x, y);

        final float l1nb15 = l1b15*l1n14/l1b14;
        final float l1b11ref = 0.5f*(l1b10 + l1b12); // is this what RS means??
        final float l1n11ref = 0.5f*(l1n10 + l1n12); // is this what RS means??
        final float l1nb11 = l1b11*l1n11ref/l1b11ref;

        l1nTile[10].setSample(x, y, l1nb11);
        l1nTile[14].setSample(x, y, l1nb15);
    }

    public static int determineAerosolModelIndex(double alpha) {
        int iaer = (int) (Math.round(-(alpha * 10.0)) + 1);
        if (iaer < 1) {
            iaer = 1;
        } else if (iaer > 26) {
            iaer = 26;
        }
        return iaer;
    }
}