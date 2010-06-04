package org.esa.beam.meris.icol.meris;

import com.bc.ceres.core.NullProgressMonitor;
import com.bc.ceres.glevel.MultiLevelImage;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.meris.icol.CoeffW;
import org.esa.beam.meris.icol.IcolConstants;
import org.esa.beam.meris.icol.utils.Convoluter;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.ResourceInstaller;
import org.esa.beam.util.SystemUtils;

import javax.media.jai.KernelJAI;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
@OperatorMetadata(alias = "Meris.BrrConvolve",
                  version = "1.0",
                  internal = true,
                  authors = "Olaf Danne",
                  copyright = "(c) 2009 by Brockmann Consult",
                  description = "Convolves BRR values before AE Rayleigh correction.")
public class MerisBrrConvolveOp extends Operator {
    @SourceProduct(alias = "l1b")
    private Product l1bProduct;
    @SourceProduct(alias = "brr")
    private Product brrProduct;

    @TargetProduct
    private Product targetProduct;

    @Parameter(defaultValue = "true")
    private boolean openclConvolution;

    @Parameter(defaultValue = "0")
    private int filterWeightsIndex;

    @Parameter(defaultValue = "_")
    private String bandPrefix;

    private transient Band invalidBand;
    private CoeffW coeffW;

    @Override
    public void initialize() throws OperatorException {

        try {
            loadAuxData();
        } catch (IOException e) {
            throw new OperatorException(e.getMessage());
        }

        String productType = l1bProduct.getProductType();
        int index = productType.indexOf("_1");
        productType = productType.substring(0, index) + "_1N";
        targetProduct = createCompatibleBaseProduct("MER", productType);
        ProductUtils.copyFlagBands(l1bProduct, targetProduct);

        if (l1bProduct.getPreferredTileSize() != null) {
            targetProduct.setPreferredTileSize(l1bProduct.getPreferredTileSize());
        }

        double[][] coeffs = coeffW.getCoeffForRR();

        KernelJAI kernelJAI = CoeffW.createKernelByRotation(coeffs[filterWeightsIndex]);

        Convoluter convoluter = new Convoluter(kernelJAI, openclConvolution);
        String[] sourceNames = brrProduct.getBandNames();
        for (String name : sourceNames) {
            if (!brrProduct.getBand(name).isFlagBand()) {
                if (name.startsWith(bandPrefix)) {
                    Band targetBand = ProductUtils.copyBand(name, brrProduct, targetProduct);
                    final MultiLevelImage sourceImage = brrProduct.getBand(name).getSourceImage();
                    RenderedImage outputImage;
                    try {
                            outputImage = convoluter.convolve(sourceImage);
                    } catch (IOException e) {
                        throw new OperatorException("cannot create convolved image", e);
                    }
                    targetBand.setSourceImage(outputImage);
                    targetBand.setName(bandPrefix + "_conv_" + name.substring(bandPrefix.length()+1, name.length()));
                }
            }
        }
        convoluter.dispose();
    }

    private void loadAuxData() throws IOException {
        String auxdataSrcPath = "auxdata/icol";
        final String auxdataDestPath = ".beam/beam-meris-icol/" + auxdataSrcPath;
        File auxdataTargetDir = new File(SystemUtils.getUserHomeDir(), auxdataDestPath);
        URL sourceUrl = ResourceInstaller.getSourceUrl(this.getClass());

        ResourceInstaller resourceInstaller = new ResourceInstaller(sourceUrl, auxdataSrcPath, auxdataTargetDir);
        resourceInstaller.install(".*", new NullProgressMonitor());

        coeffW = new CoeffW(auxdataTargetDir, false, IcolConstants.AE_CORRECTION_MODE_RAYLEIGH);
    }


    private Product createCompatibleBaseProduct(String name, String type) {
        final int sceneWidth = l1bProduct.getSceneRasterWidth();
        final int sceneHeight = l1bProduct.getSceneRasterHeight();

        Product tProduct = new Product(name, type, sceneWidth, sceneHeight);
        ProductUtils.copyTiePointGrids(l1bProduct, tProduct);
        ProductUtils.copyGeoCoding(l1bProduct, tProduct);
        tProduct.setStartTime(l1bProduct.getStartTime());
        tProduct.setEndTime(l1bProduct.getEndTime());
        return tProduct;
    }

    public static class Spi extends OperatorSpi {
        public Spi() {
            super(MerisBrrConvolveOp.class);
        }
    }

}
