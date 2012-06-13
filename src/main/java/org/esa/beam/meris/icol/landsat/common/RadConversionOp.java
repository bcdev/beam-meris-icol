package org.esa.beam.meris.icol.landsat.common;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.TiePointGrid;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.meris.icol.utils.LandsatUtils;
import org.esa.beam.meris.icol.utils.OperatorUtils;
import org.esa.beam.meris.l2auxdata.Utils;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.math.MathUtils;

import javax.media.jai.BorderExtender;
import java.awt.Rectangle;
import java.awt.image.DataBuffer;
import java.util.Map;

/**
 *
 * Class providing conversion of radiances into reflectances and temperature (TM6) for Landsat
 *
 * @author Olaf Danne
 */
@OperatorMetadata(alias = "Landsat.RadConversion",
        version = "1.0",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2009 by Brockmann Consult",
        description = "Converts radiances into reflectances and temperature (TM6) for Landsat.")
public class RadConversionOp extends Operator {

    private transient Band[] radianceBands;
    private transient Band[] reflectanceBands;

    private static final float k1 = 666.09f;    // ATBD ICOL_D4, eq. (24)
    private static final float k2 = 1282.71f;   // ATBD ICOL_D4, eq. (24)

    @SourceProduct(alias="l1g")
    private Product sourceProduct;
    @SourceProduct(alias="downscaled")
    private Product downscaledProduct;
    @TargetProduct
    private Product targetProduct;
    @Parameter
    private String startTime;
    @Parameter
    private String stopTime;
    @Parameter
    private String[] radianceBandNames;
    @Parameter
    private String[] reflectanceBandNames;

    private double seasonalFactor;

    final int NO_DATA_VALUE = -1;

    @Override
    public void initialize() throws OperatorException {

        final String startTimeString = sourceProduct.getStartTime().toString().substring(0,20);
        int daysSince2000 = LandsatUtils.getDaysSince2000(startTimeString);
        seasonalFactor = Utils.computeSeasonalFactor(daysSince2000,
                                                      LandsatConstants.SUN_EARTH_DISTANCE_SQUARE);

        radianceBands = new Band[radianceBandNames.length];
        reflectanceBands = new Band[radianceBandNames.length];
        int sceneWidth = downscaledProduct.getSceneRasterWidth();
        int sceneHeight = downscaledProduct.getSceneRasterHeight();
        targetProduct = new Product(sourceProduct.getName() + "_ICOL", downscaledProduct.getProductType(), sceneWidth, sceneHeight);
        targetProduct.setStartTime(downscaledProduct.getStartTime());
        targetProduct.setEndTime(downscaledProduct.getEndTime());
        for (int i = 0; i < radianceBandNames.length; i++) {
            radianceBands[i] = downscaledProduct.getBand(radianceBandNames[i]);

            reflectanceBands[i] = targetProduct.addBand(reflectanceBandNames[i],
                                                        ProductData.TYPE_FLOAT32);
            reflectanceBands[i].setNoDataValueUsed(true);
            reflectanceBands[i].setNoDataValue(NO_DATA_VALUE);
            reflectanceBands[i].setSpectralBandIndex(i);
        }

        ProductUtils.copyGeoCoding(downscaledProduct, targetProduct);
        addTiePointGrids();
    }
    
    private void addTiePointGrids() {
        // Add tie point grids for sun/view zenith/azimuths. Use MERIS notation.
        Band szaBand = downscaledProduct.getBand(DownscaleOp.SUN_ZENITH_BAND_NAME);
        Band saaBand = downscaledProduct.getBand(DownscaleOp.SUN_AZIMUTH_BAND_NAME);
        Band vzaBand = downscaledProduct.getBand(DownscaleOp.VIEW_ZENITH_BAND_NAME);
        Band vaaBand = downscaledProduct.getBand(DownscaleOp.VIEW_AZIMUTH_BAND_NAME);
        Band altitudeBand = downscaledProduct.getBand(DownscaleOp.ALTITUDE_BAND_NAME);

        // SZA
        DataBuffer dataBuffer = szaBand.getSourceImage().getData().getDataBuffer();
        float[] tpgData = new float[dataBuffer.getSize()];
        for (int i = 0; i < dataBuffer.getSize(); i++) {
            tpgData[i] = dataBuffer.getElemFloat(i);
        }
        TiePointGrid tpg = new TiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME,
                                               downscaledProduct.getSceneRasterWidth(),
                                               downscaledProduct.getSceneRasterHeight(),
                                               0.0f, 0.0f, 1.0f, 1.0f, tpgData);
        targetProduct.addTiePointGrid(tpg);

