/*
 * $Id: ZmaxOp.java,v 1.4 2007/05/10 13:04:27 marcoz Exp $
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
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.gpf.operators.standard.BandMathsOp;
import org.esa.beam.meris.icol.meris.MerisAeMaskOp;
import org.esa.beam.meris.icol.meris.MerisCoastDistanceOp;
import org.esa.beam.util.math.MathUtils;

import java.awt.Rectangle;


@OperatorMetadata(alias = "Meris.Zmax",
        version = "1.0",
        internal = true,
        authors = "Marco Zühlke",
        copyright = "(c) 2007 by Brockmann Consult",
        description = "Zmax computation.")
public class ZmaxOp extends MerisBasisOp {

    public static final String ZMAX = "zmax";

    private Band isAemBand;

    @SourceProduct(alias="l1b")
    private Product l1bProduct;
    @SourceProduct(alias="ae_mask")
    private Product aeMaskProduct;
    @SourceProduct(alias="coastDistance")
    private Product ldProduct;
    @TargetProduct
    private Product targetProduct;
    @Parameter
    private boolean correctOverLand;

    @Override
    public void initialize() throws OperatorException {
    	targetProduct = createCompatibleProduct(l1bProduct, "zmax_"+l1bProduct.getName(), "ZMAX");
        Band zmaxBand = targetProduct.addBand(ZMAX, ProductData.TYPE_FLOAT32);
        zmaxBand.setNoDataValue(-1);
        zmaxBand.setNoDataValueUsed(true);

//        BandMathsOp bandArithmeticOp = BandMathsOp.createBooleanExpressionBand(MerisAeMaskOp.AE_MASK_RAYLEIGH + ".aep", aeMaskProduct);
        BandMathsOp bandArithmeticOp;
        if (correctOverLand) {
            bandArithmeticOp = BandMathsOp.createBooleanExpressionBand("true", aeMaskProduct);
        } else {
            bandArithmeticOp = BandMathsOp.createBooleanExpressionBand(MerisAeMaskOp.AE_MASK_RAYLEIGH + ".aep", aeMaskProduct);
        }
        isAemBand = bandArithmeticOp.getTargetProduct().getBandAt(0);
    }
    
    @Override
    public void computeTile(Band band, Tile zmax, ProgressMonitor pm) throws OperatorException {
    	
    	final Rectangle targetRectangle = zmax.getRectangle();
        final int size = targetRectangle.height * targetRectangle.width;
        pm.beginTask("Processing frame...", size + 1);
        try {
        	Tile sza = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME), targetRectangle, pm);
        	Tile coastDistance = getSourceTile(ldProduct.getBand("coast_distance"), targetRectangle, pm);
        	Tile isAEM = getSourceTile(isAemBand, targetRectangle, pm);

            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                    double zMaxValue = -1;
                    if (isAEM.getSampleBoolean(x, y)) {
                        int l = coastDistance.getSampleInt(x, y);
                        if (l != MerisCoastDistanceOp.NO_DATA_VALUE) {
                            float szaValue = sza.getSampleFloat(x, y);
                            zMaxValue = l / Math.tan(szaValue * MathUtils.DTOR);
                        }
                    }
                    zmax.setSample(x, y, (float) (zMaxValue));
                }
            }
        } finally {
            pm.done();
        }
    }


    public static class Spi extends OperatorSpi {
        public Spi() {
            super(ZmaxOp.class);
        }
    }
}
