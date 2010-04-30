package org.esa.beam.dataio.landsat.geotiff;

import org.esa.beam.framework.dataio.DecodeQualification;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.util.io.BeamFileFilter;

import java.io.File;
import java.util.Locale;

/**
 * Plugin class for the {@link LandsatGeotiffReader} reader.
 */
public class LandsatGeotiffReaderPlugin implements ProductReaderPlugIn {

    private static final Class[] READER_INPUT_TYPES = new Class[]{String.class,File.class};

    private static final String[] FORMAT_NAMES = new String[]{"LandsatGeoTIFF"};
    private static final String[] DEFAULT_FILE_EXTENSIONS = new String[]{".txt", ".TXT"};
    private static final String READER_DESCRIPTION = "Landsat Data Products (GeoTIFF)";
    private static final BeamFileFilter FILE_FILTER = new LandsatGeoTiffFileFilter();

    @Override
    public DecodeQualification getDecodeQualification(Object input) {
        return DecodeQualification.INTENDED;  //TODO
    }

    @Override
    public Class[] getInputTypes() {
        return READER_INPUT_TYPES;
    }

    @Override
    public ProductReader createReaderInstance() {
        return new LandsatGeotiffReader(this);
    }

    @Override
    public String[] getFormatNames() {
        return FORMAT_NAMES;
    }

    @Override
   public String[] getDefaultFileExtensions() {
        return DEFAULT_FILE_EXTENSIONS;
    }

    @Override
    public String getDescription(Locale locale) {
        return READER_DESCRIPTION;
    }

    @Override
    public BeamFileFilter getProductFileFilter() {
        return FILE_FILTER;
    }

    private static class LandsatGeoTiffFileFilter extends BeamFileFilter {

        public LandsatGeoTiffFileFilter() {
            super();
            setFormatName(FORMAT_NAMES[0]);
            setDescription(READER_DESCRIPTION);
        }

        @Override
        public boolean accept(final File file) {
            if (file.isDirectory()) {
                return true;
            }
            String filename = file.getName().toLowerCase();
            if (filename.endsWith("_mtl.txt")) {
                return true;
            }
            return false;
        }

    }
}
