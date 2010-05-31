/*
 * $Id: CoeffWD.java,v 1.1 2007/04/30 15:45:26 marcoz Exp $
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
import java.util.Arrays;

/**
 * Created by marcoz.
 *
 * @author marcoz
 * @version $Revision: 1.1 $ $Date: 2007/04/30 15:45:26 $
 */
public class CoeffWD {

    public static final String W_FR = "WD_ray_fr.csv";
    public static final String W_RR = "WD_ray_rr.csv";

    private double[] wCoeff;

    public CoeffWD(Reader reader) throws IOException {
        loadCoefficient(reader);
    }

    private void loadCoefficient(Reader reader) throws IOException {
        try {
            final char [] separator = {','};
            final CsvReader csvReader = new CsvReader(reader, separator);
            String[] record;
            int maxIndex = 0;
            double[] tmpArray = new double[250];
            while ((record = csvReader.readRecord()) != null) {
                int index = Integer.parseInt(record[0].trim());
                double w = Double.parseDouble(record[1].trim());
                maxIndex = Math.max(maxIndex, index);
                tmpArray[index] = w;
            }
            wCoeff = Arrays.copyOf(tmpArray, maxIndex + 1);
        } finally {
            reader.close();
        }
    }

    public double[] getWCoeff() {
        return wCoeff;
    }
}
