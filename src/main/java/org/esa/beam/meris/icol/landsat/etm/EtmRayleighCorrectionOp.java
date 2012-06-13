package org.esa.beam.meris.icol.landsat.etm;

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
import org.esa.beam.meris.brr.HelperFunctions;
import org.esa.beam.meris.brr.LandClassificationOp;
import org.esa.beam.meris.icol.landsat.common.CloudClassificationOp;
import org.esa.beam.meris.icol.landsat.common.DownscaleOp;
import org.esa.beam.meris.icol.landsat.common.LandsatConstants;
import org.esa.beam.meris.icol.landsat.tm.TmBasisOp;
import org.esa.beam.meris.icol.landsat.tm.TmGaseousCorrectionOp;
import org.esa.beam.meris.icol.utils.OperatorUtils;
import org.esa.beam.meris.l2auxdata.Constants;
import org.esa.beam.meris.l2auxdata.L2AuxData;
import org.esa.beam.meris.l2auxdata.L2AuxDataProvider;
import org.esa.beam.util.BitSetter;
import org.esa.beam.util.math.MathUtils;

import javax.media.jai.BorderExtender;
import java.awt.*;
import java.util.Map;

/**
 * Class providing Landsat7 ETM+ rayleigh correction
 *
 * @author Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
@OperatorMetadata(alias = "Landsat7.Etm.EtmRayleighCorrection",
                  version = "1.0",
                  internal = true,
                  authors = "Olaf Danne",
                  copyright = "(c) 2009 by Brockmann Consult",
                  description = "Landsat7 ETM+ rayleigh correction.")
public class EtmRayleighCorrectionOp extends TmBasisOp implements Constants {

    public static final int NO_DATA_VALUE = -1;
    public static final String BRR_BAND_PREFIX = "brr";
    public static final String RAYLEIGH_REFL_BAND_PREFIX = "rayleigh_refl";
    public static final String RAY_CORR_FLAGS = "ray_corr_flags";

    protected L2AuxData auxData;
    protected EtmRayleighCorrection rayleighCorrection;

    private Band isLandBand;
    private Band[] brrBands;
    private Band[] rayleighReflBands;

    private Band flagBand;
    private Band[] transRvBands;
    private Band[] transRsBands;
    private Band[] tauRBands;

    private Band[] sphAlbRBands;
    @SourceProduct(alias = "refl")
    private Product sourceProduct;
    @SourceProduct(alias = "downscaled")
    private Product downscaledProduct;
    @SourceProduct(alias = "fresnel")
    private Product fresnelProduct;
    @SourceProduct(alias = "land")
    private Product landProduct;
    @SourceProduct(alias = "ctp")
    private Product ctpProduct;
    @SourceProduct(alias = "cloud", optional = true)
    private Product cloudProduct;
    @TargetProduct
    private Product targetProduct;
    @Parameter
    boolean correctWater = false;
    @Parameter
    boolean exportRayCoeffs = false;
    @Parameter
    boolean exportRhoR = false;
    @Parameter(interval = "[300.0, 1060.0]", defaultValue = "1013.25")
    private double userPSurf;


    @Override
    public void initialize() throws OperatorException {
        try {
            auxData = L2AuxDataProvider.getInstance().getAuxdata(sourceProduct);
            rayleighCorrection = new EtmRayleighCorrection(auxData);
        } catch (Exception e) {
            throw new OperatorException("could not load L2Auxdata", e);
        }
        createTargetProduct();
    }

    private void createTargetProduct() throws OperatorException {
        targetProduct = createCompatibleProduct(sourceProduct, "MER", "MER_L2");

        brrBands = addBandGroup(BRR_BAND_PREFIX);
        rayleighReflBands = addBandGroup(RAYLEIGH_REFL_BAND_PREFIX);

        flagBand = targetProduct.addBand(RAY_CORR_FLAGS, ProductData.TYPE_INT16);
        FlagCoding flagCoding = createFlagCoding(brrBands.length);
        targetProduct.getFlagCodingGroup().add(flagCoding);
        flagBand.setSampleCoding(flagCoding);

        if (exportRayCoeffs) {
            transRvBands = addBandGroup("transRv");
            transRsBands = addBandGroup("transRs");
            tauRBands = addBandGroup("tauR");
            sphAlbRBands = addBandGroup("sphAlbR");
        }
        BandMathsOp bandArithmeticOp =
                BandMathsOp.createBooleanExpressionBand(LandClassificationOp.LAND_FLAGS + ".F_LANDCONS", landProduct);
        isLandBand = bandArithmeticOp.getTargetProduct().getBandAt(0);
    }

    private Band[] addBandGroup(String prefix) {
        return OperatorUtils.addBandGroup(sourceProduct, LandsatConstants.LANDSAT7_NUM_SPECTRAL_BANDS,
                                          new int[]{}, targetProduct, prefix, NO_DATA_VALUE, false);
    }

    public static FlagCoding createFlagCoding(int bandLength) {
        FlagCoding flagCoding = new FlagCoding(RAY_CORR_FLAGS);
        int bitIndex = 0;
        for (int i = 0; i < bandLength; i++) {
            flagCoding.addFlag("F_NEGATIV_BRR_" + (i + 1), BitSetter.setFlag(0, bitIndex), null);
            bitIndex++;
        }
        return flagCoding;
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle rectangle, ProgressMonitor pm) throws
                                                                                                       OperatorException {
        pm.beginTask("Processing frame...", rectangle.height + 1);
        try {
            Tile szaTile = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME),
                                         rectangle, BorderExtender.createInstance(BorderExtender.BORDER_COPY));
            Tile vzaTile = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME),
                                         rectangle, BorderExtender.createInstance(BorderExtender.BORDER_COPY));
            Tile saaTile = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME),
                                         rectangle, BorderExtender.createInstance(BorderExtender.BORDER_COPY));
            Tile vaaTile = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_AZIMUTH_DS_NAME),
                                         rectangle, BorderExtender.createInstance(BorderExtender.BORDER_COPY));
            Tile altitudeTile = getSourceTile(
                    sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_DEM_ALTITUDE_DS_NAME), rectangle,
                    BorderExtender.createInstance(BorderExtender.BORDER_COPY));
            Tile scattAngleTile = getSourceTile(downscaledProduct.getBand(DownscaleOp.SCATTERING_ANGLE_BAND_NAME),
                                                rectangle, BorderExtender.createInstance(BorderExtender.BORDER_COPY));

            Tile[] rhoNg = new Tile[LandsatConstants.LANDSAT7_NUM_SPECTRAL_BANDS];
            for (int i = 0; i < rhoNg.length; i++) {
                rhoNg[i] = getSourceTile(
                        fresnelProduct.getBand(TmGaseousCorrectionOp.RHO_NG_BAND_PREFIX + "_" + (i + 1)), rectangle,
                        BorderExtender.createInstance(BorderExtender.BORDER_COPY));
            }
            Tile isLandCons = getSourceTile(isLandBand, rectangle, BorderExtender.createInstance(BorderExtender.BORDER_COPY));

            Tile cloudTopPressure = null;
            Tile cloudFlags = null;
            if (cloudProduct != null) {
                cloudTopPressure = getSourceTile(ctpProduct.getBand(LandsatConstants.LANDSAT_CTP_BAND_NAME), rectangle,
                        BorderExtender.createInstance(BorderExtender.BORDER_COPY));
                cloudFlags = getSourceTile(cloudProduct.getBand(CloudClassificationOp.CLOUD_FLAGS), rectangle,
                        BorderExtender.createInstance(BorderExtender.BORDER_COPY));
            }

            Tile[] transRvData = null;
            Tile[] transRsData = null;
            Tile[] tauRData = null;
            Tile[] sphAlbRData = null;
            if (exportRayCoeffs) {
                transRvData = OperatorUtils.getTargetTiles(targetTiles, transRvBands);
                transRsData = OperatorUtils.getTargetTiles(targetTiles, transRsBands);
                tauRData = OperatorUtils.getTargetTiles(targetTiles, tauRBands);
                sphAlbRData = OperatorUtils.getTargetTiles(targetTiles, sphAlbRBands);
            }
            Tile[] brr = OperatorUtils.getTargetTiles(targetTiles, brrBands);
            Tile[] rayleigh_refl = OperatorUtils.getTargetTiles(targetTiles, rayleighReflBands);
            Tile brrFlags = targetTiles.get(flagBand);

            boolean[][] do_corr = new boolean[SUBWIN_HEIGHT][SUBWIN_WIDTH];
            // rayleigh phase function coefficients, PR in DPM
            double[] phaseR = new double[3];
            // rayleigh optical thickness, tauR0 in DPM
            double[] tauR = new double[LandsatConstants.LANDSAT7_NUM_SPECTRAL_BANDS];
            // rayleigh reflectance, rhoR4x4 in DPM
            double[] rhoR = new double[LandsatConstants.LANDSAT7_NUM_SPECTRAL_BANDS];
            // rayleigh down transmittance, T_R_thetas_4x4
            double[] transRs = new double[LandsatConstants.LANDSAT7_NUM_SPECTRAL_BANDS];
            // rayleigh up transmittance, T_R_thetav_4x4
            double[] transRv = new double[LandsatConstants.LANDSAT7_NUM_SPECTRAL_BANDS];
            // rayleigh spherical albedo, SR_4x4
            double[] sphAlbR = new double[LandsatConstants.LANDSAT7_NUM_SPECTRAL_BANDS];

            for (int y = rectangle.y; y < rectangle.y + rectangle.height; y += Constants.SUBWIN_HEIGHT) {
                for (int x = rectangle.x; x < rectangle.x + rectangle.width; x += Constants.SUBWIN_WIDTH) {
                    final int xWinEnd = Math.min(rectangle.x + rectangle.width, x + Constants.SUBWIN_WIDTH) - 1;
                    final int yWinEnd = Math.min(rectangle.y + rectangle.height, y + Constants.SUBWIN_HEIGHT) - 1;
                    boolean correctPixel = false;

                    for (int iy = y; iy <= yWinEnd; iy++) {
                        for (int ix = x; ix <= xWinEnd; ix++) {
                            if (rhoNg[0].getSampleFloat(ix,
                                                        iy) != BAD_VALUE && (correctWater || isLandCons.getSampleBoolean(
                                    ix, iy))) {
                                correctPixel = true;
                                do_corr[iy - y][ix - x] = true;
                            } else {
                                do_corr[iy - y][ix - x] = false;
                                for (int bandId = 0; bandId < LandsatConstants.LANDSAT7_NUM_SPECTRAL_BANDS; bandId++) {
                                    brr[bandId].setSample(ix, iy, BAD_VALUE);
                                }
                            }
                        }
                    }

                    if (correctPixel) {
                        /* average downscaled, ozone for window DPM : just use corner pixel ! */
                        final double szaRad = szaTile.getSampleFloat(x, y) * MathUtils.DTOR;
                        final double vzaRad = vzaTile.getSampleFloat(x, y) * MathUtils.DTOR;
                        final double sins = Math.sin(szaRad);
                        final double sinv = Math.sin(vzaRad);
                        final double mus = Math.cos(szaRad);
                        final double muv = Math.cos(vzaRad);
                        final double deltaAzimuth = HelperFunctions.computeAzimuthDifference(
                                vaaTile.getSampleFloat(x, y), saaTile.getSampleFloat(x, y));
                        final double cosScattAngle = scattAngleTile.getSampleFloat(x, y);

                        /*
                        * 2. Rayleigh corrections (DPM section 7.3.3.3.2, step 2.6.15)
                        */
                        double press = HelperFunctions.correctEcmwfPressure((float) userPSurf,
                                                                            altitudeTile.getSampleFloat(x, y),
                                                                            auxData.press_scale_height); /* DPM #2.6.15.1-3 */
                        final double airMass = HelperFunctions.calculateAirMassMusMuv(muv, mus);

                        /* correct pressure in presence of clouds */
                        if (cloudProduct != null) {
                            final boolean isCloud = cloudFlags.getSampleBit(x, y, CloudClassificationOp.F_CLOUD);
                            if (isCloud) {
                                final double pressureCorrectionCloud = cloudTopPressure.getSampleDouble(x,
                                                                                                        y) / userPSurf;
                                press = press * pressureCorrectionCloud;
                            }
                        }

                        /* Rayleigh phase function Fourier decomposition */
                        rayleighCorrection.phase_rayleigh(mus, muv, sins, sinv, phaseR);

                        /* Rayleigh optical thickness */
                        for (int bandId = 0; bandId < LandsatConstants.LANDSAT7_NUM_SPECTRAL_BANDS; bandId++) {
                            tauR[bandId] = LandsatConstants.LANDSAT7_NOMINAL_RAYLEIGH_OPTICAL_THICKNESS[bandId];
                        }

                        /* Rayleigh reflectance*/
                        rayleighCorrection.ref_rayleigh(deltaAzimuth, szaTile.getSampleFloat(x, y),
                                                        vzaTile.getSampleFloat(x, y), mus, muv,
                                                        airMass, phaseR, tauR, rhoR);

                        /* Rayleigh transmittance */
                        rayleighCorrection.trans_rayleigh(mus, tauR, transRs);
                        rayleighCorrection.trans_rayleigh(muv, tauR, transRv);

                        /* Rayleigh spherical albedo */
                        rayleighCorrection.sphAlb_rayleigh(tauR, sphAlbR);

                        /* process each pixel */
                        for (int iy = y; iy <= yWinEnd; iy++) {
                            for (int ix = x; ix <= xWinEnd; ix++) {
                                for (int bandId = LandsatConstants.LANDSAT_RADIANCE_5_BAND_INDEX;
                                     bandId < LandsatConstants.LANDSAT7_NUM_SPECTRAL_BANDS; bandId++) {
                                    // apply single scattering approximation
                                    // ICOL D4 ATBD eqs. 26a-c
                                    rhoR[bandId] = 3.0 * tauR[bandId] * (1.0 + cosScattAngle * cosScattAngle) / (16.0 * mus * muv);
                                    transRs[bandId] = Math.exp(-tauR[bandId] / (2.0 * mus));
                                    transRv[bandId] = Math.exp(-tauR[bandId] / (2.0 * muv));
                                    sphAlbR[bandId] = tauR[bandId];
                                }
                                if (do_corr[iy - y][ix - x]) {
                                    /* Rayleigh correction for each pixel */
                                    rayleighCorrection.corr_rayleigh(rhoR, sphAlbR, transRs, transRv,
                                                                     rhoNg, brr, ix, iy); /*  (2.6.15.4) */

                                    /* flag negative Rayleigh-corrected reflectance */
                                    for (int bandId = 0; bandId < LandsatConstants.LANDSAT7_NUM_SPECTRAL_BANDS; bandId++) {
                                        rayleigh_refl[bandId].setSample(ix, iy, rhoR[bandId]);
                                        if (brr[bandId].getSampleFloat(ix, iy) <= 0.) {
                                            /* set annotation flag for reflectance product - v4.2 */
                                            brrFlags.setSample(ix, iy, bandId, true);
                                        }
                                    }
                                    if (exportRayCoeffs) {
                                        for (int bandId = 0; bandId < LandsatConstants.LANDSAT7_NUM_SPECTRAL_BANDS; bandId++) {
                                            transRvData[bandId].setSample(ix, iy, transRv[bandId]);
                                            transRsData[bandId].setSample(ix, iy, transRs[bandId]);
                                            tauRData[bandId].setSample(ix, iy, tauR[bandId]);
                                            sphAlbRData[bandId].setSample(ix, iy, sphAlbR[bandId]);
                                        }
                                    }
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


    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(EtmRayleighCorrectionOp.class);
        }
    }
}
