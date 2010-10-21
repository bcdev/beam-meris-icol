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

import com.bc.ceres.glevel.MultiLevelImage;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.internal.OperatorContext;

import java.awt.image.RenderedImage;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

public class GraphGen {

    private Set<Product> productNodes = new HashSet<Product>();
    private Set<Operator> operatorNodes = new HashSet<Operator>();
    private Set<ProductOpKey> productOpKeys = new HashSet<ProductOpKey>();
    private Set<OpBandKey> opBandKeys = new HashSet<OpBandKey>();
    private Set<OpProductKey> opProductKeys = new HashSet<OpProductKey>();

    public void generateGraph(Product product, Handler handler) {
        handler.handleBeginGraph();
        generateNodes(product, handler);
        generateEdges(product, handler);
        handler.handleEndGraph();
    }

    private void generateNodes(Product product, Handler handler) {
        final Band[] bands = product.getBands();
        for (Band band : bands) {
            if (band.isSourceImageSet()) {
                final MultiLevelImage image = band.getSourceImage();
                final RenderedImage image0 = image.getImage(0);
                Operator operator = getOperator(image0);
                if (operator == null) {
                    continue;
                }

                final Product[] sourceProducts = operator.getSourceProducts();
                for (Product sourceProduct : sourceProducts) {
                    if (!productNodes.contains(sourceProduct)) {
                        productNodes.add(sourceProduct);
                        generateNodes(sourceProduct, handler);
                    }
                }
                if (!operatorNodes.contains(operator)) {
                    operatorNodes.add(operator);
                    handler.generateOpNode(operator);
                }

            }
        }
        handler.generateProductNode(product);
    }

    private void generateEdges(Product product, Handler handler) {
        final Band[] bands = product.getBands();
        for (Band band : bands) {
            if (band.isSourceImageSet()) {
                final MultiLevelImage image = band.getSourceImage();
                final RenderedImage image0 = image.getImage(0);
                Operator operator = getOperator(image0);
                if (operator == null) {
                    continue;
                }

                final Product[] sourceProducts = operator.getSourceProducts();
                for (Product sourceProduct : sourceProducts) {
                    final ProductOpKey productOpKey = new ProductOpKey(sourceProduct, operator);
                    if (!productOpKeys.contains(productOpKey)) {
                        productOpKeys.add(productOpKey);
                        generateEdges(sourceProduct, handler);
                        handler.generateProduct2OpEdge(sourceProduct, operator);
                    }
                }

                final OpBandKey opBandKey = new OpBandKey(operator, band);
                if (!opBandKeys.contains(opBandKey)) {
                    opBandKeys.add(opBandKey);
                    handler.generateOp2BandEdge(operator, band);
                }

                final OpProductKey opProductKey = new OpProductKey(operator, product);
                if (!opProductKeys.contains(opProductKey)) {
                    opProductKeys.add(opProductKey);
                    handler.generateOp2ProductEdge(operator, product);
                }
            }
        }
    }

    private Operator getOperator(RenderedImage image0) {
        Operator operator = null;
        try {
            final Field field = getOperatorContextField(image0.getClass());
            field.setAccessible(true);
            OperatorContext operatorContext = (OperatorContext) field.get(image0);
            operator = operatorContext.getOperator();
        } catch (NoSuchFieldException e) {
            // ok
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        return operator;
    }

    private Field getOperatorContextField(Class<?> aClass) throws NoSuchFieldException {

        try {
            return aClass.getDeclaredField("operatorContext");
        } catch (NoSuchFieldException e) {
            final Class<?> superclass = aClass.getSuperclass();
            if (superclass == null) {
                throw e;
            }
            return getOperatorContextField(superclass);
        }
    }

    private class ProductOpKey {

        private Product product;
        private Operator op;

        private ProductOpKey(Product product, Operator op) {
            this.op = op;
            this.product = product;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ProductOpKey that = (ProductOpKey) o;
            return that.product.equals(product) && that.op.equals(op);

        }

        @Override
        public int hashCode() {
            int result = product.hashCode();
            result = 31 * result + op.hashCode();
            return result;
        }
    }

    private class OpBandKey {

        private Operator op;
        private Band band;

        private OpBandKey(Operator op, Band band) {
            this.band = band;
            this.op = op;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            OpBandKey opBandKey = (OpBandKey) o;

            return band.equals(opBandKey.band) && op.equals(opBandKey.op);

        }

        @Override
        public int hashCode() {
            int result = op.hashCode();
            result = 31 * result + band.hashCode();
            return result;
        }
    }

    private class OpProductKey {

        private Operator op;
        private Product band;

        private OpProductKey(Operator op, Product band) {
            this.band = band;
            this.op = op;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            OpProductKey that = (OpProductKey) o;

            if (band != null ? !band.equals(that.band) : that.band != null) {
                return false;
            }
            if (op != null ? !op.equals(that.op) : that.op != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = op != null ? op.hashCode() : 0;
            result = 31 * result + (band != null ? band.hashCode() : 0);
            return result;
        }
    }

    public interface Handler {

        void handleBeginGraph();

        void handleEndGraph();

        void generateOpNode(Operator operator);

        void generateProductNode(Product product);

        void generateOp2BandEdge(Operator operator, Band band);

        void generateOp2ProductEdge(Operator operator, Product product);

        void generateProduct2OpEdge(Product sourceProduct, Operator operator);

    }
}
