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
package org.esa.beam.meris.icol.common;

import com.bc.ceres.core.NullProgressMonitor;
import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.standard.BandMathsOp;
import org.esa.beam.meris.brr.CloudClassificationOp;
import org.esa.beam.meris.brr.GaseousCorrectionOp;
import org.esa.beam.meris.brr.LandClassificationOp;
import org.esa.beam.meris.icol.CoeffW;
import org.esa.beam.meris.icol.FresnelReflectionCoefficient;
import org.esa.beam.meris.icol.IcolConstants;
import org.esa.beam.meris.icol.IcolConvolutionAlgo;
import org.esa.beam.meris.icol.IcolConvolutionJaiConvolve;
import org.esa.beam.meris.icol.IcolConvolutionKernellLoop;
import org.esa.beam.meris.icol.Instrument;
import org.esa.beam.meris.icol.meris.CloudLandMaskOp;
import org.esa.beam.meris.icol.utils.IcolUtils;
import org.esa.beam.meris.icol.utils.OperatorUtils;
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


/**
 * Operator for Rayleigh part of AE correction.
 *
 * @author Marco Zuehlke, Olaf Danne
 */
@OperatorMetadata(alias = "AERayleigh",
                  version = "2.9.5",
                  internal = true,
                  authors = "Marco Zuehlke, Olaf Danne",
                  copyright = "(c) 2010 by Brockmann Consult",
                  description = "Contribution of rayleigh to the adjacency effect.")
public class AdjacencyEffectRayleighOp extends Operator {

    private static final double NO_DATA_VALUE = -1.0;
    private static final double HR = 8000; // Rayleigh scale height

    private FresnelReflectionCoefficient fresnelCoefficient;
    private CoeffW coeffW;
    IcolConvolutionAlgo icolConvolutionAlgo;
    IcolConvolutionAlgo lcFlagConvAlgo;

    private Band[] aeRayBands;
    private Band[] rhoAeRcBands;
    private Band[] rhoAgBracketBands;        // additional output for RS

    private Band[] fresnelDebugBands;
    private Band[] rayleighdebugBands;

    private Band isLandBand;
    private Band isCloudBand;

    private Band landFlagConvBand;
    private Band cloudFlagConvBand;

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;
    @SourceProduct(alias = "land")
    private Product landProduct;
    @SourceProduct(alias = "aemask")
    private Product aemaskProduct;
    @SourceProduct(alias = "ray1b")
    private Product ray1bProduct;
    @SourceProduct(alias = "ray1bconv", optional = true)
    private Product ray1bconvProduct;
    @SourceProduct(alias = "rhoNg")
    private Product gasCorProduct;
    @SourceProduct(alias = "zmax")
    private Product zmaxProduct;
    @SourceProduct(alias = "cloud")
    private Product cloudProduct;
    @SourceProduct(alias = "zmaxCloud")
    private Product zmaxCloudProduct;
    @SourceProduct(alias = "cloudLandMask")
    private Product cloudLandMaskProduct;

    @TargetProduct
    private Product targetProduct;

    @Parameter
    private Instrument instrument;
    @Parameter
    private String landExpression;
    @Parameter
    private String cloudExpression;
    @Parameter(defaultValue = "true")
    private boolean openclConvolution = true;
    @Parameter(defaultValue = "true")
    private boolean reshapedConvolution;
    @Parameter
    private boolean exportSeparateDebugBands = false;


