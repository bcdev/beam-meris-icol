package org.esa.beam.meris.icol;

import java.util.regex.Pattern;

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

    /**
     * A pattern which matches MERIS L1 Amorgos product type
     *
     * @see java.util.regex.Matcher
     */
    public static final Pattern MERIS_L1_AMORGOS_TYPE_PATTERN = Pattern.compile("MER_..._1N");

    /**
     * A pattern which matches MERIS L1P product type (as derived i.e. from Coastcolour L1P processing)
     *
     * @see java.util.regex.Matcher
     */
    public static final Pattern MERIS_L1_CC_L1P_TYPE_PATTERN = Pattern.compile("MER_..._CCL1P");

}
