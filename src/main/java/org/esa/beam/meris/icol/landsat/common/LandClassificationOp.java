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
 * Class providing Landsat land classification
 *
 * @author Olaf Danne
 */
@OperatorMetadata(alias = "Landsat.LandClassification",
                  version = "1.0",
                  internal = true,
                  authors = "Olaf Danne",
                  copyright = "(c) 2009 by Brockmann Consult",
                  description = "Landsat land classification.")
public class LandClassificationOp extends TmBasisOp {

    public static final String LAND_FLAGS = "land_classif_flags";

    public static final int F_LANDCONS = 0;
    public static final int F_LOINLD = 1;
    public static final int F_NDVI = 2;
    public static final int F_TEMP = 3;
    public static final int F_ICE = 4;

    private transient Band[] reflectanceBands;

    @SourceProduct(alias = "refl")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;
    @Parameter(defaultValue = "true")
    private boolean landsatLandFlagApplyNdviFilter;
    @Parameter(defaultValue = "true")
    private boolean landsatLandFlagApplyTemperatureFilter;
    @Parameter(interval = "[0.0, 1.0]", defaultValue = "0.2")
    private double landNdviThreshold;
    @Parameter(interval = "[200.0, 320.0]", defaultValue = "300.0")
    private double landTM6Threshold;
    @Parameter(defaultValue = "", valueSet = {LandsatConstants.LAND_FLAGS_SUMMER,
            LandsatConstants.LAND_FLAGS_WINTER})
    private String landsatSeason;
    @Parameter
    private String[] reflectanceBandNames;

    @Override
    public void initialize() throws OperatorException {
        final int sceneWidth = sourceProduct.getSceneRasterWidth();
        final int sceneHeight = sourceProduct.getSceneRasterHeight();
        targetProduct = new Product(sourceProduct.getName() + "_TM_FLAGS", "ICOL", sceneWidth, sceneHeight);

        Band landFlagBand = targetProduct.addBand(LAND_FLAGS, ProductData.TYPE_INT16);
        FlagCoding flagCoding = createFlagCoding();
        landFlagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);

        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);

        reflectanceBands = new Band[3];
        reflectanceBands[0] = sourceProduct.getBand(reflectanceBandNames[2]);
        reflectanceBands[1] = sourceProduct.getBand(reflectanceBandNames[3]);
        reflectanceBands[2] = sourceProduct.getBand(reflectanceBandNames[5]);
    }

    public static FlagCoding createFlagCoding() {
        FlagCoding flagCoding = new FlagCoding(LAND_FLAGS);
        flagCoding.addFlag("F_LANDCONS", BitSetter.setFlag(0, F_LANDCONS), null);
        flagCoding.addFlag("F_LOINLD", BitSetter.setFlag(0, F_LOINLD), null);
        flagCoding.addFlag("F_NDVI", BitSetter.setFlag(0, F_NDVI), null);
        flagCoding.addFlag("F_TEMP", BitSetter.setFlag(0, F_TEMP), null);
        flagCoding.addFlag("F_ICE", BitSetter.setFlag(0, F_ICE), null);

        return flagCoding;
    }


    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        Rectangle rectangle = targetTile.getRectangle();

        Tile[] reflectanceTile = new Tile[reflectanceBands.length];
        for (int i = 0; i < reflectanceTile.length; i++) {
            reflectanceTile[i] = getSourceTile(reflectanceBands[i], rectangle,
                                               BorderExtender.createInstance(BorderExtender.BORDER_COPY));
        }

        pm.beginTask("Processing frame...", rectangle.height);
        try {
            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                    final float tm3 = reflectanceTile[0].getSampleFloat(x, y);
                    final float tm4 = reflectanceTile[1].getSampleFloat(x, y);
                    final float tm6 = reflectanceTile[2].getSampleFloat(x, y);

                    if (landsatLandFlagApplyNdviFilter) {
                        double ndvi = (tm4 - tm3) / (tm4 + tm3);
                        boolean isNdvi = (ndvi > landNdviThreshold);
                        targetTile.setSample(x, y, F_NDVI, isNdvi);
                    } else {
                        targetTile.setSample(x, y, F_NDVI, false);
                    }

                    if (landsatLandFlagApplyTemperatureFilter) {
                        boolean isTemp;
                        if (landsatSeason.equals(LandsatConstants.LAND_FLAGS_SUMMER)) {
                            isTemp = (tm6 > landTM6Threshold);
                        } else {
                            isTemp = (tm6 < landTM6Threshold);
                        }
                        targetTile.setSample(x, y, F_TEMP, isTemp);
                    } else {
                        targetTile.setSample(x, y, F_TEMP, false);
                    }

                    boolean is_ice = false;
                    // todo: RS to define ice criterion
                    targetTile.setSample(x, y, F_ICE, is_ice);

                    boolean isLand = isLand(x, y, targetTile);
                    targetTile.setSample(x, y, F_LANDCONS, isLand);

                    // todo: RS to define inland water criterion if needed
                    boolean isInlandWater = false;
                    targetTile.setSample(x, y, F_LOINLD, isInlandWater);
                }
                pm.worked(1);
            }
        } catch (Exception e) {
            throw new OperatorException(e);
        } finally {
            pm.done();
        }
    }

    private boolean isLand(int x, int y, Tile targetTile) {

        if (!landsatLandFlagApplyNdviFilter && !landsatLandFlagApplyTemperatureFilter) {
            // no filter applied
            return false;
        }

        boolean isLand1 = targetTile.getSampleBit(x, y, F_NDVI) || !landsatLandFlagApplyNdviFilter;
        boolean isLand2 = targetTile.getSampleBit(x, y, F_TEMP) || !landsatLandFlagApplyTemperatureFilter;

        return isLand1 || isLand2;

    }

    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(LandClassificationOp.class);
        }
    }
}
