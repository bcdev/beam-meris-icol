package org.esa.beam.meris.icol.tm;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.meris.brr.GaseousCorrectionOp;
import org.esa.beam.meris.icol.common.AeMaskOp;
import org.esa.beam.meris.icol.utils.IcolUtils;
import org.esa.beam.meris.icol.utils.LandsatUtils;
import org.esa.beam.meris.icol.utils.OperatorUtils;
import org.esa.beam.meris.l2auxdata.Utils;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.math.MathUtils;

import java.awt.Rectangle;

/**
 * @author Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
@OperatorMetadata(alias = "Tm.AeMerge",
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
    private Product aemaskRayleighProduct;
    @SourceProduct(alias="aemaskAer")
    private Product aemaskAerosolProduct;

    @TargetProduct
    private Product targetProduct;

    @Override
    public void initialize() throws OperatorException {

        targetProduct = createCompatibleProduct(sourceProduct, sourceProduct.getName() + "_AETOTAL", sourceProduct.getProductType());
        Band[] aeTotalBands = addBandGroup("rho_ae_total", 0);
    }

    private Band[] addBandGroup(String prefix, double noDataValue) {
        return OperatorUtils.addBandGroup(sourceProduct, TmConstants.LANDSAT5_NUM_SPECTRAL_BANDS, new int[]{5}, targetProduct, prefix, noDataValue, false);
    }

    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        Rectangle rectangle = targetTile.getRectangle();
        pm.beginTask("Processing frame...", rectangle.height);
        try {
            String bandName = band.getName();
            final int bandNumber = band.getSpectralBandIndex() + 1;
            if (bandName.startsWith("rho_ae_total") &&
                    !IcolUtils.isIndexToSkip(bandNumber - 1,
                                            new int[]{TmConstants.LANDSAT5_RADIANCE_6_BAND_INDEX})) {
                Tile aeRayleigh = getSourceTile(aeRayProduct.getBand("rho_aeRay_" + bandNumber), rectangle, pm);
                Tile aeAerosol = getSourceTile(aeAerosolProduct.getBand("rho_aeAer_" + bandNumber), rectangle, pm);
                Tile aepRayleigh = getSourceTile(aemaskRayleighProduct.getBand(AeMaskOp.AE_MASK_RAYLEIGH), rectangle, pm);
                Tile aepAerosol = getSourceTile(aemaskAerosolProduct.getBand(AeMaskOp.AE_MASK_AEROSOL), rectangle, pm);

                for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                    for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                        double corrected = 0.0001;
                        if (aepRayleigh.getSampleInt(x, y) == 1) {
                            final double aeRayleighValue = aeRayleigh.getSampleDouble(x, y);
                            corrected = aeRayleighValue;
                            if (aepAerosol.getSampleInt(x, y) == 1) {
                                final double aeAerosolValue = aeAerosol.getSampleDouble(x, y);
                                corrected += aeAerosolValue;
                            }
                        }
                        // todo: correction term must be in radiance units for upscaling to final product
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