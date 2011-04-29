package org.esa.beam.meris.icol.performance;


import com.bc.ceres.glevel.MultiLevelImage;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.meris.icol.meris.MerisOp;

import javax.media.jai.JAI;
import java.io.File;
import java.io.IOException;

public class TileRecorder {

    public static final int TILE_CACHE_MEGAS = 256;

    static {
        GPF.getDefaultInstance().getOperatorSpiRegistry().loadOperatorSpis();
        JAI.getDefaultInstance().getTileCache().setMemoryCapacity(TILE_CACHE_MEGAS * 1024 * 1024);
        System.setProperty("beam.gpf.tileComputationObserver", TilePrinter.class.getName());
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("Usage: TileRecorder <product>");
            System.exit(1);
        }

        Product product = ProductIO.readProduct(new File(args[0]));
        MerisOp merisOp = new MerisOp();
        merisOp.setSourceProduct(product);

        final Product targetProduct = merisOp.getTargetProduct();
        Band[] bands = targetProduct.getBands();

        long tt0 = System.currentTimeMillis();
        for (Band band : bands) {
            //if ("aot_flags".equals(band.getName())) {
            MultiLevelImage sourceImage = band.getSourceImage();
            // Fetch center tile only
            int tileX = sourceImage.getNumXTiles() / 2;
            int tileY = sourceImage.getNumYTiles() / 2;
            long t0 = System.currentTimeMillis();
            sourceImage.getTile(tileX, tileY);
            long t1 = System.currentTimeMillis();
            //System.out.println("Computation of band " + band.getName() + " for tile " + tileX + "," + tileY + " took " + (t1 - t0) + " ms");
            //}
        }
        long tt1 = System.currentTimeMillis();
        System.out.println("Complete computation of center tile took " + (tt1 - tt0) + " ms");
    }
}
