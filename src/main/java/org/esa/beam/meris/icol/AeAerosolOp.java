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
import org.esa.beam.framework.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.meris.icol.AerosolScatteringFuntions.RV;
import org.esa.beam.meris.l2auxdata.Constants;
import org.esa.beam.util.BitSetter;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.RectangleExtender;
import org.esa.beam.util.ResourceInstaller;
import org.esa.beam.util.SystemUtils;
import org.esa.beam.util.math.MathUtils;

import com.bc.ceres.core.NullProgressMonitor;
import com.bc.ceres.core.ProgressMonitor;


@OperatorMetadata(alias = "Meris.AEAerosol",
        version = "1.0",
        internal = true,
        authors = "Marco Zühlke",
        copyright = "(c) 2007 by Brockmann Consult",
        description = "Contribution of aerosol to the adjacency effect.")
public class AeAerosolOp extends MerisBasisOp {

    public static final String AOT_FLAGS = "aot_flags";

    //vertical scale height
    private static final double HA = 3000;

	@SourceProduct(alias="l1b")
    private Product l1bProduct;
	@SourceProduct(alias="aemask")
    private Product aemaskProduct;
	@SourceProduct(alias="zmax")
    private Product zmaxProduct;
	@SourceProduct(alias = "input")
    private Product sourceProduct;
	
    @TargetProduct
    private Product targetProduct;
    
    @Parameter(defaultValue="false", description="export the aerosol and fresnel correction term as bands")
    private boolean exportSeparateDebugBands = false;
    @Parameter(defaultValue="false")
    private boolean useUserAlphaAndAot = false;
    @Parameter(interval="[-2.1, -0.4]", defaultValue="-1")
    private double userAlpha;
    @Parameter(interval="[0, 1.5]", defaultValue="0")
    private double userAot;
    
	private int sourceExtend;
	private RectangleExtender rectCalculator;
	private Band flagBand;
	
	private Band alphaBand;
	private Band aotBand;
	
	private Band[] aeAerBands;
	private Band[] rhoAeAcBands;
	
	private Band[] fresnelDebugBands;
	private Band[] aerosolDebugBands;
	
	private CoeffW coeffW;
	private double[][] w;
	private AerosolScatteringFuntions aerosolScatteringFuntions;
	private FresnelReflectionCoefficient fresnelCoefficient;
	private WeightedMeanCalculator meanCalculator;
    
	@Override
    public void initialize() throws OperatorException {
		targetProduct = createCompatibleProduct(sourceProduct, "ae_" + sourceProduct.getName(), "AE");

        flagBand = targetProduct.addBand(AOT_FLAGS, ProductData.TYPE_UINT8);
        FlagCoding flagCoding = createFlagCoding();
        flagBand.setFlagCoding(flagCoding);
        targetProduct.addFlagCoding(flagCoding);
        
        alphaBand = targetProduct.addBand("alpha", ProductData.TYPE_FLOAT32);
        aotBand = targetProduct.addBand("aot", ProductData.TYPE_FLOAT32);
        
        rhoAeAcBands = addBandGroup("rho_ray_aeac", -1);
        aeAerBands = addBandGroup("rho_aeAer", 0);
        
        if (exportSeparateDebugBands) {
            aerosolDebugBands = addBandGroup("rho_aeAer_aerosol", -1);
            fresnelDebugBands = addBandGroup("rho_aeAer_fresnel", -1);
        }
        
        try {
            loadAuxData();
        } catch (IOException e) {
            throw new OperatorException(e);
        }
        
        final String productType = l1bProduct.getProductType();
        if (productType.indexOf("_RR") > -1) {
            w = coeffW.getCoeffForRR();
            sourceExtend = 25;
        } else {
            w = coeffW.getCoeffForFR();
            sourceExtend = 100;
        }
        meanCalculator = new WeightedMeanCalculator(sourceExtend);
        
        rectCalculator = new RectangleExtender(new Rectangle(l1bProduct.getSceneRasterWidth(), l1bProduct.getSceneRasterHeight()), sourceExtend, sourceExtend);
        aerosolScatteringFuntions = new AerosolScatteringFuntions();
        if (l1bProduct.getPreferredTileSize() != null) {
            targetProduct.setPreferredTileSize(l1bProduct.getPreferredTileSize());
        }
	}

