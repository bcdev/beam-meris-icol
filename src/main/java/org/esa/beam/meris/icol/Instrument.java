package org.esa.beam.meris.icol;

import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.meris.icol.tm.TmConstants;


public enum Instrument {

    MERIS(EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS, new int[]{10,14}),
    TM5(TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS, new int[]{5}),
    ETM7(TmConstants.LANDSAT7_NUM_SPECTRAL_BANDS, new int[]{5,6});

    public final int numSpectralBands;
    public final int[] bandsToSkip;

    private Instrument(int numSpectralBands, int[] bandsToSkip) {
        this.numSpectralBands = numSpectralBands;
        this.bandsToSkip = bandsToSkip;
    }
}
