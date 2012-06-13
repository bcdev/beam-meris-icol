package org.esa.beam.meris.icol.tm;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.util.ProductUtils;

import javax.media.jai.BorderExtender;
import java.awt.Rectangle;

/**
 * @author Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
public class TmCloudTopPressureOp extends TmBasisOp {

    private transient Band reflectanceBand6;

    @SourceProduct(alias = "refl")
    private Product sourceProduct;
    @SourceProduct(alias = "cloud")
    private Product cloudProduct;
    @TargetProduct
    private Product targetProduct;
    @Parameter(interval = "[300.0, 1060.0]", defaultValue = "1013.25")
    private double userPSurf;
    @Parameter(interval = "[200.0, 320.0]", defaultValue = "300.0")
    private double userTm60;

    @Override
    public void initialize() throws OperatorException {
        // todo: implement
        final int sceneWidth = sourceProduct.getSceneRasterWidth();
        final int sceneHeight = sourceProduct.getSceneRasterHeight();
        targetProduct = new Product(sourceProduct.getName() + "_CTP", "ICOL", sceneWidth, sceneHeight);

        targetProduct.addBand(TmConstants.LANDSAT_CTP_BAND_NAME, ProductData.TYPE_FLOAT32);

        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);

        reflectanceBand6 = sourceProduct.getBand(TmConstants.LANDSAT5_REFLECTANCE_6_BAND_NAME);
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        Rectangle rectangle = targetTile.getRectangle();

        Tile reflectance6Tile = getSourceTile(reflectanceBand6, rectangle, BorderExtender.createInstance(BorderExtender.BORDER_COPY));
        Tile cloudFlags = getSourceTile(cloudProduct.getBand(TmCloudClassificationOp.CLOUD_FLAGS), rectangle,
                BorderExtender.createInstance(BorderExtender.BORDER_COPY));

        pm.beginTask("Processing frame...", rectangle.height);
        try {
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                    final boolean isCloud = cloudFlags.getSampleBit(x, y, TmCloudClassificationOp.F_CLOUD);
                    double ctp = 300.0; // TBC
                    if (isCloud) {
                        final float tm6 = reflectance6Tile.getSampleFloat(x, y);
                        ctp = userPSurf + TmConstants.CTP_K_FACTOR * (tm6 - userTm60);
                    }
                    targetTile.setSample(x, y, ctp);
                }
                pm.worked(1);
            }
        } catch (Exception e) {
            throw new OperatorException(e);
        } finally {
            pm.done();
        }
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(TmCloudTopPressureOp.class);
        }
    }
}
