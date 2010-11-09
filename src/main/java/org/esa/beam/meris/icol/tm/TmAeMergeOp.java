package org.esa.beam.meris.icol.tm;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.meris.icol.common.AdjacencyEffectMaskOp;
import org.esa.beam.meris.icol.utils.IcolUtils;
import org.esa.beam.meris.icol.utils.LandsatUtils;
import org.esa.beam.meris.icol.utils.OperatorUtils;
import org.esa.beam.meris.l2auxdata.Utils;
import org.esa.beam.util.math.MathUtils;

import java.awt.Rectangle;

/**
 * @author Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
@OperatorMetadata(alias = "Tm.AeMerge",
                  internal=true,
        version = "1.1",
        authors = "Marco Zuehlke, Olaf Danne",
        copyright = "(c) 2007-2009 by Brockmann Consult",
        description = "For every radiance band, this operator merges the sum of all AE correction terms into one product.")
public class TmAeMergeOp extends TmBasisOp {
    @SourceProduct(alias="original")
    private Product sourceProduct;
    @SourceProduct(alias="aeRay")
    private Product aeRayProduct;
    @SourceProduct(alias="aeAer")
    private Product aeAerosolProduct;
    @SourceProduct(alias="aemaskRay")
    private Product aeMaskRayleighProduct;
    @SourceProduct(alias="aemaskAer")
    private Product aeMaskAerosolProduct;

    @TargetProduct
    private Product targetProduct;

    @Parameter
    private int productType;

    private int daysSince2000;
    private double seasonalFactor;

    public static final String AE_TOTAL = "ae_total";

    @Override
    public void initialize() throws OperatorException {

        daysSince2000 = LandsatUtils.getDaysSince2000(sourceProduct.getStartTime().getElemString());
        seasonalFactor = Utils.computeSeasonalFactor(daysSince2000,
                                                      TmConstants.SUN_EARTH_DISTANCE_SQUARE);

        targetProduct = createCompatibleProduct(sourceProduct, sourceProduct.getName() + "_AETOTAL", sourceProduct.getProductType());
        Band[] aeTotalBands = addBandGroup(AE_TOTAL, Float.NaN);
    }

    private Band[] addBandGroup(String prefix, double noDataValue) {
        return OperatorUtils.addBandGroup(sourceProduct, TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS, new int[]{5}, targetProduct, prefix, noDataValue, false);
    }

    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        Rectangle rectangle = targetTile.getRectangle();
        pm.beginTask("Processing frame...", rectangle.height);
        try {
            final String bandName = band.getName();
            final int bandNumber = band.getSpectralBandIndex() + 1;
            Tile szaTile = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME), rectangle, pm);

            if (bandName.startsWith(TmAeMergeOp.AE_TOTAL) &&
                    !IcolUtils.isIndexToSkip(bandNumber - 1,
                                            new int[]{TmConstants.LANDSAT5_RADIANCE_6_BAND_INDEX})) {
                Tile aeRayleigh = getSourceTile(aeRayProduct.getBand("rho_aeRay_" + bandNumber), rectangle, pm);
                Tile aeAerosol = getSourceTile(aeAerosolProduct.getBand("rho_aeAer_" + bandNumber), rectangle, pm);
                Tile aeMaskRayleigh = getSourceTile(aeMaskRayleighProduct.getBand(AdjacencyEffectMaskOp.AE_MASK_RAYLEIGH), rectangle, pm);
                Tile aeMaskAerosol = getSourceTile(aeMaskAerosolProduct.getBand(AdjacencyEffectMaskOp.AE_MASK_AEROSOL), rectangle, pm);

                for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                    for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                        double corrected = 0;
                        final double sza = szaTile.getSampleFloat(x, y);
                        final double cosSza = Math.cos(sza * MathUtils.DTOR);
                        if (aeMaskRayleigh.getSampleInt(x, y) == 1) {
                            final double aeRayleighValue = aeRayleigh.getSampleDouble(x, y);
                            corrected = aeRayleighValue;
                            if (aeMaskAerosol.getSampleInt(x, y) == 1) {
                                final double aeAerosolValue = aeAerosol.getSampleDouble(x, y);
                                corrected += aeAerosolValue;
                            }
                        }
                        // todo: if productType = 0, correction term must be in radiance units for upscaling to final product
                        if (productType == 0) {
                            corrected = LandsatUtils.convertReflToRad(corrected, cosSza, bandNumber-1, seasonalFactor);
                        }
                        targetTile.setSample(x, y, corrected);
                    }
                    pm.worked(1);
                }
            }
        } catch (Exception e) {
            throw new OperatorException(e);
        } finally {
            pm.done();
        }
    }

    public static class Spi extends OperatorSpi {
        public Spi() {
            super(TmAeMergeOp.class);
        }
    }

}