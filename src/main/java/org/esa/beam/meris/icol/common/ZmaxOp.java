package org.esa.beam.meris.icol.common;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.core.SubProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
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
import org.esa.beam.meris.icol.utils.NavigationUtils;
import org.esa.beam.meris.icol.utils.OperatorUtils;
import org.esa.beam.util.math.MathUtils;

import java.awt.*;

/**
 * Operator providing Zmax for land or cloud contribution in AE correction.
 *
 * @author Olaf Danne
 */
@OperatorMetadata(alias = "Zmax",
                  version = "1.0",
                  internal = true,
                  authors = "Marco Zühlke, Olaf Danne",
                  copyright = "(c) 2007-2010 by Brockmann Consult",
                  description = "Zmax computation for land or cloud contribution in AE correction.")
public class ZmaxOp extends Operator {

    public static final String ZMAX = "zmax";

    private static final int NO_DATA_VALUE = -1;

    private Band isAemBand;
    private Band distanceBand;
    private double distanceNoDataValue;

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;
    @SourceProduct(alias = "ae_mask")
    private Product aeMaskProduct;
    @SourceProduct(alias = "distance")
    private Product distanceProduct;
    @TargetProduct
    private Product targetProduct;
    @Parameter(notEmpty = true)
    private String aeMaskExpression;
    @Parameter(notEmpty = true)
    private String distanceBandName;

    @Override
    public void initialize() throws OperatorException {
        targetProduct = OperatorUtils.createCompatibleProduct(l1bProduct, "zmax_" + l1bProduct.getName(), "ZMAX");
        Band zmaxBand = targetProduct.addBand(ZMAX, ProductData.TYPE_FLOAT32);
        zmaxBand.setNoDataValue(NO_DATA_VALUE);
        zmaxBand.setNoDataValueUsed(true);

        BandMathsOp bandArithmeticOp = BandMathsOp.createBooleanExpressionBand(aeMaskExpression, aeMaskProduct);
        isAemBand = bandArithmeticOp.getTargetProduct().getBandAt(0);
        distanceBand = distanceProduct.getBand(distanceBandName);
        distanceNoDataValue = distanceBand.getNoDataValue();
    }

    @Override
    public void computeTile(Band band, Tile zmax, ProgressMonitor pm) throws OperatorException {

        final Rectangle targetRect = zmax.getRectangle();
        pm.beginTask("Processing frame...", targetRect.height + 3);
        try {
            Tile sza = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME), targetRect, SubProgressMonitor.create(pm, 1));
            Tile cloudDistance = getSourceTile(distanceBand, targetRect, SubProgressMonitor.create(pm, 1));
            Tile isAeMask = getSourceTile(isAemBand, targetRect, SubProgressMonitor.create(pm, 1));

            Tile saa = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME), targetRect, pm);
            Tile vaa = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_AZIMUTH_DS_NAME), targetRect, pm);
            Tile vza = getSourceTile(l1bProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME), targetRect, pm);

            PixelPos pPix = new PixelPos();
            for (int y = targetRect.y; y < targetRect.y + targetRect.height; y++) {
                pPix.y = y;
                for (int x = targetRect.x; x < targetRect.x + targetRect.width; x++) {
                    pPix.x = x;
                    float zMaxValue = NO_DATA_VALUE;
                    if (isAeMask.getSampleBoolean(x, y)) {

                        boolean found = false;

//                        if (distanceBandName.equals(CoastDistanceOp.COAST_DISTANCE)) {
//                            double z0 = 0;
//                            double z1 = 60000;
//                            double z;
//                            final double azDiffRad = computeAzimuthDifferenceRad(vaa.getSampleFloat(x, y), saa.getSampleFloat(x, y));
//                            do {
//                                z = (z0 + z1) / 2;
//                                final double pp0Length = z * Math.tan(vza.getSampleFloat(x, y) * MathUtils.DTOR);
//                                final GeoPos pGeo = l1bProduct.getGeoCoding().getGeoPos(pPix, null);
//                                final GeoPos p0Geo = NavigationUtils.lineWithAngle(pGeo, pp0Length, azDiffRad);
//                                final PixelPos p0Pix = l1bProduct.getGeoCoding().getPixelPos(p0Geo, null);
//
//                                if (targetRect.contains(p0Pix)) {
//                                    int p0x = MathUtils.floorInt(p0Pix.x);
//                                    int p0y = MathUtils.floorInt(p0Pix.y);
//                                    final int l = cloudDistance.getSampleInt(p0x, p0y);
//                                    if (l == -1) {
//                                        z1 = z;
//                                    } else {
//                                        final double lz = Math.tan(sza.getSampleFloat(p0x, p0y) * MathUtils.DTOR) * z;
//                                        if (lz > l) {
//                                            z1 = z;
//                                        } else {
//                                            z0 = z;
//                                            found = true;
//                                        }
//                                    }
//                                } else {
//                                    z1 = z;
//                                }
//                                zMaxValue = (float) z;
//                            } while ((z1 - z0) > 200);
//                        }

                        if (!found) {
                            int distance = cloudDistance.getSampleInt(x, y);
                            if (distance != distanceNoDataValue) {
                                float szaValue = sza.getSampleFloat(x, y);
                                zMaxValue = (float) (distance / Math.tan(szaValue * MathUtils.DTOR));
                            }
                        }
                    }
                    zmax.setSample(x, y, zMaxValue);
                }
            }
            checkForCancelation(pm);
            pm.worked(1);
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