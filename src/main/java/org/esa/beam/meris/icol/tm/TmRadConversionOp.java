package org.esa.beam.meris.icol.tm;

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

import java.awt.Rectangle;
import java.awt.image.DataBuffer;
import java.util.Map;

/**
 * @author Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
@OperatorMetadata(alias = "Landsat.RadConversion",
        version = "1.0",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2009 by Brockmann Consult",
        description = "Converts radiances into reflectances and temperature (TM6) for Landsat.")
public class TmRadConversionOp extends Operator {

    private transient Band[] radianceBands;
    private transient Band[] reflectanceBands;

    private static final float k1 = 666.09f;    // ATBD ICOL_D4, eq. (24)
    private static final float k2 = 1282.71f;   // ATBD ICOL_D4, eq. (24)

    @SourceProduct(alias="l1g")
    private Product sourceProduct;
    @SourceProduct(alias="geometry")
    private Product geometryProduct;
    @TargetProduct
    private Product targetProduct;
    @Parameter
    private String startTime;
    @Parameter
    private String stopTime;

     private int daysSince2000;
    private double seasonalFactor;

    final int NO_DATA_VALUE = -1;

    @Override
    public void initialize() throws OperatorException {

        daysSince2000 = LandsatUtils.getDaysSince2000(sourceProduct.getStartTime().getElemString());
        seasonalFactor = Utils.computeSeasonalFactor(daysSince2000,
                                                      TmConstants.SUN_EARTH_DISTANCE_SQUARE);

        radianceBands = new Band[TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS];
        reflectanceBands = new Band[TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS];
        int sceneWidth = geometryProduct.getSceneRasterWidth();
        int sceneHeight = geometryProduct.getSceneRasterHeight();
        targetProduct = new Product(sourceProduct.getName() + "_ICOL", geometryProduct.getProductType(), sceneWidth, sceneHeight);
        targetProduct.setStartTime(geometryProduct.getStartTime());
        targetProduct.setEndTime(geometryProduct.getEndTime());
        for (int i = 0; i < TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS; i++) {
            radianceBands[i] = geometryProduct.getBand(TmConstants.LANDSAT5_RADIANCE_BAND_NAMES[i]);

            reflectanceBands[i] = targetProduct.addBand(TmConstants.LANDSAT5_REFLECTANCE_BAND_NAMES[i],
                                                        ProductData.TYPE_FLOAT32);
            reflectanceBands[i].setNoDataValueUsed(true);
            reflectanceBands[i].setNoDataValue(NO_DATA_VALUE);
            reflectanceBands[i].setSpectralBandIndex(i);
        }

        ProductUtils.copyGeoCoding(geometryProduct, targetProduct);
        addTiePointGrids();
    }
    
    private void addTiePointGrids() {
        // Add tie point grids for sun/view zenith/azimuths. Use MERIS notation.
        Band szaBand = geometryProduct.getBand(TmGeometryOp.SUN_ZENITH_BAND_NAME);
        Band saaBand = geometryProduct.getBand(TmGeometryOp.SUN_AZIMUTH_BAND_NAME);
        Band vzaBand = geometryProduct.getBand(TmGeometryOp.VIEW_ZENITH_BAND_NAME);
        Band vaaBand = geometryProduct.getBand(TmGeometryOp.VIEW_AZIMUTH_BAND_NAME);
        Band altitudeBand = geometryProduct.getBand(TmGeometryOp.ALTITUDE_BAND_NAME);

        // SZA
        DataBuffer dataBuffer = szaBand.getSourceImage().getData().getDataBuffer();
        float[] tpgData = new float[dataBuffer.getSize()];
        for (int i = 0; i < dataBuffer.getSize(); i++) {
            tpgData[i] = dataBuffer.getElemFloat(i);
        }
        TiePointGrid tpg = new TiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME,
                                               geometryProduct.getSceneRasterWidth(),
                                               geometryProduct.getSceneRasterHeight(),
                                               0.0f, 0.0f, 1.0f, 1.0f, tpgData);
        targetProduct.addTiePointGrid(tpg);

        // SAA
        dataBuffer = saaBand.getSourceImage().getData().getDataBuffer();
        tpgData = new float[dataBuffer.getSize()];
        for (int i = 0; i < dataBuffer.getSize(); i++) {
            tpgData[i] = dataBuffer.getElemFloat(i);
        }
        tpg = new TiePointGrid(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME,
                                               geometryProduct.getSceneRasterWidth(),
                                               geometryProduct.getSceneRasterHeight(),
                                               0.0f, 0.0f, 1.0f, 1.0f, tpgData);
        targetProduct.addTiePointGrid(tpg);

        // VZA
        dataBuffer = vzaBand.getSourceImage().getData().getDataBuffer();
        tpgData = new float[dataBuffer.getSize()];
        for (int i = 0; i < dataBuffer.getSize(); i++) {
            tpgData[i] = dataBuffer.getElemFloat(i);
        }
        tpg = new TiePointGrid(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME,
                                               geometryProduct.getSceneRasterWidth(),
                                               geometryProduct.getSceneRasterHeight(),
                                               0.0f, 0.0f, 1.0f, 1.0f, tpgData);
        targetProduct.addTiePointGrid(tpg);

        // VAA
        dataBuffer = vaaBand.getSourceImage().getData().getDataBuffer();
        tpgData = new float[dataBuffer.getSize()];
        for (int i = 0; i < dataBuffer.getSize(); i++) {
            tpgData[i] = dataBuffer.getElemFloat(i);
        }
        tpg = new TiePointGrid(EnvisatConstants.MERIS_VIEW_AZIMUTH_DS_NAME,
                                               geometryProduct.getSceneRasterWidth(),
                                               geometryProduct.getSceneRasterHeight(),
                                               0.0f, 0.0f, 1.0f, 1.0f, tpgData);
        targetProduct.addTiePointGrid(tpg);

         // altitude
        dataBuffer = altitudeBand.getSourceImage().getData().getDataBuffer();
        tpgData = new float[dataBuffer.getSize()];
        for (int i = 0; i < dataBuffer.getSize(); i++) {
            tpgData[i] = dataBuffer.getElemFloat(i);
        }
        tpg = new TiePointGrid(EnvisatConstants.MERIS_DEM_ALTITUDE_DS_NAME,
                                               geometryProduct.getSceneRasterWidth(),
                                               geometryProduct.getSceneRasterHeight(),
                                               0.0f, 0.0f, 1.0f, 1.0f, tpgData);
        targetProduct.addTiePointGrid(tpg);

    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle rectangle, ProgressMonitor pm) throws OperatorException {
        pm.beginTask("Processing frame...", rectangle.height);
        try {
        	Tile[] radianceTile = new Tile[radianceBands.length];
        	for (int i = 0; i < radianceTile.length; i++) {
        		radianceTile[i] = getSourceTile(radianceBands[i], rectangle, pm);
            }
            Tile szaTile = getSourceTile(geometryProduct.getBand(TmGeometryOp.SUN_ZENITH_BAND_NAME), rectangle, pm);

            Tile[] reflectanceTile = OperatorUtils.getTargetTiles(targetTiles, reflectanceBands);

            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
				for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                    final double cosSza = Math.cos(szaTile.getSampleFloat(x, y) * MathUtils.DTOR);
                    for (int bandId = 0; bandId < TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS; bandId++) {
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
                checkForCancelation(pm);
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
            super(TmRadConversionOp.class);
        }
    }
}
