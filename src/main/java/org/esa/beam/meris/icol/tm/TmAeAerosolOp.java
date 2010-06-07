package org.esa.beam.meris.icol.tm;

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
import org.esa.beam.meris.brr.CloudClassificationOp;
import org.esa.beam.meris.icol.AerosolScatteringFuntions;
import org.esa.beam.meris.icol.CoeffW;
import org.esa.beam.meris.icol.FresnelReflectionCoefficient;
import org.esa.beam.meris.icol.IcolConstants;
import org.esa.beam.meris.icol.RhoBracketAlgo;
import org.esa.beam.meris.icol.RhoBracketJaiConvolve;
import org.esa.beam.meris.icol.RhoBracketKernellLoop;
import org.esa.beam.meris.icol.common.ZmaxOp;
import org.esa.beam.meris.icol.meris.MerisAeMaskOp;
import org.esa.beam.meris.icol.utils.IcolUtils;
import org.esa.beam.meris.l2auxdata.Constants;
import org.esa.beam.util.BitSetter;
import org.esa.beam.util.ProductUtils;
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
@OperatorMetadata(alias = "Landsat5.AEAerosol",
        version = "1.0",
        internal = true,
        authors = "Olaf Danne",
        copyright = "(c) 2009 by Brockmann Consult",
        description = "Contribution of aerosol to the adjacency effect (Landsat5).")
public class TmAeAerosolOp extends TmBasisOp {
    public static final String AOT_FLAGS = "aot_flags";

    //vertical scale height
    private static final double HA = 3000;

    private Band isLandBand;

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
    @SourceProduct(alias = "cloud", optional=true)
    private Product cloudProduct;
    @SourceProduct(alias = "ctp", optional=true)
    private Product ctpProduct;
    @SourceProduct(alias = "zmaxCloud")
    private Product zmaxCloudProduct;

    @TargetProduct
    private Product targetProduct;

    @Parameter(defaultValue = "false", description = "export the aerosol and fresnel correction term as bands")
    private boolean exportSeparateDebugBands = false;
    @Parameter(defaultValue = "true")
    private boolean icolAerosolForWater = true;
    @Parameter(interval = "[-2.5, 0.0]", defaultValue = "-1")
    private double userAlpha;
    @Parameter(interval = "[0, 1.5]", defaultValue = "0")
    private double userAot;
    // new in v1.1
    @Parameter(interval = "[1, 26]", defaultValue = "10")
    private int iaerConv;
    @Parameter(interval = "[1, 3]", defaultValue = "1")
    private int convolveAlgo;
    @Parameter(defaultValue="false")
    private boolean reshapedConvolution;
    @Parameter
    private String landExpression;
    @Parameter
    private String instrument;
    @Parameter(interval = "[300.0, 1060.0]", defaultValue = "1013.25")
    private double userPSurf;

    RhoBracketAlgo rhoBracketAlgo;

    private Band flagBand;

    private Band alphaBand;
    private Band alphaIndexBand;
    private Band aotBand;

    private Band[] aeAerBands;
    private Band[] rhoAeAcBands;
    private Band[] rhoRaecBracketBands;       // additional output for RS
    private Band[] rhoRaecDiffBands;       // additional output for RS

    private Band[] fresnelDebugBands;
    private Band[] aerosolDebugBands;

    private CoeffW coeffW;
    private double[/*26*/][/*RR=26|FR=101*/] w;
    private AerosolScatteringFuntions aerosolScatteringFuntions;
    private FresnelReflectionCoefficient fresnelCoefficient;

    private int numSpectralBands;
    private int[] bandsToSkip;

