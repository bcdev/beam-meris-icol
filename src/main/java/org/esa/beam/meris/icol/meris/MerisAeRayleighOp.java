/*
 * $Id: MerisAeRayleighOp.java,v 1.5 2007/05/10 17:01:06 marcoz Exp $
 *
 * Copyright (C) 2007 by Brockmann Consult (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation. This program is distributed in the hope it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.esa.beam.meris.icol.meris;

import com.bc.ceres.core.NullProgressMonitor;
import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.dataio.ProductWriter;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.framework.gpf.internal.TileImpl;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.gpf.operators.standard.BandMathsOp;
import org.esa.beam.meris.brr.CloudClassificationOp;
import org.esa.beam.meris.brr.GaseousCorrectionOp;
import org.esa.beam.meris.icol.CoeffW;
import org.esa.beam.meris.icol.FresnelReflectionCoefficient;
import org.esa.beam.meris.icol.IcolConstants;
import org.esa.beam.meris.icol.RhoBracketAlgo;
import org.esa.beam.meris.icol.RhoBracketJaiConvolve;
import org.esa.beam.meris.icol.RhoBracketKernellLoop;
import org.esa.beam.meris.icol.utils.IcolUtils;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.ResourceInstaller;
import org.esa.beam.util.SystemUtils;
import org.esa.beam.util.math.MathUtils;

import java.awt.Rectangle;
import java.awt.image.Raster;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.util.Map;


/**
 * Operator for Rayleigh part of AE correction.
 *
 * @author Marco Zuehlke, Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
@OperatorMetadata(alias = "Meris.AERayleigh",
        version = "1.0",
        internal = true,
        authors = "Marco Zuehlke",
        copyright = "(c) 2007 by Brockmann Consult",
        description = "Contribution of rayleigh to the adjacency effect.")
public class MerisAeRayleighOp extends MerisBasisOp {

    private FresnelReflectionCoefficient fresnelCoefficient;
    private static final int NUM_BANDS = EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS - 2;
    private static final double NO_DATA_VALUE = -1.0;

    private static final double HR = 8000; // Rayleigh scale height
    private CoeffW coeffW;
    RhoBracketAlgo rhoBracketAlgo;

    private double[][] w;

    private Band[] aeRayBands;
    private Band[] rhoAeRcBands;
    private Band[] rhoAgBracketBands;        // additional output for RS

    private Band[] fresnelDebugBands;
    private Band[] rayleighdebugBands;

    private Band isLandBand;

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;
    @SourceProduct(alias = "land")
    private Product landProduct;
    @SourceProduct(alias = "aemask")
    private Product aemaskProduct;
    @SourceProduct(alias = "ray1b")
    private Product ray1bProduct;
    @SourceProduct(alias = "rhoNg")
    private Product gasCorProduct;
    @SourceProduct(alias = "zmax")
    private Product zmaxProduct;
    @SourceProduct(alias = "cloud")
    private Product cloudProduct;
    @SourceProduct(alias = "zmaxCloud")
    private Product zmaxCloudProduct;

    @TargetProduct
    private Product targetProduct;

    @Parameter
    private String landExpression;
    @Parameter(interval = "[1, 3]", defaultValue = "1")
    private int convolveAlgo;
    @Parameter(defaultValue="true")
    private boolean reshapedConvolution;
    @Parameter
    private boolean exportSeparateDebugBands = false;
    private long convolutionTime = 0L;
    private int convolutionCount = 0;
    private int[] bandsToSkip;

    @Override
    public void initialize() throws OperatorException {
        try {
            loadAuxData();
        } catch (IOException e) {
            throw new OperatorException(e);
        }
        bandsToSkip = new int[]{10, 14};
        createTargetProduct();

        BandMathsOp bandArithmeticOp =
            BandMathsOp.createBooleanExpressionBand(landExpression, landProduct);
        isLandBand = bandArithmeticOp.getTargetProduct().getBandAt(0);
    }

    private void loadAuxData() throws IOException {
        String auxdataSrcPath = "auxdata/icol";
        final String auxdataDestPath = ".beam/beam-meris-icol/" + auxdataSrcPath;
        File auxdataTargetDir = new File(SystemUtils.getUserHomeDir(), auxdataDestPath);
        URL sourceUrl = ResourceInstaller.getSourceUrl(this.getClass());

        ResourceInstaller resourceInstaller = new ResourceInstaller(sourceUrl, auxdataSrcPath, auxdataTargetDir);
        resourceInstaller.install(".*", new NullProgressMonitor());

        File fresnelFile = new File(auxdataTargetDir, FresnelReflectionCoefficient.FRESNEL_COEFF);
        final Reader reader = new FileReader(fresnelFile);
        fresnelCoefficient = new FresnelReflectionCoefficient(reader);

        coeffW = new CoeffW(auxdataTargetDir, reshapedConvolution, IcolConstants.AE_CORRECTION_MODE_RAYLEIGH);
    }

    private void createTargetProduct() {
        String productType = l1bProduct.getProductType();
//        if (convolveAlgo == 1) {
//            rhoBracketAlgo = new RhoBracketKernellLoop(l1bProduct, coeffW, nestedConvolution);
//        } else if (convolveAlgo == 2) {
//            rhoBracketAlgo = new RhoBracketJaiConvolve(ray1bProduct, coeffW, "brr_", 1, false, nestedConvolution);
//        } else if (convolveAlgo == 3) {
//            rhoBracketAlgo = new RhoBracketJaiConvolve(ray1bProduct, coeffW, "brr_", 1, true, nestedConvolution);
//        } else {
//            throw new OperatorException("Illegal convolution algorithm.");
//        }
        // just use either new nested convolution scheme with FFT or 'old ICOL' convolution (no FFT)
        if (reshapedConvolution) {
            rhoBracketAlgo = new RhoBracketJaiConvolve(ray1bProduct, productType, coeffW, "brr_", 1,
                                                       EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS,
                                                       bandsToSkip);
        } else {
            rhoBracketAlgo = new RhoBracketKernellLoop(l1bProduct, coeffW, IcolConstants.AE_CORRECTION_MODE_RAYLEIGH);
        }

        targetProduct = createCompatibleProduct(l1bProduct, "ae_ray_" + l1bProduct.getName(), "MER_AE_RAY");
//        aeRayBands = addBandGroup("rho_aeRay", l1bProduct, 0);
        aeRayBands = addBandGroup("rho_aeRay", l1bProduct, NO_DATA_VALUE);
        rhoAeRcBands = addBandGroup("rho_ray_aerc", l1bProduct, NO_DATA_VALUE);
        rhoAgBracketBands = addBandGroup("rho_ag_bracket", l1bProduct, NO_DATA_VALUE);

        if (exportSeparateDebugBands) {
            rayleighdebugBands = addBandGroup("rho_aeRay_rayleigh", l1bProduct, NO_DATA_VALUE);
            fresnelDebugBands = addBandGroup("rho_aeRay_fresnel", l1bProduct, NO_DATA_VALUE);
        }

        if (l1bProduct.getPreferredTileSize() != null) {
            targetProduct.setPreferredTileSize(l1bProduct.getPreferredTileSize());
        }
    }

    // todo - This is the better writeProduct() impl. - move to ProductUtils
    public static void writeProduct(Product product, String filePath, String format) {
        try {
            System.out.println("Writing " + filePath);
            ProductWriter writer = ProductIO.getProductWriter(format);
            writer.writeProductNodes(product, new File(filePath));
            Band[] bands = product.getBands();
            for (Band band : bands) {
                System.out.println("Writing band " + band.getName());
                Raster data = band.getSourceImage().getData();
                TileImpl tile = new TileImpl(band, data);
                // todo - write in tiles
                writer.writeBandRasterData(band, 0, 0, band.getRasterWidth(), band.getRasterHeight(), tile.getDataBuffer(), ProgressMonitor.NULL);
            }
            System.out.println("Writing " + filePath + " done.");
        } catch (IOException e) {
            System.out.println("Writing " + filePath + " failed:");
            e.printStackTrace();
        }
    }

    private Band[] addBandGroup(String prefix, Product srcProduct, double noDataValue) {
        Band[] bands = new Band[NUM_BANDS];
        int j = 0;
        for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            if (IcolUtils.isIndexToSkip(i, bandsToSkip)) {
                continue;
            }
            Band inBand = srcProduct.getBandAt(i);

            bands[j] = targetProduct.addBand(prefix + "_" + (i + 1), ProductData.TYPE_FLOAT32);
//            ProductUtils.copySpectralAttributes(inBand, bands[j]);
            ProductUtils.copySpectralBandProperties(inBand, bands[j]);
            bands[j].setNoDataValueUsed(true);
            bands[j].setNoDataValue(noDataValue);
            j++;
        }
        return bands;
    }

    private Tile[] getTileGroup(final Product inProduct, String bandPrefix, Rectangle rectangle, ProgressMonitor pm) throws OperatorException {
        final Tile[] bandData = new Tile[NUM_BANDS];
        int j = 0;
        for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            if (IcolUtils.isIndexToSkip(i, bandsToSkip)) {
                continue;
            }
            bandData[j] = getSourceTile(inProduct.getBand(bandPrefix + "_" + (i + 1)), rectangle, pm);
            j++;
        }
        return bandData;
    }

    private Tile[] getTargetTiles(Band[] bands, Map<Band, Tile> targetTiles) {
        final Tile[] bandData = new Tile[NUM_BANDS];
        for (int i = 0; i < bands.length; i++) {
            Band band = bands[i];
            bandData[i] = targetTiles.get(band);
        }
        return bandData;
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRect, ProgressMonitor pm) throws OperatorException {

        Rectangle sourceRect = rhoBracketAlgo.mapTargetRect(targetRect);
        pm.beginTask("Processing frame...", targetRect.height + 1);
        try {
            // sources
            Tile isLand = getSourceTile(isLandBand, sourceRect, pm);

            Tile sza = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME), targetRect, pm);
            Tile zmax = getSourceTile(zmaxProduct.getBand("zmax"), targetRect, pm);
            Tile zmaxCloud = getSourceTile(zmaxCloudProduct.getBand("zmaxCloud"), targetRect, pm);
            Tile aep = getSourceTile(aemaskProduct.getBand(MerisAeMaskOp.AE_MASK_RAYLEIGH), targetRect, pm);
            Tile cloudFlags = getSourceTile(cloudProduct.getBand(CloudClassificationOp.CLOUD_FLAGS), targetRect, pm);

            Tile[] rhoNg = getTileGroup(gasCorProduct, GaseousCorrectionOp.RHO_NG_BAND_PREFIX, targetRect, pm);
            Tile[] transRup = getTileGroup(ray1bProduct, "transRv", targetRect, pm); //up
            Tile[] transRdown = getTileGroup(ray1bProduct, "transRs", targetRect, pm); //down
            Tile[] tauR = getTileGroup(ray1bProduct, "tauR", targetRect, pm);
            Tile[] sphAlbR = getTileGroup(ray1bProduct, "sphAlbR", targetRect, pm);

            Tile[] rhoAg = getTileGroup(ray1bProduct, "brr", sourceRect, pm);
            final RhoBracketAlgo.Convolver convolver = rhoBracketAlgo.createConvolver(this, rhoAg, targetRect, pm);

            //targets
            Tile[] aeRayTiles = getTargetTiles(aeRayBands, targetTiles);
            Tile[] rhoAeRcTiles = getTargetTiles(rhoAeRcBands, targetTiles);
            Tile[] rhoAgBracket = null;
            if (System.getProperty("additionalOutputBands") != null && System.getProperty("additionalOutputBands").equals("RS")) {
                rhoAgBracket = getTargetTiles(rhoAgBracketBands, targetTiles);
            }

            Tile[] rayleighDebug = null;
            Tile[] fresnelDebug = null;
            if (exportSeparateDebugBands) {
                rayleighDebug = getTargetTiles(rayleighdebugBands, targetTiles);
                fresnelDebug = getTargetTiles(fresnelDebugBands, targetTiles);
            }

//            final int numBands = rhoNg.length;
            final int numBands = rhoNg.length-1;
            for (int y = targetRect.y; y < targetRect.y + targetRect.height; y++) {
                for (int x = targetRect.x; x < targetRect.x + targetRect.width; x++) {
                    for (int b = 0; b < numBands; b++) {
                        if (exportSeparateDebugBands) {
                            fresnelDebug[b].setSample(x, y, -1);
                            rayleighDebug[b].setSample(x, y, -1);
                        }
                    }
                    if (x == 130 && y == 150)
                        System.out.println("");

                    boolean isCloud = cloudFlags.getSampleBit(x, y, CloudClassificationOp.F_CLOUD);
                    if (aep.getSampleInt(x, y) == 1 && !isCloud && rhoAg[0].getSampleFloat(x, y) != -1) {
                        long t1 = System.currentTimeMillis();
                        double[] means = convolver.convolvePixel(x, y, 1);
//                        double[] means = convolveRhoAg(convolver, rhoAg, cloudFlags, x, y, targetRect, 1);
                        long t2 = System.currentTimeMillis();
                        convolutionCount++;
                        this.convolutionTime += (t2-t1);

                        final double muS = Math.cos(sza.getSampleFloat(x, y) * MathUtils.DTOR);
                        for (int b = 0; b < numBands; b++) {
                            final double tmpRhoRayBracket = means[b];

                            // rayleigh contribution without AE (tmpRhoRayBracket)
                            double aeRayRay = 0.0;

                            // over water, compute the rayleigh contribution to the AE
                            final float rhoAgValue = rhoAg[b].getSampleFloat(x, y);
                            aeRayRay = (transRup[b].getSampleFloat(x, y) - Math
                                .exp(-tauR[b].getSampleFloat(x, y) / muS))
                                * (tmpRhoRayBracket - rhoAgValue) * (transRdown[b].getSampleFloat(x, y) /
                                (1d - tmpRhoRayBracket * sphAlbR[b].getSampleFloat(x, y)));

                            //compute the additional molecular contribution from the LFM  - ICOL+ ATBD eq. (10)
                            double zmaxPart = 0.0;
                            if (zmax.getSampleFloat(x, y) >= 0) {
                                zmaxPart = Math.exp(-zmax.getSampleFloat(x, y) / HR);
                            }
                            if (x == 40 && y == 190)
                                System.out.println("");
                            double zmaxCloudPart = 0.0;
                            if (zmaxCloud.getSampleFloat(x, y) >= 0) {
                                zmaxCloudPart = Math.exp(-zmaxCloud.getSampleFloat(x, y) / HR);
                            }

                            final double r1v = fresnelCoefficient.getCoeffFor(sza.getSampleFloat(x, y));
                            double aeRayFresnelLand = 0.0d;
                            if (zmax.getSampleFloat(x, y) >= 0) {
                                aeRayFresnelLand = rhoNg[b].getSampleFloat(x, y) * r1v * zmaxPart;
                                if (isLand.getSampleBoolean(x, y)) {
                                   // contribution must be subtracted over land - ICOL+ ATBD section 4.2 
                                   aeRayFresnelLand *= -1.0;
                                }
                            }
                            double aeRayFresnelCloud = 0.0d;
                            if (zmaxCloud.getSampleFloat(x, y) >= 0) {
                                aeRayFresnelCloud = rhoNg[b].getSampleFloat(x, y) * r1v * zmaxCloudPart;
                            }

                            if (exportSeparateDebugBands) {
                                fresnelDebug[b].setSample(x, y, aeRayFresnelLand+aeRayFresnelCloud);
                                rayleighDebug[b].setSample(x, y, aeRayRay);
                            }

                            final double aeRay = aeRayRay - aeRayFresnelLand - aeRayFresnelCloud;

                            aeRayTiles[b].setSample(x, y, aeRay);
                            //correct the top of aerosol reflectance for the AE_RAY effect
                            rhoAeRcTiles[b].setSample(x, y, rhoAg[b].getSampleFloat(x, y) - aeRay);
                            if (System.getProperty("additionalOutputBands") != null && System.getProperty("additionalOutputBands").equals("RS")) {
                                rhoAgBracket[b].setSample(x, y, tmpRhoRayBracket);
                            }
                        }
                    } else {
                        for (int b = 0; b < numBands; b++) {
                            rhoAeRcTiles[b].setSample(x, y, rhoAg[b].getSampleFloat(x, y));
                            if (System.getProperty("additionalOutputBands") != null && System.getProperty("additionalOutputBands").equals("RS")) {
                                rhoAgBracket[b].setSample(x, y, -1f);
                            }
                        }
                    }
                }
                pm.worked(1);
//                System.out.println("Accumulated convolve time (" + convolutionCount + "*'convolvePixel'): " +
//                        this.convolutionTime);
            }

        } catch (Exception e) {
            throw new OperatorException(e);
        } finally {
            pm.done();
        }
    }

    private double[] convolveRhoAg(RhoBracketAlgo.Convolver convolver, Tile[] rhoAg, Tile cloudFlags, int x, int y, Rectangle targetRect, int iaer) {
        double[] means = new double[rhoAg.length];

        int sourceExtend;

        final String productType = l1bProduct.getProductType();
        if (productType.indexOf("_RR") > -1) {
            sourceExtend = CoeffW.RR_KERNEL_SIZE;
        } else {
            sourceExtend = CoeffW.FR_KERNEL_SIZE;
        }

//        int xMin = Math.max(targetRect.x,  x-sourceExtend);
//        int xMax = Math.min(targetRect.x + targetRect.width,  x+sourceExtend);
//        int yMin = Math.max(targetRect.y,  y-sourceExtend);
//        int yMax = Math.min(targetRect.y + targetRect.height,  y+sourceExtend);
//


//        if (!cloudFlags.getSampleBit(xMin, yMin, CloudClassificationOp.F_CLOUD) ||
//             !cloudFlags.getSampleBit(x, yMin, CloudClassificationOp.F_CLOUD) ||
//             !cloudFlags.getSampleBit(xMax, yMin, CloudClassificationOp.F_CLOUD) ||
//             !cloudFlags.getSampleBit(xMin, y, CloudClassificationOp.F_CLOUD) ||
//             !cloudFlags.getSampleBit(xMax, y, CloudClassificationOp.F_CLOUD) ||
//             !cloudFlags.getSampleBit(xMin, yMax, CloudClassificationOp.F_CLOUD) ||
//             !cloudFlags.getSampleBit(x, yMax, CloudClassificationOp.F_CLOUD) ||
//             !cloudFlags.getSampleBit(xMax, yMax, CloudClassificationOp.F_CLOUD)) {
//            convolve = true;
//        }

        int xMin = Math.max(targetRect.x, x - 9);
        int xMax = Math.min(targetRect.x + targetRect.width - 1, x + 9);
        int yMin = Math.max(targetRect.y, y - 9);
        int yMax = Math.min(targetRect.y + targetRect.height - 1, y + 9);

        boolean convolve = true;
        for (int i = xMin; i <= xMax; i++) {
            for (int j = yMin; j <= yMax; j++) {
                if (cloudFlags.getSampleBit(i, j, CloudClassificationOp.F_CLOUD)) {
                    convolve = false;
                    break;
                }
            }
            if (!convolve) {
                break;
            }
        }

        if (convolve) {
            means = convolver.convolvePixel(x, y, iaer);
        } else {
            for (int b=0; b<rhoAg.length; b++) {
                means[b] = rhoAg[b].getSampleDouble(x, y);
            }
        }

        return means;
    }

    public static class Spi extends OperatorSpi {
        public Spi() {
            super(MerisAeRayleighOp.class);
        }
    }
}
