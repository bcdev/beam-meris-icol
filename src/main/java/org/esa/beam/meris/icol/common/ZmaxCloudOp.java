package org.esa.beam.meris.icol.common;

import com.bc.ceres.core.ProgressMonitor;
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
import org.esa.beam.meris.icol.meris.MerisAeMaskOp;
import org.esa.beam.meris.icol.utils.NavigationUtils;
import org.esa.beam.meris.icol.utils.OperatorUtils;
import org.esa.beam.util.RectangleExtender;
import org.esa.beam.util.math.MathUtils;

import java.awt.*;

/**
 * Operator providing Zmax for cloud contribution in AE correction.
 *
 * @author Olaf Danne
 */
@OperatorMetadata(alias = "ZmaxCloud",
        version = "1.0",
        internal = true,
        authors = "Marco Zühlke,Olaf Danne",
        copyright = "(c) 2007-2010 by Brockmann Consult",
        description = "Zmax computation for cloud.")
public class ZmaxCloudOp extends Operator {
    public static final String ZMAX_CLOUD = "zmaxcloud";

    private static final int SOURCE_EXTEND_RR = 80;
    private static final int SOURCE_EXTEND_FR = 320;

    private RectangleExtender rectCalculator;
    private GeoCoding geoCoding;
    private Band isAemBand;
    private Band cloudDistanceBand;
    private double cloudDistanceNoDataValue;

    @SourceProduct(alias="l1b")
    private Product l1bProduct;
    @SourceProduct(alias="ae_mask")
    private Product aeMaskProduct;
    @SourceProduct(alias="cloudDistance")
    private Product cdProduct;
    @TargetProduct
    private Product targetProduct;
    @Parameter(defaultValue = MerisAeMaskOp.AE_MASK_RAYLEIGH + ".aep") //TODO replace with sensor neutral constant
    private String aeMaskExpression;

    @Override
    public void initialize() throws OperatorException {
    	targetProduct = OperatorUtils.createCompatibleProduct(l1bProduct, "zmaxcloud_" + l1bProduct.getName(), "ZMAXCLOUD");
        Band zmaxBand = targetProduct.addBand(ZMAX_CLOUD, ProductData.TYPE_FLOAT32);
        zmaxBand.setNoDataValue(-1);
        zmaxBand.setNoDataValueUsed(true);

        int sourceExtend;
        if (l1bProduct.getProductType().indexOf("_RR") > -1) {
            sourceExtend = SOURCE_EXTEND_RR;
        } else {
            sourceExtend = SOURCE_EXTEND_FR;
        }
        geoCoding = l1bProduct.getGeoCoding();
        rectCalculator = new RectangleExtender(new Rectangle(l1bProduct.getSceneRasterWidth(), l1bProduct.getSceneRasterHeight()), sourceExtend, sourceExtend);

        BandMathsOp bandArithmeticOp =
            BandMathsOp.createBooleanExpressionBand(aeMaskExpression, aeMaskProduct);
        isAemBand = bandArithmeticOp.getTargetProduct().getBandAt(0);
        cloudDistanceBand = cdProduct.getBand("cloud_distance");
        cloudDistanceNoDataValue = cloudDistanceBand.getNoDataValue();
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
            Tile cloudDistance = getSourceTile(cloudDistanceBand, sourceRectangle, pm);
        	Tile isAeMask = getSourceTile(isAemBand, sourceRectangle, pm);

            PixelPos pPix = new PixelPos();
            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                pPix.y = y;
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                    float zMaxValue = -1;
                    if (isAeMask.getSampleBoolean(x, y)) {
                        pPix.x = x;
                        final double azDiffRad = computeAzimuthDifferenceRad(vaa.getSampleFloat(x, y), saa.getSampleFloat(x, y));

                        final double pp0Length = 0.0;
                        final GeoPos pGeo = geoCoding.getGeoPos(pPix, null);
                        final GeoPos p0Geo = NavigationUtils.lineWithAngle(pGeo, pp0Length, azDiffRad);
                        final PixelPos p0Pix = geoCoding.getPixelPos(p0Geo, null);

                        if (sourceRectangle.contains(p0Pix)) {
                            int p0x = MathUtils.floorInt(p0Pix.x);
                            int p0y = MathUtils.floorInt(p0Pix.y);
                            float szaValue = sza.getSampleFloat(p0x, p0y);
                            int l = cloudDistance.getSampleInt(p0x, p0y);
                            if (l != cloudDistanceNoDataValue) {
                                zMaxValue = (float) (l / Math.tan(szaValue * MathUtils.DTOR));
                            }
                        }
                    }
                    zmax.setSample(x, y, zMaxValue);
                }
            }
        } finally {
            pm.done();
        }
    }

    private static double computeAzimuthDifferenceRad(double viewAzimuth, double sunAzimuth) {
        return Math.acos(Math.cos(MathUtils.DTOR * (viewAzimuth - sunAzimuth)));
    }


    public static class Spi extends OperatorSpi {
        public Spi() {
            super(ZmaxCloudOp.class);
        }
    }
}