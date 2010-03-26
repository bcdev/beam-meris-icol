package org.esa.beam.meris.icol;

import com.bc.ceres.core.NullProgressMonitor;
import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.util.ResourceInstaller;
import org.esa.beam.util.SystemUtils;

import java.awt.Rectangle;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;

/**
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
public class CompareConvolutionOp extends MerisBasisOp {

    private static final double NO_DATA_VALUE = -1.0;

    @SourceProduct(alias = "l1b")
    private Product l1bProduct;
    @SourceProduct(alias = "ae_ray")
    private Product aeRayProduct;

    @TargetProduct
    private Product targetProduct;

    RhoBracketAlgo rhoBracketAlgo;

    private Band flagBand;

    private Band correctedBand;

    private CoeffW coeffW;
    private double[/*26*/][/*RR=26|FR=101*/] w;
    private long totalTime;
    private int totalPixels;


    @Override
    public void initialize() throws OperatorException {
        targetProduct = createCompatibleProduct(aeRayProduct, "ae_" + aeRayProduct.getName(), "AE");
        correctedBand = targetProduct.addBand("rhoTest", ProductData.TYPE_FLOAT32);

        try {
            loadAuxData();
        } catch (IOException e) {
            throw new OperatorException(e);
        }
        rhoBracketAlgo = new RhoBracketKernellLoop(l1bProduct, coeffW, IcolConstants.AE_CORRECTION_MODE_AEROSOL);

        totalTime = 0;
        totalPixels = 0;
    }



    private void loadAuxData() throws IOException {
        String auxdataSrcPath = "auxdata/icol";
        final String auxdataDestPath = ".beam/beam-meris-icol/" + auxdataSrcPath;

        File auxdataTargetDir = new File(SystemUtils.getUserHomeDir(), auxdataDestPath);
        URL sourceUrl = ResourceInstaller.getSourceUrl(this.getClass());

        ResourceInstaller resourceInstaller = new ResourceInstaller(sourceUrl, auxdataSrcPath, auxdataTargetDir);
        resourceInstaller.install(".*", new NullProgressMonitor());

        File fresnelFile = new File(auxdataTargetDir, FresnelReflectionCoefficient.FRESNEL_COEFF);
        final Reader reader = new FileReader(fresnelFile);

        coeffW = new CoeffW(auxdataTargetDir, false, IcolConstants.AE_CORRECTION_MODE_AEROSOL);
    }



    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {
        Rectangle targetRect = targetTile.getRectangle();
        Rectangle sourceRect = rhoBracketAlgo.mapTargetRect(targetRect);

        Tile[] rhoRaec = new Tile[10];
        for (int i = 0; i < 10; i++) {
            rhoRaec[i] = getSourceTile(aeRayProduct.getBand("rho_ray_aerc_" + (i + 1)), sourceRect, pm);
        }
        final RhoBracketAlgo.Convolver convolver = rhoBracketAlgo.createConvolver(this, rhoRaec, targetRect, pm);

        long t1 = System.currentTimeMillis();
        for (int y = targetRect.y; y < targetRect.y + targetRect.height; y++) {
            for (int x = targetRect.x; x < targetRect.x + targetRect.width; x++) {
                if (x > 25 && y > 25 &&
                        x < targetBand.getSceneRasterWidth() - 25 && y < targetBand.getSceneRasterHeight() - 25) {
                    double roAerMean = convolver.convolveSample(x, y, 1, 8);
                    targetTile.setSample(x, y, roAerMean);
                } else {
                    targetTile.setSample(x, y, -1.0);
                }
            }
            pm.worked(1);
        }


        


        long t2 = System.currentTimeMillis();
        totalTime += (t2 - t1);
        totalPixels += (targetRect.width*targetRect.height);
        
        System.out.println("Tile / total time / total pixels: " + targetRect + " / " + totalTime+ " / " + totalPixels);
        System.out.println("Time per pixel: " + totalTime*1.0/totalPixels);
    }

    private void convolveCuda(Rectangle rect) {
        
    }


    public static class Spi extends OperatorSpi {

        public Spi() {
            super(CompareConvolutionOp.class);
        }
    }
}
