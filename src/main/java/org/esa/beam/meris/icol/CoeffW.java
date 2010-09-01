/*
 * $Id: CoeffW.java,v 1.2 2007/04/30 15:45:26 marcoz Exp $
 *
 * Copyright (C) 2007 by Brockmann Consult (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation. This program is distributed in the hope it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.esa.beam.meris.icol;

import org.esa.beam.util.io.CsvReader;

import javax.media.jai.KernelJAI;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Random;

/**
 * Created by marcoz.
 *
 * @author marcoz
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
public class CoeffW {

    public static final String FILENAME = "WHA3_FR_rayleigh_5S";

    public static final int FR_KERNEL_SIZE = 100;
    public static final int RR_KERNEL_SIZE = 25; 

    private File auxdataTargetDir;

    private double[][] wFR;
    private double[][] wRR;

    // correction mode:
    //    0: reshaped Rayleigh
    //    1: reshaped aerosol
    private int correctionMode;

    public CoeffW(File auxdataTargetDir,
                  boolean reshapedConvolution, int correctionMode) throws IOException {
        this.correctionMode = correctionMode;
        this.auxdataTargetDir = auxdataTargetDir;

        File wFile = new File(auxdataTargetDir, FILENAME);
        Reader reader = new FileReader(wFile);
        loadCoefficient(reader);
    }

    public CoeffW(Reader reader) throws IOException {
        File wFile = new File(auxdataTargetDir, FILENAME);
        loadCoefficient(reader);
    }

    private void loadCoefficient(Reader reader) throws IOException {
        try {
            final char [] separator = {' '};
            final CsvReader csvReader = new CsvReader(reader, separator);
            String[] record;
            int controlIndex = 0;
            wFR = new double[26][101];
            while ((record = csvReader.readRecord()) != null) {
                int index = Integer.parseInt(record[0].trim());
                if (index != controlIndex) {
                    throw new IllegalArgumentException("bad file for coeff W");
                }
                for (int i = 0; i < 26; i++) {
                    wFR[i][index] = Double.parseDouble(record[i + 1].trim());
                }
                controlIndex++;
            }
        } finally {
            reader.close();
        }
    }

    public double[][] getCoeffForFR() {
        return wFR;
    }

    public double[][] getCoeffForRR() {
        double[][] wRR = new double[26][26];
        for (int iaer = 0; iaer < 26; iaer++) {
            wRR[iaer][0] = wFR[iaer][0] + wFR[iaer][1] + wFR[iaer][2] * 0.5;
            int irr = 0;
            for (int i = 2; i <= 94; i += 4) {
                irr++;                                                        
                wRR[iaer][irr] = wFR[iaer][i] * 0.5 +
                					wFR[iaer][i+1] +
                					wFR[iaer][i+2] +
                					wFR[iaer][i+3] +
					                wFR[iaer][i+4] * 0.5;
            }
            wRR[iaer][25] = wFR[iaer][98] * 0.5 + wFR[iaer][99] + wFR[iaer][100];
        }
        return wRR;
    }

    private double[][] getReshapedRayleighCoeffForRR() {
        double[][] wRR = new double[26][9];
        double[][] wRROrig = getCoeffForRR();
        for (int iaer = 0; iaer < 26; iaer++) {
            for (int i=0; i<9; i++) {
                // reduce resolution to 3.6km: take every third value
                wRR[iaer][i] = wRROrig[iaer][3*i];
            }
        }

        return wRR;
    }

    public double[] getReshapedRayleighCoeffForRR(int iaer) {
        double[][] wRR = getReshapedRayleighCoeffForRR();
        for (int i=0; i<wRR[iaer-1].length; i++) {
            System.out.println("" + wRR[iaer-1][i]);
        }
        return wRR[iaer-1];
    }

    /**
     * Creates an array of the specified size, containing some random data
     */
    public static double[] createRandomDoubleData(int x, double stdev) {
        Random random = new Random(0);
        double a[] = new double[x];
        for (int i = 0; i < x; i++) {
            a[i] = stdev*random.nextGaussian();
        }
        return a;
    }

    private double[][] getReshapedRayleighCoeffForFR() {
        double[][] wFR = new double[26][8];
        double[][] wFROrig = getCoeffForFR();
        for (int iaer = 0; iaer < 26; iaer++) {
            for (int i=0; i<8; i++) {
                // reduce resolution to 3.6km: take every twelveth value
                wFR[iaer][i] = wFROrig[iaer][12*i];
            }
        }

        return wFR;
    }

    public double[] getReshapedRayleighCoeffForFR(int iaer) {
        double[][] wFR = getReshapedRayleighCoeffForFR();
        return wFR[iaer-1];
    }

    private double[][] getReshapedAerosolCoeffForRR() {
        double[][] wRR = new double[26][10];
        double[][] wRROrig = getCoeffForRR();
        for (int iaer = 0; iaer < 26; iaer++) {
            for (int i=0; i<10; i++) {
                wRR[iaer][i] = wRROrig[iaer][i];
            }
        }

        return wRR;
    }

    public double[] getReshapedAerosolCoeffForRR(int iaer) {
        double[][] wRR = getReshapedAerosolCoeffForRR();
        return wRR[iaer-1];
    }

    private double[][] getReshapedAerosolCoeffForFR() {
        double[][] wFR = new double[26][40];
        double[][] wFROrig = getCoeffForFR();
        for (int iaer = 0; iaer < 26; iaer++) {
            for (int i=0; i<40; i++) {
                wFR[iaer][i] = wFROrig[iaer][i];
            }
        }

        return wFR;
    }

    public double[] getReshapedAerosolCoeffForFR(int iaer) {
        double[][] wFR = getReshapedAerosolCoeffForFR();
        return wFR[iaer-1];
    }

    public KernelJAI getReshapedConvolutionKernelForRR(int iaer) {
        KernelJAI kernel = null;
        if (correctionMode == IcolConstants.AE_CORRECTION_MODE_RAYLEIGH) {
           kernel = createKernelByRotation(getReshapedRayleighCoeffForRR(iaer));
        }  else if (correctionMode == IcolConstants.AE_CORRECTION_MODE_AEROSOL) {
           kernel = createKernelByRotation(getReshapedAerosolCoeffForRR(iaer));
        }
        return kernel;
    }

    public KernelJAI getReshapedConvolutionKernelForRROffNadir(int iaer) {
        // test
        KernelJAI kernel = null;
        if (correctionMode == IcolConstants.AE_CORRECTION_MODE_RAYLEIGH) {
           kernel = createKernelOffNadir(createFilterOffNadir("W_ray30.txt"));
        }  else if (correctionMode == IcolConstants.AE_CORRECTION_MODE_AEROSOL) {
           kernel = createKernelOffNadir(createFilterOffNadir("W_aer30.txt"));
        }
        return kernel;
    }

    public KernelJAI getReshapedConvolutionKernelForFR(int iaer) {
        KernelJAI kernel = null;
        if (correctionMode == IcolConstants.AE_CORRECTION_MODE_RAYLEIGH) {
           kernel = createKernelByRotation(getReshapedRayleighCoeffForFR(iaer));
        }  else if (correctionMode == IcolConstants.AE_CORRECTION_MODE_AEROSOL) {
           kernel = createKernelByRotation(getReshapedAerosolCoeffForFR(iaer));
        }
        return kernel;
    }

    public int getCorrectionMode() {
        return correctionMode;
    }

    public static KernelJAI createKernelByRotation(double[] array) {
        float[] kernelData = createFilterByRotation(array);

        int m = 2 * array.length - 1;
        return new KernelJAI(m, m, kernelData);
    }

    public static KernelJAI createKernelOffNadir(float[] array) {
        int m = (int) (Math.sqrt(array.length));
        return new KernelJAI(m, m, array);
    }

    public static float[] createFilterOffNadir(String fileName) {
        KernelOffNadir kernelOffNadir = new KernelOffNadir(fileName);
        float[] kernelData = kernelOffNadir.getKernel();
        normalize(kernelData);
        return kernelData;
    }

    public static float[] createFilterByRotation(double[] array) {
        int n = array.length;
        int m = 2 * n - 1;
        float[] kernelData = new float[m * m];
        HashMap<Integer, Integer> counts = new HashMap<Integer, Integer>();
        for (int y = 0; y < m; y++) {
            for (int x = 0; x < m; x++) {
                int index = computeIndex(x, y, n);
                if (index < n) {
                    Integer count = counts.get(index);
                    if (count == null) {
                        counts.put(index, 1);
                    } else {
                        counts.put(index, count + 1);
                    }
                    // System.out.print("" + index);
                    kernelData[y * m + x] = (float) array[index];
                } else {
                    // System.out.print("#");
                    kernelData[y * m + x] = 0.0f;
                }
            }
        }

        for (int y = 0; y < m; y++) {
            for (int x = 0; x < m; x++) {
                int index = computeIndex(x, y, n);
                if (index < n) {
                    Integer count = counts.get(index);
                    //System.out.print("" + count%10);
                    kernelData[y * m + x] /= count;
                } else {
                    //System.out.print("0");
                }
            }
        }

        normalize(kernelData);
        return kernelData;
    }

    private static void normalize(float[] kernelData) {
        float sum = 0;
        for (int i = 0; i < kernelData.length; i++) {
            sum += kernelData[i];
        }
        System.out.println("kernel sum = " + sum);
        for (int i = 0; i < kernelData.length; i++) {
            kernelData[i] /= sum;
        }
    }

    static int computeIndex(int x, int y, int n) {
        int dx = x - (n - 1);
        int dy = y - (n - 1);
        return (int) Math.sqrt(dx * dx + dy * dy);
    }
}