    @Override
    public void initialize() throws OperatorException {
        try {
            loadFresnelReflectionCoefficient();
        } catch (IOException e) {
            throw new OperatorException(e);
        }
        createTargetProduct();

        BandMathsOp bandArithmeticLandOp =
                BandMathsOp.createBooleanExpressionBand(landExpression, landProduct);
        isLandBand = bandArithmeticLandOp.getTargetProduct().getBandAt(0);

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
            icolConvolutionAlgo = new IcolConvolutionJaiConvolve(ray1bProduct, productType, coeffW, "brr_", 1,
                                                                 instrument.numSpectralBands,
                                                                 instrument.bandsToSkip);
            lcFlagConvAlgo = new IcolConvolutionJaiConvolve(cloudLandMaskProduct, productType, coeffW, "lcflag_", 1, 2, null);
        } else {
            icolConvolutionAlgo = new IcolConvolutionKernellLoop(l1bProduct, coeffW, IcolConstants.AE_CORRECTION_MODE_RAYLEIGH);
            lcFlagConvAlgo = new IcolConvolutionKernellLoop(l1bProduct, coeffW, IcolConstants.AE_CORRECTION_MODE_RAYLEIGH);
        }

        targetProduct = OperatorUtils.createCompatibleProduct(l1bProduct, "ae_ray_" + l1bProduct.getName(),
                                                              "MER_AE_RAY");
        aeRayBands = addBandGroup("rho_aeRay");
        rhoAeRcBands = addBandGroup("rho_ray_aerc");
        rhoAgBracketBands = addBandGroup("rho_ag_bracket");

        if (exportSeparateDebugBands) {
            rayleighdebugBands = addBandGroup("rho_aeRay_rayleigh");
            fresnelDebugBands = addBandGroup("rho_aeRay_fresnel");
        }

