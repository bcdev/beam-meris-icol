/*
 * $Id: $
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

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import junit.framework.TestCase;

import javax.media.jai.KernelJAI;

/**
 * Created by marcoz.
 *
 * @author marcoz
 * @version $Revision: $ $Date: $
 */
public class CoeffWTest extends TestCase {

    private CoeffW coeffW;

    @Override
    protected void setUp() throws Exception {
        InputStream inputStream = CoeffW.class.getResourceAsStream("/auxdata/icol/" + CoeffW.FILENAME);
        Reader wReader = new InputStreamReader(inputStream);
        coeffW = new CoeffW(wReader);
    }

    public void testFR() {
        double[][] coeffForFR = coeffW.getCoeffForFR();
        checkNormalizedToOne(coeffForFR);
    }

    public void testRR() {
        double[][] coeffForRR = coeffW.getCoeffForRR();
        checkNormalizedToOne(coeffForRR);
    }

    private void checkNormalizedToOne(double[][] coeff) {
        for (int iaer = 1; iaer < coeff.length; iaer++) {
            double sum = 0;
            for (int j = 0; j < coeff[iaer].length; j++) {
                sum += coeff[iaer][j];
            }
            assertEquals("for iaer=" + iaer + "", 1, sum, 0.001);
        }
    }

    public void testKernelJai() {
        final double[] inputArray1D = new double[]{1.0d, 0.72d, 0.48d, 0.32d, 0.08d};
        final int N = inputArray1D.length;

        KernelJAI kernel = CoeffW.createKernelByRotation(inputArray1D);
        assertNotNull(kernel);

        assertEquals(N - 1, kernel.getXOrigin());
        assertEquals(N - 1, kernel.getYOrigin());
        assertEquals(2 * N - 1, kernel.getWidth());
        assertEquals(2 * N - 1, kernel.getHeight());

        float[] kernelData = kernel.getKernelData();
        assertEquals((2 * N - 1) * (2 * N - 1), kernelData.length);

        assertEquals(0.0f, kernel.getElement(0, 0), 0.0f);
        assertEquals(0.0f, kernel.getElement(0, 2 * N - 2), 0.0f);
        assertEquals(0.0f, kernel.getElement(2 * N - 2, 0), 0.0f);
        assertEquals(0.0f, kernel.getElement(2 * N - 2, 2 * N - 2), 0.0f);

    }

    public void testReadKernelOffNadir() {
        // Rayleigh:
        float[] kernel = CoeffW.createFilterOffNadir("W_ray0.txt");
        assertEquals(289, kernel.length);
        // todo: consider normalization
//        assertEquals(0.0f, kernel[0]);
//        assertEquals(0.000137f, kernel[7], 1.E-5);
//        assertEquals(0.000296f,
//                     kernel[144] , 1.E-5);
//        assertEquals(0.0f, kernel[288]);

        // aerosol:
        kernel = CoeffW.createFilterOffNadir("W_aer0.txt");
        assertEquals(2601, kernel.length);
    }
   
}
