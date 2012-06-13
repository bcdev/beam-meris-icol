package org.esa.beam.meris.icol.landsat.common;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.meris.icol.landsat.tm.TmBasisOp;
import org.esa.beam.util.BitSetter;
import org.esa.beam.util.ProductUtils;

import javax.media.jai.BorderExtender;
import java.awt.Rectangle;

/**
 * Landsat5 TM cloud classification
 *
 * @author Olaf Danne
 */
@OperatorMetadata(alias = "Landsat.CloudClassification",
        version = "1.0",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2009 by Brockmann Consult",
        description = "Landsat cloud classification.")
public class CloudClassificationOp extends TmBasisOp {

    public static final String CLOUD_FLAGS = "cloud_classif_flags";

    public static final int F_CLOUD = 0;
    public static final int F_BRIGHT = 1;
    public static final int F_NDVI = 2;
    public static final int F_NDSI = 3;
    public static final int F_TEMP = 4;

    private transient Band[] reflectanceBands;

    @SourceProduct(alias = "refl")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;
    @Parameter(defaultValue="true")
    private boolean landsatCloudFlagApplyBrightnessFilter;
    @Parameter(defaultValue="true")
    private boolean landsatCloudFlagApplyNdviFilter;
    @Parameter(defaultValue="true")
    private boolean landsatCloudFlagApplyNdsiFilter;
    @Parameter(defaultValue="true")
    private boolean landsatCloudFlagApplyTemperatureFilter;
    @Parameter(interval = "[0.0, 1.0]", defaultValue="0.3")
    private double cloudBrightnessThreshold;
    @Parameter(interval = "[0.0, 1.0]", defaultValue="0.2")
    private double cloudNdviThreshold;
    @Parameter(interval = "[0.0, 10.0]", defaultValue="3.0")
    private double cloudNdsiThreshold;
    @Parameter(interval = "[200.0, 320.0]", defaultValue="300.0")
    private double cloudTM6Threshold;
    @Parameter
    private String[] reflectanceBandNames;

    @Override
    public void initialize() throws OperatorException {
        final int sceneWidth = sourceProduct.getSceneRasterWidth();
        final int sceneHeight = sourceProduct.getSceneRasterHeight();
        targetProduct = new Product(sourceProduct.getName() + "_TM_FLAGS", "ICOL", sceneWidth, sceneHeight);

        Band cloudFlagBand = targetProduct.addBand(CLOUD_FLAGS, ProductData.TYPE_INT16);
        FlagCoding flagCoding = createFlagCoding();
        cloudFlagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);

        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);

        reflectanceBands = new Band[5];
        reflectanceBands[0] = sourceProduct.getBand(reflectanceBandNames[1]);
        reflectanceBands[1] = sourceProduct.getBand(reflectanceBandNames[2]);
        reflectanceBands[2] = sourceProduct.getBand(reflectanceBandNames[3]);
        reflectanceBands[3] = sourceProduct.getBand(reflectanceBandNames[4]);
        reflectanceBands[4] = sourceProduct.getBand(reflectanceBandNames[5]);

    }

    public static FlagCoding createFlagCoding() {
        FlagCoding flagCoding = new FlagCoding(CLOUD_FLAGS);
        flagCoding.addFlag("F_CLOUD", BitSetter.setFlag(0, F_CLOUD), null);
        flagCoding.addFlag("F_BRIGHT", BitSetter.setFlag(0, F_BRIGHT), null);
        flagCoding.addFlag("F_NDVI", BitSetter.setFlag(0, F_NDVI), null);
        flagCoding.addFlag("F_NDSI", BitSetter.setFlag(0, F_NDSI), null);
        flagCoding.addFlag("F_TEMP", BitSetter.setFlag(0, F_TEMP), null);

        return flagCoding;
    }


    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        Rectangle rectangle = targetTile.getRectangle();

        Tile[] reflectanceTile = new Tile[reflectanceBands.length];
        for (int i = 0; i < reflectanceTile.length; i++) {
            reflectanceTile[i] = getSourceTile(reflectanceBands[i], rectangle, BorderExtender.createInstance(BorderExtender.BORDER_COPY));
        }

        pm.beginTask("Processing frame...", rectangle.height);
        try {
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                    final float tm2 = reflectanceTile[0].getSampleFloat(x, y);
                    final float tm3 = reflectanceTile[1].getSampleFloat(x, y);
                    final float tm4 = reflectanceTile[2].getSampleFloat(x, y);
                    final float tm5 = reflectanceTile[3].getSampleFloat(x, y);
                    final float tm6 = reflectanceTile[4].getSampleFloat(x, y);

                    if (landsatCloudFlagApplyBrightnessFilter) {
                        final boolean isBright =  (tm3 > cloudBrightnessThreshold);
                        targetTile.setSample(x, y, F_BRIGHT, isBright);
                    } else {
                        targetTile.setSample(x, y, F_BRIGHT, false);
                    }

                    if (landsatCloudFlagApplyNdviFilter) {
                        final double ndvi = (tm4 - tm3)/(tm4 + tm3);
                        final boolean isNdvi =  (ndvi < cloudNdviThreshold);
                        targetTile.setSample(x, y, F_NDVI, isNdvi);
                    } else {
                        targetTile.setSample(x, y, F_NDVI, false);
                    }

                    if (landsatCloudFlagApplyNdsiFilter) {
                        final double ndsi = (tm2 - tm5)/(tm2 + tm5);
                        final boolean isNdsi =  (ndsi < cloudNdsiThreshold);
                        targetTile.setSample(x, y, F_NDSI, isNdsi);
                    } else {
                        targetTile.setSample(x, y, F_NDSI, false);
                    }

                    if (landsatCloudFlagApplyTemperatureFilter) {
                        final boolean isTemp =  (tm6 < cloudTM6Threshold);
                        targetTile.setSample(x, y, F_TEMP, isTemp);
                    } else {
                        targetTile.setSample(x, y, F_TEMP, false);
                    }

                    final boolean isCloud = isCloud(x, y, targetTile);
                    targetTile.setSample(x, y, F_CLOUD, isCloud);
                }
                pm.worked(1);
            }
        } catch (Exception e) {
            throw new OperatorException(e);
        } finally {
            pm.done();
        }
    }

    private boolean isCloud(int x, int y, Tile targetTile) {
        if (!landsatCloudFlagApplyBrightnessFilter &&
            !landsatCloudFlagApplyNdviFilter &&
            !landsatCloudFlagApplyNdsiFilter &&
            !landsatCloudFlagApplyTemperatureFilter) {
            // no filter applied
            return false;
        }

        final boolean isCloud1 = (targetTile.getSampleBit(x, y, F_BRIGHT) ||
                !landsatCloudFlagApplyBrightnessFilter);
        final boolean isCloud2 = (targetTile.getSampleBit(x, y, F_NDVI) ||
                !landsatCloudFlagApplyNdviFilter);
        final boolean isCloud3 = (targetTile.getSampleBit(x, y, F_NDSI) ||
                !landsatCloudFlagApplyNdsiFilter);
        final boolean isCloud4 = (targetTile.getSampleBit(x, y, F_TEMP) ||
                !landsatCloudFlagApplyTemperatureFilter);

        return isCloud1 && isCloud2 && isCloud3 && isCloud4;
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(CloudClassificationOp.class);
        }
    }
}