        landFlagConvBand = targetProduct.addBand("land_flag_ray_conv", ProductData.TYPE_FLOAT32);
        cloudFlagConvBand = targetProduct.addBand("cloud_flag_ray_conv", ProductData.TYPE_FLOAT32);
    }

    private Band[] addBandGroup(String prefix) {
        return OperatorUtils.addBandGroup(l1bProduct, instrument.numSpectralBands, instrument.bandsToSkip,
                                          targetProduct, prefix, NO_DATA_VALUE, false);
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRect, ProgressMonitor pm) throws
                                                                                                        OperatorException {

        Rectangle sourceRect = icolConvolutionAlgo.mapTargetRect(targetRect);
        pm.beginTask("Processing frame...", targetRect.height + 1);
        try {
            // sources
            Tile isLand = getSourceTile(cloudLandMaskProduct.getBand(CloudLandMaskOp.LAND_MASK_NAME), targetRect,
                                        BorderExtender.createInstance(BorderExtender.BORDER_COPY));
            Tile isCloud = getSourceTile(cloudLandMaskProduct.getBand(CloudLandMaskOp.CLOUD_MASK_NAME), targetRect,
                                         BorderExtender.createInstance(BorderExtender.BORDER_COPY));

            Tile sza = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME), targetRect,
                                     BorderExtender.createInstance(BorderExtender.BORDER_COPY));
            Tile vza = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME), targetRect,
                                     BorderExtender.createInstance(BorderExtender.BORDER_COPY));
            Tile[] zmaxs = ZmaxOp.getSourceTiles(this, zmaxProduct, targetRect, pm);
            Tile zmaxCloud = ZmaxOp.getSourceTile(this, zmaxCloudProduct, targetRect);
            Tile aep = getSourceTile(aemaskProduct.getBand(AdjacencyEffectMaskOp.AE_MASK_RAYLEIGH), targetRect,
                                     BorderExtender.createInstance(BorderExtender.BORDER_COPY));
            Tile cloudFlags = getSourceTile(cloudProduct.getBand(CloudClassificationOp.CLOUD_FLAGS), targetRect,
                                            BorderExtender.createInstance(BorderExtender.BORDER_COPY));
            Tile landFlags = getSourceTile(landProduct.getBand(LandClassificationOp.LAND_FLAGS), targetRect,
                                           BorderExtender.createInstance(BorderExtender.BORDER_COPY));

            Tile[] rhoNg = OperatorUtils.getSourceTiles(this, gasCorProduct, GaseousCorrectionOp.RHO_NG_BAND_PREFIX,
                                                        instrument, targetRect);
            Tile[] transRup = OperatorUtils.getSourceTiles(this, ray1bProduct, "transRv", instrument, targetRect
            ); //up
            Tile[] transRdown = OperatorUtils.getSourceTiles(this, ray1bProduct, "transRs", instrument, targetRect
            ); //down
            Tile[] tauR = OperatorUtils.getSourceTiles(this, ray1bProduct, "tauR", instrument, targetRect);
            Tile[] sphAlbR = OperatorUtils.getSourceTiles(this, ray1bProduct, "sphAlbR", instrument, targetRect);

            Tile[] rhoAg = OperatorUtils.getSourceTiles(this, ray1bProduct, "brr", instrument, sourceRect);
            Tile[] rhoAgConv = null;
            if (openclConvolution && ray1bconvProduct != null) {
                rhoAgConv = OperatorUtils.getSourceTiles(this, ray1bconvProduct, "brr_conv", instrument, sourceRect
                );
            }
            final IcolConvolutionAlgo.Convolver convolver = icolConvolutionAlgo.createConvolver(this, rhoAg, targetRect, pm);
            final IcolConvolutionAlgo.Convolver lcFlagConvolver =
                    lcFlagConvAlgo.createConvolver(this, new Tile[]{isLand, isCloud}, targetRect, pm);

            //targets
            Tile[] aeRayTiles = OperatorUtils.getTargetTiles(targetTiles, aeRayBands);
            Tile[] rhoAeRcTiles = OperatorUtils.getTargetTiles(targetTiles, rhoAeRcBands);
            Tile[] rhoAgBracket = null;
            final boolean isRSAdditionalOutputBands = System.getProperty("additionalOutputBands") != null &&
                                                      System.getProperty("additionalOutputBands").equals("RS");
            if (isRSAdditionalOutputBands) {
                rhoAgBracket = OperatorUtils.getTargetTiles(targetTiles, rhoAgBracketBands);
            }

            Tile lfConvTile = targetTiles.get(landFlagConvBand);
            Tile cfConvTile = targetTiles.get(cloudFlagConvBand);

            Tile[] rayleighDebug = null;
            Tile[] fresnelDebug = null;
            if (exportSeparateDebugBands) {
                rayleighDebug = OperatorUtils.getTargetTiles(targetTiles, rayleighdebugBands);
                fresnelDebug = OperatorUtils.getTargetTiles(targetTiles, fresnelDebugBands);
            }

            final int numBands = rhoNg.length;
            for (int y = targetRect.y; y < targetRect.y + targetRect.height; y++) {
                for (int x = targetRect.x; x < targetRect.x + targetRect.width; x++) {
                    if (exportSeparateDebugBands) {
                        for (int b = 0; b < numBands; b++) {
                            if (!IcolUtils.isIndexToSkip(b, instrument.bandsToSkip)) {
                                fresnelDebug[b].setSample(x, y, -1);
                                rayleighDebug[b].setSample(x, y, -1);
                            }
                        }
                    }

                    double lfConv;
                    double cfConv;
                    if (reshapedConvolution) {
                        lfConv = lcFlagConvolver.convolveSample(x, y, 1, 0);
                        cfConv = lcFlagConvolver.convolveSample(x, y, 1, 1);
                    } else {
                        lfConv = lcFlagConvolver.convolveSampleBoolean(x, y, 1, 0);
                        cfConv = lcFlagConvolver.convolveSampleBoolean(x, y, 1, 1);
                    }
                    lfConvTile.setSample(x, y, lfConv);
                    cfConvTile.setSample(x, y, cfConv);

                    boolean cloudy = cloudFlags.getSampleBit(x, y, CloudClassificationOp.F_CLOUD);
                    boolean icy = landFlags.getSampleBit(x, y, LandClassificationOp.F_ICE);
                    final boolean cloudfreeOrIce = !cloudy || icy;
                    if (aep.getSampleInt(x, y) == 1 && cloudfreeOrIce && rhoAg[0].getSampleFloat(x, y) != -1) {
                        double[] means = new double[numBands];
                        if (rhoAgConv == null) {
                            means = convolver.convolvePixel(x, y, 1);
                        }

                        final double muV = Math.cos(vza.getSampleFloat(x, y) * MathUtils.DTOR);

                        //compute the additional molecular contribution from the LFM  - ICOL+ ATBD eq. (10)
                        final double zmaxPart = ZmaxOp.computeZmaxPart(zmaxs, x, y, HR);
                        final double zmaxCloudPart = ZmaxOp.computeZmaxPart(zmaxCloud, x, y, HR);

                        final double r1v = fresnelCoefficient.getCoeffFor(sza.getSampleFloat(x, y));
                        final boolean isLandPixel = isLand.getSampleBoolean(x, y);

                        for (int b = 0; b < numBands; b++) {
                            if (!IcolUtils.isIndexToSkip(b, instrument.bandsToSkip)) {
                                final double tmpRhoRayBracket;
                                if (rhoAgConv != null) {
                                    tmpRhoRayBracket = rhoAgConv[b].getSampleFloat(x, y);
                                } else {
                                    tmpRhoRayBracket = means[b];
                                }
                                // rayleigh contribution without AE (tmpRhoRayBracket)

                                // over water, compute the rayleigh contribution to the AE
                                float rhoAgValue = rhoAg[b].getSampleFloat(x, y);
                                float transRupValue = transRup[b].getSampleFloat(x, y);
                                float tauRValue = tauR[b].getSampleFloat(x, y);
                                float transRdownValue = transRdown[b].getSampleFloat(x, y);
                                float sphAlbValue = sphAlbR[b].getSampleFloat(x, y);

                                double aeRayRay = (transRupValue - Math
                                        .exp(-tauRValue / muV))
                                                  * (tmpRhoRayBracket - rhoAgValue) * (transRdownValue /
                                                                                       (1d - tmpRhoRayBracket * sphAlbValue));

                                double aeRayFresnelLand = 0.0d;
                                if (zmaxPart != 0) {
                                    aeRayFresnelLand = rhoNg[b].getSampleFloat(x, y) * r1v * zmaxPart;
                                    if (isLandPixel) {
                                        // contribution must be subtracted over land - ICOL+ ATBD section 4.2
                                        aeRayFresnelLand *= -1.0;
                                    }
                                }
                                double aeRayFresnelCloud = 0.0d;
                                if (zmaxCloudPart != 0) {
                                    aeRayFresnelCloud = rhoNg[b].getSampleFloat(x, y) * r1v * zmaxCloudPart;
                                }

                                if (exportSeparateDebugBands) {
                                    fresnelDebug[b].setSample(x, y, aeRayFresnelLand + aeRayFresnelCloud);
                                    rayleighDebug[b].setSample(x, y, aeRayRay);
                                }

                                final double aeRay = aeRayRay - aeRayFresnelLand - aeRayFresnelCloud;

                                aeRayTiles[b].setSample(x, y, aeRay);
                                //correct the top of aerosol reflectance for the AE_RAY effect
                                rhoAeRcTiles[b].setSample(x, y, rhoAgValue - aeRay);
                                if (isRSAdditionalOutputBands) {
                                    rhoAgBracket[b].setSample(x, y, tmpRhoRayBracket);
                                }
                            }
                        }
                    } else {
                        for (int b = 0; b < numBands; b++) {
                            if (!IcolUtils.isIndexToSkip(b, instrument.bandsToSkip)) {
                                rhoAeRcTiles[b].setSample(x, y, rhoAg[b].getSampleFloat(x, y));
                                if (isRSAdditionalOutputBands) {
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
            super(AdjacencyEffectRayleighOp.class);
        }
    }
}
