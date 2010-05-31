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

import java.awt.Rectangle;

import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.framework.gpf.operators.common.BandArithmeticOp;
import org.esa.beam.framework.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.util.RectangleExtender;
import org.esa.beam.util.math.MathUtils;

import com.bc.ceres.core.ProgressMonitor;


@OperatorMetadata(alias = "Meris.Zmax",
        version = "1.0",
        internal = true,
        authors = "Marco Zühlke",
        copyright = "(c) 2007 by Brockmann Consult",
        description = "Zmax computation.")
public class ZmaxOp extends MerisBasisOp {

    private static final int SOURCE_EXTEND_RR = 80;
    private static final int SOURCE_EXTEND_FR = 320;

    private RectangleExtender rectCalculator;
    private int sourceExtend;
    private GeoCoding geoCoding;
    private Band isAemBand;
    
    @SourceProduct(alias="l1b")
    private Product l1bProduct;
    @SourceProduct(alias="ae_mask")
    private Product aeMaskProduct;
    @SourceProduct(alias="landDistance")
    private Product ldProduct;
    @TargetProduct
    private Product targetProduct;

    @Override
    public void initialize() throws OperatorException {
    	targetProduct = createCompatibleProduct(l1bProduct, "zmax_"+l1bProduct.getName(), "ZMAX");
        Band zmaxBand = targetProduct.addBand("zmax", ProductData.TYPE_FLOAT32);
        zmaxBand.setNoDataValue(-1);
        zmaxBand.setNoDataValueUsed(true);

        final String productType = l1bProduct.getProductType();
        if (productType.indexOf("_RR") > -1) {
            sourceExtend = SOURCE_EXTEND_RR;
        } else {
            sourceExtend = SOURCE_EXTEND_FR;
        }
        geoCoding = l1bProduct.getGeoCoding();
        rectCalculator = new RectangleExtender(new Rectangle(l1bProduct.getSceneRasterWidth(), l1bProduct.getSceneRasterHeight()), sourceExtend, sourceExtend);
        
        BandArithmeticOp bandArithmeticOp = 
            BandArithmeticOp.createBooleanExpressionBand("ae_mask.aep", aeMaskProduct);
        isAemBand = bandArithmeticOp.getTargetProduct().getBandAt(0);
        if (l1bProduct.getPreferredTileSize() != null) {
            targetProduct.setPreferredTileSize(l1bProduct.getPreferredTileSize());
        }
    }
    
    @Override
    public void computeTile(Band band, Tile zmax, ProgressMonitor pm) throws OperatorException {
    	
    	final Rectangle targetRectangle = zmax.getRectangle();
        final Rectangle sourceRectangle = rectCalculator.extend(targetRectangle);
        final int size = targetRectangle.height * targetRectangle.width;
        pm.beginTask("Processing frame...", size + 1);
        try {
        	Tile saa = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME), sourceRectangle, pm);
        	Tile sza = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME), sourceRectangle, pm);
        	Tile vaa = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_AZIMUTH_DS_NAME), sourceRectangle, pm);
        	Tile vza = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME), sourceRectangle, pm);
        	Tile landDistance = getSourceTile(ldProduct.getBand("land_distance"), sourceRectangle, pm);
        	Tile isAEM = getSourceTile(isAemBand, sourceRectangle, pm);
        	
            PixelPos pPix = new PixelPos();
            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                pPix.y = y;
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                    zmax.setSample(x, y, -1);
                    if (!isAEM.getSampleBoolean(x, y)) {
                        continue;
                    }
                    pPix.x = x;
                    double z0 = 0;
                    double z1 = 60000;
                    double z;
                    boolean found = false;
                    final double azDiffRad = computeAzimuthDifferenceRad(vaa.getSampleFloat(x, y), saa.getSampleFloat(x, y));
                    do {
                        z = (z0 + z1) / 2;
                        final double pp0Length = z * Math.tan(vza.getSampleFloat(x, y) * MathUtils.DTOR);
                        final GeoPos pGeo = geoCoding.getGeoPos(pPix, null);
                        final GeoPos p0Geo = NavigationUtils.lineWithAngle(pGeo, pp0Length, azDiffRad);
                        final PixelPos p0Pix = geoCoding.getPixelPos(p0Geo, null);

                        if (sourceRectangle.contains(p0Pix)) {
                            int p0x = MathUtils.floorInt(p0Pix.x);
                            int p0y = MathUtils.floorInt(p0Pix.y);
                            final int l = landDistance.getSampleInt(p0x, p0y);
                            if (l == -1) {
                                z1 = z;
                            } else {
                                final double lz = Math.tan(sza.getSampleFloat(p0x, p0y) * MathUtils.DTOR) * z;
                                if (lz > l) {
                                    z1 = z;
                                } else {
                                    z0 = z;
                                    found = true;
                                }
                            }
                        } else {
                            z1 = z;
                        }
                    } while ((z1 - z0) > 200);

                    if (found) {
                        zmax.setSample(x, y, (float)z);
                    }
                }
            }
        } finally {
            pm.done();
        }
    }

    private double computeAzimuthDifferenceRad(final double viewAzimuth,
                                               final double sunAzimuth) {
        return Math.acos(Math.cos(MathUtils.DTOR * (viewAzimuth - sunAzimuth)));
    }


    public static class Spi extends OperatorSpi {
        public Spi() {
            super(ZmaxOp.class);
        }
    }
}
