/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
 * 
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 * 
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.beam.meris.icol;

import com.bc.ceres.core.NullProgressMonitor;
import org.esa.beam.util.ResourceInstaller;
import org.esa.beam.util.SystemUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.net.URL;

import static org.junit.Assert.*;

/**
 * @author Thomas Storm
 */
public class FresnelReflectionCoefficientTest {

    private FresnelReflectionCoefficient coefficients;

    @Before
    public void setUp() throws Exception {
        String auxdataSrcPath = "auxdata/icol";
        final String auxdataDestPath = ".beam/beam-meris-icol/" + auxdataSrcPath;
        File auxdataTargetDir = new File(SystemUtils.getUserHomeDir(), auxdataDestPath);
        URL sourceUrl = ResourceInstaller.getSourceUrl(this.getClass());

        ResourceInstaller resourceInstaller = new ResourceInstaller(sourceUrl, auxdataSrcPath, auxdataTargetDir);
        resourceInstaller.install(".*", new NullProgressMonitor());

        File fresnelFile = new File(auxdataTargetDir, FresnelReflectionCoefficient.FRESNEL_COEFF);
        final Reader reader = new FileReader(fresnelFile);
        coefficients = new FresnelReflectionCoefficient(reader);
    }

    @Test
    public void testGetData() throws Exception {
        assertEquals( 0.81760066, coefficients.getCoeffFor( 88.144 ), 0.00001 );
        assertEquals( 0.27630406E-01, coefficients.getCoeffFor( 43.611 ), 0.00001 );
        // averaged value
        assertEquals( 0.684608855, coefficients.getCoeffFor( 86.2885 ), 0.00001 );
    }

}
