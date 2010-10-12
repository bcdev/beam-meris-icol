/*
 * $Id: FresnelReflectionCoefficient.java,v 1.1 2007/04/27 15:30:03 marcoz Exp $
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
import org.esa.beam.util.math.FractIndex;
import org.esa.beam.util.math.Interp;
import org.esa.beam.util.math.LUT;

import java.io.IOException;
import java.io.Reader;

/**
 * Created by marcoz.
 *
 * @author marcoz
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
public class FresnelReflectionCoefficient {

    public static final String FRESNEL_COEFF = "fresnel.txt";

    private LUT frCoeff;
    private FractIndex[] fractIndex;

    public FresnelReflectionCoefficient(Reader reader) throws IOException {
        loadFresnelReflectionCoefficient(reader);
        fractIndex = FractIndex.createArray(1);
    }

    private void loadFresnelReflectionCoefficient(Reader reader) throws IOException {
        try {
            final char [] separator = {','};
            final CsvReader csvReader = new CsvReader(reader, separator);
            double[] angel = new double[25];
            double[] fresnelData = new double[25];
            String[] record;
            int index = 0;
            while ((record = csvReader.readRecord()) != null) {
                angel[index] = Double.parseDouble(record[0].trim());
                fresnelData[index] = Double.parseDouble(record[1].trim());
                index++;
            }
            frCoeff = new LUT(fresnelData);
            frCoeff.setTab(0, angel);
        } finally {
            reader.close();
        }
    }

    public double getCoeffFor(double angle) {
        double[] tab = frCoeff.getTab(0);
		Interp.interpCoord(angle, tab, fractIndex[0]);
        return Interp.interpolate(frCoeff.getJavaArray(), fractIndex);
    }
}
