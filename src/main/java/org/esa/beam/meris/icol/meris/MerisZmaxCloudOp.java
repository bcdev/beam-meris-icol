package org.esa.beam.meris.icol.meris;

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
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.gpf.operators.standard.BandMathsOp;
import org.esa.beam.meris.brr.CloudClassificationOp;
import org.esa.beam.meris.icol.utils.NavigationUtils;
import org.esa.beam.util.RectangleExtender;
import org.esa.beam.util.math.MathUtils;

import java.awt.Rectangle;

/**
 * Operator providing Zmax for cloud contribution in AE correction.
 *
 * @author Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
@OperatorMetadata(alias = "Meris.Zmaxcloud",
        version = "1.0",
        internal = true,
        authors = "Marco Zühlke",
        copyright = "(c) 2007 by Brockmann Consult",
        description = "Zmax computation for cloud.")
public class MerisZmaxCloudOp extends MerisBasisOp {
    public static final String ZMAX_CLOUD = "zmaxcloud";

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
    @SourceProduct(alias = "cloud")
    private Product cloudProduct;
    @SourceProduct(alias="cloudDistance")
    private Product cdProduct;
    @TargetProduct
    private Product targetProduct;

    @Override
    public void initialize() throws OperatorException {
    	targetProduct = createCompatibleProduct(l1bProduct, "zmaxcloud_"+l1bProduct.getName(), "ZMAXCLOUD");
        Band zmaxBand = targetProduct.addBand(ZMAX_CLOUD, ProductData.TYPE_FLOAT32);
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
//        if (l1bProduct.getPreferredTileSize() != null) {
//            targetProduct.setPreferredTileSize(l1bProduct.getPreferredTileSize());
//        }
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
        	Tile cloudDistance = getSourceTile(cdProduct.getBand("cloud_distance"), sourceRectangle, pm);
        	Tile isAEM = getSourceTile(isAemBand, sourceRectangle, pm);

            Tile surfacePressure = getSourceTile(cloudProduct.getBand(CloudClassificationOp.PRESSURE_SURFACE), targetRectangle, pm);
            Tile cloudTopPressure = getSourceTile(cloudProduct.getBand(CloudClassificationOp.PRESSURE_CTP), targetRectangle, pm);
            Tile cloudFlags = getSourceTile(cloudProduct.getBand(CloudClassificationOp.CLOUD_FLAGS), targetRectangle, pm);


            PixelPos pPix = new PixelPos();
            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                pPix.y = y;
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                    zmax.setSample(x, y, -1);
                    if (x == 40 && y == 210)
                        System.out.println("");
                    boolean isCloud = cloudFlags.getSampleBit(x, y, CloudClassificationOp.F_CLOUD);
//                    if (!isAEM.getSampleBoolean(x, y) || !isCloud) {
                    if (!isAEM.getSampleBoolean(x, y)) {
                        continue;
                    }
                    pPix.x = x;
                    double z0 = 0;
                    double z1 = 60000;
                    double z;
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
//                            int p0x = MathUtils.floorInt(p0Pix.x);
//                            int p0y = MathUtils.floorInt(p0Pix.y);
//
//                            final double ctp = cloudTopPressure.getSampleDouble(x, y);
//                            double zCloud = 0.0;
//                            if (ctp > 0.0) {
//                                final double pressureCorrectionCloud = ctp / surfacePressure.getSampleDouble(x, y);
//                                // cloud height
//                                zCloud = 8.0 * Math.log(1.0 / pressureCorrectionCloud);
//                            } else {
//                                // RS, 2009/11/09:
//                                zCloud = 12000.0;
//                            }
//
//                            if (cloudDistance.getSampleInt(p0x, p0y) == -1) {
//                                z1 = z;
//                            } else {
//                                final double l = cloudDistance.getSampleInt(p0x, p0y) -
//                                    zCloud/Math.tan(sza.getSampleFloat(p0x, p0y) * MathUtils.DTOR);
//                                final double lz = Math.tan(sza.getSampleFloat(p0x, p0y) * MathUtils.DTOR) * z;
//                                if (lz > l) {
//                                    z1 = z;
//                                } else {
//                                    z0 = z;
//                                    found = true;
//                                }
//                            }
//                        } else {
//                            z1 = z;
//                        }
//                    } while ((z1 - z0) > 200);

//                    if (found) {
//                        zmax.setSample(x, y, (float)z);
//                    }

                    double zMax = 30000.0;

                    final double pp0Length = 0.0;
                    final GeoPos pGeo = geoCoding.getGeoPos(pPix, null);
                    final GeoPos p0Geo = NavigationUtils.lineWithAngle(pGeo, pp0Length, azDiffRad);
                    final PixelPos p0Pix = geoCoding.getPixelPos(p0Geo, null);

                    if (sourceRectangle.contains(p0Pix)) {
                        p0x = MathUtils.floorInt(p0Pix.x);
                        p0y = MathUtils.floorInt(p0Pix.y);
                        float szaValue = sza.getSampleFloat(p0x, p0y);
                        l = cloudDistance.getSampleInt(p0x, p0y);
                        if (l == MerisCloudDistanceOp.NO_DATA_VALUE) {
                            zmax.setSample(x, y, -1);
                        } else {
                            zMax = l / Math.tan(szaValue * MathUtils.DTOR);
                            zmax.setSample(x, y, (float) (zMax));
                        }
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
            super(MerisZmaxCloudOp.class);
        }
    }
}
