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

import org.esa.beam.meris.icol.AerosolScatteringFuntions;

import junit.framework.TestCase;

/**
 * Created by marcoz.
 *
 * @author marcoz
 * @version $Revision: $ $Date: $
 */
public class AerosolScatteringFuntionsTest extends TestCase {

	public void testSelectIzaNearest() throws Exception {
		assertEquals(0, AerosolScatteringFuntions.selectIzaNearest(0));
		assertEquals(1, AerosolScatteringFuntions.selectIzaNearest(5));
		assertEquals(2, AerosolScatteringFuntions.selectIzaNearest(16));
		assertEquals(12, AerosolScatteringFuntions.selectIzaNearest(90));
	}
	
	public void testSelectIzaUpperIndex() throws Exception {
		assertEquals(1, AerosolScatteringFuntions.selectIzaUpperIndex(0));
		assertEquals(2, AerosolScatteringFuntions.selectIzaUpperIndex(5));
		assertEquals(2, AerosolScatteringFuntions.selectIzaUpperIndex(16));
		assertEquals(12, AerosolScatteringFuntions.selectIzaUpperIndex(80));
		assertEquals(12, AerosolScatteringFuntions.selectIzaUpperIndex(90));
	}
	
	public void testAerosolTranmittance() throws Exception {
		AerosolScatteringFuntions aerosolScatteringFuntions = new AerosolScatteringFuntions();
		double d = aerosolScatteringFuntions.aerosolTranmittance(1, 1, 1);
		assertEquals(0.992361, d);
	}
	
	public void testAerosol() throws Exception {
		AerosolScatteringFuntions aerosolScatteringFuntions = new AerosolScatteringFuntions();
		double d = aerosolScatteringFuntions.aerosolPrimaryReflectance(45, 45, 13, 7);
		assertEquals(1.237436867076458, d);
	}
	
	public void testReadFourierAerosol() throws Exception {
		AerosolScatteringFuntions aerosolScatteringFuntions = new AerosolScatteringFuntions();
		double[][][][] ds = aerosolScatteringFuntions.readFourierAerosol(1);
		assertEquals(1.00274, ds[1][1][0][0]);
		double[][][][] ds8 = aerosolScatteringFuntions.readFourierAerosol(8);
		assertEquals(1.00724, ds8[1][1][0][0]);
		double[][][][] ds4= aerosolScatteringFuntions.readFourierAerosol(4);
		assertEquals(1.00724, ds8[1][1][0][0]);
	}
	
	public void testAerosolPhase() throws Exception {
		AerosolScatteringFuntions aerosolScatteringFuntions = new AerosolScatteringFuntions();
		double d = aerosolScatteringFuntions.aerosolPhase(45, 1);
		assertEquals(1.2018744286197576, d);
	}
}
