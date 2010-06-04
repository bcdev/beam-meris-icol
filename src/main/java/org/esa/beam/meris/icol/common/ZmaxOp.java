package org.esa.beam.meris.icol.common;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.core.SubProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.GeoCoding;
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
import org.esa.beam.util.RectangleExtender;
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
    private static final int SOURCE_EXTEND_RR = 80;
    private static final int SOURCE_EXTEND_FR = 320;

    private Band isAemBand;
    private Band distanceBand;
    private double distanceNoDataValue;
    private GeoCoding geoCoding;
    private RectangleExtender rectCalculator;

    @SourceProduct(alias = "source")
    private Product sourceProduct;
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
        targetProduct = OperatorUtils.createCompatibleProduct(sourceProduct, "zmax_" + sourceProduct.getName(), "ZMAX");
        Band zmaxBand = targetProduct.addBand(ZMAX, ProductData.TYPE_FLOAT32);
        zmaxBand.setNoDataValue(NO_DATA_VALUE);
        zmaxBand.setNoDataValueUsed(true);

        geoCoding = sourceProduct.getGeoCoding();

        int sourceExtend;
        if (sourceProduct.getProductType().indexOf("_RR") > -1) {
            sourceExtend = SOURCE_EXTEND_RR;
        } else {
            sourceExtend = SOURCE_EXTEND_FR;
        }
        rectCalculator = new RectangleExtender(new Rectangle(sourceProduct.getSceneRasterWidth(), sourceProduct.getSceneRasterHeight()), sourceExtend, sourceExtend);

        BandMathsOp bandArithmeticOp = BandMathsOp.createBooleanExpressionBand(aeMaskExpression, aeMaskProduct);
        isAemBand = bandArithmeticOp.getTargetProduct().getBandAt(0);
        distanceBand = distanceProduct.getBand(distanceBandName);
        distanceNoDataValue = distanceBand.getNoDataValue();
    }

    @Override
    public void computeTile(Band band, Tile zmax, ProgressMonitor pm) throws OperatorException {

        final Rectangle targetRect = zmax.getRectangle();
        final Rectangle sourceRect = rectCalculator.extend(targetRect);

        pm.beginTask("Processing frame...", targetRect.height + 3);
        try {

            Tile saa = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME), targetRect, SubProgressMonitor.create(pm, 1));
            Tile sza = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME), sourceRect, SubProgressMonitor.create(pm, 1));
            Tile vaa = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_AZIMUTH_DS_NAME), targetRect, SubProgressMonitor.create(pm, 1));
            Tile vza = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME), targetRect, SubProgressMonitor.create(pm, 1));
            Tile distance = getSourceTile(distanceBand, sourceRect, SubProgressMonitor.create(pm, 1));
            Tile isAeMask = getSourceTile(isAemBand, targetRect, SubProgressMonitor.create(pm, 1));

            PixelPos pPix = new PixelPos();
            for (int y = targetRect.y; y < targetRect.y + targetRect.height; y++) {
                pPix.y = y;
                for (int x = targetRect.x; x < targetRect.x + targetRect.width; x++) {
                    pPix.x = x;
                    float zMaxValue = NO_DATA_VALUE;
                    if (isAeMask.getSampleBoolean(x, y)) {
                        boolean found = false;
                        double z0 = 0;
                        double z1 = 60000;
                        double z;
                        final double azDiffRad = computeAzimuthDifferenceRad(vaa.getSampleFloat(x, y), saa.getSampleFloat(x, y));
                        final double tanVza = Math.tan(vza.getSampleFloat(x, y) * MathUtils.DTOR);
                        final GeoPos pGeo = geoCoding.getGeoPos(pPix, null);
                        do {
                            z = (z0 + z1) / 2;
                            final double pp0Length = z * tanVza;
                            final GeoPos p0Geo = NavigationUtils.lineWithAngle(pGeo, pp0Length, azDiffRad);
                            final PixelPos p0Pix = geoCoding.getPixelPos(p0Geo, null);

                            if (sourceRect.contains(p0Pix)) {
                                int p0x = MathUtils.floorInt(p0Pix.x);
                                int p0y = MathUtils.floorInt(p0Pix.y);
                                final int distanceValue = distance.getSampleInt(p0x, p0y);
                                if (distanceValue != distanceNoDataValue) {
                                    final double lz = Math.tan(sza.getSampleFloat(p0x, p0y) * MathUtils.DTOR) * z;
                                    if (lz <= distanceValue) {
                                        z0 = z;
                                        found = true;
                                    } else {
                                        z1 = z;
                                    }
                                } else {
                                    z1 = z;
                                }
                            } else {
                                z1 = z;
                            }
                            zMaxValue = (float) z;
                        } while ((z1 - z0) > 200);

                        if (!found) {
                            int distanceValue = distance.getSampleInt(x, y);
                            if (distanceValue != distanceNoDataValue) {
                                float szaValue = sza.getSampleFloat(x, y);
                                zMaxValue = (float) (distanceValue / Math.tan(szaValue * MathUtils.DTOR));
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

    private double computeAzimuthDifferenceRad(final double viewAzimuth, final double sunAzimuth) {
        return Math.acos(Math.cos(MathUtils.DTOR * (viewAzimuth - sunAzimuth)));
    }

    public static class Spi extends OperatorSpi {
        public Spi() {
            super(ZmaxOp.class);
        }
    }
}