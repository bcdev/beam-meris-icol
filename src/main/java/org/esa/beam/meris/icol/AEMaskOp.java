/*
 * $Id: AEMaskOp.java,v 1.5 2007/05/10 13:04:27 marcoz Exp $
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
import java.awt.geom.Area;

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
import org.esa.beam.framework.gpf.operators.common.BandArithmeticOp;
import org.esa.beam.framework.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.util.BitSetter;
import org.esa.beam.util.RectangleExtender;

import com.bc.ceres.core.ProgressMonitor;

@OperatorMetadata(alias = "Meris.AEMask",
        version = "1.0",
        internal = true,
        authors = "Marco Zühlke",
        copyright = "(c) 2007 by Brockmann Consult",
        description = "Adjacency mask computation.")
public class AEMaskOp extends MerisBasisOp {

    public static final String AE_MASK = "ae_mask";

    private static final int RR_WIDTH = 25;
    private static final int FR_WIDTH = 100;

    private RectangleExtender rectCalculator;
    private Rectangle relevantRect;
    private int aeWidth;
    private Band isLandBand;
    private Band isCoastlineBand;

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;
    @SourceProduct(alias = "land")
    private Product landProduct;
    @TargetProduct
    private Product targetProduct;
    @Parameter
    private String landExpression;


    @Override
    public void initialize() throws OperatorException {
        targetProduct = createCompatibleProduct(l1bProduct, "ae_mask_" + l1bProduct.getName(), "AEMASK");

        final String productType = l1bProduct.getProductType();
        if (productType.indexOf("_RR") > -1) {
            aeWidth = RR_WIDTH;
        } else {
            aeWidth = FR_WIDTH;
        }

        Band band = targetProduct.addBand(AE_MASK, ProductData.TYPE_INT8);

        FlagCoding flagCoding = createFlagCoding();
        band.setFlagCoding(flagCoding);
        targetProduct.addFlagCoding(flagCoding);
        
        BandArithmeticOp bandArithmeticOp1 = 
            BandArithmeticOp.createBooleanExpressionBand(landExpression, landProduct);
        isLandBand = bandArithmeticOp1.getTargetProduct().getBandAt(0);
        
        BandArithmeticOp bandArithmeticOp2 = 
            BandArithmeticOp.createBooleanExpressionBand("l1_flags.COASTLINE", l1bProduct);
        isCoastlineBand = bandArithmeticOp2.getTargetProduct().getBandAt(0);

        relevantRect = new Rectangle(aeWidth, aeWidth,
                                     l1bProduct.getSceneRasterWidth() - 2 * aeWidth,
                                     l1bProduct.getSceneRasterHeight() - 2 * aeWidth);
        rectCalculator = new RectangleExtender(new Rectangle(l1bProduct.getSceneRasterWidth(), l1bProduct.getSceneRasterHeight()), aeWidth, aeWidth);
        if (l1bProduct.getPreferredTileSize() != null) {
            targetProduct.setPreferredTileSize(l1bProduct.getPreferredTileSize());
        }
    }

    private FlagCoding createFlagCoding() {
        FlagCoding flagCoding = new FlagCoding(AE_MASK);
        flagCoding.addFlag("aep", BitSetter.setFlag(0, 0), null);
        return flagCoding;
    }

    @Override
    public void computeTile(Band band, Tile aeMask, ProgressMonitor pm) throws OperatorException {

        Rectangle targetRect = aeMask.getRectangle();
        Rectangle sourceRect = rectCalculator.extend(targetRect);
        Rectangle relevantTragetRect = targetRect.intersection(relevantRect);
        pm.beginTask("Processing frame...", sourceRect.height + relevantTragetRect.height);
        try {
            Tile isLand = getSourceTile(isLandBand, sourceRect, pm);
            Tile isCoastline = getSourceTile(isCoastlineBand, sourceRect, pm);

            Rectangle box = new Rectangle();
            Area costalArea = new Area();
            for (int y = sourceRect.y; y < sourceRect.y + sourceRect.height; y++) {
                for (int x = sourceRect.x; x < sourceRect.x + sourceRect.width; x++) {
                    if (isCoastline.getSampleBoolean(x, y)) {
                        box.setBounds(x - aeWidth, y - aeWidth, 2 * aeWidth, 2 * aeWidth);
                        Area area2 = new Area(box);
                        costalArea.add(area2);
                    }
                }
                pm.worked(1);
            }
            for (int y = relevantTragetRect.y; y < relevantTragetRect.y + relevantTragetRect.height; y++) {
                for (int x = relevantTragetRect.x; x < relevantTragetRect.x + relevantTragetRect.width; x++) {
                    if (!isLand.getSampleBoolean(x, y) && costalArea.contains(x, y)) {
                        aeMask.setSample(x, y, 1);
                    }
                }
                pm.worked(1);
            }
        } finally {
            pm.done();
        }
    }


    public static class Spi extends OperatorSpi {

        public Spi() {
            super(AEMaskOp.class);
        }
    }
}
