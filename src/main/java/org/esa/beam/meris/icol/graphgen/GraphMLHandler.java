package org.esa.beam.meris.icol.graphgen;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

/**
 * Generates GraphML XML files.
 *
 * @author Thomas Storm
 * @author Norman Fomferra
 */
class GraphMLHandler implements GraphGenHandler {

    private Writer writer;
    private boolean hideBands;
    private boolean hideProducts;

    private Map<Band, Integer> bandIds = new HashMap<Band, Integer>();
    private Map<Op, Integer> operatorIds = new HashMap<Op, Integer>();
    private Map<Product, Integer> productIds = new HashMap<Product, Integer>();

    private int nodeId = 1;
    private int graphId = 2;
    private int edgeId = 1;

    private static final String SHAPE_RECTANGLE = "rectangle3d";
    private static final String SHAPE_OCTAGON = "octagon";
    private static final String SHAPE_TRAPEZOID = "trapezoid";

    GraphMLHandler(Writer writer, boolean hideBands, boolean hideProducts) {
        this(writer, hideBands);
        this.hideProducts = hideProducts;
    }

    GraphMLHandler(Writer writer, boolean hideBands) {
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
    public void generateOp2BandEdge(Op operator, Band band) {
        if (!hideBands) {
            try {
                if (operator.isTargetProduct(band.getProduct())) {
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
    public void generateOp2ProductEdge(Op operator, Product product) {
        if (hideBands && !hideProducts) {
            try {
                if (operator.isTargetProduct(product)) {
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
    public void generateProduct2OpEdge(Product sourceProduct, Op operator) {
        if (!hideProducts) {
            try {
                writer.write(String.format("        <edge id=\"e%d\" source=\"n%d\" target=\"n%d\"/>\n", edgeId++,
                                           productIds.get(sourceProduct), operatorIds.get(operator)));
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void generateOp2OpEdge(Op source, Op target) {
        if (hideProducts) {
            try {
                writer.write(String.format("        <edge id=\"e%d\" source=\"n%d\" target=\"n%d\"/>\n", edgeId++,
                                           operatorIds.get(source), operatorIds.get(target)));
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void generateOpNode(Op operator) {
        operatorIds.put(operator, nodeId);
        try {
            writer.write(String.format("        <node id=\"n%d\">\n", nodeId++));
            writer.write(generateLabelTag(operator.getName(), SHAPE_RECTANGLE));
            writer.write("        </node>\n");
        } catch (IOException ignored) {
        }
    }

    @Override
    public void generateProductNode(Product product) {
        if (hideProducts) {
            return;
        }
        final Band[] bands = product.getBands();
        productIds.put(product, nodeId);

        try {
            writer.write(String.format("        <node id=\"n%d\">\n", nodeId++));
            writer.write(generateLabelTag(product.getName(), SHAPE_OCTAGON));
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

    private static String generateLabelTag(String label, String shape) {
        int fontSize = 35;
        int height = fontSize + 10;
        int width = label.length() * (fontSize - 10);
        if (label.endsWith("Op")) {
            label = label.substring(0, label.length() - 2);
        }
        String[] parts = label.split("(?<!^)(?=[A-Z])");
        label = "";
        for (String part : parts) {
            label += part + " ";
        }
        return String.format(
                "            <data key=\"d0\">\n" +
                        "                <y:ShapeNode>\n" +
                        "                    <y:Geometry height=\"" + height + "\" width=\"" + width + "\"/>\n" +
                        "                    <y:NodeLabel fontSize=\"" + fontSize + "\">%s</y:NodeLabel>\n" +
                        "                    <y:Shape type=\"%s\"/>\n" +
                        "                </y:ShapeNode>\n" +
                        "            </data>\n",
                label.trim(), shape);
    }

}
