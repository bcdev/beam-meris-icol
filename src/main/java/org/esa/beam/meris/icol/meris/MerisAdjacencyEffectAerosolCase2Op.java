/*
 * $Id: $
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
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.gpf.operators.standard.BandMathsOp;
import org.esa.beam.meris.brr.CloudClassificationOp;
import org.esa.beam.meris.icol.*;
import org.esa.beam.meris.icol.AerosolScatteringFunctions.RV;
import org.esa.beam.meris.icol.IcolConvolutionKernellLoop;
import org.esa.beam.meris.icol.common.AdjacencyEffectMaskOp;
import org.esa.beam.meris.icol.common.ZmaxOp;
import org.esa.beam.meris.icol.utils.IcolUtils;
import org.esa.beam.meris.icol.utils.OperatorUtils;
import org.esa.beam.meris.l2auxdata.Constants;
import org.esa.beam.util.BitSetter;
import org.esa.beam.util.ResourceInstaller;
import org.esa.beam.util.SystemUtils;
import org.esa.beam.util.math.MathUtils;

import javax.media.jai.BorderExtender;
import java.awt.Rectangle;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.util.Map;

import static org.esa.beam.meris.l2auxdata.Constants.L1_F_GLINTRISK;


/**
 * Operator for aerosol part of AE correction.
 *
 * @author Marco Zuehlke, Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
@SuppressWarnings({"FieldCanBeLocal"})
@OperatorMetadata(alias = "Meris.AEAerosolCase2",
        version = "1.0",
        internal = true,
        authors = "Marco Zuehlke",
        copyright = "(c) 2007 by Brockmann Consult",
        description = "Contribution of aerosol to the adjacency effect.")
public class MerisAdjacencyEffectAerosolCase2Op extends MerisBasisOp {

    public static final String AOT_FLAGS = "aot_flags";

    //vertical scale height
    private static final double HA = 3000;
    private static final double NO_DATA_VALUE = -1.0;
    private static final double AE_AOT_THRESHOLD = 0.01;

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
    @SourceProduct(alias = "cloud", optional = true)
    private Product cloudProduct;
    @SourceProduct(alias = "zmaxCloud")
    private Product zmaxCloudProduct;
    @SourceProduct(alias = "cloudLandMask")
    private Product cloudLandMaskProduct;

    @TargetProduct
    private Product targetProduct;

    @Parameter(defaultValue = "false", description = "export the aerosol and fresnel correction term as bands")
    private boolean exportSeparateDebugBands = false;
    @Parameter(defaultValue = "false")
    private boolean icolAerosolForWater;
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
    @Parameter(defaultValue = "true")
    private boolean reshapedConvolution;
    @Parameter
    private String landExpression;

    IcolConvolutionAlgo icolConvolutionAlgo;
    IcolConvolutionAlgo lcFlagConvAlgo;

    private Band flagBand;

    private Band alphaBand;
    private Band alphaIndexBand;
    private Band aotBand;
    private Band rhoW9Band;   // additional output for RS

    private Band[] aeAerBands;
    private Band[] rhoAeAcBands;
    private Band[] rhoRaecBracketBands;       // additional output for RS
    private Band[] rhoRaecDiffBands;       // additional output for RS

    private Band[] fresnelDebugBands;
    private Band[] aerosolDebugBands;

    private Band landFlagConvBand;
    private Band cloudFlagConvBand;

    private CoeffW coeffW;
    private AerosolScatteringFunctions aerosolScatteringFunctions;
    private FresnelReflectionCoefficient fresnelCoefficient;

    // tables for case 2 aerosol correction:
    private static final float[] rhoB9Table = new float[20];
    private static final float[] rhoB12Table = new float[]{
            0.0000f, 0.0015f, 0.0032f, 0.0050f, 0.0069f, 0.0089f, 0.0110f, 0.0132f, 0.0157f, 0.0183f,
            0.0212f, 0.0245f, 0.0281f, 0.0322f, 0.0368f, 0.0421f, 0.0483f, 0.0553f, 0.0636f, 0.0732f
    };
    private static final float[] rhoB13Table = new float[]{
            0.0000f, 0.0009f, 0.0018f, 0.0029f, 0.0040f, 0.0052f, 0.0065f, 0.0079f, 0.0095f, 0.0112f,
            0.0131f, 0.0152f, 0.0177f, 0.0206f, 0.0240f, 0.0281f, 0.0331f, 0.0394f, 0.0474f, 0.0579f
    };
    private int[] bandsToSkip;
    private double userAot865;

    @Override
    public void initialize() throws OperatorException {
        userAot865 = IcolUtils.convertAOT(userAot, userAlpha, userAerosolReferenceWavelength, 865.0);
        bandsToSkip = new int[]{10, 14};
        targetProduct = createCompatibleProduct(aeRayProduct, "ae_" + aeRayProduct.getName(), "AE");

        flagBand = targetProduct.addBand(AOT_FLAGS, ProductData.TYPE_UINT8);
        FlagCoding flagCoding = createFlagCoding();
        flagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);

        alphaBand = targetProduct.addBand("alpha", ProductData.TYPE_FLOAT32);
        alphaIndexBand = targetProduct.addBand("alpha_index", ProductData.TYPE_UINT8);
        aotBand = targetProduct.addBand("aot", ProductData.TYPE_FLOAT32);

        rhoW9Band = targetProduct.addBand("rhoW9", ProductData.TYPE_FLOAT32);

        rhoAeAcBands = addBandGroup("rho_ray_aeac");
        if (System.getProperty("additionalOutputBands") != null && System.getProperty("additionalOutputBands").equals(
                "RS")) {
            rhoRaecBracketBands = addBandGroup("rho_raec_bracket");
            rhoRaecDiffBands = addBandGroup("rho_raec_diff");
        }
        aeAerBands = addBandGroup("rho_aeAer");

        if (exportSeparateDebugBands) {
            aerosolDebugBands = addBandGroup("rho_aeAer_aerosol");
            fresnelDebugBands = addBandGroup("rho_aeAer_fresnel");
        }

        landFlagConvBand = targetProduct.addBand("land_flag_aer_conv", ProductData.TYPE_FLOAT32);
        cloudFlagConvBand = targetProduct.addBand("cloud_flag_aer_conv", ProductData.TYPE_FLOAT32);

        try {
            loadAuxData();
        } catch (IOException e) {
            throw new OperatorException(e);
        }

        String productType = l1bProduct.getProductType();


        if (reshapedConvolution) {
            icolConvolutionAlgo = new IcolConvolutionJaiConvolve(aeRayProduct, productType, coeffW, "rho_ray_aerc_", iaerConv,
                    EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS,
                    bandsToSkip);
            lcFlagConvAlgo = new IcolConvolutionJaiConvolve(cloudLandMaskProduct, productType, coeffW, "lcflag_", iaerConv, 2, null);
        } else {
            icolConvolutionAlgo = new IcolConvolutionKernellLoop(l1bProduct, coeffW, IcolConstants.AE_CORRECTION_MODE_AEROSOL);
            lcFlagConvAlgo = new IcolConvolutionKernellLoop(l1bProduct, coeffW, IcolConstants.AE_CORRECTION_MODE_RAYLEIGH);
        }


        aerosolScatteringFunctions = new AerosolScatteringFunctions();

        BandMathsOp bandArithmeticOp =
                BandMathsOp.createBooleanExpressionBand(landExpression, landProduct);
        isLandBand = bandArithmeticOp.getTargetProduct().getBandAt(0);
    }

    private Band[] addBandGroup(String prefix) {
        return OperatorUtils.addBandGroup(l1bProduct, EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS, bandsToSkip,
                targetProduct, prefix, NO_DATA_VALUE, false);
    }

    private FlagCoding createFlagCoding() {
        FlagCoding flagCoding = new FlagCoding(AOT_FLAGS);
        flagCoding.addFlag("bad_aerosol_model", BitSetter.setFlag(0, 0), null);
        flagCoding.addFlag("bad_aot_model", BitSetter.setFlag(0, 1), null);
        flagCoding.addFlag("high_turbid_water", BitSetter.setFlag(0, 2), null);
        flagCoding.addFlag("sunglint", BitSetter.setFlag(0, 3), null);
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

    private Tile[] getTargetTiles(Map<Band, Tile> targetTiles, Band[] bands) {
        return OperatorUtils.getTargetTiles(targetTiles, bands, bandsToSkip);
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRect, ProgressMonitor pm) throws
            OperatorException {
        Rectangle sourceRect = icolConvolutionAlgo.mapTargetRect(targetRect);

        Tile aep = getSourceTile(aemaskProduct.getBand(AdjacencyEffectMaskOp.AE_MASK_AEROSOL), targetRect,
                BorderExtender.createInstance(BorderExtender.BORDER_COPY));

        Tile vza = null;
        Tile sza = null;
        Tile vaa = null;
        Tile saa = null;

        Tile isLand = null;
        Tile[] zmaxs = null;
        Tile zmaxCloud = null;

        Tile[] rhoRaec = OperatorUtils.getSourceTiles(this, aeRayProduct, "rho_ray_aerc",
                EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS, bandsToSkip,
                sourceRect);

        Tile isMaskLand = getSourceTile(cloudLandMaskProduct.getBand(CloudLandMaskOp.LAND_MASK_NAME), targetRect,
                BorderExtender.createInstance(BorderExtender.BORDER_COPY));
        Tile isMaskCloud = getSourceTile(cloudLandMaskProduct.getBand(CloudLandMaskOp.CLOUD_MASK_NAME), targetRect,
                BorderExtender.createInstance(BorderExtender.BORDER_COPY));

        IcolConvolutionAlgo.Convolver convolver = null;

        Tile flagTile = targetTiles.get(flagBand);

        Tile aotTile = targetTiles.get(aotBand);
        Tile alphaTile = targetTiles.get(alphaBand);
        Tile alphaIndexTile = targetTiles.get(alphaIndexBand);

        Tile l1FlagsTile = getSourceTile(l1bProduct.getBand(EnvisatConstants.MERIS_L1B_FLAGS_DS_NAME), targetRect);

        Tile rhoW9Tile = targetTiles.get(rhoW9Band);

        Tile[] rhoAeAcRaster = getTargetTiles(targetTiles, rhoAeAcBands);
        Tile[] aeAerRaster = getTargetTiles(targetTiles, aeAerBands);
        Tile[] rhoRaecBracket = null;
        Tile[] rhoRaecDiffRaster = null;

        final boolean debugMode = System.getProperty("additionalOutputBands") != null && System.getProperty(
                "additionalOutputBands").equals("RS");
        if (debugMode) {
            rhoRaecBracket = getTargetTiles(targetTiles, rhoRaecBracketBands);
            rhoRaecDiffRaster = getTargetTiles(targetTiles, rhoRaecDiffBands);
        }
        Tile[] aerosolDebug = null;
        Tile[] fresnelDebug = null;
        if (exportSeparateDebugBands) {
            aerosolDebug = getTargetTiles(targetTiles, aerosolDebugBands);
            fresnelDebug = getTargetTiles(targetTiles, fresnelDebugBands);
        }

        Tile lfConvTile = targetTiles.get(landFlagConvBand);
        Tile cfConvTile = targetTiles.get(cloudFlagConvBand);

        Tile surfacePressure = null;
        Tile cloudTopPressure = null;
        Tile cloudFlags = null;
        if (cloudProduct != null) {
            surfacePressure = getSourceTile(cloudProduct.getBand(CloudClassificationOp.PRESSURE_SURFACE), targetRect,
                    BorderExtender.createInstance(BorderExtender.BORDER_COPY));
            cloudTopPressure = getSourceTile(cloudProduct.getBand(CloudClassificationOp.PRESSURE_CTP), targetRect,
                    BorderExtender.createInstance(BorderExtender.BORDER_COPY));
            cloudFlags = getSourceTile(cloudProduct.getBand(CloudClassificationOp.CLOUD_FLAGS), targetRect,
                    BorderExtender.createInstance(BorderExtender.BORDER_COPY));
        }

        final IcolConvolutionAlgo.Convolver lcFlagConvolver =
                lcFlagConvAlgo.createConvolver(this, new Tile[]{isMaskLand, isMaskCloud}, targetRect, pm);

        try {
            for (int y = targetRect.y; y < targetRect.y + targetRect.height; y++) {
                for (int x = targetRect.x; x < targetRect.x + targetRect.width; x++) {
                    final double rho_13 = rhoRaec[Constants.bb865].getSampleFloat(x, y);
                    final double rho_12 = rhoRaec[Constants.bb775].getSampleFloat(x, y);
                    final double rho_9 = rhoRaec[Constants.bb705].getSampleFloat(x, y);
                    /* correct pressure in presence of clouds  - ICOL+ ATBD eqs. (6)-(8) */
                    double rhoCloudCorrFac = 1.0d;
                    boolean isCloud = false;
                    if (cloudProduct != null) {
                        isCloud = cloudFlags.getSampleBit(x, y, CloudClassificationOp.F_CLOUD);

                        if (isCloud) {
                            final double pressureCorrectionCloud = cloudTopPressure.getSampleDouble(x, y) /
                                    surfacePressure.getSampleDouble(x, y);
                            // cloud height
                            double zCloud = 8.0 * Math.log(1.0 / pressureCorrectionCloud);
                            rhoCloudCorrFac = Math.exp(-zCloud / HA);
                        }
                    }

                    double lfConv;
                    double cfConv;
                    if (reshapedConvolution) {
                        lfConv = lcFlagConvolver.convolveSample(x, y, iaerConv, 0);
                        cfConv = lcFlagConvolver.convolveSample(x, y, iaerConv, 1);
                    } else {
                        lfConv = lcFlagConvolver.convolveSampleBoolean(x, y, iaerConv, 0);
                        cfConv = lcFlagConvolver.convolveSampleBoolean(x, y, iaerConv, 1);
                    }
                    lfConvTile.setSample(x, y, lfConv);
                    cfConvTile.setSample(x, y, cfConv);

                    if (l1FlagsTile.getSampleBit(x, y, L1_F_GLINTRISK)) {
                        flagTile.setSample(x, y, flagTile.getSampleInt(x, y) + 8);
                    }

                    if (aep.getSampleInt(x, y) == 1 && rho_13 >= 0.0 && rho_12 >= 0.0) {
                        // attempt to optimise
                        if (vza == null || sza == null || vaa == null || saa == null || isLand == null || zmaxs == null ||
                                zmaxCloud == null || convolver == null) {
                            vza = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME),
                                    targetRect, BorderExtender.createInstance(BorderExtender.BORDER_COPY));
                            sza = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME),
                                    targetRect, BorderExtender.createInstance(BorderExtender.BORDER_COPY));
                            vaa = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_AZIMUTH_DS_NAME),
                                    targetRect, BorderExtender.createInstance(BorderExtender.BORDER_COPY));
                            saa = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME),
                                    targetRect, BorderExtender.createInstance(BorderExtender.BORDER_COPY));
                            isLand = getSourceTile(isLandBand, sourceRect, BorderExtender.createInstance(BorderExtender.BORDER_COPY));
                            zmaxs = ZmaxOp.getSourceTiles(this, zmaxProduct, targetRect, pm);
                            zmaxCloud = ZmaxOp.getSourceTile(this, zmaxCloudProduct, targetRect);
                            convolver = icolConvolutionAlgo.createConvolver(this, rhoRaec, targetRect, pm);
                        }
                        // end of optimisation attempt

                        final boolean useUserAlpha = isLand.getSampleBoolean(x, y) || !icolAerosolForWater;
                        double alpha = computeAlpha(useUserAlpha, rhoRaec[Constants.bb775], y, x, rho_13);
                        int iaer = IcolUtils.determineAerosolModelIndex(alpha);
                        if (iaer < 1 || iaer > 26) {
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

                        double pab = aerosolScatteringFunctions.aerosolPhase(thetab, iaer);
                        // aot at 865nm depending on aerosol model:
                        double taua865Iaer = 0.1 * Math.pow((550.0 / 865.0), (iaer / 10.0));
                        double paerFB = aerosolScatteringFunctions.aerosolPhaseFB(thetaf, thetab, iaer);
                        double corrFac;

                        double zmaxPart = ZmaxOp.computeZmaxPart(zmaxs, x, y, HA);
                        if (isLand.getSampleBoolean(x, y)) {
                            // contribution must be subtracted over land - ICOL+ ATBD section 4.2
                            zmaxPart *= -1.0;
                        }
                        double zmaxCloudPart = ZmaxOp.computeZmaxPart(zmaxCloud, x, y, HA);

                        double aot;
                        double[] rhoBrr865 = new double[17];     // B13
                        double[] rhoBrr775 = new double[17];     // B12
                        double[] rhoBrr705 = new double[17];     // B9

                        //  ICOL D6a ATBD: begin case 2 algorithm

                        // for two iaer, compute two values of rhoBrrBracket in B13for inter-/extrapolation...
                        final double rhoBrr865Bracket16 = convolver.convolveSample(x, y, 16, Constants.bb865); // B13
                        final double rhoBrr865Bracket06 = convolver.convolveSample(x, y, 6, Constants.bb865);
                        final double rhoBrr775Bracket16 = convolver.convolveSample(x, y, 16, Constants.bb775); // B12
                        final double rhoBrr775Bracket06 = convolver.convolveSample(x, y, 6, Constants.bb775);
                        final double rhoBrr705Bracket16 = convolver.convolveSample(x, y, 16, Constants.bb705); // B9
                        final double rhoBrr705Bracket06 = convolver.convolveSample(x, y, 6, Constants.bb705);
                        final double deltaRhoBrr865Bracket06 = Math.abs(
                                (rhoBrr865Bracket16 - rhoBrr865Bracket06) / 10.0);
                        final double deltaRhoBrr775Bracket06 = Math.abs(
                                (rhoBrr775Bracket16 - rhoBrr775Bracket06) / 10.0);
                        final double deltaRhoBrr705Bracket06 = Math.abs(
                                (rhoBrr705Bracket16 - rhoBrr705Bracket06) / 10.0);

                        int searchIAOT = -1;

                        if (!isLand.getSampleBoolean(x, y) && icolAerosolForWater) {
                            double alphaBest = 0.0;
                            double aot865Best = 0.0;
                            double rhoBrr705Best;
                            double rhoBrr705BestPrev = 0.0;
                            double rhoW705Interpolated = 0.0;

                            // With the 3-band algorithm (705, 775, 865), we want to retrieve 3 unknowns:
                            // - the water reflectance at 705nm
                            // - the aerosol type
                            // - the AOT at 865nm

                            // the final index of retrieved water reflectance rhoW705
                            int jrhow705;

                            // rhoW705 loop (B9, Gerald Moore table):
                            for (int irhow705 = 0; irhow705 < 20; irhow705++) {
                                rhoB9Table[irhow705] = irhow705 * 0.005f;
                                if (irhow705 > 0 && searchIAOT != -1) {
                                    // 'best' value of rhoBrr705 retrieved with previous irhoW
                                    rhoBrr705BestPrev = rhoBrr705[searchIAOT];
                                }
                                double[] aot865 = new double[26];
                                double taua865C2;
                                // loop over the 26 aerosol models:
                                for (int iaerC2 = 1; iaerC2 <= 26; iaerC2++) {
                                    double rhoBrrBracket775C2 = rhoBrr775Bracket06 + (iaerC2 - 6) * deltaRhoBrr775Bracket06;
                                    double rhoBrrBracket865C2 = rhoBrr865Bracket06 + (iaerC2 - 6) * deltaRhoBrr865Bracket06;

                                    // aot at 865nm depending on case 2 aerosol model:
                                    final double taua865IaerC2 = 0.1 * Math.pow((550.0 / 865.0), (iaerC2 - 1) / 10.0);

                                    pab = aerosolScatteringFunctions.aerosolPhase(thetab, iaerC2);
                                    paerFB = aerosolScatteringFunctions.aerosolPhaseFB(thetaf, thetab, iaerC2);
                                    corrFac = 1.0 + paerFB * (r1v + r1s * (1.0 - zmaxPart - zmaxCloudPart));
                                    searchIAOT = -1;
                                    for (int iiaot = 1; iiaot <= 16 && searchIAOT == -1; iiaot++) {
                                        // rhoBrr865 computation as for case 1, but with case 2 aerosol model and aot indices:
                                        taua865C2 = taua865IaerC2 * iiaot;
                                        RV rv = aerosolScatteringFunctions.aerosol_f(taua865C2, iaerC2, pab,
                                                sza.getSampleFloat(x, y),
                                                vza.getSampleFloat(x, y), phi);
                                        //  - this reflects ICOL D6a ATBD, eq. (2): rhoa = rho_a, rv.rhoa = rho_a0 !!!
                                        final double rhoa0 = rv.rhoa;
                                        final double rhoa = rhoa0 * corrFac;
                                        rhoBrr865[iiaot] = rhoa + rhoBrrBracket865C2 * rv.tds * (rv.tus - Math.exp(
                                                -taua865C2 / muv));
                                        // now add water reflectance contribution in B13 from table for given rhoW index
                                        rhoBrr865[iiaot] += (rhoB13Table[irhow705] * rv.tds * rv.tus);

                                        // if we reached the MERIS measurement in B13, we are done and have the final
                                        // index of aot at 865nm (aot865best, third unknown) for THIS aerosol type
                                        // (iaer) and THIS rhoW (irhow705)
                                        if (rhoBrr865[iiaot] > rho_13) {
                                            searchIAOT = iiaot - 1;
                                        }
                                    } // end iaot loop
                                    if (searchIAOT != -1) {
                                        // interpolate aot865 for retrieved index and given case 2 aerosol model
                                        aot865[iaerC2 - 1] = aerosolScatteringFunctions.interpolateLin(
                                                rhoBrr865[searchIAOT], searchIAOT,
                                                rhoBrr865[searchIAOT + 1], searchIAOT + 1, rho_13) * taua865IaerC2;

                                        // rhoBrr775 computation as for case 1, but with case 2 aerosol model index and DERIVED AOT 865!!:
                                        double taua775C2 = aot865[iaerC2 - 1] * Math.pow((865.0 / 775.0),
                                                (iaerC2 - 1) / 10.0);
                                        RV rv775 = aerosolScatteringFunctions.aerosol_f(taua775C2, iaerC2, pab,
                                                sza.getSampleFloat(x, y),
                                                vza.getSampleFloat(x, y), phi);
                                        final double rhoa0775 = rv775.rhoa;
                                        final double rhoa775 = rhoa0775 * corrFac;
                                        rhoBrr775[searchIAOT] = rhoa775 + rhoBrrBracket775C2 * rv775.tds * (rv775.tus - Math.exp(
                                                -taua775C2 / muv));
                                        // now add water reflectance contribution in B12 from table for given rhoW index
                                        rhoBrr775[searchIAOT] += (rhoB12Table[irhow705] * rv775.tds * rv775.tus);

                                        // ATBD D6a, 2.2, (iii) for ICOL 2.0
                                        // if we reached the MERIS measurement in B12, we are done and have the final
                                        // aerosol model (iaer, second unknown) for THIS rhoW (irhow705)
                                        if (rhoBrr775[searchIAOT] > rho_12) {
                                            iaer = iaerC2;
                                            alphaBest = 0.1 - iaer / 10.0;
                                            aot865Best = aot865[iaerC2 - 1];
                                            break;
                                        }
                                    }
                                }  // end iaer loop
                                if (searchIAOT != -1) {
                                    // rhoBrr705 computation with derived case 2 aerosol type index (iaer) and DERIVED AOT 865
                                    double tauaConst705 = aot865[iaer - 1] * Math.pow((865.0 / 705.0),
                                            ((iaer - 1) / 10.0));
                                    pab = aerosolScatteringFunctions.aerosolPhase(thetab, iaer);
                                    RV rv = aerosolScatteringFunctions.aerosol_f(tauaConst705, iaer, pab,
                                            sza.getSampleFloat(x, y),
                                            vza.getSampleFloat(x, y), phi);
                                    //  - this reflects ICOL D6a ATBD, eq. (2): rhoa = rho_a, rv.rhoa = rho_a0 !!!
                                    final double rhoa0705 = rv.rhoa;
                                    corrFac = 1.0 + paerFB * (r1v + r1s * (1.0 - zmaxPart - zmaxCloudPart));
                                    final double rhoa705 = rhoa0705 * corrFac;
                                    double rhoBrrBracket705C1 = rhoBrr705Bracket06 + (iaer - 5) * deltaRhoBrr705Bracket06;
                                    rhoBrr705[searchIAOT] = rhoa705 + rhoBrrBracket705C1 * rv.tds * (rv.tus - Math.exp(
                                            -tauaConst705 / muv));
                                    // now add water reflectance contribution in B9 from table for given rhoW index
                                    rhoBrr705[searchIAOT] += (rhoB9Table[irhow705] * rv.tds * rv.tus);

                                    // if we reached the MERIS measurement in B9, we are done and have the final
                                    // 'experimental' water reflectance at 705nm (ATBD D6a, eq. 11).
                                    if (rhoBrr705[searchIAOT] > rho_9) {
                                        rhoBrr705Best = rhoBrr705[searchIAOT];
                                        jrhow705 = irhow705;
                                        if (jrhow705 > 0) {
                                            double delta705 = (rho_9 - rhoBrr705BestPrev) / (rhoBrr705BestPrev - rhoBrr705Best);
                                            if (Math.abs(delta705) > 1.0) {
                                                delta705 = 0.0;
                                            }
                                            rhoW705Interpolated = rhoB9Table[jrhow705] + delta705 *
                                                    (rhoB9Table[jrhow705 - 1] - rhoB9Table[jrhow705]);
                                        }
                                        break;
                                    }
                                } else {
                                    // raise 'high turbid water' flag
                                    flagTile.setSample(x, y, flagTile.getSampleInt(x, y) + 4);
                                }
                            } // end irhow705 loop

                            // We are done with the case 2 retrieval. Set final values of alpha, AOT, these go into the
                            // the product
                            aot = aot865Best;
                            alpha = alphaBest;

                            if (exportSeparateDebugBands && searchIAOT != -1) {
                                rhoW9Tile.setSample(x, y, rhoW705Interpolated);
                            }
                        } else {
                            aot = userAot865;
                            searchIAOT = MathUtils.floorInt(aot * 10);
                            iaer = getAerosolModelIndex(userAlpha);
                            double rhoBrrBracket865C1 = rhoBrr865Bracket06 + (iaer - 5) * deltaRhoBrr865Bracket06;
                            for (int iiaot = searchIAOT; iiaot <= searchIAOT + 1; iiaot++) {
                                final double taua = taua865Iaer * iiaot;
                                RV rv = aerosolScatteringFunctions.aerosol_f(taua, iaer, pab, sza.getSampleFloat(x, y),
                                        vza.getSampleFloat(x, y), phi);
                                //  - this reflects ICOL D6a ATBD, eq. (2): rhoa = rho_a, rv.rhoa = rho_a0 !!!
                                final double rhoa0865 = rv.rhoa;
                                corrFac = 1.0 + paerFB * (r1v + r1s * (1.0 - zmaxPart - zmaxCloudPart));
                                final double rhoa865 = rhoa0865 * corrFac;
                                rhoBrr865[iiaot] = rhoa865 + rhoBrrBracket865C1 * rv.tds * (rv.tus - Math.exp(
                                        -taua / muv));
                            }
                        }
                        //  end case 2 algorithm

                        alphaIndexTile.setSample(x, y, iaer);
                        alphaTile.setSample(x, y, alpha);
                        // write aot865 to output...
                        aotTile.setSample(x, y, aot);

                        //Correct from AE with AEROSOLS
                        for (int iwvl = 0; iwvl < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; iwvl++) {
                            if (IcolUtils.isIndexToSkip(iwvl, bandsToSkip)) {
                                continue;
                            }
                            if (!isLand.getSampleBoolean(x, y) && iwvl == 9) {
                                continue;
                            }

                            if (exportSeparateDebugBands) {
                                aerosolDebug[iwvl].setSample(x, y, -1);
                                fresnelDebug[iwvl].setSample(x, y, -1);
                            }
                            if (searchIAOT != -1 && aot > AE_AOT_THRESHOLD) {
                                double roAerMean;
                                if (iwvl == Constants.bb9) {
                                    roAerMean = rhoBrr705Bracket06 + (iaer - 5) * deltaRhoBrr705Bracket06;
                                } else if (iwvl == Constants.bb12) {
                                    roAerMean = rhoBrr775Bracket06 + (iaer - 5) * deltaRhoBrr775Bracket06;
                                } else if (iwvl == Constants.bb13) {
                                    roAerMean = rhoBrr865Bracket06 + (iaer - 5) * deltaRhoBrr865Bracket06;
                                } else {
                                    roAerMean = convolver.convolveSample(x, y, iaer, iwvl);
                                }

                                float rhoRaecIwvl = rhoRaec[iwvl].getSampleFloat(x, y);
                                Band band = (Band) rhoRaec[iwvl].getRasterDataNode();
                                float wvl = band.getSpectralWavelength();

                                //Compute the aerosols functions for the first aot
                                final double taua1 = 0.1 * searchIAOT * Math.pow((550.0 / wvl), (iaer / 10.0));
                                RV rv1 = aerosolScatteringFunctions.aerosol_f(taua1, iaer, pab,
                                        sza.getSampleFloat(x, y),
                                        vza.getSampleFloat(x, y), phi);

                                double aerosol1;
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
                                RV rv2 = aerosolScatteringFunctions.aerosol_f(taua2, iaer, pab,
                                        sza.getSampleFloat(x, y),
                                        vza.getSampleFloat(x, y), phi);

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

                                if (debugMode) {
                                    rhoRaecBracket[iwvl].setSample(x, y, roAerMean);
                                    rhoRaecDiffRaster[iwvl].setSample(x, y, rhoRaecIwvl - aerosol1 + fresnel1);
                                }
                            } else {
                                float rhoRaecIwvl = rhoRaec[iwvl].getSampleFloat(x, y);
                                if (isCloud) {
                                    rhoRaecIwvl *= rhoCloudCorrFac;
                                }
                                rhoAeAcRaster[iwvl].setSample(x, y, rhoRaecIwvl);
                                if (debugMode) {
                                    rhoRaecBracket[iwvl].setSample(x, y, -1f);
                                }
                            }
                        }
                    } else {
                        for (int iwvl = 0; iwvl < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; iwvl++) {
                            if (IcolUtils.isIndexToSkip(iwvl, bandsToSkip)) {
                                continue;
                            }
                            if (isLand == null) {
                                isLand = getSourceTile(isLandBand, sourceRect, BorderExtender.createInstance(BorderExtender.BORDER_COPY));
                            }
                            if (!isLand.getSampleBoolean(x, y) && iwvl == 9) {
                                continue;
                            }
                            rhoAeAcRaster[iwvl].setSample(x, y, rhoRaec[iwvl].getSampleFloat(x, y));
                            if (debugMode) {
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

    private double computeAlpha(boolean useUserAlpha, Tile tile, int y, int x, double rho_13) {
        double alpha;
        if (useUserAlpha) {
            alpha = userAlpha;
        } else {
            //Aerosols type determination
            double rhoRaecTmp = tile.getSampleDouble(x, y);
            final double epsilon = rhoRaecTmp / rho_13;
            alpha = Math.log(epsilon) / Math.log(778.0 / 865.0);
        }
        return alpha;
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
            super(MerisAdjacencyEffectAerosolCase2Op.class);
        }
    }
}