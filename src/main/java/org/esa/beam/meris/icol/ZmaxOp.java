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
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.meris.icol.meris.MerisAeMaskOp;
import org.esa.beam.meris.icol.meris.MerisCoastDistanceOp;
import org.esa.beam.meris.icol.utils.NavigationUtils;
import org.esa.beam.util.RectangleExtender;
import org.esa.beam.util.math.MathUtils;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.gpf.operators.standard.BandMathsOp;

import java.awt.Rectangle;


@OperatorMetadata(alias = "Meris.Zmax",
        version = "1.0",
        internal = true,
        authors = "Marco Zühlke",
        copyright = "(c) 2007 by Brockmann Consult",
        description = "Zmax computation.")
public class ZmaxOp extends MerisBasisOp {

    public static final String ZMAX = "zmax";

    private static final int SOURCE_EXTEND_RR = 80;
    private static final int SOURCE_EXTEND_FR = 320;

    private RectangleExtender rectCalculator;
    private int sourceExtend;
    private GeoCoding geoCoding;
    private Band isAemBand;
    private Band isLandBand;
    
    @SourceProduct(alias="l1b")
    private Product l1bProduct;
    @SourceProduct(alias = "land")
    private Product landProduct;
    @SourceProduct(alias="ae_mask")
    private Product aeMaskProduct;
    @SourceProduct(alias="coastDistance")
    private Product ldProduct;
    @TargetProduct
    private Product targetProduct;
    @Parameter
    private String landExpression;
    @Parameter
    private boolean correctOverLand;

