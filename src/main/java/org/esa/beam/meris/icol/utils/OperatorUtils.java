package org.esa.beam.meris.icol.utils;

import com.bc.ceres.core.PrintWriterProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.TiePointGrid;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.gpf.operators.standard.WriteOp;
import org.esa.beam.meris.icol.Instrument;
import org.esa.beam.util.Guardian;
import org.esa.beam.util.ProductUtils;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;


public class OperatorUtils {

    private static final int[] ALL_BANDS = new int[]{};

    private OperatorUtils() {
    }

    public static Product createCompatibleProduct(Product sourceProduct, String name, String type) {
        return createCompatibleProduct(sourceProduct, name, type, false);
    }

    /**
     * Creates a new product with the same size.
     * Copies geocoding and the start and stop time.
     */
    public static Product createCompatibleProduct(Product sourceProduct, String name, String type,
                                                  boolean includeTiepoints) {
        final int sceneWidth = sourceProduct.getSceneRasterWidth();
        final int sceneHeight = sourceProduct.getSceneRasterHeight();

        Product targetProduct = new Product(name, type, sceneWidth, sceneHeight);
        if (includeTiepoints) {
            ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        }
        copyProductBase(sourceProduct, targetProduct);
        return targetProduct;
    }

    /**
     * Copies geocoding and the start and stop time.
     */
    public static void copyProductBase(Product sourceProduct, Product targetProduct) {
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
    }

    public static void copyFlagBandsWithImages(Product sourceProduct, Product targetProduct) {
        ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
    }

    public static Band[] addBandGroup(Product srcProduct, int numSrcBands, int[] bandsToSkip, Product targetProduct,
                                      String targetPrefix, double noDataValue, boolean compactTargetArray) {
        int numTargetBands = numSrcBands;
        if (compactTargetArray && bandsToSkip.length > 0) {
            numTargetBands -= bandsToSkip.length;
        }
        Band[] targetBands = new Band[numTargetBands];
        int targetIndex = 0;
        for (int srcIndex = 0; srcIndex < numSrcBands; srcIndex++) {
            if (!IcolUtils.isIndexToSkip(srcIndex, bandsToSkip)) {
                Band srcBand = srcProduct.getBandAt(srcIndex);
                final String bandName = targetPrefix + "_" + (srcIndex + 1);
                if (!targetProduct.containsRasterDataNode(bandName)) {
                    Band targetBand = targetProduct.addBand(bandName, ProductData.TYPE_FLOAT32);
                    ProductUtils.copySpectralBandProperties(srcBand, targetBand);
                    targetBand.setNoDataValueUsed(true);
                    targetBand.setNoDataValue(noDataValue);
                    targetBands[targetIndex] = targetBand;
                    targetIndex++;
                }
            } else {
                // skip this src index
                if (!compactTargetArray) {
                    targetIndex++;
                }
            }
        }
        return targetBands;
    }

    public static Tile[] getSourceTiles(Operator op, Product srcProduct, String bandPrefix, Instrument instrument,
                                        Rectangle rect) throws OperatorException {
        return getSourceTiles(op, srcProduct, bandPrefix, instrument.numSpectralBands, instrument.bandsToSkip, rect);
    }

    public static Tile[] getSourceTiles(Operator op, Product srcProduct, String bandPrefix, int numBands,
                                        int[] bandsToSkip, Rectangle rect) throws OperatorException {
        Tile[] tiles = new Tile[numBands];
        for (int i = 0; i < tiles.length; i++) {
            if (!IcolUtils.isIndexToSkip(i, bandsToSkip)) {
                String bandName = bandPrefix + "_" + (i + 1);
                Band srcBand = srcProduct.getBand(bandName);
                if (srcBand == null) {
                    throw new OperatorException("Missing band " + bandName + " in source product " + srcProduct.getName());
                }
                tiles[i] = op.getSourceTile(srcBand, rect);
            }
        }
        return tiles;
    }

    public static Tile[] getTargetTiles(Map<Band, Tile> targetTiles, Band[] bands) {
        return getTargetTiles(targetTiles, bands, ALL_BANDS);
    }

    public static Tile[] getTargetTiles(Map<Band, Tile> targetTiles, Band[] bands, int[] bandsToSkip) {
        Tile[] tiles = new Tile[bands.length];
        for (int i = 0; i < bands.length; i++) {
            if (IcolUtils.isIndexToSkip(i, bandsToSkip)) {
                continue;
            }
            tiles[i] = targetTiles.get(bands[i]);
        }
        return tiles;
    }

    public static void copyBandProperties(Product sourceProduct, Product targetProduct) {
        for (Band targetBand : targetProduct.getBands()) {
            final Band sourceBand = sourceProduct.getBand(targetBand.getName());
            if (sourceBand != null) {
                copyBandProperties(sourceBand, targetBand);
            }
        }
    }