        // SAA
        dataBuffer = saaBand.getSourceImage().getData().getDataBuffer();
        tpgData = new float[dataBuffer.getSize()];
        for (int i = 0; i < dataBuffer.getSize(); i++) {
            tpgData[i] = dataBuffer.getElemFloat(i);
        }
        tpg = new TiePointGrid(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME,
                                               downscaledProduct.getSceneRasterWidth(),
                                               downscaledProduct.getSceneRasterHeight(),
                                               0.0f, 0.0f, 1.0f, 1.0f, tpgData);
        targetProduct.addTiePointGrid(tpg);

        // VZA
        dataBuffer = vzaBand.getSourceImage().getData().getDataBuffer();
        tpgData = new float[dataBuffer.getSize()];
        for (int i = 0; i < dataBuffer.getSize(); i++) {
            tpgData[i] = dataBuffer.getElemFloat(i);
        }
        tpg = new TiePointGrid(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME,
                                               downscaledProduct.getSceneRasterWidth(),
                                               downscaledProduct.getSceneRasterHeight(),
                                               0.0f, 0.0f, 1.0f, 1.0f, tpgData);
        targetProduct.addTiePointGrid(tpg);

        // VAA
        dataBuffer = vaaBand.getSourceImage().getData().getDataBuffer();
        tpgData = new float[dataBuffer.getSize()];
        for (int i = 0; i < dataBuffer.getSize(); i++) {
            tpgData[i] = dataBuffer.getElemFloat(i);
        }
        tpg = new TiePointGrid(EnvisatConstants.MERIS_VIEW_AZIMUTH_DS_NAME,
                                               downscaledProduct.getSceneRasterWidth(),
                                               downscaledProduct.getSceneRasterHeight(),
                                               0.0f, 0.0f, 1.0f, 1.0f, tpgData);
        targetProduct.addTiePointGrid(tpg);

         // altitude
        dataBuffer = altitudeBand.getSourceImage().getData().getDataBuffer();
        tpgData = new float[dataBuffer.getSize()];
        for (int i = 0; i < dataBuffer.getSize(); i++) {
            tpgData[i] = dataBuffer.getElemFloat(i);
        }
        tpg = new TiePointGrid(EnvisatConstants.MERIS_DEM_ALTITUDE_DS_NAME,
                                               downscaledProduct.getSceneRasterWidth(),
                                               downscaledProduct.getSceneRasterHeight(),
                                               0.0f, 0.0f, 1.0f, 1.0f, tpgData);
        targetProduct.addTiePointGrid(tpg);

    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle rectangle, ProgressMonitor pm) throws OperatorException {
        pm.beginTask("Processing frame...", rectangle.height);
        try {
        	Tile[] radianceTile = new Tile[radianceBands.length];
        	for (int i = 0; i < radianceTile.length; i++) {
        		radianceTile[i] = getSourceTile(radianceBands[i], rectangle, BorderExtender.createInstance(BorderExtender.BORDER_COPY));
            }
            Tile szaTile = getSourceTile(downscaledProduct.getBand(DownscaleOp.SUN_ZENITH_BAND_NAME), rectangle,
                    BorderExtender.createInstance(BorderExtender.BORDER_COPY));

            Tile[] reflectanceTile = OperatorUtils.getTargetTiles(targetTiles, reflectanceBands);

            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
				for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                    final double cosSza = Math.cos(szaTile.getSampleFloat(x, y) * MathUtils.DTOR);
                    for (int bandId = 0; bandId < radianceBandNames.length; bandId++) {
                        final float rad = radianceTile[bandId].getSampleFloat(x, y);
                        if (bandId == 5) {
                            // ATBD ICOL_D4, eq. (24)
                            final double temperature = k2/Math.log((k1/rad) + 1.0);
                            reflectanceTile[bandId].setSample(x, y, temperature);
                        } else {
                            // ATBD ICOL_D4, eq. (23)
                            final double aRhoToa = LandsatUtils.convertRadToRefl(rad, cosSza, bandId, seasonalFactor);
                            reflectanceTile[bandId].setSample(x, y, aRhoToa);
                        }
                    }
                }
                checkForCancellation();
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
            super(RadConversionOp.class);
        }
    }
}
