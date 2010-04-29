package org.esa.beam.meris.icol.utils;

import org.geotools.math.Complex;

import java.util.Random;

/**
 * FFT utility class
 *
 * @author Olaf Danne
 * @version $Revision: 8400 $ $Date: 2010-02-12 16:34:16 +0100 (Fr, 12 Feb 2010) $
 */
public class FFTUtils {

    /**
     * A simple 1D DFT of complex input
     *
     * @param xr - array of real parts
     * @param xi - array of imaginary parts
     * @param forward  - direction (true if forward)
     * @return  Complex[] - FFT output
     */
    public static Complex[] simpleDFT(float[] xr, float[] xi, boolean forward) {
        int N = xr.length;
        Complex[] X = new Complex[N];
        for (int n=0; n<N; n++) {
            X[n] = new Complex();
            X[n].real = 0.0;
            X[n].imag = 0.0;
            for (int m=0; m<N; m++) {
                double w = 2.0 * Math.PI * m * n / N;
                if (forward) {
                    w *= -1.0;
                }
                double wr = Math.cos(w);
                double wi = Math.sin(w);
                X[n].real += (wr*xr[m] - wi*xi[m]); 
                X[n].imag += (wr*xi[m] + wi*xr[m]);
            }
            X[n].real *= (2.0/Math.sqrt(N));
            X[n].imag *= (2.0/Math.sqrt(N));
        }
        return X;
    }

    /**
     * A simple 1D DFT of complex input
     *
     * @param x - complex input array
     * @param forward  - direction (true if forward)
     * @return  Complex[] - FFT output
     */
    public static Complex[] simpleDFT(Complex[] x, boolean forward) {
        int N = x.length;
        Complex[] X = new Complex[N];
        for (int n=0; n<N; n++) {
            X[n] = new Complex();
            X[n].real = 0.0;
            X[n].imag = 0.0;
            for (int m=0; m<N; m++) {
                double w = 2.0 * Math.PI * m * n / N;
                if (forward) {
                    w *= -1.0;
                }
                double wr = Math.cos(w);
                double wi = Math.sin(w);
                X[n].real += (wr*x[m].real - wi*x[m].imag);
                X[n].imag += (wr*x[m].imag + wi*x[m].real);
            }
            X[n].real *= (2.0/Math.sqrt(N));
            X[n].imag *= (2.0/Math.sqrt(N));
        }
        return X;
    }

    /**
     * A simple 1D DFT of complex input
     *
     * @param x - input array
     * @param forward  - direction (true if forward)
     * @return  Complex[] - FFT output
     */
    public static Complex[] simpleDFT(float[] x, boolean forward) {
        int N = x.length/2;
        Complex[] X = new Complex[N];
        for (int n=0; n<N; n++) {
            X[n] = new Complex();
            X[n].real = 0.0;
            X[n].imag = 0.0;
            for (int m=0; m<N; m++) {
                double w = 2.0 * Math.PI * m * n / N;
                if (forward) {
                    w *= -1.0;
                }
                double wr = Math.cos(w);
                double wi = Math.sin(w);
                X[n].real += (wr*x[2*m] - wi*x[2*m+1]);
                X[n].imag += (wr*x[2*m+1] + wi*x[2*m]);
            }
            X[n].real *= (2.0/Math.sqrt(N));
            X[n].imag *= (2.0/Math.sqrt(N));
        }
        return X;
    }

    /**
     * splits a complex array [c1,c2,...,cn] into float array [x1r,x1i,x2r,x2i,...,xnr,xni]
     *
     * @param co - input array
     * @return float[] - output array
     */
    public static float[] splitComplexToFloatArray(Complex[] co) {
        float[] floatArray = new float[2*co.length];
        int floatArrayIndex = 0;
        for (int i=0; i<co.length; i++) {
            floatArray[floatArrayIndex] = (float) co[i].real;
            floatArrayIndex++;
            floatArray[floatArrayIndex] = (float) co[i].imag;
            floatArrayIndex++;
        }
        return floatArray;
    }