    @Override
    public void initialize() throws OperatorException {
    	targetProduct = createCompatibleProduct(l1bProduct, "zmax_"+l1bProduct.getName(), "ZMAX");
        Band zmaxBand = targetProduct.addBand(ZMAX, ProductData.TYPE_FLOAT32);
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
        
        BandMathsOp bandArithmeticOp =
            BandMathsOp.createBooleanExpressionBand(MerisAeMaskOp.AE_MASK_RAYLEIGH + ".aep", aeMaskProduct);
        isAemBand = bandArithmeticOp.getTargetProduct().getBandAt(0);

        BandMathsOp bandArithmeticOp1 =
            BandMathsOp.createBooleanExpressionBand(landExpression, landProduct);
        isLandBand = bandArithmeticOp1.getTargetProduct().getBandAt(0);

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
        	Tile coastDistance = getSourceTile(ldProduct.getBand("coast_distance"), sourceRectangle, pm);
        	Tile isAEM = getSourceTile(isAemBand, sourceRectangle, pm);
            Tile isLand = getSourceTile(isLandBand, sourceRectangle, pm);
        	
            PixelPos pPix = new PixelPos();
            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                pPix.y = y;
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                    zmax.setSample(x, y, -1);
                    if (x == 150 && y == 200)
                    if (!correctOverLand && !isAEM.getSampleBoolean(x, y)) {
                        continue;
                    }
                    pPix.x = x;
                    double z0 = 0;
                    double z1 = 60000;
//                    double z;
                    boolean found = false;
                    final double azDiffRad = computeAzimuthDifferenceRad(vaa.getSampleFloat(x, y), saa.getSampleFloat(x, y));
                    int l = -1;
                    double lz = 0.0;
                    int p0x = -1;
                    int p0y = -1;
//                    do {
//                        z = (z0 + z1) / 2;
//                        final double pp0Length = z * Math.tan(vza.getSampleFloat(x, y) * MathUtils.DTOR);
//                        final GeoPos pGeo = geoCoding.getGeoPos(pPix, null);
//                        final GeoPos p0Geo = NavigationUtils.lineWithAngle(pGeo, pp0Length, azDiffRad);
//                        final PixelPos p0Pix = geoCoding.getPixelPos(p0Geo, null);
//
//                        if (sourceRectangle.contains(p0Pix)) {
//                            p0x = MathUtils.floorInt(p0Pix.x);
//                            p0y = MathUtils.floorInt(p0Pix.y);
//                            l = coastDistance.getSampleInt(p0x, p0y);
//                            if (l == -1) {
//                                z1 = z;
//                            } else {
//                                float szaValue = sza.getSampleFloat(p0x, p0y);
//                                if (isLand.getSampleBoolean(p0x, p0y)) {
//                                    szaValue += 180.0;
//                                }
//                                lz = Math.tan(szaValue * MathUtils.DTOR) * z;
//                                if (lz > l) {
//                                    z1 = z;
//                                } else {
//                                    z0 = z;
//                                    found = true;
//                                }
////                                z = l/Math.tan(szaValue * MathUtils.DTOR);
////                                found = true;
//                            }
//                        } else {
//                            z1 = z;
//                        }
//                        // todo: check results for off-nadir case - still looks strange
//                        if (x == 150 && y == 200)
//                            System.out.println(" " + p0x + "," + p0y + "//" + z0 + "," + z1 + "," + z + "," + l + "," + lz + "//" + found);
//                    } while ((z1 - z0) > 20);


                    double largeNumber = Double.MAX_VALUE;
                    double zMax = 30000.0;

                    final double pp0Length = 0.0;
                    final GeoPos pGeo = geoCoding.getGeoPos(pPix, null);
                    final GeoPos p0Geo = NavigationUtils.lineWithAngle(pGeo, pp0Length, azDiffRad);
                    final PixelPos p0Pix = geoCoding.getPixelPos(p0Geo, null);

                    if (sourceRectangle.contains(p0Pix)) {
                        p0x = MathUtils.floorInt(p0Pix.x);
                        p0y = MathUtils.floorInt(p0Pix.y);
                        float szaValue = sza.getSampleFloat(p0x, p0y);
                        l = coastDistance.getSampleInt(p0x, p0y);
                        if (l == MerisCoastDistanceOp.NO_DATA_VALUE) {
                            zmax.setSample(x, y, -1);
                        } else {
                            zMax = l / Math.tan(szaValue * MathUtils.DTOR);
                            zmax.setSample(x, y, (float) (zMax));
                        }
                    }

//                    for (int i=0; i<150; i++) {
//                         double z = i*200;
////                         final double pp0Length = z * Math.tan(vza.getSampleFloat(x, y) * MathUtils.DTOR);
//                         final double pp0Length = 0.0;
//                         final GeoPos pGeo = geoCoding.getGeoPos(pPix, null);
//                         final GeoPos p0Geo = NavigationUtils.lineWithAngle(pGeo, pp0Length, azDiffRad);
//                         final PixelPos p0Pix = geoCoding.getPixelPos(p0Geo, null);
//
//                         if (sourceRectangle.contains(p0Pix)) {
//                             p0x = MathUtils.floorInt(p0Pix.x);
//                             p0y = MathUtils.floorInt(p0Pix.y);
//                             float szaValue = sza.getSampleFloat(p0x, p0y);
//                             l = coastDistance.getSampleInt(p0x, p0y);
//                            lz = Math.tan(szaValue * MathUtils.DTOR) * z;
////                            zMax = l/Math.tan(szaValue * MathUtils.DTOR);
//                            if (Math.abs(l - lz) < largeNumber) {
//                                largeNumber = Math.abs(l - lz);
//                                if (largeNumber > 200) {
//                                    zMax = 30000;
//                                } else {
//                                    zMax = z;
//                                }
//                                found = true;
//                            }
//                         } else {
//
//                         }
//                         if (x == 200 && y == 250)
//                            System.out.println(" " + p0x + "," + p0y +  "," + z + "," + zMax + "," + l + "," + lz + "," + largeNumber);
//                     }

//                    if (found) {
//                        zmax.setSample(x, y, (float) (zMax));
//                    }
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