    @Override
    public void initialize() throws OperatorException {
        targetProduct = createCompatibleProduct(aeRayProduct, "ae_" + aeRayProduct.getName(), "AE");

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

        flagBand = targetProduct.addBand(AOT_FLAGS, ProductData.TYPE_UINT8);
        FlagCoding flagCoding = createFlagCoding();
        flagBand.setFlagCoding(flagCoding);
        targetProduct.addFlagCoding(flagCoding);

        alphaBand = targetProduct.addBand("alpha", ProductData.TYPE_FLOAT32);
        alphaIndexBand = targetProduct.addBand("alpha_index", ProductData.TYPE_UINT8);
        aotBand = targetProduct.addBand("aot", ProductData.TYPE_FLOAT32);

        rhoAeAcBands = addBandGroup("rho_ray_aeac", -1, instrument);
        if (System.getProperty("additionalOutputBands") != null && System.getProperty("additionalOutputBands").equals("RS")) {
            rhoRaecBracketBands = addBandGroup("rho_raec_bracket", -1, instrument);
            rhoRaecDiffBands = addBandGroup("rho_raec_diff", -1, instrument);
        }
        aeAerBands = addBandGroup("rho_aeAer", 0, instrument);

        if (exportSeparateDebugBands) {
            aerosolDebugBands = addBandGroup("rho_aeAer_aerosol", -1, instrument);
            fresnelDebugBands = addBandGroup("rho_aeAer_fresnel", -1, instrument);
        }

        try {
            loadAuxData();
        } catch (IOException e) {
            throw new OperatorException(e);
        }

        String productType = l1bProduct.getProductType();

        if (reshapedConvolution) {
            rhoBracketAlgo = new RhoBracketJaiConvolve(aeRayProduct, productType, coeffW, "rho_ray_aerc_", iaerConv,
                                                       numSpectralBands, bandsToSkip);
        } else {
            rhoBracketAlgo = new RhoBracketKernellLoop(l1bProduct, coeffW, IcolConstants.AE_CORRECTION_MODE_AEROSOL);
        }


        aerosolScatteringFuntions = new AerosolScatteringFuntions();
        if (l1bProduct.getPreferredTileSize() != null) {
            targetProduct.setPreferredTileSize(l1bProduct.getPreferredTileSize());
        }

        BandMathsOp bandArithmeticOp =
            BandMathsOp.createBooleanExpressionBand(landExpression, landProduct);
        isLandBand = bandArithmeticOp.getTargetProduct().getBandAt(0);
    }

    private Band[] addBandGroup(String prefix, double noDataValue, String instrument) {
        Band[]  bands = new Band[numSpectralBands];
        for (int i = 0; i < bands.length; i++) {
            if (IcolUtils.isIndexToSkip(i, bandsToSkip)) {
                continue;
            }
            Band inBand = l1bProduct.getBandAt(i);
            bands[i] = targetProduct.addBand(prefix + "_" + (i + 1), ProductData.TYPE_FLOAT32);
//            ProductUtils.copySpectralAttributes(inBand, bands[i]);
            ProductUtils.copySpectralBandProperties(inBand, bands[i]);
            bands[i].setNoDataValueUsed(true);
            bands[i].setNoDataValue(noDataValue);
        }
        return bands;
    }

