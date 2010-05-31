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

import java.io.IOException;
import java.io.Reader;

/**
 * Created by marcoz.
 *
 * @author marcoz
 * @version $Revision: 1.2 $ $Date: 2007/04/30 15:45:26 $
 */
public class CoeffW {

    public static final String FILENAME = "WHA3_FR";

    private double[][] wFR;

    public CoeffW(Reader reader) throws IOException {
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
}
