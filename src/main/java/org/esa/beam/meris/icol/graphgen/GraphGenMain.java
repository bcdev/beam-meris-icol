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


package org.esa.beam.meris.icol.graphgen;

import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.meris.icol.meris.MerisOp;
import org.esa.beam.meris.icol.tm.TmOp;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Test tool for the {@link GraphGen} class.
 * <pre>
 *     Usage: GraphGenMain <productPath> <graphmlPath> 'meris'|'landsat' [[<hideBands>] <hideProducts>]
 * </pre>
 *
 * @author Thomas Storm
 * @author Norman Fomferra
 */
public class GraphGenMain {

    static {
        GPF.getDefaultInstance().getOperatorSpiRegistry().loadOperatorSpis();
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.out.println("Usage: GraphGenMain <productPath> <graphmlPath> 'meris'|'landsat' [[<hideBands>] <hideProducts>]");
        }
        String productPath = args[0];
        String graphmlPath = args[1];
        String opSelector = args[2];
        String hideBandsArg = args.length > 3 ? args[3] : null;
        String hideProductsArg = args.length > 4 ? args[4] : null;

        Operator op;
        if (opSelector.equalsIgnoreCase("meris")) {
            op = new MerisOp();
        } else if (opSelector.equalsIgnoreCase("landsat")) {
            op = new TmOp();
        } else {
            throw new IllegalArgumentException("argument 3 must be 'meris' or 'landsat'.");
        }

        final Product sourceProduct = ProductIO.readProduct(new File(productPath));
        op.setSourceProduct(sourceProduct);
        final Product targetProduct = op.getTargetProduct();

        FileWriter fileWriter = new FileWriter(new File(graphmlPath));
        BufferedWriter writer = new BufferedWriter(fileWriter);

        final GraphGen graphGen = new GraphGen();
        boolean hideBands = hideBandsArg != null && Boolean.parseBoolean(hideBandsArg);
        final boolean hideProducts = hideProductsArg != null && Boolean.parseBoolean(hideProductsArg);
        if (hideProducts) {
            hideBands = true;
        }
        GraphMLHandler handler = new GraphMLHandler(writer, hideBands, hideProducts);
        graphGen.generateGraph(targetProduct, handler);
        writer.close();
    }

}
