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

import java.awt.Rectangle;

import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.framework.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.meris.brr.GaseousCorrectionOp;
import org.esa.beam.meris.l2auxdata.L2AuxData;
import org.esa.beam.meris.l2auxdata.L2AuxdataProvider;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.math.MathUtils;

import com.bc.ceres.core.ProgressMonitor;


/**
 * Created by marcoz.
 *
 * @author marcoz
 * @version $Revision: 1.1 $ $Date: 2007/03/27 12:51:41 $
 */
public class ReverseOp extends MerisBasisOp  {

    private transient L2AuxData auxData;
    
    @SourceProduct(alias="l1b")
    private Product l1bProduct;
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
        try {
            auxData = L2AuxdataProvider.getInstance().getAuxdata(l1bProduct);
        } catch (Exception e) {
            throw new OperatorException("could not load L2Auxdata", e);
        }

        targetProduct = createCompatibleProduct(l1bProduct, "MER", "MER_L1N");
        Band[] sourceBands = l1bProduct.getBands();
        for (Band srcBand : sourceBands) {
        	if (srcBand.getName().startsWith("radiance")) {
        		ProductUtils.copyBand(srcBand.getName(), l1bProduct, targetProduct);
        	}
		}
    }
    
    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {

    	Rectangle rectangle = targetTile.getRectangle();
        pm.beginTask("Processing frame...", rectangle.height);
        try {
			int bandId = band.getSpectralBandIndex();
			if (bandId == -1) {
				return;
			} else if (bandId == 10|| bandId== 14) {
				Tile radianceR = getSourceTile(l1bProduct.getBand("radiance_" +  (bandId + 1)), rectangle, pm);
				for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
					for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
						targetTile.setSample(x, y, radianceR.getSampleDouble(x, y));
					}
					pm.worked(1);
				}
			} else {
				int bandNumber = bandId + 1;
			
				Tile gasCor = getSourceTile(gasCorProduct.getBand(GaseousCorrectionOp.RHO_NG_BAND_PREFIX + "_" + bandNumber), rectangle, pm);
				Tile tg = getSourceTile(gasCorProduct.getBand(GaseousCorrectionOp.TG_BAND_PREFIX + "_" + bandNumber), rectangle, pm);
				
				Tile aeRayleigh = getSourceTile(aeRayProduct.getBand("rho_aeRay_"+bandNumber), rectangle, pm);
				Tile aeAerosol = getSourceTile(aeAerosolProduct.getBand("rho_aeAer_"+bandNumber), rectangle, pm);
			
				Tile aep = getSourceTile(aemaskProduct.getBand(AEMaskOp.AE_MASK), rectangle, pm);
				Tile sza = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME), rectangle, pm);
				Tile detectorIndex = getSourceTile(l1bProduct.getBand(EnvisatConstants.MERIS_DETECTOR_INDEX_DS_NAME), rectangle, pm);
				Tile radianceR = getSourceTile(l1bProduct.getBand("radiance_" +  bandNumber), rectangle, pm);

				for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
					for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
						double radiance = 0;
						double gasCorValue = gasCor.getSampleDouble(x, y);
						if (aep.getSampleInt(x, y) == 1 && gasCorValue != -1) {
							double aeRayleighValue = aeRayleigh.getSampleDouble(x, y);
							double aeAerosolValue = aeAerosol.getSampleDouble(x, y);
							double corrected = gasCorValue - aeRayleighValue - aeAerosolValue;
							if (corrected > 0) {
								double tgValue = tg.getSampleDouble(x, y);
								double rhoToa = corrected * tgValue;
							
								radiance = (rhoToa * Math.cos(sza.getSampleDouble(x, y) * MathUtils.DTOR) * 
										auxData.detector_solar_irradiance[bandNumber-1][detectorIndex.getSampleInt(x, y)]) /
										(Math.PI * auxData.seasonal_factor);
							}
						}
						if (radiance == 0) {
							radiance = radianceR.getSampleDouble(x, y);
						}
						targetTile.setSample(x, y, radiance);
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
            super(ReverseOp.class, "Meris.IcolReverse");
        }
    }
}
