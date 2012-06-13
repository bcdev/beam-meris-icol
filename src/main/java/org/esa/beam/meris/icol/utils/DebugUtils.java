package org.esa.beam.meris.icol.utils;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.meris.brr.Rad2ReflOp;
import org.esa.beam.meris.brr.RayleighCorrectionOp;

/**
 * @author Olaf Danne
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
public class DebugUtils {

    public static void addSingleDebugFlagBand(Product targetProduct, Product sourceProduct, FlagCoding flagCoding, String flagBandName) {
        for (Band band : sourceProduct.getBands()) {
            if (!targetProduct.containsRasterDataNode(band.getName())) {
                if (band.getName().equals(flagBandName)) {
                    band.setSampleCoding(flagCoding);
                }
                targetProduct.addBand(band);
            }
        }
    }

    public static void addSingleDebugBand(Product targetProduct, Product sourceProduct, String bandName) {
        for (Band band : sourceProduct.getBands()) {
            if (band.getName().equals(bandName)) {
                targetProduct.addBand(band);
            }
        }
    }


    public static void addAeRayleighProductDebugBands(Product targetProduct, Product aeRayProduct) {
        // (v) rho_r_bracket from AE Rayleigh correction
        for (Band band : aeRayProduct.getBands()) {
            if (band.getName().startsWith("rho_ag_bracket")) {
                targetProduct.addBand(band);
            }
        }

        // (vi) delta_rho_ae_r from AE Rayleigh correction
        for (Band band : aeRayProduct.getBands()) {
            if (band.getName().startsWith("rho_aeRay_rayleigh")) {
                targetProduct.addBand(band);
            }
        }

        // (vii) delta_rho_lfm_r from AE Rayleigh correction
        for (Band band : aeRayProduct.getBands()) {
            if (band.getName().startsWith("rho_aeRay_fresnel")) {
                targetProduct.addBand(band);
            }
        }

        // (viii) = (iii) - (vi) + (vii)
        for (Band band : aeRayProduct.getBands()) {
            if (band.getName().startsWith("rho_ray_aerc")) {
                targetProduct.addBand(band);
            }
        }
    }

    public static void addAeAerosolProductDebugBands(Product targetProduct, Product aeAerProduct) {
        for (Band band : aeAerProduct.getBands()) {
            if (band.getName().equals("alpha_index")) {
                targetProduct.addBand(band);
            }
        }
        for (Band band : aeAerProduct.getBands()) {
            if (band.getName().equals("alpha")) {
                targetProduct.addBand(band);
            }
        }

        // (x) AOT
        for (Band band : aeAerProduct.getBands()) {
            if (band.getName().equals("aot")) {
                targetProduct.addBand(band);
            }
        }

        // (xa) rhoW*, rhoA*
        for (Band band : aeAerProduct.getBands()) {
            if (band.getName().startsWith("rhoW") || band.getName().startsWith("rhoA")) {
                targetProduct.addBand(band);
            }
        }
        // (xb) rho_brr_9
        for (Band band : aeAerProduct.getBands()) {
            if (band.getName().startsWith("rho_brr_")) {
                targetProduct.addBand(band);
            }
        }

        // (xii) rho_raec_bracket from AE Aerosol correction
        for (Band band : aeAerProduct.getBands()) {
            if (band.getName().startsWith("rho_raec_bracket")) {
                targetProduct.addBand(band);
            }
        }

        // (xiv) delta_rho_ae_r from AE Aerosol correction
        for (Band band : aeAerProduct.getBands()) {
            if (band.getName().startsWith("rho_aeAer_aerosol")) {
                targetProduct.addBand(band);
            }
        }

        // (xv) delta_rho_lfm_r from AE Aerosol correction
        for (Band band : aeAerProduct.getBands()) {
            if (band.getName().startsWith("rho_aeAer_fresnel")) {
                targetProduct.addBand(band);
            }
        }

        // (xiii) = (viii) - (xiv) + (xv)
        for (Band band : aeAerProduct.getBands()) {
            if (band.getName().startsWith("rho_raec_diff")) {
                targetProduct.addBand(band);
            }
        }
    }

    public static void addRad2ReflDebugBands(Product targetProduct, Product rad2ReflProduct) {
        for (Band band : rad2ReflProduct.getBands()) {
            if (band.getName().startsWith(Rad2ReflOp.RHO_TOA_BAND_PREFIX)) {
                targetProduct.addBand(band);
            }
        }
    }


    public static void addRayleighCorrDebugBands(Product targetProduct, Product rayleighProduct) {
        // (iii) BRR before AE correction
        for (Band band : rayleighProduct.getBands()) {
            if (band.getName().startsWith(RayleighCorrectionOp.BRR_BAND_PREFIX)) {
                targetProduct.addBand(band);
            }
        }
    }

    public static void addCtpProductDebugBand(Product targetProduct, Product ctpProduct, String ctpBandName) {
        for (Band band : ctpProduct.getBands()) {
            if (band.getName().equals(ctpBandName)) {
                targetProduct.addBand(band);
            }
        }
    }
}
