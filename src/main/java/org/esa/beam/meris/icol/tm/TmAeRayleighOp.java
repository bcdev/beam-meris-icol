package org.esa.beam.meris.icol.tm;

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
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.framework.gpf.internal.TileImpl;
import org.esa.beam.gpf.operators.standard.BandMathsOp;
import org.esa.beam.meris.brr.GaseousCorrectionOp;
import org.esa.beam.meris.icol.CoeffW;
import org.esa.beam.meris.icol.FresnelReflectionCoefficient;
import org.esa.beam.meris.icol.IcolConstants;
import org.esa.beam.meris.icol.RhoBracketAlgo;
import org.esa.beam.meris.icol.RhoBracketJaiConvolve;
import org.esa.beam.meris.icol.RhoBracketKernellLoop;
import org.esa.beam.meris.icol.common.ZmaxOp;
import org.esa.beam.meris.icol.meris.MerisAeMaskOp;
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
 * @author Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
public class TmAeRayleighOp extends TmBasisOp {
    private static final int NUM_BANDS = TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS;
    private static final double HR = 8000; // Rayleigh scale height

    private FresnelReflectionCoefficient fresnelCoefficient;
    private CoeffW coeffW;
    RhoBracketAlgo rhoBracketAlgo;

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
    @SourceProduct(alias = "zmaxCloud")
    private Product zmaxCloudProduct;

    @TargetProduct
    private Product targetProduct;

    @Parameter
    private String landExpression;
    @Parameter(interval = "[1, 3]", defaultValue = "1")
    private int convolveAlgo;
    @Parameter(defaultValue="false")
    private boolean reshapedConvolution;
    @Parameter
    private boolean exportSeparateDebugBands = false;
    @Parameter
    private String instrument;
    
    private long convolutionTime = 0L;
    private int convolutionCount = 0;

    private int numSpectralBands;
    private int[] bandsToSkip;

    @Override
    public void initialize() throws OperatorException {

        // this separation can be useful for an instrument-independent usage later
        // currently, this operator is used for Landsat5 TM only unless all algorithm specifications
        // are finally clarified
        if (instrument.toUpperCase().equals("MERIS")) {
            numSpectralBands = EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS;
            bandsToSkip = new int[]{10,14};
        } else if (instrument.toUpperCase().equals("LANDSAT5 TM")) {
            numSpectralBands = TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS;
            bandsToSkip = new int[]{5};
        }

        try {
            loadFresnelReflectionCoefficient();
        } catch (IOException e) {
            throw new OperatorException(e);
        }
        createTargetProduct();

        BandMathsOp bandArithmeticOp =
            BandMathsOp.createBooleanExpressionBand(landExpression, landProduct);
        isLandBand = bandArithmeticOp.getTargetProduct().getBandAt(0);
    }

    private void loadFresnelReflectionCoefficient() throws IOException {
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
        if (reshapedConvolution) {
            rhoBracketAlgo = new RhoBracketJaiConvolve(ray1bProduct, productType, coeffW, "brr_", 1,
                                                       TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS,
                                                       new int[]{5});
        } else {
            rhoBracketAlgo = new RhoBracketKernellLoop(l1bProduct, coeffW, IcolConstants.AE_CORRECTION_MODE_RAYLEIGH);
        }

        targetProduct = createCompatibleProduct(l1bProduct, "ae_ray_" + l1bProduct.getName(), "MER_AE_RAY");
        aeRayBands = addBandGroup("rho_aeRay", l1bProduct, 0);
        rhoAeRcBands = addBandGroup("rho_ray_aerc", l1bProduct, -1);
        rhoAgBracketBands = addBandGroup("rho_ag_bracket", l1bProduct, -1);

        if (exportSeparateDebugBands) {
            rayleighdebugBands = addBandGroup("rho_aeRay_rayleigh", l1bProduct, -1);
            fresnelDebugBands = addBandGroup("rho_aeRay_fresnel", l1bProduct, -1);
        }

        if (l1bProduct.getPreferredTileSize() != null) {
            targetProduct.setPreferredTileSize(l1bProduct.getPreferredTileSize());
        }
    }

