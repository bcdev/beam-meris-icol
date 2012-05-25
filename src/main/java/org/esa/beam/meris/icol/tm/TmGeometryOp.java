package org.esa.beam.meris.icol.tm;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.CrsGeoCoding;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.MapGeoCoding;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.dataop.dem.ElevationModel;
import org.esa.beam.framework.dataop.dem.ElevationModelDescriptor;
import org.esa.beam.framework.dataop.dem.ElevationModelRegistry;
import org.esa.beam.framework.dataop.resamp.Resampling;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.meris.icol.utils.LandsatUtils;
import org.esa.beam.util.math.MathUtils;
import org.opengis.referencing.operation.MathTransform;

import javax.media.jai.BorderExtender;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.text.ParseException;

/**
 * @author Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
@OperatorMetadata(alias = "Landsat.Geometry",
                  version = "1.0",
                  internal = true,
                  authors = "Olaf Danne",
                  copyright = "(c) 2009 by Brockmann Consult",
                  description = "Landsat geometry computation.")
public class TmGeometryOp extends TmBasisOp {

    public static final int NO_DATA_VALUE = -1;
    public static final int LANDSAT_ORIGINAL_RESOLUTION = 30;

    public static final String SUN_ZENITH_BAND_NAME = "sunZenith";
    public static final String SUN_AZIMUTH_BAND_NAME = "sunAzimuth";
    public static final String VIEW_ZENITH_BAND_NAME = "viewZenith";
    public static final String VIEW_AZIMUTH_BAND_NAME = "viewAzimuth";
    public static final String AIR_MASS_BAND_NAME = "airMass";
    public static final String SCATTERING_ANGLE_BAND_NAME = "scatteringAngle";
    public static final String SPECULAR_ANGLE_BAND_NAME = "specularAngle";

    public static final String LATITUDE_BAND_NAME = "latitude";
    public static final String LONGITUDE_BAND_NAME = "longitude";
    public static final String ALTITUDE_BAND_NAME = "altitude";

    private GeoCoding sourceGeocoding;
    private ElevationModel getasseElevationModel;
    private final float SEA_LEVEL_PRESSURE = 1013.25f;

    private int aveBlock;

    @SourceProduct(alias = "l1g")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;
    @Parameter
    private int landsatTargetResolution;
    // TODO: remove parameters, set both start and stop time to SCENE_CENTER_SCAN_TIME (get from PRODUCT_METADATA)
    @Parameter
    private String startTime;
    @Parameter
    private String stopTime;

    private int doy;
    private double gmt;

    @Override
    public void initialize() throws OperatorException {

        final String demName = "GETASSE30";
        final ElevationModelDescriptor demDescriptor = ElevationModelRegistry.getInstance().getDescriptor(
                demName);
        if (demDescriptor == null || !demDescriptor.isDemInstalled()) {
            throw new OperatorException("DEM not installed: " + demName + ". Please install with Module Manager.");
        }
        getasseElevationModel = demDescriptor.createDem(Resampling.NEAREST_NEIGHBOUR);

        sourceGeocoding = sourceProduct.getGeoCoding();

        aveBlock = landsatTargetResolution / (2 * LANDSAT_ORIGINAL_RESOLUTION);

        int sceneWidth = sourceProduct.getSceneRasterWidth() / (2 * aveBlock);
        int sceneHeight = sourceProduct.getSceneRasterHeight() / (2 * aveBlock);

        final String productType;
        if (landsatTargetResolution == TmConstants.LANDSAT5_GEOM_FR) {
            productType = "L1G_FR_";
        } else {
            productType = "L1G_RR_";
        }
        targetProduct = new Product(sourceProduct.getName() + "_downscaled", productType, sceneWidth, sceneHeight);
        GeoCoding srcGeoCoding = sourceProduct.getGeoCoding();
        if (srcGeoCoding instanceof CrsGeoCoding || srcGeoCoding instanceof MapGeoCoding) {
            MathTransform imageToMapTransform = srcGeoCoding.getImageToMapTransform();
            if (imageToMapTransform instanceof AffineTransform) {
                AffineTransform affineTransform = (AffineTransform) imageToMapTransform;
                final AffineTransform destTransform = new AffineTransform(affineTransform);
                double scaleX = ((double) sourceProduct.getSceneRasterWidth()) / sceneWidth;
                double scaleY = ((double) sourceProduct.getSceneRasterHeight()) / sceneHeight;
                destTransform.scale(scaleX, scaleY);
                Rectangle destBounds = new Rectangle(sceneWidth, sceneHeight);
                try {
                    targetProduct.setGeoCoding(new CrsGeoCoding(srcGeoCoding.getMapCRS(), destBounds, destTransform));
                } catch (Exception e) {
                    throw new OperatorException(e);
                }
            }
        }

        doy = LandsatUtils.getDayOfYear(startTime);
        final String startGmtString = startTime.substring(12, 20);
        final String stopGmtString = stopTime.substring(12, 20);
        gmt = LandsatUtils.getDecimalGMT(startGmtString, stopGmtString);

        try {
            targetProduct.setStartTime(ProductData.UTC.parse(startTime));
            targetProduct.setEndTime(ProductData.UTC.parse(stopTime));
        } catch (ParseException e) {
            throw new OperatorException(
                    "Start or stop time invalid or has wrong format - must be 'yyyymmdd hh:mm:ss'.");
        }

        for (int i = 0; i < TmConstants.LANDSAT5_RADIANCE_BAND_NAMES.length; i++) {
            Band band = targetProduct.addBand(TmConstants.LANDSAT5_RADIANCE_BAND_NAMES[i], ProductData.TYPE_FLOAT32);
            band.setGeophysicalNoDataValue(NO_DATA_VALUE);
            band.setNoDataValueUsed(true);
        }

        targetProduct.addBand(LATITUDE_BAND_NAME, ProductData.TYPE_FLOAT32);
        targetProduct.addBand(LONGITUDE_BAND_NAME, ProductData.TYPE_FLOAT32);
        targetProduct.addBand(ALTITUDE_BAND_NAME, ProductData.TYPE_FLOAT32);

        targetProduct.addBand(SUN_ZENITH_BAND_NAME, ProductData.TYPE_FLOAT32);
        targetProduct.addBand(SUN_AZIMUTH_BAND_NAME, ProductData.TYPE_FLOAT32);
        targetProduct.addBand(VIEW_ZENITH_BAND_NAME, ProductData.TYPE_FLOAT32);
        targetProduct.addBand(VIEW_AZIMUTH_BAND_NAME, ProductData.TYPE_FLOAT32);
        targetProduct.addBand(AIR_MASS_BAND_NAME, ProductData.TYPE_FLOAT32);
        targetProduct.addBand(SCATTERING_ANGLE_BAND_NAME, ProductData.TYPE_FLOAT32);
        targetProduct.addBand(SPECULAR_ANGLE_BAND_NAME, ProductData.TYPE_FLOAT32);

    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        Rectangle targetRectangle = targetTile.getRectangle();
        Rectangle sourceRectangle = new Rectangle(0, 0, sourceProduct.getSceneRasterWidth(),
                                                  sourceProduct.getSceneRasterHeight());

        Tile radianceSourceTile = null;
        if (targetBand.getName().startsWith(TmConstants.LANDSAT5_RADIANCE_BAND_PREFIX)) {
            radianceSourceTile = getSourceTile(sourceProduct.getBand(targetBand.getName()), sourceRectangle,
                    BorderExtender.createInstance(BorderExtender.BORDER_COPY));
        }

        pm.beginTask("Processing frame...", targetRectangle.height);
        try {
            // averaging
            int x1 = sourceRectangle.x;
            int x2 = sourceRectangle.x + sourceRectangle.width - 1;
            int y1 = sourceRectangle.y;
            int y2 = sourceRectangle.y + sourceRectangle.height - 1;

            int aveSize = 2 * aveBlock;
            for (int iSrcY = y1 + aveBlock; iSrcY <= y2 + aveBlock; iSrcY += aveSize) {
                for (int iSrcX = x1 + aveBlock; iSrcX <= x2 + aveBlock; iSrcX += aveSize) {
                    int iTarX = ((iSrcX - x1 - aveBlock) / aveSize);
                    int iTarY = ((iSrcY - y1 - aveBlock) / aveSize);

                    if (!(LandsatUtils.isCoordinatesOutOfBounds(iTarX, iTarY, targetTile))) {
                        final GeoPos geoPosAve = getGeoPosSpatialAverage(iSrcX, iSrcY);

                        final double sza = LandsatUtils.getSunAngles(geoPosAve, doy, gmt).getZenith();
                        final double saa = LandsatUtils.getSunAngles(geoPosAve, doy, gmt).getAzimuth();
                        final double vza = 0.0f; // RS, 10/11/2009
                        final double vaa = 0.0f; // RS, 10/11/2009

                        final double mus = Math.cos(sza * MathUtils.DTOR);
                        final double muv = Math.cos(vza * MathUtils.DTOR);
                        final double nus = Math.sin(sza * MathUtils.DTOR);
                        final double nuv = Math.sin(vza * MathUtils.DTOR);

                        final double phi = saa - vaa;

                        if (targetBand.getName().equals("latitude")) {
                            targetTile.setSample(iTarX, iTarY, geoPosAve.getLat());
                        } else if (targetBand.getName().equals("longitude")) {
                            targetTile.setSample(iTarX, iTarY, geoPosAve.getLon());
                        } else if (targetBand.getName().equals("altitude")) {
                            final float altAve = getAltitudeSpatialAverage(iSrcX, iSrcY);
                            targetTile.setSample(iTarX, iTarY, altAve);
                        } else if (targetBand.getName().equals("sunZenith")) {
                            targetTile.setSample(iTarX, iTarY, sza);
                        } else if (targetBand.getName().equals("sunAzimuth")) {
                            targetTile.setSample(iTarX, iTarY, saa);
                        } else if (targetBand.getName().equals("viewZenith")) {
                            targetTile.setSample(iTarX, iTarY, vza);
                        } else if (targetBand.getName().equals("viewAzimuth")) {
                            targetTile.setSample(iTarX, iTarY, vaa);
                        } else if (targetBand.getName().equals("airMass")) {
                            final double airMass = 1.0 / mus + 1.0 / muv;
                            targetTile.setSample(iTarX, iTarY, airMass);
                        } else if (targetBand.getName().equals("scatteringAngle")) {
                            //compute the COSINE of the back scattering angle
                            final double csb = mus * muv + nus * nuv * Math.cos(phi * MathUtils.DTOR);
                            targetTile.setSample(iTarX, iTarY, csb);
                        } else if (targetBand.getName().equals("specularAngle")) {
                            //compute the COSINE of the forward scattering angle
                            final double csf = mus * muv - nus * nuv * Math.cos(phi * MathUtils.DTOR);
                            targetTile.setSample(iTarX, iTarY, csf);
                        } else if (radianceSourceTile != null) {
                            final float radianceAve = getRadianceSpatialAverage(radianceSourceTile, iSrcX, iSrcY);
                            targetTile.setSample(iTarX, iTarY, radianceAve);
                        }
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


    private float getRadianceSpatialAverage(Tile radianceTile, int iTarX, int iTarY) throws Exception {

        double radianceAve = 0.0;
        double srcNoDataValue = radianceTile.getRasterDataNode().getGeophysicalNoDataValue();
        int n = 0;
        final int minX = Math.max(0, iTarX - aveBlock);
        final int minY = Math.max(0, iTarY - aveBlock);
        final int maxX = Math.min(sourceProduct.getSceneRasterWidth() - 1, iTarX + aveBlock);
        final int maxY = Math.min(sourceProduct.getSceneRasterHeight() - 1, iTarY + aveBlock);

        for (int iy = minY; iy <= maxY; iy++) {
            for (int ix = minX; ix <= maxX; ix++) {
                final double radiance = radianceTile.getSampleDouble(ix, iy);
                if (srcNoDataValue != radiance) {
                    n++;
                    radianceAve += radiance;
                }
            }
        }
        if (n > 0) {
            radianceAve /= n;
        } else {
            radianceAve = NO_DATA_VALUE;
        }

        return (float) radianceAve;
    }

    private float getAltitudeSpatialAverage(int iTarX, int iTarY) throws Exception {

        float altAve = 0.0f;

        int n = 0;
        final int minX = Math.max(0, iTarX - aveBlock);
        final int minY = Math.max(0, iTarY - aveBlock);
        final int maxX = Math.min(sourceProduct.getSceneRasterWidth() - 1, iTarX + aveBlock);
        final int maxY = Math.min(sourceProduct.getSceneRasterHeight() - 1, iTarY + aveBlock);

        for (int iy = minY; iy <= maxY; iy++) {
            for (int ix = minX; ix <= maxX; ix++) {
                final PixelPos pixelPos = new PixelPos(ix, iy);
                final GeoPos geoPos = sourceGeocoding.getGeoPos(pixelPos, null);
                final float alt = getasseElevationModel.getElevation(geoPos);
                boolean valid = (Double.compare(alt, NO_DATA_VALUE) != 0);
                if (valid) {
                    n++;
                    altAve += alt;
                }
            }
        }
        if (n > 0) {
            altAve /= n;
        } else {
            altAve = NO_DATA_VALUE;
        }

        return altAve;
    }

    private GeoPos getGeoPosSpatialAverage(int iSrcX, int iSrcY) {

        float latAve = 0.0f;
        float lonAve = 0.0f;

        int n = 0;
        final int minX = Math.max(0, iSrcX - aveBlock);
        final int minY = Math.max(0, iSrcY - aveBlock);
        final int maxX = Math.min(sourceProduct.getSceneRasterWidth() - 1, iSrcX + aveBlock);
        final int maxY = Math.min(sourceProduct.getSceneRasterHeight() - 1, iSrcY + aveBlock);

        for (int iy = minY; iy <= maxY; iy++) {
            for (int ix = minX; ix <= maxX; ix++) {
                final PixelPos pixelPos = new PixelPos(ix, iy);
                final GeoPos geoPos = sourceGeocoding.getGeoPos(pixelPos, null);
                boolean valid = (Double.compare(geoPos.getLat(), NO_DATA_VALUE) != 0) &&
                                (Double.compare(geoPos.getLon(), NO_DATA_VALUE) != 0);
                if (valid) {
                    n++;
                    latAve += geoPos.getLat();
                    lonAve += geoPos.getLon();
                }
            }
        }
        if (n > 0) {
            latAve /= n;
            lonAve /= n;
        } else {
            latAve = NO_DATA_VALUE;
            lonAve = NO_DATA_VALUE;
        }

        return new GeoPos(latAve, lonAve);
    }


    /**
     * The Service Provider Interface (SPI) for the operator.
     * It provides operator meta-data and is a factory for new operator instances.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(TmGeometryOp.class);
        }
    }
}
