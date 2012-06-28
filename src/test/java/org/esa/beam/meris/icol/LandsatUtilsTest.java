package org.esa.beam.meris.icol;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.TiePointGrid;
import org.esa.beam.meris.icol.utils.LandsatUtils;
import org.esa.beam.meris.l2auxdata.L2AuxData;
import org.esa.beam.meris.l2auxdata.L2AuxDataProvider;

/**
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
public class LandsatUtilsTest extends TestCase {

    public LandsatUtilsTest(String name) {
        super(name);
    }

    public static Test suite() {
        return new TestSuite(LandsatUtilsTest.class);
    }

    @Override
    protected void setUp() {
//        _plugIn = new LandsatTMReaderPlugIn();
//        assertNotNull(_plugIn);
    }

    public void testGetSunZenith() {
        // take test data from Meris L1b product tie point grid...
        int doy = 158;
        double gmt = 9.54;
        GeoPos geoPos = new GeoPos(55.766f, 13.913f);
        double sunZenith = LandsatUtils.getSunAngles(geoPos, doy, gmt).getZenith();
        assertEquals(52.868, sunZenith, 0.3);

        doy = 164;
        gmt = 8.5;
        geoPos = new GeoPos(32.067326f, 27.566187f);
        sunZenith = LandsatUtils.getSunAngles(geoPos, doy, gmt).getZenith();
        assertEquals(66.259, sunZenith, 0.3);
    }

    public void testGetSunAzimuth() {
        // take test data from Meris L1b product tie point grid...
        int doy = 158;
        double gmt = 9.54;
        GeoPos geoPos = new GeoPos(55.766f, 13.913f);
        double sunAzimuth = LandsatUtils.getSunAngles(geoPos, doy, gmt).getAzimuth();
        assertEquals(143.711, sunAzimuth, 0.3);

        doy = 164;
        gmt = 8.5;
        geoPos = new GeoPos(32.067326f, 27.566187f);
        sunAzimuth = LandsatUtils.getSunAngles(geoPos, doy, gmt).getAzimuth();
        assertEquals(105.69186, sunAzimuth, 0.3);

    }

    public void testGetDayOfYear() {
//        String date = "20090215";
        String date = "15-Feb-2009";
        int doy = LandsatUtils.getDayOfYear(date);
        assertEquals(46, doy);
        date = "31-Dec-2000";
        doy = LandsatUtils.getDayOfYear(date);
        assertEquals(366, doy);
        date = "31-Dec-2004";
        doy = LandsatUtils.getDayOfYear(date);
        assertEquals(366, doy);
        date = "31-Dec-2009";
        doy = LandsatUtils.getDayOfYear(date);
        assertEquals(365, doy);
    }

    public void testGetDaysSince2000() {
        String date = "15-Feb-2009";
        int daysSince2000 = LandsatUtils.getDaysSince2000(date);
        assertEquals(3334, daysSince2000);
        date = "01-Jan-2001";
        daysSince2000 = LandsatUtils.getDaysSince2000(date);
        assertEquals(367, daysSince2000);
    }

    public void testGetDecimalGmt() {
        String startTimeString = "09:35:30";
        String stopTimeString = "09:42:30";

        double gmt = LandsatUtils.getDecimalGMT(startTimeString, stopTimeString);
        assertEquals(9.65, gmt, 0.01);
    }

    public void testReadMerisL2AuxdataForLandsat() {
        Product testProduct = new Product("MER_RR__1P_dummy", "test", 50, 50);
//        public static final String DATE_FORMAT_PATTERN = "dd-MMM-yyyy HH:mm:ss";

        try {
            testProduct.setProductType("MER_RR__1P");
            testProduct.setStartTime(ProductData.UTC.parse("06-AUG-2006 09:30:00"));
            testProduct.setEndTime(ProductData.UTC.parse("06-AUG-2006 09:40:00"));
            // we need to set up an SZA tie point grid to read successfully...
            float[] tiePoints = new float[2500];
            for (int i = 0; i < 2500; i++) {
                tiePoints[i] = 1.0f;
            }
            TiePointGrid tpg = new TiePointGrid("sun_zenith", 50, 50, 0.0f, 0.0f, 10.0f, 10.0f, tiePoints);
            testProduct.addTiePointGrid(tpg);
            L2AuxData auxdata = L2AuxDataProvider.getInstance().getAuxdata(testProduct);
            assertNotNull(auxdata);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
}