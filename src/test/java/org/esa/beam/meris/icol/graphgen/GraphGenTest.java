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

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.VirtualBand;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.junit.Ignore;
import org.junit.Test;

import javax.media.jai.operator.ConstantDescriptor;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class GraphGenTest {

    /*
     * S(u,v) -> Op
     * Op -> T(a,b,c)
     */
    @Test
    @Ignore
    public void test1() throws IOException {
        final DummyOp op = new DummyOp();
        final Product s = createSourceProduct("s");
        op.setSourceProduct(s);
        final Product t = op.getTargetProduct();
        final GraphGen graphGen = new GraphGen();
        MyHandler handler = new MyHandler();
        graphGen.generateGraph(t, handler);
        // todo: differences in line endings - check why
        assertEquals(read("GraphGenTest_test1.graphml"), handler.xml.toString());
    }


    /*
     * S1(u,v) -> Op
     * S2(u,v) -> Op
     * Op -> T(a,b,c)
     */
    @Test
    @Ignore
    public void test2() throws IOException {
        final DummyOp op = new DummyOp();
        final Product s1 = createSourceProduct("s1");
        final Product s2 = createSourceProduct("s2");
        op.setSourceProduct("s1", s1);
        op.setSourceProduct("s2", s2);
        final Product t = op.getTargetProduct();
        final GraphGen graphGen = new GraphGen();
        MyHandler handler = new MyHandler();
        graphGen.generateGraph(t, handler);
        // todo: differences in line endings - check why
        assertEquals(read("GraphGenTest_test2.graphml"), handler.xml.toString());
    }

    /*
     * S1(u,v) -> Op1
     * S1(u,v) -> Op2
     * Op1 -> T1(a,b,c)
     * Op2 -> T2(a,b,c)
     * T1,T2 -> Op3
     * Op3 -> T3
     */
    @Test
    @Ignore
    public void test3() throws IOException {
        final DummyOp op1 = new DummyOp();
        final Product s1 = createSourceProduct("source");
        op1.setSourceProduct(s1);

        DummyOp op2 = new DummyOp();
        op2.setSourceProduct(s1);

        DummyOp op3 = new DummyOp();
        op3.setSourceProduct("t1", op1.getTargetProduct());
        op3.setSourceProduct("t2", op2.getTargetProduct());

        final Product t3 = op3.getTargetProduct();
        final GraphGen graphGen = new GraphGen();
        MyHandler handler = new MyHandler();
        graphGen.generateGraph(t3, handler);
        // todo: differences in line endings - check why
        assertEquals(read("GraphGenTest_test3.graphml"), handler.xml.toString());
    }

    /*
     * S(u,v) -> Op
     * Op -> T(a,b,c,E,F) (additional bands e and f not computed by Op)
     */
    @Test
    @Ignore
    public void test4() throws IOException {
        final DummyOp2 op = new DummyOp2();
        final Product sourceProduct = createSourceProduct("s");
        op.setSourceProduct(sourceProduct);
        final Product targetProduct = op.getTargetProduct();

        final GraphGen graphGen = new GraphGen();
        MyHandler handler = new MyHandler();
        graphGen.generateGraph(targetProduct, handler);
        // todo: differences in line endings - check why
        assertEquals(read("GraphGenTest_test4.graphml"), handler.xml.toString());
    }

    /**
     * S(u,v) -> Op1
     * S(u,v) -> Op2
     * Op1 -> T1(a,b,c, T2.x, T2.y)
     * Op2 -> T2(x,y)  // T2 is not expected to appear in output graph
     */
    @Test
    public void test5(){
        // todo implement
    }

    private String read(String fileName) throws IOException {
        final InputStreamReader streamReader = new InputStreamReader(
                getClass().getResourceAsStream(fileName));
        final char[] contents = new char[1024 * 16];
        final int len = streamReader.read(contents);
        assertTrue(len > 0 && len < contents.length);
        streamReader.close();

        return new String(contents, 0, len);
    }

    private Product createSourceProduct(String name) {
        final Product sourceProduct = new Product(name, "type1", 10, 10);
        sourceProduct.addBand("u", ProductData.TYPE_FLOAT32);
        sourceProduct.addBand("v", ProductData.TYPE_FLOAT32);
        return sourceProduct;
    }

    private static class DummyOp extends Operator {

        @Override
        public void initialize() throws OperatorException {
            final Product product = new Product("T", "type1", 10, 10);
            product.addBand("a", ProductData.TYPE_FLOAT32);
            product.addBand("b", ProductData.TYPE_FLOAT32);
            product.addBand("c", ProductData.TYPE_FLOAT32);
            setTargetProduct(product);
        }
    }

    private static class DummyOp2 extends Operator {

        @Override
        public void initialize() throws OperatorException {
            final Product product = new Product("T", "type1", 10, 10);
            product.addBand("a", ProductData.TYPE_FLOAT32);
            product.addBand("b", ProductData.TYPE_FLOAT32);
            product.addBand("c", ProductData.TYPE_FLOAT32);

            // A constant band
            final Band band1 = product.addBand("d", ProductData.TYPE_FLOAT32);
            band1.setSourceImage(ConstantDescriptor.create(10f, 10f, new Float[]{3.14f}, null));

            // A virtual band
            product.addBand(new VirtualBand("e", ProductData.TYPE_FLOAT32, 10, 10, "a + 1"));

            setTargetProduct(product);
        }
    }


    private static class MyHandler implements GraphGenHandler {

        StringBuilder xml = new StringBuilder();

        Map<Band, Integer> bandIds = new HashMap<Band, Integer>();
        Map<Op, Integer> operatorIds = new HashMap<Op, Integer>();
        Map<Product, Integer> productIds = new HashMap<Product, Integer>();

        private int nodeId = 1;
        private int graphId = 2;
        private int edgeId = 1;

        @Override
        public void handleBeginGraph() {
            xml.append("<graphml>\n" +
                       "    <graph id=\"g1\">\n");
        }

        @Override
        public void handleEndGraph() {
            xml.append("    </graph>\n" +
                       "</graphml>");
        }

        @Override
        public void generateOp2BandEdge(Op operator, Band band) {
            xml.append(String.format("        <edge id=\"e%d\" source=\"n%d\" target=\"n%d\"/>\n", edgeId++,
                                     operatorIds.get(operator),
                                     bandIds.get(band)));
        }

        @Override
        public void generateProduct2OpEdge(Product sourceProduct, Op operator) {
            xml.append(String.format("        <edge id=\"e%d\" source=\"n%d\" target=\"n%d\"/>\n", edgeId++,
                                     productIds.get(sourceProduct), operatorIds.get(operator)));
        }

        @Override
        public void generateOp2OpEdge(Op source, Op target) {
        }

        @Override
        public void generateOpNode(Op operator) {
            operatorIds.put(operator, nodeId);
            xml.append(String.format("        <node id=\"n%d\"/>\n", nodeId++));
        }

        @Override
        public void generateProductNode(Product product) {
            final Band[] bands = product.getBands();
            productIds.put(product, nodeId);
            xml.append(String.format("        <node id=\"n%d\">\n", nodeId++));
            xml.append(String.format("            <graph id=\"g%d\">\n", graphId++));
            for (Band band : bands) {
                bandIds.put(band, nodeId);
                xml.append(String.format("                <node id=\"n%d\"/>\n", nodeId++));
            }
            xml.append(String.format("            </graph>\n"));
            xml.append(String.format("        </node>\n"));
        }

        @Override
        public void generateOp2ProductEdge(Op operator, Product product) {
            // does intentionally nothing 
        }
    }
}
