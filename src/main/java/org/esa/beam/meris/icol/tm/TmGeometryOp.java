package org.esa.beam.meris.icol.tm;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.GeoPos;
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
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.math.MathUtils;

import java.awt.Rectangle;
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

    public static final String RADIANCE_1_NAME = "radiance_1_blue_30m";
    public static final String RADIANCE_2_NAME = "radiance_2_green_30m";
    public static final String RADIANCE_3_NAME = "radiance_3_red_30m";
    public static final String RADIANCE_4_NAME = "radiance_4_nearir_30m";
    public static final String RADIANCE_5_NAME = "radiance_5_midir_30m";
    public static final String RADIANCE_6_NAME = "radiance_6_thermalir_120m";
    public static final String RADIANCE_7_NAME = "radiance_7_midir_30m";

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

    private GeoCoding geocoding;
    private ElevationModel getasseElevationModel;
    private final float SEA_LEVEL_PRESSURE = 1013.25f;

    private int aveBlock;
    private int minNAve;

    @SourceProduct(alias="l1g")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;
    @Parameter
    private int landsatTargetResolution;
    @Parameter
    private String startTime;
    @Parameter
    private String stopTime;
    
    private int sceneWidth;
    private int sceneHeight;

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

        geocoding = sourceProduct.getGeoCoding();

        aveBlock = landsatTargetResolution /(2*LANDSAT_ORIGINAL_RESOLUTION);
        minNAve = (2*aveBlock+1)*(2*aveBlock+1) - 1;

        sceneWidth = sourceProduct.getSceneRasterWidth()/(2*aveBlock+1) + 1;
        sceneHeight = sourceProduct.getSceneRasterHeight()/(2*aveBlock+1) + 1;

        String resolution = "";
        if (landsatTargetResolution == TmConstants.LANDSAT5_RR) {
            resolution = "_RR_";
        } else {
            resolution = "_FR_";
        }
        targetProduct = new Product(sourceProduct.getName() + "_ICOL", "LANDSAT" + resolution, sceneWidth, sceneHeight);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);

        doy = LandsatUtils.getDayOfYear(startTime);
        final String startGmtString = startTime.substring(12, 20);
        final String stopGmtString = stopTime.substring(12, 20);
        gmt = LandsatUtils.getDecimalGMT(startGmtString, stopGmtString);

        try {
            targetProduct.setStartTime(ProductData.UTC.parse(startTime));
            targetProduct.setEndTime(ProductData.UTC.parse(stopTime));
        } catch (ParseException e) {
            throw new OperatorException("Start or stop time invalid or has wrong format - must be 'yyyymmdd hh:mm:ss'.");
        }

        for (int i=0; i< TmConstants.LANDSAT5_RADIANCE_BAND_NAMES.length; i++) {
            targetProduct.addBand(TmConstants.LANDSAT5_RADIANCE_BAND_NAMES[i], ProductData.TYPE_FLOAT32);
        }
        
        Band latitudeBand = targetProduct.addBand(LATITUDE_BAND_NAME, ProductData.TYPE_FLOAT32);
        Band longitudeBand = targetProduct.addBand(LONGITUDE_BAND_NAME, ProductData.TYPE_FLOAT32);
        Band altitudeBand = targetProduct.addBand(ALTITUDE_BAND_NAME, ProductData.TYPE_FLOAT32);

        Band szaBand = targetProduct.addBand(SUN_ZENITH_BAND_NAME, ProductData.TYPE_FLOAT32);
        Band saaBand = targetProduct.addBand(SUN_AZIMUTH_BAND_NAME, ProductData.TYPE_FLOAT32);
        Band vzaBand = targetProduct.addBand(VIEW_ZENITH_BAND_NAME, ProductData.TYPE_FLOAT32);
        Band vaaBand = targetProduct.addBand(VIEW_AZIMUTH_BAND_NAME, ProductData.TYPE_FLOAT32);
        Band airMassBand = targetProduct.addBand(AIR_MASS_BAND_NAME, ProductData.TYPE_FLOAT32);
        Band scatteringAngleBand = targetProduct.addBand(SCATTERING_ANGLE_BAND_NAME, ProductData.TYPE_FLOAT32);
        Band specularAngleBand = targetProduct.addBand(SPECULAR_ANGLE_BAND_NAME, ProductData.TYPE_FLOAT32);

    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        Rectangle targetRectangle = targetTile.getRectangle();
        Rectangle sourceRectangle = new Rectangle(0, 0, sourceProduct.getSceneRasterWidth(),
                sourceProduct.getSceneRasterHeight());

        Tile radianceSourceTile = null;
        if (targetBand.getName().startsWith(TmConstants.LANDSAT5_RADIANCE_BAND_PREFIX)) {
            radianceSourceTile = getSourceTile(sourceProduct.getBand(targetBand.getName()), sourceRectangle, pm);
        }

        pm.beginTask("Processing frame...", targetRectangle.height);
        try {
            // averaging
            int x1 = sourceRectangle.x;
            int x2 = sourceRectangle.x + sourceRectangle.width - 1;
            int y1 = sourceRectangle.y;
            int y2 = sourceRectangle.y + sourceRectangle.height - 1;
            int tx1 = targetTile.getRectangle().x;
            int tx2 = targetTile.getRectangle().x + targetTile.getRectangle().width - 1;
            int ty1 = targetTile.getRectangle().y;
            int ty2 = targetTile.getRectangle().y + targetTile.getRectangle().height - 1;


            for (int iTarY = y1+ aveBlock; iTarY <= y2+ aveBlock; iTarY+=2* aveBlock +1) {
                for (int iTarX = x1+ aveBlock; iTarX <= x2+ aveBlock; iTarX+=2* aveBlock +1) {
                    int iX = ((iTarX-x1- aveBlock)/(2* aveBlock +1));
                    int iY = ((iTarY-y1- aveBlock)/(2* aveBlock +1));
                    // todo: make sure they are within tile boundary values
                    if (iX >= tx1 && iX <= tx2 && iY >= ty1 && iY <= ty2) {

                        final PixelPos pixelPos = new PixelPos(iX, iY);
//                    final GeoPos geoPos = geocoding.getGeoPos(pixelPos, null);
                        final GeoPos geoPosAve = getGeoposSpatialAverage(iTarX, iTarY);

                        final double sza = (LandsatUtils.getSunAngles(geoPosAve, doy, gmt)).getZenith();
                        final double saa = LandsatUtils.getSunAngles(geoPosAve, doy, gmt).getAzimuth();
                        final double vza = getViewZenithAngle(iTarX, iTarY);
                        final double vaa = getViewAzimuthAngle(iTarX, iTarY);

                        final double mus = Math.cos(sza * MathUtils.DTOR);
                        final double muv = Math.cos(vza * MathUtils.DTOR);
                        final double nus = Math.sin(sza * MathUtils.DTOR);
                        final double nuv = Math.sin(vza * MathUtils.DTOR);

                        final double phi = saa - vaa;

                        if (targetBand.getName().equals("latitude")) {
//                        System.out.println("ix, iy: " + iX + "," + iY + " // " + iTarX + "," + iTarY);
                            targetTile.setSample(iX, iY, geoPosAve.getLat());
                        } else if (targetBand.getName().equals("longitude")) {
                            targetTile.setSample(iX, iY, geoPosAve.getLon());
                        } else if (targetBand.getName().equals("altitude")) {
                            final float altAve = getAltitudeSpatialAverage(iTarX, iTarY);
                            targetTile.setSample(iX, iY, altAve);
                        } else if (targetBand.getName().equals("sunZenith")) {
//                            System.out.println("iTarX, iTarY, iX, iY: " + iTarX + "," + iTarY + "," + iX + "," + iY +
//                                    "// " + targetTile.getRectangle());
                            targetTile.setSample(iX, iY, sza);
                        } else if (targetBand.getName().equals("sunAzimuth")) {
                            targetTile.setSample(iX, iY, saa);
                        } else if (targetBand.getName().equals("viewZenith")) {
                            targetTile.setSample(iX, iY, vza);
                        } else if (targetBand.getName().equals("viewAzimuth")) {
                            targetTile.setSample(iX, iY, vaa);
                        } else if (targetBand.getName().equals("airMass")) {
                            final double airMass = 1.0 / mus + 1.0 / muv;
                            targetTile.setSample(iX, iY, airMass);
                        } else if (targetBand.getName().equals("scatteringAngle")) {
                            //compute the COSINE of the back scattering angle
                            final double csb = mus * muv + nus * nuv * Math.cos(phi * MathUtils.DTOR);
                            targetTile.setSample(iX, iY, csb);
                        } else if (targetBand.getName().equals("specularAngle")) {
                            //compute the COSINE of the forward scattering angle
                            final double csf = mus * muv - nus * nuv * Math.cos(phi * MathUtils.DTOR);
                            targetTile.setSample(iX, iY, csf);
                        } else if (radianceSourceTile != null) {
                            final float radianceAve = getRadianceSpatialAverage(radianceSourceTile, iTarX, iTarY);
                            targetTile.setSample(iX, iY, radianceAve);
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

        float radianceAve = 0.0f;

        int n = 0;
        final int minX = Math.max(0,iTarX-aveBlock);
        final int minY = Math.max(0,iTarY-aveBlock);
        final int maxX = Math.min(sourceProduct.getSceneRasterWidth()-1,iTarX+aveBlock);
        final int maxY = Math.min(sourceProduct.getSceneRasterHeight()-1,iTarY+aveBlock);
//        final int maxX = Math.min(radianceTile.getWidth()-1,iTarX+aveBlock);
//        final int maxY = Math.min(radianceTile.getHeight()-1,iTarY+aveBlock);

        for (int iy = minY; iy <= maxY; iy++) {
            for (int ix = minX; ix <= maxX; ix++) {
                final float radiance = radianceTile.getSampleFloat(ix, iy);
                boolean valid = (Double.compare(radiance, NO_DATA_VALUE) != 0);
                if (valid) {
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

        return radianceAve;
    }

//    private float getRadianceSpatialAverage(Tile radianceTile, int iTarX, int iTarY) throws Exception {
//
//        float radianceAve = 0.0f;
//
//        int n = 0;
//        final int minX = Math.max(0,iTarX-aveBlock);
//        final int minY = Math.max(0,iTarY-aveBlock);
//        final int rasterWidth = sourceProduct.getSceneRasterWidth();
//        final int rasterHeight = sourceProduct.getSceneRasterHeight();
////        final int maxX = Math.min(rasterWidth -1,iTarX+aveBlock);
////        final int maxY = Math.min(rasterHeight -1,iTarY+aveBlock);
////        final int maxX = Math.min(radianceTile.getWidth()-1,iTarX+aveBlock);
////        final int maxY = Math.min(radianceTile.getHeight()-1,iTarY+aveBlock);
//
//        final int maxX = (iTarX + aveBlock >= rasterWidth) ? (rasterWidth- iTarX - aveBlock) : aveBlock;
//        final int maxY = (iTarY + aveBlock >= rasterHeight) ? (rasterHeight - iTarY - aveBlock) : aveBlock;
//
//        for (int iy = minY; iy <= maxY; iy++) {
//            for (int ix = minX; ix <= maxX; ix++) {
//                final float radiance = radianceTile.getSampleFloat(ix, iy);
//                boolean valid = (Double.compare(radiance, NO_DATA_VALUE) != 0);
//                if (valid) {
//                    n++;
//                    radianceAve += radiance;
//                }
//            }
//        }
//        if (n > 0) {
//            radianceAve /= n;
//        } else {
//            radianceAve = NO_DATA_VALUE;
//        }
//
//        return radianceAve;
//    }


    private float getAltitudeSpatialAverage(int iTarX, int iTarY) throws Exception {

        float altAve = 0.0f;

        int n = 0;
        final int minX = Math.max(0,iTarX-aveBlock);
        final int minY = Math.max(0,iTarY-aveBlock);
        final int maxX = Math.min(sourceProduct.getSceneRasterWidth()-1,iTarX+aveBlock);
        final int maxY = Math.min(sourceProduct.getSceneRasterHeight()-1,iTarY+aveBlock);

        for (int iy = minY; iy <= maxY; iy++) {
            for (int ix = minX; ix <= maxX; ix++) {
                final PixelPos pixelPos = new PixelPos(ix, iy);
                final GeoPos geoPos = geocoding.getGeoPos(pixelPos, null);
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

    private GeoPos getGeoposSpatialAverage(int iTarX, int iTarY) {

        float latAve = 0.0f;
        float lonAve = 0.0f;

        int n = 0;
        final int minX = Math.max(0,iTarX-aveBlock);
        final int minY = Math.max(0,iTarY-aveBlock);
        final int maxX = Math.min(sourceProduct.getSceneRasterWidth()-1,iTarX+aveBlock);
        final int maxY = Math.min(sourceProduct.getSceneRasterHeight()-1,iTarY+aveBlock);

        for (int iy = minY; iy <= maxY; iy++) {
            for (int ix = minX; ix <= maxX; ix++) {
                final PixelPos pixelPos = new PixelPos(ix, iy);
                final GeoPos geoPos = geocoding.getGeoPos(pixelPos, null);
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

        GeoPos geoPosAve = new GeoPos(latAve, lonAve);
        return geoPosAve;
    }


    private float getViewZenithAngle(int iTarX, int iTarY) {
        float vza = 0.0f;  // RS, 10/11/2009
        return vza;
    }

    private float getViewAzimuthAngle(int iTarX, int iTarY) {
        float vaa = 0.0f;  // RS, 10/11/2009
        return vaa;
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
