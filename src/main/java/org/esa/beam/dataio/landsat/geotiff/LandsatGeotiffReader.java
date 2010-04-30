package org.esa.beam.dataio.landsat.geotiff;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.geotiff.GeoTiffProductReaderPlugIn;
import org.esa.beam.framework.dataio.AbstractProductReader;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.MetadataAttribute;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.jdom.Element;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.logging.XMLFormatter;

/**
 * This reader is capable of reading Landsat data products
 * where each bands is distributes as a single GeoTIFF image.
 */
public class LandsatGeotiffReader extends AbstractProductReader {

    public LandsatGeotiffReader(LandsatGeotiffReaderPlugin readerPlugin) {
        super(readerPlugin);
    }

    @Override
    protected Product readProductNodesImpl() throws IOException {
        final File mtlFile = new File(getInput().toString());
        if (!mtlFile.canRead()) {
            throw new IOException("Can not read metadata file: "+ mtlFile.getAbsolutePath());
        }
        Metadata metadata = new Metadata(new FileReader(mtlFile));
        MetadataElement metadataElement = metadata.getMetaDataElementRoot();
        Product product = new Product(mtlFile.getName(), metadata.getProductType(), metadata.getProductWidth(), metadata.getProductHeight());
        product.getMetadataRoot().addElement(metadataElement);

        addBands(product, metadataElement.getElement("PRODUCT_METADATA"), mtlFile.getParentFile());

        return product;
    }

    private static void addBands(Product product, MetadataElement element, File folder) throws IOException {
        GeoTiffProductReaderPlugIn plugIn = new GeoTiffProductReaderPlugIn();
        MetadataAttribute[] metadataAttributes = element.getAttributes();
        int bandId = 1;
        for (int i = 0; i < metadataAttributes.length; i++) {
            MetadataAttribute metadataAttribute = metadataAttributes[i];
            String attributeName = metadataAttribute.getName();
            if (attributeName.matches("BAND._FILE_NAME")) {
                String fileName = metadataAttribute.getData().getElemString();
                File bandFile = new File(folder, fileName);
                ProductReader productReader = plugIn.createReaderInstance();
                Product srcProduct = productReader.readProductNodes(bandFile, null);
                if (srcProduct != null) {
                    if (bandId == 1) {
                        product.setGeoCoding(srcProduct.getGeoCoding());
                    }
                    Band srcBand = srcProduct.getBandAt(0);
                    String bandName = "band_" + bandId++;
                    Band band = product.addBand(bandName, srcBand.getDataType());
                    band.setSourceImage(srcBand.getSourceImage());
                    band.setNoDataValue(0.0);
                    band.setNoDataValueUsed(true);
                }
            }
        }

    }

    @Override
    protected void readBandRasterDataImpl(int sourceOffsetX, int sourceOffsetY, int sourceWidth, int sourceHeight, int sourceStepX, int sourceStepY, Band destBand, int destOffsetX, int destOffsetY, int destWidth, int destHeight, ProductData destBuffer, ProgressMonitor pm) throws IOException {
        // all bands use source images as source for its data
        throw new IllegalStateException();
    }
}