    /**
     * converts FFT 1D output array [x1r,x1i,x2r,x2i,...,xnr,xni] to 1D array of complex numbers [c1,c2,...,cn]
     *
     * @param outputFFT  - original output from FFT
     * @return Complex[] - the complex array
     */
    public static Complex[] getFFTOutputAsComplexNumbers(float[] outputFFT) {
        Complex[] co = new Complex[outputFFT.length/2];
        for (int i=0; i<co.length; i++) {
            co[i] = new Complex();
            co[i].real = outputFFT[2*i];
            co[i].imag = outputFFT[2*i+1];
        }
        return co;
    }

    /**
     * adds complex conjugate output to FFT 1D results
     *
     * @param coIn  - input array
     * @return  Complex[] - output array
     */
     public static Complex[] addComplexConjugates(Complex[] coIn) {
         int N;
         Complex[] coOut = null;
         if (coIn.length % 2 == 0) {
             // originally odd input!
             N = 2*coIn.length -1;
             coOut = new Complex[N];
             for (int i=0; i<coIn.length; i++) {
                 coOut[i] = new Complex(coIn[i].real, coIn[i].imag);
             }
             for (int i=0; i<coIn.length-1; i++) {
                 coOut[coIn.length+i] = new Complex(coIn[coIn.length-1-i].real, -coIn[coIn.length-1-i].imag);
             }
         } else {
             N = 2*coIn.length -2;
             coOut = new Complex[N];
             for (int i=0; i<coIn.length; i++) {
                 coOut[i] = new Complex(coIn[i].real, coIn[i].imag);
             }
             for (int i=0; i<coIn.length-2; i++) {
                 coOut[coIn.length+i] = new Complex(coIn[coIn.length-2-i].real, -coIn[coIn.length-2-i].imag);
             }
         }
         return coOut;
     }

    /**
     * CURRENTLY NOT USED
     * creates 1D complex array (with zero imaginary parts) from 2D real input
     *
     * @param real2DInput - real input array
     * @return  float[] - complex array
     */
    public static float[] createJCudaFFTComplexInputArray(float[][] real2DInput) {
         int NX = real2DInput.length;
         int NY = real2DInput[0].length;
         float[] result = new float[2*NX*NY];

         int resultIndex = 0;
         for (int i=0; i<NX; i++) {
             for (int j=0; j<NY; j++) {
                 result[resultIndex] = real2DInput[i][j]; // real part
                 resultIndex++;
                 result[resultIndex] = 0.0f;  // imaginary part
                 resultIndex++;
             }
         }
         return result;
     }

    /**
     * Creates an array of the specified size, containing some random data
     *
     * @param x - dimension of output array
     * @return  float[]
     */
    public static float[] createRandomFloatData(int x) {
        Random random = new Random(0);
        float a[] = new float[x];
        for (int i = 0; i < x; i++) {
            a[i] = random.nextFloat();
        }
        return a;
    }

    //////////////////////////////////////////////////////////////////////
    // END OF PUBLIC
    /////////////////////////////////////////////////////////////////////

    private static Complex[][] getFFTResultAsTransposedComplexArray(float[][] fftResultArray) {
        int NY = fftResultArray.length;
        int NX = fftResultArray[0].length;

        Complex[][] coArray = new Complex[NX / 2][NY];
        for (int i = 0; i < NX - 1; i += 2) {
            coArray[i / 2] = new Complex[NY];
            for (int j = 0; j < NY; j++) {
                coArray[i / 2][j] = new Complex();
                coArray[i / 2][j].real = fftResultArray[j][i];
                coArray[i / 2][j].imag = fftResultArray[j][i + 1];
            }
        }


        return coArray;
    }

    private static Complex[][] getFFTResultAsTransposedComplexArray(Complex[][] fftResultArray) {
        int NY = fftResultArray.length;
        int NX = fftResultArray[0].length;

        Complex[][] coArray = new Complex[NX][NY];
        for (int i = 0; i < NX; i++) {
            coArray[i] = new Complex[NY];
            for (int j = 0; j < NY; j++) {
                coArray[i][j] = new Complex(fftResultArray[j][i].real, fftResultArray[j][i].imag);
            }
        }

        return coArray;
    }

    private static float[][] swap(int NX, int NY, float[][] input) {
        float[][] inputSwapped = new float[NY][NX];
        for (int i = 0; i < NX; i++) {
            for (int j = 0; j < NY; j++) {
                inputSwapped[j][i] = input[i][j];
            }
        }
        return inputSwapped;
    }

}
