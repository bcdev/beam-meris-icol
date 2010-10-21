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
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.meris.icol.meris.MerisOp;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

public class GraphGenMain {

    static {
        GPF.getDefaultInstance().getOperatorSpiRegistry().loadOperatorSpis();
    }

    public static void main(String[] args) throws IOException {
        MerisOp op = new MerisOp();
        if (args.length < 2) {
            throw new IllegalArgumentException("Input and output file locations needed.");
        }

        final Product sourceProduct = ProductIO.readProduct(new File(args[0]));
        op.setSourceProduct(sourceProduct);
        final Product target = op.getTargetProduct();

        FileWriter fileWriter = new FileWriter(new File(args[1]));
        BufferedWriter writer = new BufferedWriter(fileWriter);

        final GraphGen graphGen = new GraphGen();
        MyHandler handler = new MyHandler(writer, args.length == 3 && Boolean.parseBoolean(args[2]));
        graphGen.generateGraph(target, handler);
        writer.close();
    }

    private static class MyHandler implements GraphGen.Handler {

        private Writer writer;
        private boolean hideBands;

        private Map<Band, Integer> bandIds = new HashMap<Band, Integer>();
        private Map<Operator, Integer> operatorIds = new HashMap<Operator, Integer>();
        private Map<Product, Integer> productIds = new HashMap<Product, Integer>();

        private int nodeId = 1;
        private int graphId = 2;
        private int edgeId = 1;

        private static final String SHAPE_RECTANGLE = "rectangle";
        private static final String SHAPE_OCTAGON = "octagon";
        private static final String SHAPE_TRAPEZOID = "trapezoid";

        private MyHandler(Writer writer, boolean hideBands) {
            this.writer = writer;
            this.hideBands = hideBands;
        }

        @Override
        public void handleBeginGraph() {
            try {
                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                             "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\" xmlns:xsi=\"http://www.w3.org/2001/" +
                             "XMLSchema-instance\" xmlns:y=\"http://www.yworks.com/xml/graphml\" xmlns:yed=\"" +
                             "http://www.yworks.com/xml/yed/3\" xsi:schemaLocation=\"http://graphml.graphdrawing.org/xmlns " +
                             "http://www.yworks.com/xml/schema/graphml/1.1/ygraphml.xsd\">\n" +
                             "    <key for=\"node\" id=\"d0\" yfiles.type=\"nodegraphics\"/>\n" +
                             "    <key for=\"edge\" id=\"d1\" yfiles.type=\"edgegraphics\"/>\n" +
                             "    <graph>\n");
            } catch (IOException ignored) {
            }
        }

        @Override
        public void handleEndGraph() {
            try {
                writer.write("    </graph>\n" +
                             "</graphml>");
            } catch (IOException ignored) {
            }
        }

        @Override
        public void generateOp2BandEdge(Operator operator, Band band) {
            if (!hideBands) {
                final boolean isTarget = band.getProduct() == operator.getTargetProduct();
                try {
                    if (isTarget) {
                        writer.write(
                                String.format("        <edge id=\"e%d\" source=\"n%d\" target=\"n%d\"/>\n", edgeId++,
                                              operatorIds.get(operator),
                                              bandIds.get(band)));
                    } else {
                        writer.write(
                                String.format("        <edge id=\"e%d\" source=\"n%d\" target=\"n%d\">\n", edgeId++,
                                              operatorIds.get(operator), bandIds.get(band)));
                        writer.write("            <data key=\"d1\">\n" +
                                     "                <y:PolyLineEdge>\n" +
                                     "                    <y:LineStyle color=\"#ff0000\" type=\"dashed\" width=\"1.0\"/>\n" +
                                     "                </y:PolyLineEdge>\n" +
                                     "            </data>\n" +
                                     "        </edge>\n"
                        );
                    }
                } catch (IOException ignored) {
                }
            }
        }

        @Override
        public void generateOp2ProductEdge(Operator operator, Product product) {
            if (hideBands) {
                final boolean isTarget = product == operator.getTargetProduct();
                try {
                    if (isTarget) {
                        writer.write(
                                String.format("        <edge id=\"e%d\" source=\"n%d\" target=\"n%d\"/>\n", edgeId++,
                                              operatorIds.get(operator), productIds.get(product)));
                    } else {
                        writer.write(
                                String.format("        <edge id=\"e%d\" source=\"n%d\" target=\"n%d\">\n", edgeId++,
                                              operatorIds.get(operator), productIds.get(product)));
                        writer.write("            <data key=\"d1\">\n" +
                                     "                <y:PolyLineEdge>\n" +
                                     "                    <y:LineStyle color=\"#ff0000\" type=\"dashed\" width=\"1.0\"/>\n" +
                                     "                </y:PolyLineEdge>\n" +
                                     "            </data>\n" +
                                     "        </edge>\n"
                        );
                    }
                } catch (IOException ignored) {
                }
            }
        }

        @Override
        public void generateProduct2OpEdge(Product sourceProduct, Operator operator) {
            try {
                writer.write(String.format("        <edge id=\"e%d\" source=\"n%d\" target=\"n%d\"/>\n", edgeId++,
                                           productIds.get(sourceProduct), operatorIds.get(operator)));
            } catch (IOException ignored) {
            }
        }

        @Override
        public void generateOpNode(Operator operator) {
            operatorIds.put(operator, nodeId);
            try {
                writer.write(String.format("        <node id=\"n%d\">\n", nodeId++));
                writer.write(generateLabelTag(operator.getClass().getSimpleName(), SHAPE_OCTAGON));
                writer.write("        </node>\n");
            } catch (IOException ignored) {
            }
        }

        @Override
        public void generateProductNode(Product product) {
            final Band[] bands = product.getBands();
            productIds.put(product, nodeId);

            try {
                writer.write(String.format("        <node id=\"n%d\">\n", nodeId++));
                writer.write(generateLabelTag(product.getName()));
                if (!hideBands) {
                    writer.write(String.format("            <graph id=\"g%d\">\n", graphId++));
                    for (Band band : bands) {
                        bandIds.put(band, nodeId);
                        writer.write(String.format("                <node id=\"n%d\">\n", nodeId++));
                        writer.write(String.format("        %s", generateLabelTag(band.getName(), SHAPE_TRAPEZOID)));
                        writer.write("                </node>\n");
                    }
                    writer.write(String.format("            </graph>\n"));
                }
                writer.write(String.format("        </node>\n"));
            } catch (IOException ignored) {
            }
        }

        private static String generateLabelTag(String label) {
            return generateLabelTag(label, SHAPE_RECTANGLE);
        }

        private static String generateLabelTag(String label, String shape) {
            return String.format(
                    "            <data key=\"d0\">\n" +
                    "                <y:ShapeNode>\n" +
                    "                    <y:NodeLabel>%s</y:NodeLabel>\n" +
                    "                    <y:Shape type=\"%s\"/>\n" +
                    "                </y:ShapeNode>\n" +
                    "            </data>\n",
                    label, shape);
        }

    }
}