    private Band[] addBandGroup(String prefix, Product srcProduct, double noDataValue) {
        Band[] bands = new Band[NUM_BANDS];
        int j = 0;
        for (int i = 0; i < TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS; i++) {
            if (i == TmConstants.LANDSAT5_RADIANCE_6_BAND_INDEX) {
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
        for (int i = 0; i < TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS; i++) {
            if (!IcolUtils.isIndexToSkip(i, bandsToSkip)) {
                final String prefixTmp = bandPrefix + "_" + (i + 1);
                final Band inBand = inProduct.getBand(prefixTmp);
                bandData[i] = getSourceTile(inBand, rectangle, pm);
                j++;
            }
        }
        return bandData;
    }

    private Tile[] getTargetTiles(Band[] bands, Map<Band, Tile> targetTiles) {
        final Tile[] bandData = new Tile[NUM_BANDS];
        int j = 0;
        for (int i = 0; i < NUM_BANDS; i++) {
            if (i == TmConstants.LANDSAT5_RADIANCE_6_BAND_INDEX) {
                continue;
            }
            Band band = bands[j];
            bandData[i] = targetTiles.get(band);
            j++;
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
            Tile[] zmaxs = ZmaxOp.getSourceTiles(this, zmaxProduct, targetRect, pm);
            Tile zmaxCloud = ZmaxOp.getSourceTile(this, zmaxCloudProduct, targetRect, pm);
            Tile aep = getSourceTile(aemaskProduct.getBand(MerisAeMaskOp.AE_MASK_RAYLEIGH), targetRect, pm);

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

            final int numBands = rhoNg.length;
            for (int y = targetRect.y; y < targetRect.y + targetRect.height; y++) {
                for (int x = targetRect.x; x < targetRect.x + targetRect.width; x++) {
                    for (int b = 0; b < numBands; b++) {
                        if (!IcolUtils.isIndexToSkip(b, bandsToSkip)) {
                            if (exportSeparateDebugBands) {
                                fresnelDebug[b].setSample(x, y, -1);
                                rayleighDebug[b].setSample(x, y, -1);
                            }
                        }
                    }
                    if (aep.getSampleInt(x, y) == 1 && rhoAg[0].getSampleFloat(x, y) != -1) {
                        long t1 = System.currentTimeMillis();
                        double[] means = convolver.convolvePixel(x, y, 1);
                        long t2 = System.currentTimeMillis();
                        convolutionCount++;
                        this.convolutionTime += (t2-t1);

                        final double muS = Math.cos(sza.getSampleFloat(x, y) * MathUtils.DTOR);
                        for (int b = 0; b < numBands; b++) {
                            if (!IcolUtils.isIndexToSkip(b, bandsToSkip)) {
                                final double tmpRhoRayBracket = means[b];

                                // rayleigh contribution without AE (tmpRhoRayBracket)
                                double aeRayRay = 0.0;

                                // over water, compute the rayleigh contribution to the AE
                                aeRayRay = (transRup[b].getSampleFloat(x, y) - Math
                                        .exp(-tauR[b].getSampleFloat(x, y) / muS))
                                        * (tmpRhoRayBracket - rhoAg[b].getSampleFloat(x, y)) * (transRdown[b].getSampleFloat(x, y) /
                                        (1d - tmpRhoRayBracket * sphAlbR[b].getSampleFloat(x, y)));

                                //compute the additional molecular contribution from the LFM  - ICOL+ ATBD eq. (10)
                                double zmaxPart = ZmaxOp.computeZmaxPart(zmaxs, x, y, HR);
                                double zmaxCloudPart = ZmaxOp.computeZmaxPart(zmaxCloud, x, y, HR);

                                final double r1v = fresnelCoefficient.getCoeffFor(sza.getSampleFloat(x, y));
                                double aeRayFresnelLand = 0.0d;
                                if (zmaxPart != 0) {
                                    aeRayFresnelLand = rhoNg[b].getSampleFloat(x, y) * r1v * zmaxPart;
                                    if (isLand.getSampleBoolean(x, y)) {
                                        // contribution must be subtracted over land - ICOL+ ATBD section 4.2
                                        aeRayFresnelLand *= -1.0;
                                    }
                                }
                                double aeRayFresnelCloud = 0.0d;
                                if (zmaxCloudPart != 0) {
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
                        }
                    } else {
                        for (int b = 0; b < numBands; b++) {
                            if (!IcolUtils.isIndexToSkip(b, bandsToSkip)) {
                                rhoAeRcTiles[b].setSample(x, y, rhoAg[b].getSampleFloat(x, y));
                                if (System.getProperty("additionalOutputBands") != null && System.getProperty("additionalOutputBands").equals("RS")) {
                                    rhoAgBracket[b].setSample(x, y, -1f);
                                }
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


    public static class Spi extends OperatorSpi {
        public Spi() {
            super(TmAeRayleighOp.class);
        }
    }
}
