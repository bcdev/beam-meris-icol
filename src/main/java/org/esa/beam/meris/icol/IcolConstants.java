package org.esa.beam.meris.icol;

/**
 * @author Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
public class IcolConstants {

    // the default maximum distance (in km) in which AE effect shall be considered
    public static final int DEFAULT_AE_DISTANCE = 30;

    // the maximum distance (in km) in which aerosol AE effect shall be considered
    public static final int RAYLEIGH_AE_DISTANCE = 30;

    // the maximum distance (in km) in which aerosol AE effect shall be considered
    // reduce to 10km as proposed by RS, 23/11/2009
    public static final int AEROSOL_AE_DISTANCE = 10;

    public static final int AE_CORRECTION_MODE_RAYLEIGH = 0;
    public static final int AE_CORRECTION_MODE_AEROSOL= 1;
}
