package org.esa.beam.meris.icol;

import junit.framework.TestCase;

import org.geotools.math.Complex;
import org.esa.beam.meris.icol.utils.FFTUtils;

/**
 * @author Olaf Danne
 * @version $Revision: 8400 $ $Date: 2010-02-12 16:34:16 +0100 (Fr, 12 Feb 2010) $
 */
public class ConvolutionTest extends TestCase {


    @Override
    protected void setUp() throws Exception {
    }

    public void testMyDFT() {
        // test of own DFT implementation

        int N = 8;
        Complex[] xc = new Complex[N];

        for (int i=0; i<N; i++) {
            xc[i] = new Complex();
            xc[i].real = 0.0;
            xc[i].imag = 0.0;
        }
        xc[2].real = 2.0;
        xc[3].real = 3.0;
        xc[4].real = 4.0;

        Complex[] X1 = FFTUtils.simpleDFT(xc, true);

        float[] xr = new float[N];
        float[] xi = new float[N];
        for (int i=0; i<N; i++) {
            xr[i] = 0.0f;
            xi[i] = 0.0f;
        }
        xr[2] = 2.0f;
        xr[3] = 3.0f;
        xr[4] = 4.0f;
        Complex[] X2 = FFTUtils.simpleDFT(xr, xi, true);

        float[] x = new float[]{0.0f, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 3.0f, 0.0f, 4.0f, 0.0f,
                0.0f,0.0f,0.0f,0.0f,0.0f,0.0f};
        Complex[] X3 = FFTUtils.simpleDFT(x, true);
        assertEquals(8, X1.length);
        assertEquals(X1.length, X2.length);
        assertEquals(X1.length, X3.length);
        for (int i = 0; i < X1.length; i++) {
            assertEquals(X1[i], X2[i]);
            assertEquals(X1[i], X3[i]);
        }
        assertEquals(6.363, X1[0].real, 1.E-3);
        assertEquals(0.0, X1[0].imag, 1.E-3);
        assertEquals(-4.328, X1[1].real, 1.E-3);
        assertEquals(-2.914, X1[1].imag, 1.E-3);
        assertEquals(1.414, X1[2].real, 1.E-3);
        assertEquals(2.121, X1[2].imag, 1.E-3);
        assertEquals(-1.328, X1[3].real, 1.E-3);
        assertEquals(-0.085, X1[3].imag, 1.E-3);
        assertEquals(2.121, X1[4].real, 1.E-3);
        assertEquals(0.0, X1[4].imag, 1.E-3);
        assertEquals(X1[5].real, X1[3].real, 1.E-3);
        assertEquals(X1[5].imag, -X1[3].imag, 1.E-3);
        assertEquals(X1[6].real, X1[2].real, 1.E-3);
        assertEquals(X1[6].imag, -X1[2].imag, 1.E-3);
        assertEquals(X1[7].real, X1[1].real, 1.E-3);
        assertEquals(X1[7].imag, -X1[1].imag, 1.E-3);
    }

    public void testSplitComplexToFloatArray(){
        int N = 3;
        Complex[] xc = new Complex[N];
        for (int i=0; i<N; i++) {
            xc[i] = new Complex();
        }
        xc[0].real = 1.0;
        xc[1].real = 2.0;
        xc[2].real = 3.0;
        xc[0].imag = 4.0;
        xc[1].imag = 5.0;
        xc[2].imag = 6.0;

        float[] array = FFTUtils.splitComplexToFloatArray(xc);
        assertEquals(2*N, array.length);
        assertEquals(1.0f, array[0]);
        assertEquals(4.0f, array[1]);
        assertEquals(2.0f, array[2]);
        assertEquals(5.0f, array[3]);
        assertEquals(3.0f, array[4]);
        assertEquals(6.0f, array[5]);
    }

    public void testGetFFTOutputAsComplexNumbers() {
        float[] input = new float[]{1.0f,4.0f,2.0f,5.0f,3.0f,6.0f};
        Complex[] co = FFTUtils.getFFTOutputAsComplexNumbers(input);
        assertEquals(3, co.length);
        assertEquals(1.0, co[0].real);
        assertEquals(4.0, co[0].imag);
        assertEquals(2.0, co[1].real);
        assertEquals(5.0, co[1].imag);
        assertEquals(3.0, co[2].real);
        assertEquals(6.0, co[2].imag);
    }

    public void testAddComplexConjugates() {
        // first test: N even
        int N = 2;
        Complex[] xc = new Complex[N];

        for (int i=0; i<N; i++) {
            xc[i] = new Complex();
        }
        xc[0].real = 18.0;
        xc[1].real = -7.5;
        xc[0].imag = 0.0;
        xc[1].imag = 0.86;

        Complex[] result = FFTUtils.addComplexConjugates(xc);
        assertEquals(3,result.length);
        assertEquals(result[0].real, xc[0].real);
        assertEquals(result[0].imag, xc[0].imag);
        assertEquals(result[1].real, xc[1].real);
        assertEquals(result[1].imag, xc[1].imag);
        assertEquals(-7.5, result[2].real);
        assertEquals(-0.86, result[2].imag);

        // second test: N odd
        N = 3;
        Complex[] xc2 = new Complex[N];

        for (int i=0; i<N; i++) {
            xc2[i] = new Complex();
        }
        xc2[0].real = 18.0;
        xc2[1].real = -7.5;
        xc2[2].real = 25.0;
        xc2[0].imag = 0.0;
        xc2[1].imag = 0.86;
        xc2[2].imag = 35.0;
        Complex[] result2 = FFTUtils.addComplexConjugates(xc2);
        assertEquals(4,result2.length);
        assertEquals(xc2[2].real, result2[2].real);
        assertEquals(xc2[2].imag, result2[2].imag);
        assertEquals(-7.5, result2[3].real);
        assertEquals(-0.86, result2[3].imag);

    }

    private void compareFloatArrays(float[] a1, float[] a2, double eps) {
        assertEquals(a1.length, a2.length);
        for (int i=0; i<a1.length; i++) {
            if (i != 1) {
                assertEquals(a1[i], a2[i], eps);
            }
        }
    }
}
