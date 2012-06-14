package org.esa.beam.meris.icol.landsat.common;

import com.bc.ceres.core.NullProgressMonitor;
import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
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
import org.esa.beam.gpf.operators.standard.BandMathsOp;
import org.esa.beam.meris.icol.*;
import org.esa.beam.meris.icol.common.AdjacencyEffectMaskOp;
import org.esa.beam.meris.icol.common.ZmaxOp;
import org.esa.beam.meris.icol.landsat.tm.TmBasisOp;
import org.esa.beam.meris.icol.utils.IcolUtils;
import org.esa.beam.meris.icol.utils.OperatorUtils;
import org.esa.beam.util.BitSetter;
import org.esa.beam.util.ResourceInstaller;
import org.esa.beam.util.SystemUtils;
import org.esa.beam.util.math.MathUtils;

import javax.media.jai.BorderExtender;
import java.awt.*;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.util.Map;

/**
 * AE aerosol correction for Landsat
 *
 * @author Olaf Danne
 */
@OperatorMetadata(alias = "Landsat.AEAerosol",
                  version = "1.0",
                  internal = true,
                  authors = "Olaf Danne",
                  copyright = "(c) 2009 by Brockmann Consult",
                  description = "Contribution of aerosol to the adjacency effect.")
public class AeAerosolOp extends TmBasisOp {

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;
    @SourceProduct(alias = "land")
    private Product landProduct;
    @SourceProduct(alias = "aemask")
    private Product aemaskProduct;
    @SourceProduct(alias = "zmax")
    private Product zmaxProduct;
    @SourceProduct(alias = "ae_ray")
    private Product aeRayProduct;
    @SourceProduct(alias = "cloud", optional = true)
    private Product cloudProduct;
    @SourceProduct(alias = "ctp", optional = true)
    private Product ctpProduct;
    @SourceProduct(alias = "zmaxCloud")
    private Product zmaxCloudProduct;

    @TargetProduct
    private Product targetProduct;

    @Parameter(defaultValue = "false", description = "export the aerosol and fresnel correction term as bands")
    private boolean exportSeparateDebugBands = false;
    @Parameter(defaultValue = "true")
    private boolean icolAerosolForWater = true;
    @Parameter(interval = "[440.0, 2225.0]", defaultValue = "550.0")
    private double userAerosolReferenceWavelength;
    @Parameter(interval = "[-2.1, -0.4]", defaultValue = "-1")
    private double userAlpha;
    @Parameter(interval = "[0, 1.5]", defaultValue = "0.2",
               description = "The aerosol optical thickness at reference wavelength")
    private double userAot;
    // new in v1.1
    @Parameter(interval = "[1, 26]", defaultValue = "10")
    private int iaerConv;
    @Parameter(defaultValue = "false")
    private boolean reshapedConvolution;
    @Parameter
    private String landExpression;
    @Parameter
    private Instrument instrument;
    @Parameter(interval = "[300.0, 1060.0]", defaultValue = "1013.25")
    private double userPSurf;
    @Parameter
    private int numSpectralBands;

    public static final String AOT_FLAGS = "aot_flags";

    private static final double AE_AOT_THRESHOLD = 0.01;

    private static final double HA = 3000;  //vertical scale height

    private IcolConvolutionAlgo icolConvolutionAlgo;

    private float[] effectiveWavelengths;

    private Band isLandBand;
    private Band flagBand;
    private Band alphaBand;
    private Band alphaIndexBand;
    private Band aotBand;
    private Band[] aeAerBands;
    private Band[] rhoAeAcBands;

    private CoeffW coeffW;
    private AerosolScatteringFunctions aerosolScatteringFunctions;
    private FresnelReflectionCoefficient fresnelCoefficient;

    private double userAot865;

