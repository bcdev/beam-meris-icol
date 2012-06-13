package org.esa.beam.meris.icol.etm;

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
import org.esa.beam.meris.icol.tm.TmBasisOp;
import org.esa.beam.meris.icol.tm.TmConstants;
import org.esa.beam.util.BitSetter;
import org.esa.beam.util.ProductUtils;

import javax.media.jai.BorderExtender;
import java.awt.*;

/**
 * @author Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
@OperatorMetadata(alias = "Landsat.EtmCloudClassification",
                  version = "1.0",
                  internal = true,
                  authors = "Olaf Danne",
                  copyright = "(c) 2009 by Brockmann Consult",
                  description = "Landsat5 TM cloud classification.")
public class EtmCloudClassificationOp extends TmBasisOp {

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
    @Parameter(defaultValue = "true")
    private boolean landsatCloudFlagApplyBrightnessFilter;
    @Parameter(defaultValue = "true")
    private boolean landsatCloudFlagApplyNdviFilter;
    @Parameter(defaultValue = "true")
    private boolean landsatCloudFlagApplyNdsiFilter;
    @Parameter(defaultValue = "true")
    private boolean landsatCloudFlagApplyTemperatureFilter;
    @Parameter(interval = "[0.0, 1.0]", defaultValue = "0.3")
    private double cloudBrightnessThreshold;
    @Parameter(interval = "[0.0, 1.0]", defaultValue = "0.2")
    private double cloudNdviThreshold;
    @Parameter(interval = "[0.0, 10.0]", defaultValue = "3.0")
    private double cloudNdsiThreshold;
    @Parameter(interval = "[200.0, 320.0]", defaultValue = "300.0")
    private double cloudTM6Threshold;

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
        reflectanceBands[0] = sourceProduct.getBand(TmConstants.LANDSAT5_REFLECTANCE_BAND_NAMES[1]);   // .._tm2
        reflectanceBands[1] = sourceProduct.getBand(TmConstants.LANDSAT5_REFLECTANCE_BAND_NAMES[2]);   // .._tm3
        reflectanceBands[2] = sourceProduct.getBand(TmConstants.LANDSAT5_REFLECTANCE_BAND_NAMES[3]);   // .._tm4
        reflectanceBands[3] = sourceProduct.getBand(TmConstants.LANDSAT5_REFLECTANCE_BAND_NAMES[4]);   // .._tm5
        if (sourceProduct.getProductType().startsWith("Landsat5")) {
            reflectanceBands[4] = sourceProduct.getBand(TmConstants.LANDSAT5_REFLECTANCE_BAND_NAMES[5]);   // .._tm6
        } else if (sourceProduct.getProductType().startsWith("Landsat7")) {
            // todo: clarify if tm6a or tm6b should be used (seem to be nearly the same)
            reflectanceBands[4] = sourceProduct.getBand(TmConstants.LANDSAT7_REFLECTANCE_BAND_NAMES[5]);   // .._tm6a
        } else {
            throw new OperatorException("Unknown source product type '" + sourceProduct.getProductType() + "' - cannot proceed.");
        }
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
//                    final float tm2 = reflectanceTile[1].getSampleFloat(x, y);
//                    final float tm3 = reflectanceTile[2].getSampleFloat(x, y);
//                    final float tm4 = reflectanceTile[3].getSampleFloat(x, y);
//                    final float tm5 = reflectanceTile[4].getSampleFloat(x, y);
//                    final float tm6 = reflectanceTile[5].getSampleFloat(x, y);

                    final float tm2 = reflectanceTile[0].getSampleFloat(x, y);
                    final float tm3 = reflectanceTile[1].getSampleFloat(x, y);
                    final float tm4 = reflectanceTile[2].getSampleFloat(x, y);
                    final float tm5 = reflectanceTile[3].getSampleFloat(x, y);
                    final float tm6 = reflectanceTile[4].getSampleFloat(x, y);

                    if (landsatCloudFlagApplyBrightnessFilter) {
                        boolean isBright = (tm3 > cloudBrightnessThreshold);
                        targetTile.setSample(x, y, F_BRIGHT, isBright);
                    } else {
                        targetTile.setSample(x, y, F_BRIGHT, false);
                    }

                    if (landsatCloudFlagApplyNdviFilter) {
                        double ndvi = (tm4 - tm3) / (tm4 + tm3);
                        boolean isNdvi = (ndvi < cloudNdviThreshold);
                        targetTile.setSample(x, y, F_NDVI, isNdvi);
                    } else {
                        targetTile.setSample(x, y, F_NDVI, false);
                    }

                    if (landsatCloudFlagApplyNdsiFilter) {
                        double ndsi = (tm2 - tm5) / (tm2 + tm5);
                        boolean isNdsi = (ndsi < cloudNdsiThreshold);
                        targetTile.setSample(x, y, F_NDSI, isNdsi);
                    } else {
                        targetTile.setSample(x, y, F_NDSI, false);
                    }

                    if (landsatCloudFlagApplyTemperatureFilter) {
                        boolean isTemp = (tm6 < cloudTM6Threshold);
                        targetTile.setSample(x, y, F_TEMP, isTemp);
                    } else {
                        targetTile.setSample(x, y, F_TEMP, false);
                    }

                    boolean isCloud = isCloud(x, y, targetTile);
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
        boolean isCloud;

        if (!landsatCloudFlagApplyBrightnessFilter &&
                !landsatCloudFlagApplyNdviFilter &&
                !landsatCloudFlagApplyNdsiFilter &&
                !landsatCloudFlagApplyTemperatureFilter) {
            // no filter applied
            return false;
        }

        boolean isCloud1 = (targetTile.getSampleBit(x, y, F_BRIGHT) ||
                !landsatCloudFlagApplyBrightnessFilter);
        boolean isCloud2 = (targetTile.getSampleBit(x, y, F_NDVI) ||
                !landsatCloudFlagApplyNdviFilter);
        boolean isCloud3 = (targetTile.getSampleBit(x, y, F_NDSI) ||
                !landsatCloudFlagApplyNdsiFilter);
        boolean isCloud4 = (targetTile.getSampleBit(x, y, F_TEMP) ||
                !landsatCloudFlagApplyTemperatureFilter);

        isCloud = isCloud1 && isCloud2 && isCloud3 && isCloud4;
        return isCloud;
    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(EtmCloudClassificationOp.class);
        }
    }
}
