package org.esa.beam.meris.icol;

import org.esa.beam.framework.gpf.OperatorException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Class for reading off-nadir kernels provided by RS.
 * (This is more or less a test class at this point. Check if it will be needed finally.)
 *
 * @author Olaf Danne
 * @version $Revision: $ $Date:  $
 */
public class KernelOffNadir {

    public static final int KERNEL_MATRIX_WIDTH_RR = 51;

    private String fileName;

    private List<Float> kernelList;

    public KernelOffNadir(String fileName) {
        this.fileName = fileName;
        try {
            if (fileName.startsWith("W_ray")) {
                readKernelMatrixRayleigh();
            } else if (fileName.startsWith("W_aer")) {
                readKernelMatrixAerosol();
            }
        } catch (IOException e) {
            // todo: handle or propagate
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    public float[] getKernelAsArray() {
        float[] result = new float[kernelList.size()];
        for (int i = 0; i < kernelList.size(); i++) {
            result[i] = kernelList.get(i);
        }
        return result;
    }

    private void readKernelMatrixRayleigh() throws IOException {
        BufferedReader bufferedReader;
        InputStream inputStream;
        inputStream = CoeffW.class.getResourceAsStream(fileName);
        bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

        StringTokenizer st;
        try {
            kernelList = new ArrayList<Float>();
            int i = 0;
            String line;
            while ((line = bufferedReader.readLine()) != null && i < KERNEL_MATRIX_WIDTH_RR) {
                line = line.trim();
                st = new StringTokenizer(line, ", ", false);
                 // reduced resulution, take every 3rd value catching the center...
                if ((i-1) % 3 == 0) {
                    int j = 0;
                    while (st.hasMoreTokens() && j < KERNEL_MATRIX_WIDTH_RR) {
                        // W matrix element
                        if ((j-1) % 3 == 0) {
                            kernelList.add(Float.parseFloat(st.nextToken()));
                        } else {
                            st.nextToken();
                        }
                        j++;
                    }
                }
                i++;
            }
        } catch (IOException e) {
            throw new OperatorException("Failed to load W matrix: \n" + e.getMessage(), e);
        } catch (NumberFormatException e) {
            throw new OperatorException("Failed to load W matrix: \n" + e.getMessage(), e);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    private void readKernelMatrixAerosol() throws IOException {
        BufferedReader bufferedReader;
        InputStream inputStream;
        inputStream = CoeffW.class.getResourceAsStream(fileName);
        bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

        StringTokenizer st;
        try {
            kernelList = new ArrayList<Float>();
            int i = 0;
            String line;
            while ((line = bufferedReader.readLine()) != null && i < KERNEL_MATRIX_WIDTH_RR) {
                line = line.trim();
                st = new StringTokenizer(line, ", ", false);

                int j = 0;
                while (st.hasMoreTokens() && j < KERNEL_MATRIX_WIDTH_RR) {
                    kernelList.add(Float.parseFloat(st.nextToken()));
                    j++;
                }
                i++;
            }
        } catch (IOException e) {
            throw new OperatorException("Failed to load W matrix: \n" + e.getMessage(), e);
        } catch (NumberFormatException e) {
            throw new OperatorException("Failed to load W matrix: \n" + e.getMessage(), e);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

}
