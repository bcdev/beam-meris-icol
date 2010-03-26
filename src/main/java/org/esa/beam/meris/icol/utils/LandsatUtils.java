package org.esa.beam.meris.icol.utils;

import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.TiePointGrid;
import org.esa.beam.meris.icol.tm.TmConstants;
import org.esa.beam.util.math.MathUtils;

import java.util.Calendar;
import java.util.HashMap;

/**
 * @author Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
public class LandsatUtils {

    public static HashMap<String, String> months = new HashMap<String, String>(12);

    static {
        months.put("JAN","01");
        months.put("FEB","02");
        months.put("MAR","03");
        months.put("APR","04");
        months.put("MAY","05");
        months.put("JUN","06");
        months.put("JUL","07");
        months.put("AUG","08");
        months.put("SEP","09");
        months.put("OCT","10");
        months.put("NOV","11");
        months.put("DEC","12");
    }

    public static SunAngles getSunAngles(GeoPos geoPos, int doy, double gmt) {
        // RS, 13.09.07: follow subroutine 'POSSOL' from 6S implementation
        SunAngles sa = new SunAngles();

        // mean solar time
        final double tsm = gmt + geoPos.getLon()/15.0;
        final double xla = geoPos.getLat()* MathUtils.DTOR;
        final double tet = 2.0*Math.PI*doy/365.0;

        // time equation
        final double[]a = new double[] {
                0.000075,
                0.001868,
                0.032077,
                0.014615,
                0.040849
        };
        final double et = 720.0*(a[0] + a[1]*Math.cos(tet) - a[2]*Math.sin(tet) -
                              a[3]*Math.cos(2.0*tet) - a[4]*Math.sin(2.0*tet))/Math.PI;

        // true solar time
        final double tsv = tsm + et/60.0 - 12.0;

        // hour angle
        final double ah = tsv * 15.0 * MathUtils.DTOR;

        // solar declination (in radians)
        final double[] b = new double[] {
                0.006918,
                0.399912,
                0.070257,
                0.006758,
                0.000907,
                0.002697,
                0.001480
        };
        final double delta = b[0] - b[1]*Math.cos(tet) + b[2]*Math.sin(tet) -
                              b[3]*Math.cos(2.0*tet) + b[4]*Math.sin(2.0*tet) -
                              b[5]*Math.cos(3.0*tet) - b[6]*Math.sin(3.0*tet);

        // elevation, azimuth
        final double amuZero = Math.sin(xla)*Math.sin(delta) +
                               Math.cos(xla)*Math.cos(delta)*Math.cos(ah);
        double elev = Math.asin(amuZero);
        double az = Math.cos(delta)*Math.sin(ah)/Math.cos(elev);
        if (Math.abs(az - 1.0) > 0.0) {
            az = Math.signum(az)*Math.abs(az);
        }
        final double caz = (-Math.cos(xla)*Math.sin(delta) +
                             Math.sin(xla)*Math.cos(delta)*Math.cos(ah))/Math.cos(elev);
        double azim = Math.asin(az);
        if (caz < 0.0) {
            azim = Math.PI - azim;
        }
        if (caz > 0.0 && az <= 0.0) {
            azim += (2.0*Math.PI);
        }
        azim += Math.PI;
        final double pi2 = 2.0*Math.PI;
        if (azim > pi2) {
            azim -= pi2;
        }

        // conversion to degrees
        elev *= MathUtils.RTOD;
        azim *= MathUtils.RTOD;

        sa.setZenith(90.0 - elev);
        sa.setAzimuth(azim);

        return sa;
    }

//    public static int getDayOfYear(String yyyymmdd) {
//        Calendar cal = Calendar.getInstance();
//        int doy = -1;
//        try {
//            final int year = Integer.parseInt(yyyymmdd.substring(0, 4));
//            final int month = Integer.parseInt(yyyymmdd.substring(4, 6)) - 1;
//            final int day = Integer.parseInt(yyyymmdd.substring(6, 8));
//            cal.set(year, month, day);
//            doy = cal.get(Calendar.DAY_OF_YEAR);
//        } catch (StringIndexOutOfBoundsException e) {
//            e.printStackTrace();
//        }  catch (NumberFormatException e) {
//            e.printStackTrace();
//        }
//        return doy;
//    }

     public static int getDayOfYear(String dd_MMM_yyyy) {
        Calendar cal = Calendar.getInstance();
        int doy = -1;
        try {
            final int year = Integer.parseInt(dd_MMM_yyyy.substring(7, 11));
            final String monthString = dd_MMM_yyyy.substring(3, 6);
            final int month = Integer.parseInt(months.get(monthString)) - 1;
            final int day = Integer.parseInt(dd_MMM_yyyy.substring(0, 2));
            cal.set(year, month, day);
            doy = cal.get(Calendar.DAY_OF_YEAR);
        } catch (StringIndexOutOfBoundsException e) {
            e.printStackTrace();
        }  catch (NumberFormatException e) {
            e.printStackTrace();
        }
        return doy;
    }

    public static double getDecimalGMT(String startGmtString, String stopGmtString) {
        double startGmt = 10.0 * Integer.parseInt(startGmtString.substring(0, 1)) +
                Integer.parseInt(startGmtString.substring(1, 2)) +
                10.0 * Integer.parseInt(startGmtString.substring(3, 4)) / 60.0 +
                Integer.parseInt(startGmtString.substring(4, 5)) / 60.0 +
                10.0 * Integer.parseInt(startGmtString.substring(6, 7)) / 3600.0 +
                Integer.parseInt(startGmtString.substring(7, 8)) / 3600.0;
        double stopGmt = 10.0 * Integer.parseInt(stopGmtString.substring(0, 1)) +
                Integer.parseInt(stopGmtString.substring(1, 2)) +
                10.0 * Integer.parseInt(stopGmtString.substring(3, 4)) / 60.0 +
                Integer.parseInt(stopGmtString.substring(4, 5)) / 60.0 +
                10.0 * Integer.parseInt(stopGmtString.substring(6, 7)) / 3600.0 +
                Integer.parseInt(stopGmtString.substring(7, 8)) / 3600.0;

        return 0.5*(startGmt + stopGmt);
    }

    public static int getDaysSince2000(String timeString) {
        final int doy = getDayOfYear(timeString);
        final int currentYear = Integer.parseInt(timeString.substring(7,11));
        final int leapYears = (currentYear - 2000)/4 + 1;
        final int days = 365*(currentYear -leapYears - 2000) +
                         366*leapYears +doy;

        return days;
    }

    public static class SunAngles {
        private double azimuth;
        private double zenith;

        public double getAzimuth() {
            return azimuth;
        }

        public void setAzimuth(double azimuth) {
            this.azimuth = azimuth;
        }

        public double getZenith() {
            return zenith;
        }

        public void setZenith(double zenith) {
            this.zenith = zenith;
        }
    }

    public static Product createMerisCompatibleProductForL2Auxdata(Product landsatInputProduct) {
        // set up MERIS dummy product
        Product merisCompatibleProduct = new Product("MER_RR__1P_dummy", "dummy",
                                                landsatInputProduct.getSceneRasterWidth(),
                                                landsatInputProduct.getSceneRasterHeight());
        merisCompatibleProduct.setProductType("MER_RR__1P");

        // product needs start and stop time...
        merisCompatibleProduct.setStartTime(landsatInputProduct.getStartTime());
        merisCompatibleProduct.setEndTime(landsatInputProduct.getEndTime());

        // product needs SZA tie point grid - get also from input product
        final TiePointGrid szaTiePointGrid = landsatInputProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME);
        merisCompatibleProduct.addTiePointGrid(szaTiePointGrid);

        return merisCompatibleProduct;
    }

    public static double convertRadToRefl(double rad, double cosSza, int bandId, double seasonalFactor) {
        final double constantTerm = (Math.PI / cosSza) * seasonalFactor;
        double refl= (float) ((rad * constantTerm) / TmConstants.LANDSAT5_SOLAR_IRRADIANCES[bandId]);

        return refl;
    }

    public static double convertReflToRad(double refl, double cosSza, int bandId, double seasonalFactor) {
        final double constantTerm = (Math.PI / cosSza) * seasonalFactor;
        double rad = refl * TmConstants.LANDSAT5_SOLAR_IRRADIANCES[bandId] / constantTerm;

        return rad;
    }

}
