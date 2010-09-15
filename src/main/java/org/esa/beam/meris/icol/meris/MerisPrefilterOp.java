package org.esa.beam.meris.icol.meris;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.gpf.operators.standard.BandMathsOp;
import org.esa.beam.meris.brr.CloudClassificationOp;
import org.esa.beam.meris.brr.LandClassificationOp;
import org.esa.beam.meris.icol.utils.OperatorUtils;
import org.esa.beam.util.ProductUtils;

import java.awt.Rectangle;

import static org.esa.beam.meris.icol.utils.OperatorUtils.*;

/**
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
@OperatorMetadata(alias = "IcolMerisPrefilter",
                  version = "1.1",
                  authors = "Marco Zuehlke, Olaf Danne",
                  copyright = "(c) 2007-2009 by Brockmann Consult",
                  description = "This operator fills the stripes with invalid pixels on left and right side with the value of first/last valid pixel in row.")
public class MerisPrefilterOp extends MerisBasisOp {

    @SourceProduct(alias = "source")
    private Product sourceProduct;

    @TargetProduct
    private Product targetProduct;

    private int sourceWidth;
    private int sourceHeight;

    @Override
    public void initialize() throws OperatorException {

        sourceWidth = sourceProduct.getSceneRasterWidth();
        sourceHeight = sourceProduct.getSceneRasterHeight();

        targetProduct = createCompatibleProduct(sourceProduct, "MER", sourceProduct.getProductType());
        targetProduct.setPreferredTileSize(256, 256);
        for (String bandName : sourceProduct.getBandNames()) {
            if (!bandName.equals("l1_flags")) {
                ProductUtils.copyBand(bandName, sourceProduct, targetProduct);
            }
        }
        OperatorUtils.copyFlagBandsWithImages(sourceProduct, targetProduct);

    }

    @Override
    public void computeTile(Band band, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Rectangle rectangle = targetTile.getRectangle();
        pm.beginTask("Processing frame...", rectangle.height + 6);
        try {
            final int bandNumber = band.getSpectralBandIndex() + 1;


            Tile detectorIndexTile = getSourceTile(
                    sourceProduct.getBand(EnvisatConstants.MERIS_DETECTOR_INDEX_DS_NAME),
                    rectangle, subPm1(pm));
            if (bandNumber > 0) {

                Tile radianceTile = getSourceTile(sourceProduct.getBand("radiance_" + bandNumber), rectangle,
                                              subPm1(pm));

                for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                    int firstValidIndex = -1;
                    int lastValidIndex = -1;
                    // check if transition from invalid to valid happens in this tile
                    if (detectorIndexTile.getSampleInt(rectangle.x, y) == -1 &&
                        detectorIndexTile.getSampleInt(rectangle.x + rectangle.width - 1, y) != -1) {
                        // determine first valid pixel in row
                        for (int x = rectangle.x; x < rectangle.x + rectangle.width - 1; x++) {
                            if (detectorIndexTile.getSampleInt(x, y) == -1 && detectorIndexTile.getSampleInt(x + 1,
                                                                                                             y) != -1) {
                                firstValidIndex = x + 1;
                                break;
                            }
                        }
                    }
                    // check if transition from valid to invalid happens in this tile
                    if (detectorIndexTile.getSampleInt(rectangle.x, y) != -1 &&
                        detectorIndexTile.getSampleInt(rectangle.x + rectangle.width - 1, y) == -1) {
                        // determine first valid pixel in row
                        for (int x = rectangle.x; x < rectangle.x + rectangle.width - 1; x++) {
                            if (detectorIndexTile.getSampleInt(x, y) != -1 && detectorIndexTile.getSampleInt(x+1, y) == -1) {
                                lastValidIndex = x;
                                break;
                            }
                        }
                    }

                    if (firstValidIndex != -1) {
                        for (int x = rectangle.x; x < firstValidIndex; x++) {
                            // replace values from all invalid pixels on the left with value from first valid one
                            final float firstValidRadiance = radianceTile.getSampleFloat(firstValidIndex, y);
                            targetTile.setSample(x, y, firstValidRadiance);
                        }
                        for (int x = firstValidIndex; x < rectangle.x + rectangle.width; x++) {
                            final float radiance = radianceTile.getSampleFloat(x, y);
                            targetTile.setSample(x, y, radiance);
                        }
                    } else if (lastValidIndex != -1) {
                        for (int x = rectangle.x; x < lastValidIndex; x++) {
                            final float radiance = radianceTile.getSampleFloat(x, y);
                            targetTile.setSample(x, y, radiance);
                        }
                        for (int x = lastValidIndex; x < rectangle.x + rectangle.width; x++) {
                            // replace values from all invalid pixels on the right with value from last valid one
                            final float lastValidRadiance = radianceTile.getSampleFloat(lastValidIndex, y);
                            targetTile.setSample(x, y, lastValidRadiance);
                        }
                    } else {
                        for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                            // keep original values
                            final float radiance = radianceTile.getSampleFloat(x, y);
                            targetTile.setSample(x, y, radiance);
                        }
                    }

                    checkForCancellation(pm);
                    pm.worked(1);
                }
            } else if (band.getName().equals(EnvisatConstants.MERIS_DETECTOR_INDEX_DS_NAME)) {
                for (int y = rectangle.y; y < rectangle.y + rectangle.height; y++) {
                    for (int x = rectangle.x; x < rectangle.x + rectangle.width; x++) {
                        final float detectorIndex = detectorIndexTile.getSampleInt(x, y);
                        targetTile.setSample(x, y, detectorIndex);
                    }
                }

            }
        } finally {
            pm.done();
        }
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(MerisPrefilterOp.class);
        }
    }

}
