/*
 * $Id: LandDistanceOp.java,v 1.6 2007/05/10 13:04:27 marcoz Exp $
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
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.framework.gpf.operators.common.BandArithmeticOp;
import org.esa.beam.framework.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.util.RectangleExtender;
import org.esa.beam.util.ShapeRasterizer.BresenhamLineRasterizer;
import org.esa.beam.util.ShapeRasterizer.LinePixelVisitor;
import org.esa.beam.util.ShapeRasterizer.LineRasterizer;
import org.esa.beam.util.math.MathUtils;

import com.bc.ceres.core.ProgressMonitor;


@OperatorMetadata(alias = "Meris.LandDistance",
        version = "1.0",
        internal = true,
        authors = "Marco Zühlke",
        copyright = "(c) 2007 by Brockmann Consult",
        description = "Land distance computation.")
public class LandDistanceOp extends MerisBasisOp {

    public static final String LAND_DISTANCE = "land_distance";
    public static final int NO_DATA_VALUE = -1;

    private static final int MAX_LINE_LENGTH = 100000;
    private static final int SOURCE_EXTEND_RR = 80; //TODO
    private static final int SOURCE_EXTEND_FR = 320; //TODO

    private RectangleExtender rectCalculator;
    private int sourceExtend;
    private GeoCoding geocoding;
    private Band isLandBand;

    @SourceProduct(alias="l1b")
    private Product l1bProduct;
    @SourceProduct(alias="land")
    private Product landProduct;
    @TargetProduct
    private Product targetProduct;
    @Parameter
    private String landExpression;

    @Override
    public void initialize() throws OperatorException {
    	targetProduct = createCompatibleProduct(l1bProduct, "land_distance_"+l1bProduct.getName(), "LANDD");

        final String productType = l1bProduct.getProductType();
        if (productType.indexOf("_RR") > -1) {
            sourceExtend = SOURCE_EXTEND_RR;
        } else {
            sourceExtend = SOURCE_EXTEND_FR;
        }

        Band band = targetProduct.addBand(LAND_DISTANCE, ProductData.TYPE_INT32);
        band.setNoDataValue(NO_DATA_VALUE);
        band.setNoDataValueUsed(true);

        geocoding = l1bProduct.getGeoCoding();
        rectCalculator = new RectangleExtender(new Rectangle(l1bProduct.getSceneRasterWidth(), l1bProduct.getSceneRasterHeight()), sourceExtend, sourceExtend);
        
        BandArithmeticOp bandArithmeticOp = 
            BandArithmeticOp.createBooleanExpressionBand(landExpression, landProduct);
        isLandBand = bandArithmeticOp.getTargetProduct().getBandAt(0);
        if (l1bProduct.getPreferredTileSize() != null) {
            targetProduct.setPreferredTileSize(l1bProduct.getPreferredTileSize());
        }
    }
    
    @Override
    public void computeTile(Band band, Tile landDistance, ProgressMonitor pm) throws OperatorException {

    	Rectangle targetRectangle = landDistance.getRectangle();
        Rectangle sourceRectangle = rectCalculator.extend(targetRectangle);
        pm.beginTask("Processing frame...", targetRectangle.height);
        try {

        	Tile saa = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME), sourceRectangle, pm);
        	Tile isLand = getSourceTile(isLandBand, sourceRectangle, pm);

            PixelPos startPix = new PixelPos();
            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                startPix.y = y;
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                    if (!isLand.getSampleBoolean(x, y)) {
                        startPix.x = x;

                        int trialLineLength = MAX_LINE_LENGTH;
                        PixelPos lineEndPix;
                        do {
                            final GeoPos startGeoPos = geocoding.getGeoPos(startPix, null);
                            final GeoPos lineEndGeoPos = NavigationUtils.lineWithAngle(startGeoPos, 
                            		trialLineLength, saa.getSampleDouble(x, y) * MathUtils.DTOR + Math.PI);
                            lineEndPix = geocoding.getPixelPos(lineEndGeoPos, null);
                            if (lineEndPix.x == -1 || lineEndPix.y == -1) {
                                trialLineLength -= 10000;
                            } else {
                                trialLineLength = 0;
                            }
                        } while (trialLineLength > 0);
                        
                        if (lineEndPix.x == -1 || lineEndPix.y == -1) {
                            landDistance.setSample(x, y, NO_DATA_VALUE);
                        } else {
                            final PixelPos landPix = findFirstLandPix(startPix, lineEndPix, isLand);
                            if (landPix != null) {
                            	landDistance.setSample(x, y, (int) NavigationUtils.distanceInMeters(geocoding, startPix, landPix));
                            } else {
                            	landDistance.setSample(x, y, NO_DATA_VALUE);
                            }
                        }
                    } else {
                    	landDistance.setSample(x, y, NO_DATA_VALUE);
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

    private PixelPos findFirstLandPix(final PixelPos startPixel, final PixelPos endPixel, final Tile isLand) {
        LineRasterizer lineRasterizer = new BresenhamLineRasterizer();
        final PixelPos[] landPixs = new PixelPos[1];
        landPixs[0] = null;
        final Rectangle isLandRect = isLand.getRectangle();
        LinePixelVisitor visitor = new LinePixelVisitor() {

            public void visit(int x, int y) {
                if (landPixs[0] == null &&
                        isLandRect.contains(x, y) &&
                        isLand.getSampleBoolean(x, y)) {
                    landPixs[0] = new PixelPos(x, y);
                }
            }
        };

        lineRasterizer.rasterize(MathUtils.floorInt(startPixel.x),
                                 MathUtils.floorInt(startPixel.y),
                                 MathUtils.floorInt(endPixel.x),
                                 MathUtils.floorInt(endPixel.y),
                                 visitor);
        return landPixs[0];
    }


    public static class Spi extends OperatorSpi {
        public Spi() {
            super(LandDistanceOp.class);
        }
    }
}