    private Tile[] getTargetTileGroup(Map<Band, Tile> targetTiles, Band[] bands, String instrument) {
        Tile[] tiles = null;

        tiles = new Tile[bands.length];
        for (int i = 0; i < bands.length; i++) {
            if (IcolUtils.isIndexToSkip(i, bandsToSkip)) {
                continue;
            }
            tiles[i] = targetTiles.get(bands[i]);
        }

        return tiles;
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


    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRect, ProgressMonitor pm) throws OperatorException {
        Rectangle sourceRect = rhoBracketAlgo.mapTargetRect(targetRect);

        Tile vza = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME), targetRect, pm);
        Tile sza = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME), targetRect, pm);
        Tile vaa = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_AZIMUTH_DS_NAME), targetRect, pm);
        Tile saa = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME), targetRect, pm);

        Tile isLand = getSourceTile(isLandBand, sourceRect, pm);
        Tile[] zmaxs = ZmaxOp.getSourceTiles(this, zmaxProduct, targetRect, pm);
        Tile zmaxCloud = ZmaxOp.getSourceTile(this, zmaxCloudProduct, targetRect, pm);
        Tile aep = getSourceTile(aemaskProduct.getBand(MerisAeMaskOp.AE_MASK_AEROSOL), targetRect, pm);

        Tile[] rhoRaec = getRhoRaecTiles(pm, sourceRect, instrument);

        final RhoBracketAlgo.Convolver convolver = rhoBracketAlgo.createConvolver(this, rhoRaec, targetRect, pm);

        Tile flagTile = targetTiles.get(flagBand);

        Tile aotTile = targetTiles.get(aotBand);
        Tile alphaTile = targetTiles.get(alphaBand);
        Tile alphaIndexTile = targetTiles.get(alphaIndexBand);

        Tile[] rhoAeAcRaster = getTargetTileGroup(targetTiles, rhoAeAcBands, instrument);
        Tile[] aeAerRaster = getTargetTileGroup(targetTiles, aeAerBands, instrument);
        Tile[] rhoRaecBracket = null;
        Tile[] rhoRaecDiffRaster = null;
        if (System.getProperty("additionalOutputBands") != null && System.getProperty("additionalOutputBands").equals("RS")) {
            rhoRaecBracket = getTargetTileGroup(targetTiles, rhoRaecBracketBands, instrument);
            rhoRaecDiffRaster = getTargetTileGroup(targetTiles, rhoRaecDiffBands, instrument);
        }
        Tile[] aerosolDebug = null;
        Tile[] fresnelDebug = null;
        if (exportSeparateDebugBands) {
            aerosolDebug = getTargetTileGroup(targetTiles, aerosolDebugBands, instrument);
            fresnelDebug = getTargetTileGroup(targetTiles, fresnelDebugBands, instrument);
        }

        Tile surfacePressure = null;
        Tile cloudTopPressure = null;
        Tile cloudFlags = null;
        if (cloudProduct != null) {
            if (instrument.toUpperCase().equals("MERIS")) {
                surfacePressure = getSourceTile(cloudProduct.getBand(CloudClassificationOp.PRESSURE_SURFACE), targetRect, pm);
                cloudTopPressure = getSourceTile(cloudProduct.getBand(CloudClassificationOp.PRESSURE_CTP), targetRect, pm);
                cloudFlags = getSourceTile(cloudProduct.getBand(CloudClassificationOp.CLOUD_FLAGS), targetRect, pm);
            } else if (instrument.toUpperCase().equals("LANDSAT5 TM")) {
                cloudTopPressure = getSourceTile(ctpProduct.getBand(TmConstants.LANDSAT5_CTP_BAND_NAME), targetRect, pm);
                cloudFlags = getSourceTile(cloudProduct.getBand(TmCloudClassificationOp.CLOUD_FLAGS), targetRect, pm);
            }
        }

        // MERIS notation
        // Landsat5 equivalents: TM4 (865, 775), TM3 (705), to be discussed
        double[] rhoBrr865 = new double[17];     // B13
        double[] rhoBrr775 = new double[17];     // B12
        double[] rhoBrr705 = new double[17];     // B9

        try {
            for (int y = targetRect.y; y < targetRect.y + targetRect.height; y++) {
                for (int x = targetRect.x; x < targetRect.x + targetRect.width; x++) {
                    double rho_13 = 0.0;
                    double rho_12 = 0.0;
                    if (instrument.toUpperCase().equals("MERIS")) {
                        rho_13 = rhoRaec[Constants.bb865].getSampleFloat(x, y);
                        rho_12 = rhoRaec[Constants.bb775].getSampleFloat(x, y);
                    } else if (instrument.toUpperCase().equals("LANDSAT5 TM")) {
                        // to be discussed!
                        rho_13 = rhoRaec[TmConstants.LANDSAT5_RADIANCE_4_BAND_INDEX].getSampleFloat(x, y);
                        rho_12 = rhoRaec[TmConstants.LANDSAT5_RADIANCE_4_BAND_INDEX].getSampleFloat(x, y);
                    }

                    /* correct pressure in presence of clouds  - ICOL+ ATBD eqs. (6)-(8) */
                    double rhoCloudCorrFac = 1.0d;
                    boolean isCloud = false;
                    if (cloudProduct != null) {
                        isCloud = cloudFlags.getSampleBit(x, y, CloudClassificationOp.F_CLOUD);

                        if (isCloud) {
                            double pressureCorrectionCloud = 1.0;
                            if (instrument.toUpperCase().equals("MERIS")) {
                                pressureCorrectionCloud = cloudTopPressure.getSampleDouble(x, y) /
                                        surfacePressure.getSampleDouble(x, y);
                            } else if (instrument.toUpperCase().equals("LANDSAT5 TM")) {
                                pressureCorrectionCloud = cloudTopPressure.getSampleDouble(x, y) /
                                        userPSurf;
                            }
                            // cloud height
                            double zCloud = 8.0 * Math.log(1.0 / pressureCorrectionCloud);
                            rhoCloudCorrFac = Math.exp(-zCloud / HA);
                        }
                    }

                    if (aep.getSampleInt(x, y) == 1 && rho_13 != -1 && rho_12 != -1) {
                        double alpha;
                        if (!isLand.getSampleBoolean(x, y) && icolAerosolForWater) {
                            // RS, 04/12/09:
                            final double rho_4 = rhoRaec[TmConstants.LANDSAT5_RADIANCE_4_BAND_INDEX].getSampleFloat(x, y);
                            final double rho_3 = rhoRaec[TmConstants.LANDSAT5_RADIANCE_3_BAND_INDEX].getSampleFloat(x, y);
                            final double epsilon = rho_3 / rho_4;
                            alpha = Math.log(epsilon) / Math.log(TmConstants.LANDSAT5_SPECTRAL_BAND_EFFECTIVE_WAVELENGTHS[2] /
                                    TmConstants.LANDSAT5_SPECTRAL_BAND_EFFECTIVE_WAVELENGTHS[3]);
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

                        float phi = saa.getSampleFloat(x, y) - vaa.getSampleFloat(x, y);
                        double mus = Math.cos(sza.getSampleFloat(x, y) * MathUtils.DTOR);
                        double nus = Math.sin(sza.getSampleFloat(x, y) * MathUtils.DTOR);
                        double muv = Math.cos(vza.getSampleFloat(x, y) * MathUtils.DTOR);
                        double nuv = Math.sin(vza.getSampleFloat(x, y) * MathUtils.DTOR);

                        //compute the back scattering angle
                        double csb = mus * muv + nus * nuv * Math.cos(phi * MathUtils.DTOR);
                        double thetab = Math.acos(-csb) * MathUtils.RTOD;
                        //compute the forward scattering angle
                        double csf = mus * muv - nus * nuv * Math.cos(phi * MathUtils.DTOR);
                        double thetaf = Math.acos(csf) * MathUtils.RTOD;

                        double pab = aerosolScatteringFuntions.aerosolPhase(thetab, iaer);
                        double tauaConst = 0.1 * Math.pow((550.0 / 865.0), (iaer / 10.0));
                        double paerFB = aerosolScatteringFuntions.aerosolPhaseFB(thetaf, thetab, iaer);
                        double corrFac = 0.0;

                        double zmaxPart = ZmaxOp.computeZmaxPart(zmaxs, x, y, HA);
                        if (isLand.getSampleBoolean(x, y)) {
                            // contribution must be subtracted over land - ICOL+ ATBD section 4.2
                            zmaxPart *= -1.0;
                        }
                        double zmaxCloudPart = ZmaxOp.computeZmaxPart(zmaxCloud, x, y, HA);

                        int searchIAOT = -1;
                        double aot = 0;

                        double taua = 0.0;
                        double rhoa = 0.0;
                        double rhoa0 = 0.0;
                        // RS, 14.09.09:
                        // over water, apply ICOL AE if option is set
                        // otherwise apply user input (as always over land)
                        // begin 'old' case 1 water --> todo: clarify how to handle for Landsat
                        double rhoBrrBracket865 = 0.0;
                        double rhoBrrBracket705 = 0.0;
                        if (instrument.toUpperCase().equals("MERIS")) {
                            rhoBrrBracket865 = convolver.convolveSample(x, y, iaer, Constants.bb865);
                            rhoBrrBracket705 = convolver.convolveSample(x, y, iaer, Constants.bb705);
                        } else if (instrument.toUpperCase().equals("LANDSAT5 TM")) {
                            rhoBrrBracket865 = convolver.convolveSample(x, y, iaer, TmConstants.LANDSAT5_RADIANCE_4_BAND_INDEX);
                            rhoBrrBracket705 = rhoBrrBracket865; // TBD
                        }
                        if (!isLand.getSampleBoolean(x, y) && icolAerosolForWater) {
                            // icolAerosolForWater = true only for MERIS
                            for (int iiaot = 1; iiaot <= 16 && searchIAOT == -1; iiaot++) {
                                taua = tauaConst * iiaot;
                                AerosolScatteringFuntions.RV rv = aerosolScatteringFuntions.aerosol_f(taua, iaer, pab, sza.getSampleFloat(x, y), vza.getSampleFloat(x, y), phi);
                                //  - this reflects ICOL D6a ATBD, eq. (2): rhoa = rho_a, rv.rhoa = rho_a0 !!!
                                rhoa0 = rv.rhoa;
                                corrFac = 1.0 + paerFB * (r1v + r1s * (1.0 - zmaxPart - zmaxCloudPart));
                                rhoa = rhoa0 * corrFac;
                                // todo: identify this eq. in ATBDs (looks like  ICOL D61 ATBD eq. (1) with rho_w = 0)
                                rhoBrr865[iiaot] = rhoa + rhoBrrBracket865 * rv.tds * (rv.tus - Math.exp(-taua / muv));
                                // test:
//                                    double rhoW13Test = 0.025;
//                                    rhoBrr865[iiaot] += rhoW13Test*Math.exp(-taua / muv)*Math.exp(-taua / mus);

                                if (rhoBrr865[iiaot] > rho_13) {
                                    searchIAOT = iiaot - 1;
                                }
                            }

                            if (searchIAOT != -1) {
                                aot = aerosolScatteringFuntions.interpolateLin(rhoBrr865[searchIAOT], searchIAOT,
                                        rhoBrr865[searchIAOT + 1], searchIAOT + 1, rho_13) * 0.1;
                            } else {
                                flagTile.setSample(x, y, flagTile.getSampleInt(x, y) + 2);
                            }
                        } else {
                            aot = userAot;
                            searchIAOT = MathUtils.floorInt(aot * 10);
                            for (int iiaot = searchIAOT; iiaot <= searchIAOT + 1; iiaot++) {
                                taua = tauaConst * iiaot;
                                AerosolScatteringFuntions.RV rv = aerosolScatteringFuntions.aerosol_f(taua, iaer, pab, sza.getSampleFloat(x, y), vza.getSampleFloat(x, y), phi);
                                //  - this reflects ICOL D6a ATBD, eq. (2): rhoa = rho_a, rv.rhoa = rho_a0 !!!
                                rhoa0 = rv.rhoa;
                                corrFac = 1.0 + paerFB * (r1v + r1s * (1.0 - zmaxPart- zmaxCloudPart));
                                rhoa = rhoa0 * corrFac;
                                // todo: identify this eq. in ATBDs (looks like  ICOL D61 ATBD eq. (1) with rho_w = 0) s.a.
                                rhoBrr865[iiaot] = rhoa + rhoBrrBracket865 * rv.tds * (rv.tus - Math.exp(-taua / muv));
                                rhoBrr705[iiaot] = rhoa + rhoBrrBracket705 * rv.tds * (rv.tus - Math.exp(-taua / muv));
                            }
                        }
                        // end old case 1 water

                        alphaIndexTile.setSample(x, y, iaer);
                        alphaTile.setSample(x, y, alpha);
                        aotTile.setSample(x, y, aot);

                        //Correct from AE with AEROSOLS
                        for (int iwvl = 0; iwvl < numSpectralBands; iwvl++) {
                            if (IcolUtils.isIndexToSkip(iwvl, bandsToSkip)) {
                                continue;
                            }
                            if (exportSeparateDebugBands) {
                                aerosolDebug[iwvl].setSample(x, y, -1);
                                fresnelDebug[iwvl].setSample(x, y, -1);
                            }
                            if (searchIAOT != -1) {
                                // todo: extract methods also in this part!

                                double roAerMean = convolver.convolveSample(x, y, iaer, iwvl);

                                float rhoRaecIwvl = rhoRaec[iwvl].getSampleFloat(x, y);
                                Band band = (Band) rhoRaec[iwvl].getRasterDataNode();
                                float wvl = band.getSpectralWavelength();
                                if (instrument.toUpperCase().equals("MERIS")) {
                                    wvl = band.getSpectralWavelength();
                                } else if (instrument.toUpperCase().equals("LANDSAT5 TM")) {
                                    wvl = TmConstants.LANDSAT5_SPECTRAL_BAND_EFFECTIVE_WAVELENGTHS[iwvl];
                                }

                                //Compute the aerosols functions for the first aot
                                final double taua1 = 0.1 * searchIAOT * Math.pow((550.0 / wvl), (iaer / 10.0));
                                AerosolScatteringFuntions.RV rv1 = aerosolScatteringFuntions.aerosol_f(taua1, iaer, pab, sza.getSampleFloat(x, y), vza.getSampleFloat(x, y), phi);

                                double aerosol1 = 0.0;
                                aerosol1 = (roAerMean - rhoRaecIwvl) * (rv1.tds / (1.0 - roAerMean * rv1.sa));
                                aerosol1 = (rv1.tus - Math.exp(-taua1 / muv)) * aerosol1;

                                final double fresnel1 = rv1.rhoa * paerFB * r1s * (zmaxPart + zmaxCloudPart);
                                final double aea1 = aerosol1 - fresnel1;

                                if (exportSeparateDebugBands) {
                                    aerosolDebug[iwvl].setSample(x, y, aerosol1);
                                    fresnelDebug[iwvl].setSample(x, y, fresnel1);
                                }

                                //Compute the aerosols functions for the second aot
                                final double taua2 = 0.1 * (searchIAOT + 1) * Math.pow((550.0 / wvl), (iaer / 10.0));
                                AerosolScatteringFuntions.RV rv2 = aerosolScatteringFuntions.aerosol_f(taua2, iaer, pab, sza.getSampleFloat(x, y), vza.getSampleFloat(x, y), phi);

                                double aea2 = (roAerMean - rhoRaecIwvl) * (rv2.tds / (1.0 - roAerMean * rv2.sa));
                                aea2 = (rv2.tus - Math.exp(-taua2 / muv)) * aea2;
                                aea2 = aea2 - rv2.rhoa * paerFB * r1s * (zmaxPart + zmaxCloudPart);

                                //AOT INTERPOLATION to get AE_aer
                                double aea = aerosolScatteringFuntions.interpolateLin(rhoBrr865[searchIAOT], aea1,
                                        rhoBrr865[searchIAOT + 1], aea2, rho_13);

                                if (isCloud) {
                                    aea *= rhoCloudCorrFac;
                                }

                                aeAerRaster[iwvl].setSample(x, y, aea);

                                rhoAeAcRaster[iwvl].setSample(x, y, rhoRaecIwvl - (float) aea);

                                if (System.getProperty("additionalOutputBands") != null && System.getProperty("additionalOutputBands").equals("RS")) {
                                    rhoRaecBracket[iwvl].setSample(x, y, roAerMean);
                                    rhoRaecDiffRaster[iwvl].setSample(x, y, rhoRaecIwvl - aerosol1 + fresnel1);
                                }
                            } else {
                                float rhoRaecIwvl = rhoRaec[iwvl].getSampleFloat(x, y);
                                if (isCloud) {
                                    rhoRaecIwvl *= rhoCloudCorrFac;
                                }
                                rhoAeAcRaster[iwvl].setSample(x, y, rhoRaecIwvl);
                                if (System.getProperty("additionalOutputBands") != null && System.getProperty("additionalOutputBands").equals("RS")) {
                                    rhoRaecBracket[iwvl].setSample(x, y, -1f);
                                }
                            }
                        }
                    } else {
                        for (int iwvl = 0; iwvl < numSpectralBands; iwvl++) {
                            if (IcolUtils.isIndexToSkip(iwvl, bandsToSkip)) {
                                continue;
                            }
                            rhoAeAcRaster[iwvl].setSample(x, y, rhoRaec[iwvl].getSampleFloat(x, y));
                            if (System.getProperty("additionalOutputBands") != null && System.getProperty("additionalOutputBands").equals("RS")) {
                                rhoRaecBracket[iwvl].setSample(x, y, -1f);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new OperatorException(e);
        }
    }

    private Tile[] getRhoRaecTiles(ProgressMonitor pm, Rectangle sourceRect, String instrument) {
        Tile[] rhoRaec = null;
        rhoRaec = new Tile[numSpectralBands];
        for (int i = 0; i < numSpectralBands; i++) {
            if (IcolUtils.isIndexToSkip(i, bandsToSkip)) {
                continue;
            }
            rhoRaec[i] = getSourceTile(aeRayProduct.getBand("rho_ray_aerc_" + (i + 1)), sourceRect, pm);
        }

        return rhoRaec;
    }

    private int getAerosolModelIndex(double alpha) {
        int iaer = (int) (Math.round(-(alpha * 10.0)) + 1);
        if (iaer < 1) {
            iaer = 1;
        } else if (iaer > 26) {
            iaer = 26;
        }
        return iaer;
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(TmAeAerosolOp.class);
        }
    }

}