    @Override
    public void initialize() throws OperatorException {
        userAot865 = IcolUtils.convertAOT(userAot, userAlpha, userAerosolReferenceWavelength, 865.0);
        targetProduct = createCompatibleProduct(aeRayProduct, "ae_" + aeRayProduct.getName(), "AE");

        flagBand = targetProduct.addBand(AOT_FLAGS, ProductData.TYPE_UINT8);
        FlagCoding flagCoding = createFlagCoding();
        targetProduct.getFlagCodingGroup().add(flagCoding);
        flagBand.setSampleCoding(flagCoding);

        alphaBand = targetProduct.addBand("alpha", ProductData.TYPE_FLOAT32);
        alphaIndexBand = targetProduct.addBand("alpha_index", ProductData.TYPE_UINT8);
        aotBand = targetProduct.addBand("aot", ProductData.TYPE_FLOAT32);

        rhoAeAcBands = addBandGroup("rho_ray_aeac", -1);
        aeAerBands = addBandGroup("rho_aeAer", 0);

        if (l1bProduct.getProductType().toUpperCase().startsWith(LandsatConstants.LANDSAT5_PRODUCT_TYPE_PREFIX)) {
            effectiveWavelengths = LandsatConstants.LANDSAT5_SPECTRAL_BAND_EFFECTIVE_WAVELENGTHS;
        } else if (l1bProduct.getProductType().toUpperCase().startsWith(LandsatConstants.LANDSAT7_PRODUCT_TYPE_PREFIX)) {
            effectiveWavelengths = LandsatConstants.LANDSAT7_SPECTRAL_BAND_EFFECTIVE_WAVELENGTHS;
        } else {
            throw new OperatorException("AeAerosolOp: Unknown product type '" + l1bProduct.getProductType() + "'.");
        }

        try {
            loadAuxData();
        } catch (IOException e) {
            throw new OperatorException(e);
        }

        String productType = l1bProduct.getProductType();

        if (reshapedConvolution) {
            icolConvolutionAlgo = new IcolConvolutionJaiConvolve(aeRayProduct, productType, coeffW, "rho_ray_aerc_", iaerConv,
                                                                 numSpectralBands, instrument.bandsToSkip);
        } else {
            icolConvolutionAlgo = new IcolConvolutionKernellLoop(l1bProduct, coeffW, IcolConstants.AE_CORRECTION_MODE_AEROSOL);
        }


        aerosolScatteringFunctions = new AerosolScatteringFunctions();

        BandMathsOp bandArithmeticOp =
                BandMathsOp.createBooleanExpressionBand(landExpression, landProduct);
        isLandBand = bandArithmeticOp.getTargetProduct().getBandAt(0);
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRect, ProgressMonitor pm) throws
            OperatorException {
        // todo: this method is too long!!!
        final Rectangle sourceRect = icolConvolutionAlgo.mapTargetRect(targetRect);

        final Tile vza = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME), targetRect,
                                       BorderExtender.createInstance(BorderExtender.BORDER_COPY));
        final Tile sza = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME), targetRect,
                                       BorderExtender.createInstance(BorderExtender.BORDER_COPY));
        final Tile vaa = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_AZIMUTH_DS_NAME), targetRect,
                                       BorderExtender.createInstance(BorderExtender.BORDER_COPY));
        final Tile saa = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME), targetRect,
                                       BorderExtender.createInstance(BorderExtender.BORDER_COPY));

        final Tile isLand = getSourceTile(isLandBand, sourceRect, BorderExtender.createInstance(BorderExtender.BORDER_COPY));
        final Tile[] zmaxs = ZmaxOp.getSourceTiles(this, zmaxProduct, targetRect, pm);
        final Tile zmaxCloud = ZmaxOp.getSourceTile(this, zmaxCloudProduct, targetRect);
        final Tile aep = getSourceTile(aemaskProduct.getBand(AdjacencyEffectMaskOp.AE_MASK_AEROSOL), targetRect,
                                       BorderExtender.createInstance(BorderExtender.BORDER_COPY));

        final Tile[] rhoRaec = getRhoRaecTiles(sourceRect);

        final IcolConvolutionAlgo.Convolver convolver = icolConvolutionAlgo.createConvolver(this, rhoRaec, targetRect, pm);

        final Tile flagTile = targetTiles.get(flagBand);
        final Tile aotTile = targetTiles.get(aotBand);
        final Tile alphaTile = targetTiles.get(alphaBand);
        final Tile alphaIndexTile = targetTiles.get(alphaIndexBand);

        final Tile[] rhoAeAcRaster = getTargetTiles(targetTiles, rhoAeAcBands);
        final Tile[] aeAerRaster = getTargetTiles(targetTiles, aeAerBands);

        Tile cloudTopPressure = null;
        Tile cloudFlags = null;
        if (cloudProduct != null) {
            cloudTopPressure = getSourceTile(ctpProduct.getBand(LandsatConstants.LANDSAT_CTP_BAND_NAME), targetRect,
                                             BorderExtender.createInstance(BorderExtender.BORDER_COPY));
            cloudFlags = getSourceTile(cloudProduct.getBand(CloudClassificationOp.CLOUD_FLAGS), targetRect,
                                       BorderExtender.createInstance(BorderExtender.BORDER_COPY));
        }

        // MERIS notation
        // Landsat5 equivalents: TM4 (865, 775), TM3 (705), to be discussed
        double[] rhoBrr865 = new double[17];     // B13

        try {
            for (int y = targetRect.y; y < targetRect.y + targetRect.height; y++) {
                for (int x = targetRect.x; x < targetRect.x + targetRect.width; x++) {

                    // to be discussed!
                    final double rho_13 = rhoRaec[LandsatConstants.LANDSAT_RADIANCE_4_BAND_INDEX].getSampleFloat(x, y);
                    final double rho_12 = rhoRaec[LandsatConstants.LANDSAT_RADIANCE_4_BAND_INDEX].getSampleFloat(x, y);

                    /* correct pressure in presence of clouds  - ICOL+ ATBD eqs. (6)-(8) */
                    double rhoCloudCorrFac = 1.0d;
                    boolean isCloud = false;
                    if (cloudProduct != null) {
                        isCloud = cloudFlags.getSampleBit(x, y, org.esa.beam.meris.brr.CloudClassificationOp.F_CLOUD);

                        if (isCloud) {
                            final double pressureCorrectionCloud = cloudTopPressure.getSampleDouble(x, y) / userPSurf;
                            // cloud height
                            double zCloud = 8.0 * Math.log(1.0 / pressureCorrectionCloud);
                            rhoCloudCorrFac = Math.exp(-zCloud / HA);
                        }
                    }

                    if (aep.getSampleInt(x, y) == 1 && rho_13 != -1 && rho_12 != -1) {
                        double alpha;
                        if (!isLand.getSampleBoolean(x, y) && icolAerosolForWater) {
                            // RS, 04/12/09:
                            final double rho_4 = rhoRaec[LandsatConstants.LANDSAT_RADIANCE_4_BAND_INDEX].getSampleFloat(x,
                                                                                                                        y);
                            final double rho_3 = rhoRaec[LandsatConstants.LANDSAT_RADIANCE_3_BAND_INDEX].getSampleFloat(x,
                                                                                                                        y);
                            final double epsilon = rho_3 / rho_4;
                            alpha = Math.log(epsilon) / Math.log(effectiveWavelengths[2] / effectiveWavelengths[3]);
                        } else {
                            alpha = userAlpha;
                        }
                        int iaer = (int) (Math.round(-(alpha * 10.0)) + 1);
                        if (iaer < 1) {
                            iaer = 1;
                            flagTile.setSample(x, y, 1);
                        } else if (iaer > 26) {
                            iaer = 26;
                            flagTile.setSample(x, y, 1);
                        }

                        //retrieve ROAG at 865 nm with two bounded AOTs
                        final double r1v = fresnelCoefficient.getCoeffFor(vza.getSampleFloat(x, y));
                        final double r1s = fresnelCoefficient.getCoeffFor(sza.getSampleFloat(x, y));

                        final float phi = saa.getSampleFloat(x, y) - vaa.getSampleFloat(x, y);
                        final double mus = Math.cos(sza.getSampleFloat(x, y) * MathUtils.DTOR);
                        final double nus = Math.sin(sza.getSampleFloat(x, y) * MathUtils.DTOR);
                        final double muv = Math.cos(vza.getSampleFloat(x, y) * MathUtils.DTOR);
                        final double nuv = Math.sin(vza.getSampleFloat(x, y) * MathUtils.DTOR);

                        //compute the back scattering angle
                        final double csb = mus * muv + nus * nuv * Math.cos(phi * MathUtils.DTOR);
                        final double thetab = Math.acos(-csb) * MathUtils.RTOD;
                        //compute the forward scattering angle
                        final double csf = mus * muv - nus * nuv * Math.cos(phi * MathUtils.DTOR);
                        final double thetaf = Math.acos(csf) * MathUtils.RTOD;

                        final double pab = aerosolScatteringFunctions.aerosolPhase(thetab, iaer);
                        final double tauaConst = 0.1 * Math.pow((550.0 / 865.0), (iaer / 10.0));
                        final double paerFB = aerosolScatteringFunctions.aerosolPhaseFB(thetaf, thetab, iaer);
                        double corrFac;

                        double zmaxPart = ZmaxOp.computeZmaxPart(zmaxs, x, y, HA);
                        if (isLand.getSampleBoolean(x, y)) {
                            // contribution must be subtracted over land - ICOL+ ATBD section 4.2
                            zmaxPart *= -1.0;
                        }
                        final double zmaxCloudPart = ZmaxOp.computeZmaxPart(zmaxCloud, x, y, HA);

                        int searchIAOT;
                        double aot;

                        double taua;
                        double rhoa;
                        double rhoa0;
                        // begin 'old' case 1 water (analogue to MERIS)
                        double rhoBrrBracket865 = convolver.convolveSample(x, y, iaer, LandsatConstants.LANDSAT_RADIANCE_4_BAND_INDEX);

                        aot = userAot865;
                        searchIAOT = MathUtils.floorInt(aot * 10) + 1;  // RS, 21/12/2010
                        for (int iiaot = searchIAOT; iiaot <= searchIAOT + 1; iiaot++) {
                            taua = tauaConst * iiaot;
                            AerosolScatteringFunctions.RV rv =
                                    aerosolScatteringFunctions.aerosol_f(taua,
                                                                         iaer,
                                                                         pab,
                                                                         sza.getSampleFloat(x, y),
                                                                         vza.getSampleFloat(x, y),
                                                                         phi);
                            //  - this reflects ICOL D6a ATBD, eq. (2): rhoa = rho_a, rv.rhoa = rho_a0 !!!
                            rhoa0 = rv.rhoa;
                            corrFac = 1.0 + paerFB * (r1v + r1s * (1.0 - zmaxPart - zmaxCloudPart));
                            rhoa = rhoa0 * corrFac;
                            rhoBrr865[iiaot] = rhoa + rhoBrrBracket865 * rv.tds * (rv.tus - Math.exp(-taua / muv));
                        }
                        // end 'old' case 1 water

                        alphaIndexTile.setSample(x, y, iaer);
                        alphaTile.setSample(x, y, alpha);
                        aotTile.setSample(x, y,
                                          IcolUtils.convertAOT(aot, alpha, 865.0, userAerosolReferenceWavelength));

                        //Correct from AE with AEROSOLS
                        for (int iwvl = 0; iwvl < numSpectralBands; iwvl++) {
                            if (IcolUtils.isIndexToSkip(iwvl, instrument.bandsToSkip)) {
                                continue;
                            }
                            if (searchIAOT != -1 && aot > AE_AOT_THRESHOLD) {
                                final double roAerMean = convolver.convolveSample(x, y, iaer, iwvl);

                                final float rhoRaecIwvl = rhoRaec[iwvl].getSampleFloat(x, y);
                                final float wvl = effectiveWavelengths[iwvl];

                                //Compute the aerosols functions for the first aot
                                final double taua1 = 0.1 * searchIAOT * Math.pow((550.0 / wvl), (iaer / 10.0));
                                final AerosolScatteringFunctions.RV rv1 =
                                        aerosolScatteringFunctions.aerosol_f(taua1, iaer, pab,
                                                                             sza.getSampleFloat(x, y),
                                                                             vza.getSampleFloat(x, y),
                                                                             phi);

                                final double downwellingTransmittanceTerm = rv1.tds / (1.0 - roAerMean * rv1.sa);
                                double aerosol1 = (roAerMean - rhoRaecIwvl) * downwellingTransmittanceTerm;
                                final double upwellingTransmittanceTerm = rv1.tus - Math.exp(-taua1 / muv);
                                aerosol1 *= upwellingTransmittanceTerm;

                                final double fresnel1 = rv1.rhoa * paerFB * r1s * (zmaxPart + zmaxCloudPart);
                                final double aea1 = aerosol1 - fresnel1;

                                //Compute the aerosols functions for the second aot
                                final double taua2 = 0.1 * (searchIAOT + 1) * Math.pow((550.0 / wvl), (iaer / 10.0));
                                final AerosolScatteringFunctions.RV rv2 =
                                        aerosolScatteringFunctions.aerosol_f(taua2, iaer, pab,
                                                                             sza.getSampleFloat(x, y),
                                                                             vza.getSampleFloat(x, y),
                                                                             phi);

                                double aea2 = (roAerMean - rhoRaecIwvl) * (rv2.tds / (1.0 - roAerMean * rv2.sa));
                                aea2 = (rv2.tus - Math.exp(-taua2 / muv)) * aea2;
                                aea2 = aea2 - rv2.rhoa * paerFB * r1s * (zmaxPart + zmaxCloudPart);

                                //AOT INTERPOLATION to get AE_aer
                                double aea = aerosolScatteringFunctions.interpolateLin(rhoBrr865[searchIAOT], aea1,
                                                                                       rhoBrr865[searchIAOT + 1], aea2,
                                                                                       rho_13);

                                if (isCloud) {
                                    aea *= rhoCloudCorrFac;
                                }

                                aeAerRaster[iwvl].setSample(x, y, aea);
                                rhoAeAcRaster[iwvl].setSample(x, y, rhoRaecIwvl - (float) aea);
                            } else {
                                float rhoRaecIwvl = rhoRaec[iwvl].getSampleFloat(x, y);
                                if (isCloud) {
                                    rhoRaecIwvl *= rhoCloudCorrFac;
                                }
                                rhoAeAcRaster[iwvl].setSample(x, y, rhoRaecIwvl);
                            }
                        }
                    } else {
                        for (int iwvl = 0; iwvl < numSpectralBands; iwvl++) {
                            if (IcolUtils.isIndexToSkip(iwvl, instrument.bandsToSkip)) {
                                continue;
                            }
                            rhoAeAcRaster[iwvl].setSample(x, y, rhoRaec[iwvl].getSampleFloat(x, y));
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new OperatorException(e);
        }
    }

    private Band[] addBandGroup(String prefix, double noDataValue) {
        return OperatorUtils.addBandGroup(l1bProduct, numSpectralBands, instrument.bandsToSkip, targetProduct, prefix, noDataValue,
                                          false);
    }

    private Tile[] getTargetTiles(Map<Band, Tile> targetTiles, Band[] bands) {
        return OperatorUtils.getTargetTiles(targetTiles, bands, instrument.bandsToSkip);
    }

    private FlagCoding createFlagCoding() {
        FlagCoding flagCoding = new FlagCoding(AOT_FLAGS);
        flagCoding.addFlag("bad_aerosol_model", BitSetter.setFlag(0, 0), null);
        flagCoding.addFlag("bad_aot_model", BitSetter.setFlag(0, 1), null);
        return flagCoding;
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

        coeffW = new CoeffW(auxdataTargetDir, reshapedConvolution, IcolConstants.AE_CORRECTION_MODE_AEROSOL);
    }

    private Tile[] getRhoRaecTiles(Rectangle sourceRect) {
        Tile[] rhoRaec = new Tile[numSpectralBands];
        for (int i = 0; i < numSpectralBands; i++) {
            if (IcolUtils.isIndexToSkip(i, instrument.bandsToSkip)) {
                continue;
            }
            rhoRaec[i] = getSourceTile(aeRayProduct.getBand("rho_ray_aerc_" + (i + 1)), sourceRect,
                                       BorderExtender.createInstance(BorderExtender.BORDER_COPY));
        }
        return rhoRaec;
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(AeAerosolOp.class);
        }
    }

}
