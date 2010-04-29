package org.esa.beam.meris.icol;

import junit.framework.TestCase;
import junit.framework.Test;
import junit.framework.TestSuite;

import javax.imageio.stream.ImageInputStream;
import java.io.File;

import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.dataio.landsat.LandsatTMReaderPlugIn;
import org.esa.beam.dataio.landsat.LandsatConstants;

/**
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
public class LandsatProductReaderPluginTest extends TestCase {
    private LandsatTMReaderPlugIn _plugIn = null;

    public LandsatProductReaderPluginTest(String name) {
        super(name);
    }

    public static Test suite() {
        return new TestSuite(LandsatProductReaderPluginTest.class);
    }

    @Override
    protected void setUp() {
        _plugIn = new LandsatTMReaderPlugIn();
        assertNotNull(_plugIn);
    }

    public void testFormatNames() {
        String[] actualNames = null;
        String[] expectedNames = new String[]{LandsatConstants.FILE_NAMES[0]};

        actualNames = _plugIn.getFormatNames();
        assertNotNull(actualNames);

        for (int n = 0; n < expectedNames.length; n++) {
            assertEquals(expectedNames[n], actualNames[n]);
        }
    }

    public void testGetDescrition() {
        assertEquals(LandsatConstants.DESCRIPTION, _plugIn.getDescription(null));
    }

    public void testGetInputTypes() {
        Class[] expected = new Class[]{String.class, File.class};
        Class[] actual = null;

        actual = _plugIn.getInputTypes();
        assertNotNull(actual);

        for (int n = 0; n < expected.length; n++) {
            assertEquals(expected[n], actual[n]);
        }
    }

    public void testCreateInstance() {
        ProductReader reader = null;

        reader = _plugIn.createReaderInstance();
        assertNotNull(reader);
    }
}
