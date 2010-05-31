/*
 * $Id: AeRayleighOp.java,v 1.5 2007/05/10 17:01:06 marcoz Exp $
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
package org.esa.beam.meris.icol;

import java.awt.Rectangle;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.util.Map;

import org.esa.beam.dataio.envisat.EnvisatConstants;
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
import org.esa.beam.framework.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.meris.brr.GaseousCorrectionOp;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.RectangleExtender;
import org.esa.beam.util.ResourceInstaller;
import org.esa.beam.util.SystemUtils;
import org.esa.beam.util.math.MathUtils;

import com.bc.ceres.core.NullProgressMonitor;
import com.bc.ceres.core.ProgressMonitor;


@OperatorMetadata(alias = "Meris.AERayleigh",
        version = "1.0",
        internal = true,
        authors = "Marco Zühlke",
        copyright = "(c) 2007 by Brockmann Consult",
        description = "Contribution of rayleigh to the adjacency effect.")
public class AeRayleighOp extends MerisBasisOp {

    private static final int NUM_BANDS = EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS - 2;
    private static final double HR = 8000; // Rayleigh scale height

    private RectangleExtender rectCalculator;
    private FresnelReflectionCoefficient fresnelCoefficient;
    private WeightedMeanCalculator meanCalculator;
    private CoeffW coeffW;
    private int sourceExtend;

    private double[][] w;

    private Band[] aeRayBands;
    private Band[] rhoAeRcBands;
    
    private Band[] fresnelDebugBands;
    private Band[] rayleighdebugBands;    

    @SourceProduct(alias="l1b")
    private Product l1bProduct;
    @SourceProduct(alias="aemask")
    private Product aemaskProduct;
    @SourceProduct(alias="ray1b")
    private Product ray1bProduct;
    @SourceProduct(alias="rhoNg")
    private Product gasCorProduct;
    @SourceProduct(alias="zmax")
    private Product zmaxProduct;
    @TargetProduct
    private Product targetProduct;
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

        File wFile = new File(auxdataTargetDir, CoeffW.FILENAME);
        Reader wReader = new FileReader(wFile);
        coeffW = new CoeffW(wReader);
    }

    private void createTargetProduct() {
        final String productType = l1bProduct.getProductType();
        if (productType.indexOf("_RR") > -1) {
            w = coeffW.getCoeffForRR();
            sourceExtend = 25;
        } else {
            w = coeffW.getCoeffForFR();
            sourceExtend = 100;
        }
        meanCalculator = new WeightedMeanCalculator(sourceExtend);

        targetProduct = createCompatibleProduct(l1bProduct, "ae_ray_" + l1bProduct.getName(), "MER_AE_RAY");
        aeRayBands = addBandGroup("rho_aeRay", l1bProduct, 0);
        rhoAeRcBands = addBandGroup("rho_ray_aerc", l1bProduct, -1);

        if (exportSeparateDebugBands) {
            rayleighdebugBands = addBandGroup("rho_aeRay_rayleigh", l1bProduct, -1);
            fresnelDebugBands = addBandGroup("rho_aeRay_fresnel", l1bProduct, -1);
        }
        
        rectCalculator = new RectangleExtender(new Rectangle(l1bProduct.getSceneRasterWidth(), l1bProduct.getSceneRasterHeight()), sourceExtend, sourceExtend);
        if (l1bProduct.getPreferredTileSize() != null) {
            targetProduct.setPreferredTileSize(l1bProduct.getPreferredTileSize());
        }
    }

    private Band[] addBandGroup(String prefix, Product srcProduct, double noDataValue) {
        Band[] bands = new Band[NUM_BANDS];
        int j = 0;
        for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            if (i == 10 || i == 14) {
                continue;
            }
            Band inBand = srcProduct.getBandAt(i);

            bands[j] = targetProduct.addBand(prefix + "_" + (i + 1), ProductData.TYPE_FLOAT32);
            ProductUtils.copySpectralAttributes(inBand, bands[j]);
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
            if (i == 10 || i == 14) {
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
    	
        Rectangle sourceRect = rectCalculator.extend(targetRect);
        pm.beginTask("Processing frame...", targetRect.height + 1);
        try {
        	// sources
        	Tile vza = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME), targetRect, pm);
            Tile zmax = getSourceTile(zmaxProduct.getBand("zmax"), targetRect, pm);
            Tile aep = getSourceTile(aemaskProduct.getBand(AEMaskOp.AE_MASK), targetRect, pm);

            Tile[] rhoNg = getTileGroup(gasCorProduct, GaseousCorrectionOp.RHO_NG_BAND_PREFIX, targetRect, pm);
            Tile[] transRup = getTileGroup(ray1bProduct, "transRv", targetRect, pm); //up
            Tile[] transRdown = getTileGroup(ray1bProduct, "transRs", targetRect, pm); //down
            Tile[] tauR = getTileGroup(ray1bProduct, "tauR", targetRect, pm);
            Tile[] sphAlbR = getTileGroup(ray1bProduct, "sphAlbR", targetRect, pm);
            
            Tile[] rhoAg = getTileGroup(ray1bProduct, "brr", sourceRect, pm);
            
            //targets
            Tile[] aeRayRaster = getTargetTiles(aeRayBands, targetTiles);
            Tile[] rhoAeRcRaster = getTargetTiles(rhoAeRcBands, targetTiles);

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
                        if(exportSeparateDebugBands) {
                            fresnelDebug[b].setSample(x, y, -1);
                            rayleighDebug[b].setSample(x, y, -1);
                        }
                    }
                    if (x == 123 && y == 141)
                        System.out.println("");
                    if (aep.getSampleInt(x, y) == 1 && rhoAg[0].getSampleFloat(x, y) != -1) {
                    	double[] means = meanCalculator.computeAll(x, y, rhoAg, w[0]);
                        final double muV = Math.cos(vza.getSampleFloat(x, y) * MathUtils.DTOR);
                        for (int b = 0; b < numBands; b++) {
                        	final double tmpRhoRayBracket = means[b];

                            //compute the rayleigh contribution to the AE
                            final float rhoAgValue = rhoAg[b].getSampleFloat(x, y);
                            final float transRupValue = transRup[b].getSampleFloat(x, y);
                            final float transRdownValue = transRdown[b].getSampleFloat(x, y);
                            final float sphAlbRValue = sphAlbR[b].getSampleFloat(x, y);
                            final float tauRValue = tauR[b].getSampleFloat(x, y);
                            final double aeRayRay = (transRupValue - Math
                        	        .exp(-tauRValue / muV))
                                    * (tmpRhoRayBracket - rhoAgValue) * (transRdownValue /
                                    (1d - tmpRhoRayBracket * sphAlbRValue));

                            //compute the additional contribution from the LFM
                            final double r1v = fresnelCoefficient.getCoeffFor(vza.getSampleFloat(x, y));
                            double aeRayFresnel = 0;
                            final float zmaxValue = zmax.getSampleFloat(x, y);
                            if (zmaxValue >= 0) {
                                aeRayFresnel = rhoNg[b].getSampleFloat(x, y) * r1v * Math.exp(-zmax.getSampleFloat(x, y) / HR);
                            }
                            
                            if(exportSeparateDebugBands) {
                                fresnelDebug[b].setSample(x, y, aeRayFresnel);
                                rayleighDebug[b].setSample(x, y, aeRayRay);
                            }
                            
                            final double aeRay = aeRayRay - aeRayFresnel;
                            aeRayRaster[b].setSample(x, y, aeRay);
                            //correct the top of aerosol reflectance for the AE_RAY effect
                            rhoAeRcRaster[b].setSample(x, y, rhoAg[b].getSampleFloat(x, y) - aeRay);
                        }
                    } else {
                    	for (int b = 0; b < numBands; b++) {
                    		rhoAeRcRaster[b].setSample(x, y, rhoAg[b].getSampleFloat(x, y));
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
            super(AeRayleighOp.class);
        }
    }
}
