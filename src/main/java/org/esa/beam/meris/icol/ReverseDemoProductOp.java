/*
 * $Id: FresnelCoefficientOp.java,v 1.1 2007/03/27 12:51:41 marcoz Exp $
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

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.meris.brr.GaseousCorrectionOp;
import org.esa.beam.meris.icol.common.AdjacencyEffectMaskOp;
import org.esa.beam.util.ProductUtils;

import javax.media.jai.BorderExtender;
import java.awt.Rectangle;


/**
 * Created by marcoz.
 *
 * @author marcoz
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
public class ReverseDemoProductOp extends MerisBasisOp {

    @SourceProduct(alias="rhotoa")
    private Product rhoToaProduct;
    
    @SourceProduct(alias="gascor")
    private Product gasCorProduct;
    @SourceProduct(alias="ae_ray")
    private Product aeRayProduct;
    @SourceProduct(alias="ae_aerosol")
    private Product aeAerosolProduct;
    @SourceProduct(alias="aemask")
    private Product aemaskProduct;
    
    @TargetProduct
    private Product targetProduct;
    
    @Override
    public void initialize() throws OperatorException {
        targetProduct = createCompatibleProduct(rhoToaProduct, "MER", "MER_L1N");
        Band[] sourceBands = rhoToaProduct.getBands();
        for (Band srcBand : sourceBands) {
        	if (srcBand.getName().startsWith("rho_toa")) {
        		int bandNo = srcBand.getSpectralBandIndex()+1;
        		Band targetBand = targetProduct.addBand("rho_toa_AERC_" + bandNo, ProductData.TYPE_FLOAT32);
//        		ProductUtils.copySpectralAttributes(srcBand, targetBand);
                ProductUtils.copySpectralBandProperties(srcBand, targetBand);
        		targetBand.setNoDataValueUsed(srcBand.isNoDataValueUsed());
        		targetBand.setNoDataValue(srcBand.getNoDataValue());
        	}
		}
        for (Band srcBand : sourceBands) {
        	if (srcBand.getName().startsWith("rho_toa")) {
        		int bandNo = srcBand.getSpectralBandIndex()+1;
        		Band targetBand = targetProduct.addBand("rho_toa_AEAC_" + bandNo, ProductData.TYPE_FLOAT32);
//        		ProductUtils.copySpectralAttributes(srcBand, targetBand);
                ProductUtils.copySpectralBandProperties(srcBand, targetBand);
        		targetBand.setNoDataValueUsed(srcBand.isNoDataValueUsed());
        		targetBand.setNoDataValue(srcBand.getNoDataValue());
        	}
		}
    }
    
    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {

    	Rectangle rectangle = targetTile.getRectangle();
        pm.beginTask("Processing frame...", rectangle.height);
        try {
			int bandId = band.getSpectralBandIndex();
			int bandNumber = bandId + 1;
			if (bandId == -1) {
				System.out.println("-1 !!!!!!!!!!!!!!");
				return;
			} else if (bandId == 10|| bandId== 14) {
				Tile radianceR = getSourceTile(rhoToaProduct.getBand("rho_toa_" + bandNumber), rectangle,
                        BorderExtender.createInstance(BorderExtender.BORDER_COPY));
				for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
					for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
						targetTile.setSample(x, y, radianceR.getSampleDouble(x, y));
					}
					pm.worked(1);
				}
			} else {
				boolean totalCorrection = true;
				if (band.getName().startsWith("rho_toa_AERC_")) {
					totalCorrection = false;
				}
			
				Tile gasCor = getSourceTile(gasCorProduct.getBand(GaseousCorrectionOp.RHO_NG_BAND_PREFIX + "_" + bandNumber), rectangle,
                        BorderExtender.createInstance(BorderExtender.BORDER_COPY));
				Tile tg = getSourceTile(gasCorProduct.getBand(GaseousCorrectionOp.TG_BAND_PREFIX + "_" + bandNumber), rectangle,
                        BorderExtender.createInstance(BorderExtender.BORDER_COPY));
				
				Tile aeRayleigh = getSourceTile(aeRayProduct.getBand("rho_aeRay_"+bandNumber), rectangle,
                        BorderExtender.createInstance(BorderExtender.BORDER_COPY));
				Tile aeAerosol = getSourceTile(aeAerosolProduct.getBand("rho_aeAer_"+bandNumber), rectangle,
                        BorderExtender.createInstance(BorderExtender.BORDER_COPY));
			
				Tile aep = getSourceTile(aemaskProduct.getBand(AdjacencyEffectMaskOp.AE_MASK_AEROSOL), rectangle,
                        BorderExtender.createInstance(BorderExtender.BORDER_COPY));
				Tile rhoToaR = getSourceTile(rhoToaProduct.getBand("rho_toa_" +  bandNumber), rectangle,
                        BorderExtender.createInstance(BorderExtender.BORDER_COPY));

				for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
					for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
						double rhoToa = 0;
						double gasCorValue = gasCor.getSampleDouble(x, y);
						if (aep.getSampleInt(x, y) == 1 && gasCorValue != -1) {
							double aeRayleighValue = aeRayleigh.getSampleDouble(x, y);
							double aeAerosolValue = aeAerosol.getSampleDouble(x, y);
							double corrected;
							if (totalCorrection) {
								corrected = gasCorValue - aeRayleighValue - aeAerosolValue;
							} else {
								corrected = gasCorValue - aeRayleighValue;
							}
							if (corrected > 0) {
								double tgValue = tg.getSampleDouble(x, y);
								rhoToa = corrected * tgValue;
							}
						}
						if (rhoToa == 0) {
							rhoToa = rhoToaR.getSampleDouble(x, y);
						}
						targetTile.setSample(x, y, rhoToa);
					}
					pm.worked(1);
				}
			}
        } catch (Exception e) {
            throw new OperatorException(e);
        } finally {
            pm.done();
        }
    }


    public static class Spi extends OperatorSpi {
        public Spi() {
            super(ReverseDemoProductOp.class, "Meris.IcolReverseGas");
        }
    }
}