	private Band[] addBandGroup(String prefix, double noDataValue) {
		Band[] bands = new Band[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];
        for (int i = 0; i < bands.length; i++) {
            if (i == 10 || i == 14) {
                continue;
            }
            Band inBand = l1bProduct.getBandAt(i);
            bands[i] = targetProduct.addBand(prefix + "_" + (i + 1), ProductData.TYPE_FLOAT32);
            ProductUtils.copySpectralAttributes(inBand, bands[i]);
            bands[i].setNoDataValueUsed(true);
            bands[i].setNoDataValue(noDataValue);
        }
        return bands;
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
        
        File wFile = new File(auxdataTargetDir, CoeffW.FILENAME);
        Reader wReader = new FileReader(wFile);
        coeffW = new CoeffW(wReader);
    }
	
	private Tile[] getTargetTileGroup(Map<Band, Tile> targetTiles, Band[] bands) {
	    Tile[] tiles = new Tile[bands.length];
	    for (int i = 0; i < bands.length; i++) {
	        if (i == 10 || i == 14) {
	            continue;
	        }
	        tiles[i] = targetTiles.get(bands[i]);
	    }
	    return tiles;
	}

	
	@Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRect, ProgressMonitor pm) throws OperatorException {
		Rectangle sourceRect = rectCalculator.extend(targetRect);
		
		Tile vza = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME), targetRect, pm);
		Tile sza = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME), targetRect, pm);
		Tile vaa = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_AZIMUTH_DS_NAME), targetRect, pm);
		Tile saa = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME), targetRect, pm);
		
		Tile zmax = getSourceTile(zmaxProduct.getBand("zmax"), targetRect, pm);
		Tile aep = getSourceTile(aemaskProduct.getBand(AEMaskOp.AE_MASK), targetRect, pm);
		
		final Tile[] rhoRaec = new Tile[EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS];
        for (int i = 0; i < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; i++) {
            if (i == 10 || i == 14) {
                continue;
            }
            rhoRaec[i] = getSourceTile(sourceProduct.getBand("rho_ray_aerc_" + (i + 1)), sourceRect, pm);
        }
		
		Tile flagTile = targetTiles.get(flagBand);
		
		Tile aotTile = targetTiles.get(aotBand);
		Tile alphaTile = targetTiles.get(alphaBand);
		
		Tile[] rhoAeAcRaster = getTargetTileGroup(targetTiles, rhoAeAcBands);
		Tile[] aeAerRaster = getTargetTileGroup(targetTiles, aeAerBands);
		
		Tile[] aerosolDebug = null;
		Tile[] fresnelDebug = null;
		if (exportSeparateDebugBands) {
		    aerosolDebug = getTargetTileGroup(targetTiles, aerosolDebugBands);
		    fresnelDebug = getTargetTileGroup(targetTiles, fresnelDebugBands);
		}
		
		try {
		for (int y = targetRect.y; y < targetRect.y + targetRect.height; y++) {
            for (int x = targetRect.x; x < targetRect.x + targetRect.width; x++) {
            	final double rho_13 = rhoRaec[Constants.bb865].getSampleFloat(x, y);
                if (aep.getSampleInt(x, y) == 1 && rho_13 != -1) {
                    double alpha;
                    if (useUserAlphaAndAot) {
                        alpha = userAlpha;
                    } else {
                        //Aerosols type determination
                        final double epsilon = rhoRaec[Constants.bb775].getSampleDouble(x, y)/rho_13;
                        alpha = Math.log(epsilon)/Math.log(778.0/865.0);
                    }
    				int iaer = (int) (Math.round(-(alpha * 10.0))+5);
    				if (iaer < 1) {
    					iaer = 1;
    					flagTile.setSample(x, y, 1);
    				} else if (iaer > 26) {
    					iaer = 26;
    					flagTile.setSample(x, y, 1);
    				}
    				alphaTile.setSample(x, y, alpha);
    				
    				//compute <RO_AER> at 865 nm
    				final double roAer865Mean = meanCalculator.compute(x, y, rhoRaec[Constants.bb865], w[iaer-1]);
    				
                    //retrieve ROAG at 865 nm with two bounded AOTs
                    final double r1v = fresnelCoefficient.getCoeffFor(vza.getSampleFloat(x, y));
                    final double r1s = fresnelCoefficient.getCoeffFor(sza.getSampleFloat(x, y));
                    
                    float phi = saa.getSampleFloat(x, y) - vaa.getSampleFloat(x, y);
                    double mus = Math.cos(sza.getSampleFloat(x, y) * MathUtils.DTOR);
                    double nus = Math.sin(sza.getSampleFloat(x, y) * MathUtils.DTOR);
                    double muv = Math.cos(vza.getSampleFloat(x, y) * MathUtils.DTOR);
                    double nuv = Math.sin(vza.getSampleFloat(x, y) * MathUtils.DTOR);
                    
                    //compute the back scattering angle 
                    double csb = mus * muv+ nus * nuv * Math.cos(phi * MathUtils.DTOR);
                    double thetab = Math.acos(-csb) * MathUtils.RTOD;
                    //compute the forward scattering angle                    
                    double csf = mus * muv - nus * nuv * Math.cos(phi * MathUtils.DTOR);
                    double thetaf = Math.acos(csf) * MathUtils.RTOD;
                    
                    double pab = aerosolScatteringFuntions.aerosolPhase(thetab, iaer);
					final double tauaConst = 0.1 * Math.pow((550.0/865.0), (iaer/10.0));
					double paerFB = aerosolScatteringFuntions.aerosolPhaseFB(thetaf, thetab, iaer);
					
					double zmaxPart = 0.0; 
					if (zmax.getSampleFloat(x, y) >= 0) {
					    zmaxPart = Math.exp(-zmax.getSampleFloat(x, y) / HA);
					}
					
					int searchIAOT = -1;
					double aot = 0;
					double[] roAGTemp = new double[17];
					if (useUserAlphaAndAot) {
					    aot = userAot;
					    searchIAOT = MathUtils.floorInt(aot * 10);
					    for (int iiaot = searchIAOT; iiaot <= searchIAOT+1; iiaot++) {
                            final double taua = tauaConst * iiaot;
                            RV rv = aerosolScatteringFuntions.aerosol_f(taua, iaer, pab, sza.getSampleFloat(x, y), vza.getSampleFloat(x, y), phi);
                            final double res = rv.rhoa *(1.0 + paerFB * ( r1v + r1s *(1.0 - zmaxPart)));
                            roAGTemp[iiaot] = res + roAer865Mean * rv.tds * (rv.tus - Math.exp(-taua/muv));
                        }

					} else {
					    for (int iiaot = 1; iiaot<=16 && searchIAOT == -1; iiaot++) {
					        final double taua = tauaConst * iiaot;
					        RV rv = aerosolScatteringFuntions.aerosol_f(taua, iaer, pab, sza.getSampleFloat(x, y), vza.getSampleFloat(x, y), phi);
					        final double res = rv.rhoa *(1.0 + paerFB * ( r1v + r1s *(1.0 - zmaxPart)));
					        roAGTemp[iiaot] = res + roAer865Mean * rv.tds * (rv.tus - Math.exp(-taua/muv));
						
					        if (roAGTemp[iiaot] > rho_13) {
					            searchIAOT = iiaot-1;
					        }
					    }
                    
					    if (searchIAOT != -1) {
					        aot = aerosolScatteringFuntions.interpolateLin(roAGTemp[searchIAOT], searchIAOT, 
                    			roAGTemp[searchIAOT+1], searchIAOT+1, rho_13) * 0.1;
					    } else {
					        flagTile.setSample(x, y, flagTile.getSampleInt(x, y) + 2);
					    }
					}
					aotTile.setSample(x, y, aot);
					
                    //Correct from AE with AEROSOLS
                    for (int iwvl = 0; iwvl < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; iwvl++) {
                    	if (iwvl == 10 || iwvl == 14) {
                    		continue;
                    	}
                    	if (exportSeparateDebugBands) {
                    	    aerosolDebug[iwvl].setSample(x, y, -1);
                    	    fresnelDebug[iwvl].setSample(x, y, -1);
                    	}
                    	if (searchIAOT != -1) {
                    		// TODO don't rcompute 13
                    		final double roAerMean = meanCalculator.compute(x, y, rhoRaec[iwvl], w[iaer-1]);
                    		float rhoRaecIwvl = rhoRaec[iwvl].getSampleFloat(x, y);
                    		Band band = (Band)rhoRaec[iwvl].getRasterDataNode();
                    		float wvl = band.getSpectralWavelength();
                    	
                    		//Compute the aerosols functions for the first aot
                    		final double taua1 = 0.1 * searchIAOT * Math.pow((550.0/wvl), (iaer/10.0));
                    		RV rv1 = aerosolScatteringFuntions.aerosol_f(taua1, iaer, pab, sza.getSampleFloat(x, y), vza.getSampleFloat(x, y), phi);

                    	    double aerosol1 = (roAerMean - rhoRaecIwvl) * (rv1.tds /(1.0 - roAerMean * rv1.sa));
                    	    aerosol1 = (rv1.tus - Math.exp(-taua1/muv)) * aerosol1;
                    	    final double fresnel1 = rv1.rhoa * paerFB * r1s * zmaxPart; 
                    		final double aea1 = aerosol1 - fresnel1;
           
                    		if (exportSeparateDebugBands) {
                    		    aerosolDebug[iwvl].setSample(x, y, aerosol1);
                    		    fresnelDebug[iwvl].setSample(x, y, fresnel1);
                    		} 

                    		//Compute the aerosols functions for the second aot
                    		final double taua2 = 0.1 * (searchIAOT+1) * Math.pow((550.0/wvl), (iaer/10.0));
                    		RV rv2 = aerosolScatteringFuntions.aerosol_f(taua2, iaer, pab, sza.getSampleFloat(x, y), vza.getSampleFloat(x, y), phi);
                    	
                    		double aea2 = (roAerMean - rhoRaecIwvl) * (rv2.tds /(1.0 - roAerMean * rv2.sa));
                    		aea2 = (rv2.tus - Math.exp(-taua2/muv)) * aea2;
                    		aea2 = aea2 - rv2.rhoa * paerFB * r1s * zmaxPart;
                        
                    		//AOT INTERPOLATION to get AE_aer
                    		final double aea = aerosolScatteringFuntions.interpolateLin(roAGTemp[searchIAOT], aea1, 
                    				roAGTemp[searchIAOT+1], aea2, rho_13);
                    		
                    		
                    		aeAerRaster[iwvl].setSample(x, y, aea);
                    		rhoAeAcRaster[iwvl].setSample(x, y, rhoRaecIwvl - (float) aea);
                    	} else {
                    		rhoAeAcRaster[iwvl].setSample(x, y, rhoRaec[iwvl].getSampleFloat(x, y));
                    	}
                    }
                } else {
                	for (int iwvl = 0; iwvl < EnvisatConstants.MERIS_L1B_NUM_SPECTRAL_BANDS; iwvl++) {
                    	if (iwvl == 10 || iwvl == 14) {
                    		continue;
                    	}
                    	rhoAeAcRaster[iwvl].setSample(x, y, rhoRaec[iwvl].getSampleFloat(x, y));
                	}
                }
            }
		}
		}catch (IOException e) {
			throw new OperatorException(e);
		}
	}

	public static class Spi extends OperatorSpi {

        public Spi() {
            super(AeAerosolOp.class);
        }
    }
}
