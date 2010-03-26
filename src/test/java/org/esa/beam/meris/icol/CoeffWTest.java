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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.File;
import java.io.IOException;

import org.esa.beam.meris.icol.CoeffW;
import org.esa.beam.util.SystemUtils;

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
//        int N = 60;
//        final double[] inputArray1D = new double[N];
        
        KernelJAI kernel = CoeffW.createKernelByRotation(inputArray1D);
        assertNotNull(kernel);

        assertEquals(N - 1, kernel.getXOrigin());
        assertEquals(N - 1, kernel.getYOrigin());
        assertEquals(2 * N - 1, kernel.getWidth());
        assertEquals(2 * N - 1, kernel.getHeight());

        float[] kernelData = kernel.getKernelData();
        assertEquals((2 * N - 1) * (2 * N - 1), kernelData.length);

//        assertEquals(1.0f, kernel.getElement(N - 1, N - 1), 0.0f);

        assertEquals(0.0f, kernel.getElement(0, 0), 0.0f);
        assertEquals(0.0f, kernel.getElement(0, 2 * N - 2), 0.0f);
        assertEquals(0.0f, kernel.getElement(2 * N - 2, 0), 0.0f);
        assertEquals(0.0f, kernel.getElement(2 * N - 2, 2 * N - 2), 0.0f);

//        assertEquals(0.08f, kernel.getElement(2 * N - 2, N), 0.0f);
//        assertEquals(0.08f, kernel.getElement(N, 2 * N - 2), 0.0f);
    }

//    public void testSetReshapedCoeffs() {
//        String auxdataSrcPath = "auxdata/icol";
//        final String auxdataDestPath = ".beam/beam-meris-icol/" + auxdataSrcPath;
//        File auxdataTargetDir = new File(SystemUtils.getUserHomeDir(), auxdataDestPath);
//        try {
//            coeffW = new CoeffW(auxdataTargetDir, true, true, 0);
//            assertNotNull(coeffW.getReshapedCoeffForRR());
//            assertNotNull(coeffW.getReshapedCoeffForFR());
//
//        } catch (IOException e) {
//            fail(e.getMessage());
//        }
//    }
}
