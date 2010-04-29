package org.esa.beam.meris.icol;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.util.ProductUtils;

import java.awt.Rectangle;
import java.awt.image.RenderedImage;
import java.util.Random;

/**
 * @author Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
public class TestImageOp extends Operator {

    @SourceProduct(alias="source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;
    private int sceneWidth;
    private int sceneHeight;

    public void initialize() throws OperatorException {

        targetProduct = createTargetProduct(sourceProduct, sourceProduct.getName() + "_const", sourceProduct.getProductType());

        String[] sourceNames = sourceProduct.getBandNames();
        for (String name : sourceNames) {
            Band sourceBand = sourceProduct.getBand(name);
            RenderedImage sourceImage = sourceProduct.getBand(name).getSourceImage();
            if (!sourceBand.isFlagBand()) {
                Band targetBand = targetProduct.addBand(name, sourceBand.getDataType());
                targetBand.setSpectralBandIndex(sourceBand.getSpectralBandIndex());
                targetBand.setNoDataValue(sourceBand.getNoDataValue());
            }
        }
    }

    public Product createTargetProduct(Product sourceProduct, String name, String type) {
        sceneWidth = sourceProduct.getSceneRasterWidth();
        sceneHeight = sourceProduct.getSceneRasterHeight();

        Product targetProduct = new Product(name, type, sceneWidth, sceneHeight);

        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        ProductUtils.copyFlagBands(sourceProduct, targetProduct);

        return targetProduct;
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        Rectangle rectangle = targetTile.getRectangle();

        pm.beginTask("Processing frame...", rectangle.height);
        try {
            Random r = new Random(0);
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                    double random;
                    double value;
                    if (x > sceneWidth / 2) {
//                        random = 0.0;
                        random = 0.01*r.nextGaussian();
                        value = Math.max(0.1 + random, 0.0);
                        targetTile.setSample(x, y, value);
                    } else if (x < sceneWidth / 2) {
//                        random = 0.0;
                        random = 0.02*r.nextGaussian();
                        value = Math.max(0.2 + random, 0.0);
                        targetTile.setSample(x, y, value);
                    } else {
//                        random = 0.0;
                        random = 0.015*r.nextGaussian();
                        value = Math.max(0.15 + random, 0.0);
                        targetTile.setSample(x, y, value);
                    }
                }
                pm.worked(1);
            }
        } catch (Exception e) {
            throw new OperatorException(e);
        } finally {
            pm.done();
        }
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(TestImageOp.class);
        }
    }
}
