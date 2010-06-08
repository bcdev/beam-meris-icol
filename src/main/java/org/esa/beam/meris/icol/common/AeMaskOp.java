/*
 * $Id: AeMaskOp.java,v 1.5 2007/05/10 13:04:27 marcoz Exp $
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

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
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
import org.esa.beam.meris.icol.AeArea;
import org.esa.beam.meris.icol.IcolConstants;
import org.esa.beam.meris.icol.utils.LandsatUtils;
import org.esa.beam.meris.icol.utils.OperatorUtils;
import org.esa.beam.util.BitSetter;
import org.esa.beam.util.RectangleExtender;

import java.awt.*;
import java.awt.geom.Area;

/**
 * Operator for computation of the mask to be used for AE correction.
 *
 * @author Marco Zuehlke, Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
@OperatorMetadata(alias = "AEMask",
        version = "1.0",
        internal = true,
        authors = "Marco Zühlke, Olaf Danne",
        copyright = "(c) 2010 by Brockmann Consult",
        description = "Adjacency mask computation.")
public class AeMaskOp extends Operator {

    public static final String AE_MASK_RAYLEIGH = "ae_mask_rayleigh";
    public static final String AE_MASK_AEROSOL = "ae_mask_aerosol";

    private static final int RR_WIDTH = 25;
    private static final int FR_WIDTH = 100;

    private RectangleExtender rectCalculator;
    private Rectangle relevantRect;
    private int aeWidth;
    private Band isLandBand;
    private Band isCoastlineBand;

    @SourceProduct(alias = "source")
    private Product sourceProduct;
    @SourceProduct(alias = "land")
    private Product landProduct;

    @TargetProduct
    private Product targetProduct;

    @Parameter
    private String landExpression;
    @Parameter
    private String coastlineExpression;

    @Parameter(defaultValue = "COSTAL_OCEAN", valueSet = {"COSTAL_OCEAN", "OCEAN", "COSTAL_ZONE", "EVERYWHERE"})
    private AeArea aeArea;
    @Parameter
    private boolean reshapedConvolution;
    @Parameter
    private int correctionMode;


    @Override
    public void initialize() throws OperatorException {
        targetProduct = OperatorUtils.createCompatibleProduct(sourceProduct, "ae_mask_" + sourceProduct.getName(), "AEMASK");

        Band maskBand = null;
        double sourceExtendReduction = 1.0;
        if (correctionMode == IcolConstants.AE_CORRECTION_MODE_RAYLEIGH) {
            // Rayleigh
            if (reshapedConvolution) {
                sourceExtendReduction = (double) IcolConstants.DEFAULT_AE_DISTANCE / (double) IcolConstants.RAYLEIGH_AE_DISTANCE;
            } else {
                sourceExtendReduction = 1.0;
            }
            maskBand = targetProduct.addBand(AE_MASK_RAYLEIGH, ProductData.TYPE_INT8);
        } else if (correctionMode == IcolConstants.AE_CORRECTION_MODE_AEROSOL) {
            // aerosol
            if (reshapedConvolution) {
                sourceExtendReduction = (double) IcolConstants.DEFAULT_AE_DISTANCE / (double) IcolConstants.AEROSOL_AE_DISTANCE;
            } else {
                sourceExtendReduction = 1.0;
            }
            maskBand = targetProduct.addBand(AE_MASK_AEROSOL, ProductData.TYPE_INT8);
        }

        final String productType = sourceProduct.getProductType();
        if (productType.indexOf("_RR") > -1) {
            aeWidth = (int) (RR_WIDTH/sourceExtendReduction);
        } else {
            aeWidth = (int) (FR_WIDTH/sourceExtendReduction);
        }

        FlagCoding flagCoding = createFlagCoding();
        maskBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);

        BandMathsOp bandArithmeticOp1 =
            BandMathsOp.createBooleanExpressionBand(landExpression, landProduct);
        isLandBand = bandArithmeticOp1.getTargetProduct().getBandAt(0);

        if (coastlineExpression != null && !coastlineExpression.isEmpty()) {
            BandMathsOp bandArithmeticOp2 =
                BandMathsOp.createBooleanExpressionBand(coastlineExpression, sourceProduct);
            isCoastlineBand = bandArithmeticOp2.getTargetProduct().getBandAt(0);
        }

        // todo: the following works for nested convolution - check if this is sufficient as 'edge processing' (proposal 3.2.1.5)
        if (reshapedConvolution) {
            relevantRect = new Rectangle(0, 0,
                                         sourceProduct.getSceneRasterWidth(),
                                         sourceProduct.getSceneRasterHeight());
        } else {
             // (AE algorithm is not applied in this case)
            if (sourceProduct.getSceneRasterWidth() - 2 * aeWidth < 0 ||
                    sourceProduct.getSceneRasterHeight() - 2 * aeWidth < 0) {
                throw new OperatorException("Product is too small to apply AE correction - must be at least " +
                        2 * aeWidth + "x" + 2 * aeWidth + " pixel.");
            }
            relevantRect = new Rectangle(aeWidth, aeWidth,
                                         sourceProduct.getSceneRasterWidth() - 2 * aeWidth,
                                         sourceProduct.getSceneRasterHeight() - 2 * aeWidth);
        }
        rectCalculator = new RectangleExtender(new Rectangle(sourceProduct.getSceneRasterWidth(), sourceProduct.getSceneRasterHeight()), aeWidth, aeWidth);
    }

    private FlagCoding createFlagCoding() {
        FlagCoding flagCoding = null;
        if (correctionMode == IcolConstants.AE_CORRECTION_MODE_RAYLEIGH) {
            flagCoding = new FlagCoding(AE_MASK_RAYLEIGH);
        } else if (correctionMode == IcolConstants.AE_CORRECTION_MODE_AEROSOL) {
            flagCoding = new FlagCoding(AE_MASK_AEROSOL);
        }
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
            Tile isCoastline = null;
            if (isCoastlineBand != null) {
                isCoastline = getSourceTile(isCoastlineBand, sourceRect, pm);
            }
            Tile sza = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME), sourceRect, pm);

            Rectangle box = new Rectangle();
            Area costalArea = new Area();
            for (int y = sourceRect.y; y < sourceRect.y + sourceRect.height; y++) {
                for (int x = sourceRect.x; x < sourceRect.x + sourceRect.width; x++) {
                    if (isCoastline(isLand, isCoastline, x, y)) {
                        box.setBounds(x - aeWidth, y - aeWidth, 2 * aeWidth, 2 * aeWidth);
                        Area area2 = new Area(box);
                        costalArea.add(area2);
                    }
                }
                pm.worked(1);
            }
            boolean correctOverLand = aeArea.correctOverLand();
            boolean correctInCoastalAreas = aeArea.correctCostalArea();
            // todo: over land, apply AE algorithm everywhere except for cloud pixels.
            // even if land pixel is far away from water
            for (int y = relevantTragetRect.y; y < relevantTragetRect.y + relevantTragetRect.height; y++) {
                for (int x = relevantTragetRect.x; x < relevantTragetRect.x + relevantTragetRect.width; x++) {
                    if (Math.abs(sza.getSampleFloat(x, y)) > 80.0) {
                        // we do not correct AE for sun zeniths > 80 deg because of limitation in aerosol scattering
                        // functions (PM4, 2010/03/04)
                        aeMask.setSample(x, y, 0);
                    } else {
                        // if 'correctOverLand',  compute for both ocean and land ...
                        if (correctOverLand) {
                            // if 'correctInCoastalAreas',  check if pixel is in coastal area...
                            if (correctInCoastalAreas && !costalArea.contains(x, y)) {
                                aeMask.setSample(x, y, 0);
                            } else {
                                aeMask.setSample(x, y, 1);
                            }
                        } else {
                             if (isLand.getSampleBoolean(x, y) ||
                                (correctInCoastalAreas && !costalArea.contains(x, y))) {
                                aeMask.setSample(x, y, 0);
                            } else {
                                aeMask.setSample(x, y, 1);
                            }
                        }
                    }
                }
                pm.worked(1);
            }
        } finally {
            pm.done();
        }
    }

    private boolean isCoastline(Tile isLandTile, Tile coastline, int x, int y) {
        if (coastline != null) {
            return coastline.getSampleBoolean(x, y);
        }
        if (LandsatUtils.isCoordinatesOutOfBounds(x-1, y-1, isLandTile) ||
            LandsatUtils.isCoordinatesOutOfBounds(x+1, y+1, isLandTile)) {
            return false;
        }
         // use this: isLandTile.getRectangle() !!!
        boolean check1 = (isLandTile.getSampleBoolean(x-1, y) && !isLandTile.getSampleBoolean(x+1, y)) ||
                (isLandTile.getSampleBoolean(x+1, y) && !isLandTile.getSampleBoolean(x-1, y));

        boolean check2 = (isLandTile.getSampleBoolean(x, y-1) && !isLandTile.getSampleBoolean(x, y+1)) ||
                (isLandTile.getSampleBoolean(x, y+1) && !isLandTile.getSampleBoolean(x, y-1));

        boolean check3 = (isLandTile.getSampleBoolean(x-1, y-1) && !isLandTile.getSampleBoolean(x+1, y+1)) ||
                (isLandTile.getSampleBoolean(x+1, y+1) && !isLandTile.getSampleBoolean(x-1, y-1));

        boolean check4 = (isLandTile.getSampleBoolean(x+1, y-1) && !isLandTile.getSampleBoolean(x-1, y+1)) ||
                (isLandTile.getSampleBoolean(x-1, y+1) && !isLandTile.getSampleBoolean(x+1, y-1));

        return (check1 || check2 || check3 || check4);
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(AeMaskOp.class);
        }
    }
}