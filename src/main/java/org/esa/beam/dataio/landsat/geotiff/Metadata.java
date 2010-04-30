package org.esa.beam.dataio.landsat.geotiff;

import org.esa.beam.framework.datamodel.MetadataAttribute;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.ProductData;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;


class Metadata {

    private final MetadataElement root;

    Metadata(Reader mtlReader) throws IOException {
         root = parseMTL(mtlReader);
    }

    private MetadataElement parseMTL(Reader mtlReader) throws IOException {

        MetadataElement base = null;
        MetadataElement currentElement = null;
        BufferedReader reader = new BufferedReader(mtlReader);
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.startsWith("GROUP")) {
                int i = line.indexOf('=');
                String groupName = line.substring(i+1).trim();
                MetadataElement element = new MetadataElement(groupName);
                if (base == null) {
                    base = element;
                    currentElement = element;
                } else {
                    currentElement.addElement(element);
                    currentElement = element;
                }
            } else if (line.startsWith("END_GROUP")) {
                currentElement = currentElement.getParentElement();
            } else if (line.equals("END")) {
                return base;
            } else {
                MetadataAttribute attribute = createAttribute(line);
                currentElement.addAttribute(attribute);
            }
        }
        return base;
    }

    private MetadataAttribute createAttribute(String line) {
        int i = line.indexOf('=');
        String name = line.substring(0, i).trim();
        String value = line.substring(i+1).trim();
        ProductData pData;
        if (value.startsWith("\"")) {
            value = value.substring(1, value.length()-1);
            pData = ProductData.createInstance(value);
        } else if (value.contains(".")) {
            try {
                double d = Double.parseDouble(value);
                pData = ProductData.createInstance(new double[]{d});
            } catch (NumberFormatException e) {
                 pData = ProductData.createInstance(value);
            }
        } else {
            try {
                int integer = Integer.parseInt(value);
                pData = ProductData.createInstance(new int[]{integer});
            } catch (NumberFormatException e) {
                 pData = ProductData.createInstance(value);
            }
        }
        return new MetadataAttribute(name, pData, true);
    }

    MetadataElement getMetaDataElementRoot() {
        return root;
    }

    int getProductWidth() {
        return root.getElement("PRODUCT_METADATA").getAttribute("PRODUCT_SAMPLES_REF").getData().getElemInt();
    }

    int getProductHeight() {
        return root.getElement("PRODUCT_METADATA").getAttribute("PRODUCT_LINES_REF").getData().getElemInt();
    }

    String getProductType() {
        return root.getElement("PRODUCT_METADATA").getAttribute("PRODUCT_TYPE").getData().getElemString();
    }
}