    public static void copyBandProperties(Band sourceBand, Band targetBand) {
        Guardian.assertNotNull("source", sourceBand);
        Guardian.assertNotNull("target", targetBand);

        targetBand.setSpectralWavelength(sourceBand.getSpectralWavelength());
        targetBand.setSpectralBandwidth(sourceBand.getSpectralBandwidth());
        targetBand.setSolarFlux(sourceBand.getSolarFlux());
        targetBand.setDescription(sourceBand.getDescription());
        targetBand.setUnit(sourceBand.getUnit());
        targetBand.setName(sourceBand.getName());
        targetBand.setNoDataValue(sourceBand.getNoDataValue());
        targetBand.setNoDataValueUsed(sourceBand.isNoDataValueUsed());
        targetBand.setValidPixelExpression(sourceBand.getValidPixelExpression());
    }

    // @author Norman

    /**
     * Persist a product to disk (temp dir), disposes it, reads it in again and returns the
     * new instance.
     * May be used to get rid of all the tiles that are in use due to the given product.
     *
     * @param product      A product to persist.
     * @param variableName The name of the persisted product file (tip: use the Product variable used in your source code).
     * @param logger       A logger.
     * @return A new product instance which contains everything that {@code product} contains.
     * @throws OperatorException If an I/O error occurs.
     */
    public static Product persist(Product product, String variableName, Logger logger) throws OperatorException {
        if (product == null) {
            return null;
        }
        String oldName = product.getName();
        try {
            File file = new File(System.getProperty("java.io.tmpdir"),
                                 String.format("%s_%s.dim", variableName, Long.toHexString(System.nanoTime())));
            logger.info("Writing product " + oldName + " to " + file);
            WriteOp writeOp = new WriteOp();
            writeOp.setFile(file);
            writeOp.setFormatName("BEAM-DIMAP");
            writeOp.setClearCacheAfterRowWrite(true);
            writeOp.setDeleteOutputOnFailure(false);
            writeOp.setWriteEntireTileRows(true);
            writeOp.setSourceProduct(product);
            writeOp.writeProduct(new PrintWriterProgressMonitor(System.out));
            product.dispose();
            logger.info("Product written. Now reading in again...");
            Product persistedProduct = ProductIO.readProduct(file);
            persistedProduct.setName(oldName);
            logger.info("Product read.");
            return persistedProduct;
        } catch (IOException e) {
            throw new OperatorException(String.format("Failed to persist product %s: %s", oldName, e.getMessage()), e);
        }
    }

    /**
     * checks for mandatory properties of MERIS input product
     *
     * @param sourceProduct - the source product
     */
    public static void validateMerisInputBands(Product sourceProduct) {

        if (sourceProduct.getStartTime() == null) {
            throw new OperatorException(String.format("Start time missing in input product."));
        }
        if (sourceProduct.getEndTime() == null) {
            throw new OperatorException(String.format("End time missing in input product."));
        }


        // we need a detector index band...
        final Band detectorIndexBand = sourceProduct.getBand(EnvisatConstants.MERIS_DETECTOR_INDEX_DS_NAME);
        if (detectorIndexBand == null) {
            throw new OperatorException(String.format("Mandatory band '%s' missing in input product.",
                                                      EnvisatConstants.MERIS_DETECTOR_INDEX_DS_NAME));
        }
        // we need a sun zenith TPG...
        final TiePointGrid sunZenihTPG = sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME);
        if (sunZenihTPG == null) {
            throw new OperatorException(String.format("Mandatory tie point grid '%s' missing in input product.",
                                                      EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME));
        }

        // we will need bands radiance_1, ..., radiance_15
        for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            final Band band = sourceProduct.getBand(EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[i]);
            if (band == null) {
                throw new OperatorException(String.format("Mandatory band '%s' missing in input product.",
                                                          EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[i]));
            }

            // for each band, we will need spectral band index, spectral wavelength
            if (band.getSpectralBandIndex() < 0 || band.getSpectralBandIndex() > 14) {
                throw new OperatorException(String.format("Input band '%s' has invalid spectral index %d.",
                                                          EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[i],
                                                          band.getSpectralBandIndex()));
            }
            if (band.getSpectralWavelength() < 402 || band.getSpectralWavelength() > 910) {
                throw new OperatorException(String.format("Input band '%s' has spectral wavelength %d - out of range.",
                                                          EnvisatConstants.MERIS_L1B_SPECTRAL_BAND_NAMES[i],
                                                          band.getSpectralBandIndex()));
            }
        }
    }

}
