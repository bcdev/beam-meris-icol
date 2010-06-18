package org.esa.beam.meris.icol.tm;

import com.bc.ceres.core.NullProgressMonitor;
import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.standard.BandMathsOp;
import org.esa.beam.meris.brr.GaseousCorrectionOp;
import org.esa.beam.meris.icol.CoeffW;
import org.esa.beam.meris.icol.FresnelReflectionCoefficient;
import org.esa.beam.meris.icol.IcolConstants;
import org.esa.beam.meris.icol.RhoBracketAlgo;
import org.esa.beam.meris.icol.RhoBracketJaiConvolve;
import org.esa.beam.meris.icol.RhoBracketKernellLoop;
import org.esa.beam.meris.icol.common.AeMaskOp;
import org.esa.beam.meris.icol.common.ZmaxOp;
import org.esa.beam.meris.icol.utils.IcolUtils;
import org.esa.beam.meris.icol.utils.OperatorUtils;
import org.esa.beam.util.ResourceInstaller;
import org.esa.beam.util.SystemUtils;
import org.esa.beam.util.math.MathUtils;

import java.awt.Rectangle;
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
public class TmAeRayleighOp extends Operator {

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
    @Parameter(defaultValue="true")
    private boolean reshapedConvolution;
    @Parameter
    private boolean exportSeparateDebugBands = false;
    @Parameter
    private String instrument;
    

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
                                                       numSpectralBands,
                                                       bandsToSkip);
        } else {
            rhoBracketAlgo = new RhoBracketKernellLoop(l1bProduct, coeffW, IcolConstants.AE_CORRECTION_MODE_RAYLEIGH);
        }

        targetProduct = OperatorUtils.createCompatibleProduct(l1bProduct, "ae_ray_" + l1bProduct.getName(), "MER_AE_RAY");
        aeRayBands = addBandGroup("rho_aeRay", 0);
        rhoAeRcBands = addBandGroup("rho_ray_aerc", -1);
        rhoAgBracketBands = addBandGroup("rho_ag_bracket", -1);

        if (exportSeparateDebugBands) {
            rayleighdebugBands = addBandGroup("rho_aeRay_rayleigh", -1);
            fresnelDebugBands = addBandGroup("rho_aeRay_fresnel", -1);
        }
    }

    private Band[] addBandGroup(String prefix, double noDataValue) {
        return OperatorUtils.addBandGroup(l1bProduct, numSpectralBands, bandsToSkip,
                targetProduct, prefix, noDataValue, false);
    }

    private Tile[] getSourceTiles(final Product inProduct, String bandPrefix, Rectangle rectangle, ProgressMonitor pm) throws OperatorException {
        final Tile[] bandData = new Tile[NUM_BANDS];
        int j = 0;
        for (int i = 0; i < numSpectralBands; i++) {
            if (IcolUtils.isIndexToSkip(i, bandsToSkip)) {
                continue;
            }
            String bandIdentifier = bandPrefix + "_" + (i + 1);
            Band inBand = inProduct.getBand(bandIdentifier);
            bandData[i] = getSourceTile(inBand, rectangle, pm);
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
            Tile vza = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME), targetRect, pm);
            Tile[] zmaxs = ZmaxOp.getSourceTiles(this, zmaxProduct, targetRect, pm);
            Tile zmaxCloud = ZmaxOp.getSourceTile(this, zmaxCloudProduct, targetRect, pm);
            Tile aep = getSourceTile(aemaskProduct.getBand(AeMaskOp.AE_MASK_RAYLEIGH), targetRect, pm);

            Tile[] rhoNg = getSourceTiles(gasCorProduct, GaseousCorrectionOp.RHO_NG_BAND_PREFIX, targetRect, pm);
            Tile[] transRup = getSourceTiles(ray1bProduct, "transRv", targetRect, pm); //up
            Tile[] transRdown = getSourceTiles(ray1bProduct, "transRs", targetRect, pm); //down
            Tile[] tauR = getSourceTiles(ray1bProduct, "tauR", targetRect, pm);
            Tile[] sphAlbR = getSourceTiles(ray1bProduct, "sphAlbR", targetRect, pm);

            Tile[] rhoAg = getSourceTiles(ray1bProduct, "brr", sourceRect, pm);
            final RhoBracketAlgo.Convolver convolver = rhoBracketAlgo.createConvolver(this, rhoAg, targetRect, pm);

            //targets
            Tile[] aeRayTiles = OperatorUtils.getTargetTiles(targetTiles, aeRayBands);
            Tile[] rhoAeRcTiles = OperatorUtils.getTargetTiles(targetTiles, rhoAeRcBands);
            Tile[] rhoAgBracket = null;
            if (System.getProperty("additionalOutputBands") != null && System.getProperty("additionalOutputBands").equals("RS")) {
                rhoAgBracket = OperatorUtils.getTargetTiles(targetTiles, rhoAgBracketBands);
            }

            Tile[] rayleighDebug = null;
            Tile[] fresnelDebug = null;
            if (exportSeparateDebugBands) {
                rayleighDebug = OperatorUtils.getTargetTiles(targetTiles, rayleighdebugBands);
                fresnelDebug = OperatorUtils.getTargetTiles(targetTiles, fresnelDebugBands);
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
                        double[] means = convolver.convolvePixel(x, y, 1);

                        final double muV = Math.cos(vza.getSampleFloat(x, y) * MathUtils.DTOR);
                        for (int b = 0; b < numBands; b++) {
                            if (!IcolUtils.isIndexToSkip(b, bandsToSkip)) {
                                final double tmpRhoRayBracket = means[b];

                                // rayleigh contribution without AE (tmpRhoRayBracket)
                                double aeRayRay = 0.0;

                                // over water, compute the rayleigh contribution to the AE
                                float rhoAgValue = rhoAg[b].getSampleFloat(x, y);
                                float transRupValue = transRup[b].getSampleFloat(x, y);
                                float tauRValue = tauR[b].getSampleFloat(x, y);
                                float transRdownValue = transRdown[b].getSampleFloat(x, y);
                                float sphAlbValue = sphAlbR[b].getSampleFloat(x, y);
                                aeRayRay = (transRupValue - Math
                                        .exp(-tauRValue / muV))
                                        * (tmpRhoRayBracket - rhoAgValue) * (transRdownValue /
                                        (1d - tmpRhoRayBracket * sphAlbValue));

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
                                rhoAeRcTiles[b].setSample(x, y, rhoAgValue - aeRay);
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
