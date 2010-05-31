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

import org.esa.beam.meris.icol.CoeffW;

import junit.framework.TestCase;

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
		InputStream inputStream = CoeffW.class.getResourceAsStream("/auxdata/icol/" +  CoeffW.FILENAME);
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
			assertEquals("for iaer="+iaer+"", 1, sum, 0.001);
		}
	}

}
