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
import org.esa.beam.jai.RasterDataNodeOpImage;
import org.esa.beam.jai.VirtualBandOpImage;

import javax.media.jai.RenderedOp;
import java.awt.image.RenderedImage;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;


// todo: move this package to beam-gpf, e.g. into a package devtools.graphgen (nf, 29.04.2011)


/**
 * Generates GPF Operator graphs given a target data product and a {@link GraphGenHandler}.
 *
 * @author Thomas Storm
 * @author Norman Fomferra
 */
public class GraphGen {

    private Set<Product> productNodes = new HashSet<Product>();
    private Set<Op> operatorNodes = new HashSet<Op>();
    private Set<ProductOpKey> productOpKeys = new HashSet<ProductOpKey>();
    private Set<OpBandKey> opBandKeys = new HashSet<OpBandKey>();
    private Set<OpProductKey> opProductKeys = new HashSet<OpProductKey>();
    private Set<OpOpKey> opOpKeys = new HashSet<OpOpKey>();

    public void generateGraph(Product targetProduct, GraphGenHandler handler) {
        handler.handleBeginGraph();
        generateNodes(targetProduct, handler);
        generateEdges(targetProduct, handler);
        handler.handleEndGraph();
    }

    private void generateNodes(Product product, GraphGenHandler handler) {
        final Band[] bands = product.getBands();
        for (Band band : bands) {
            Op op = getOp(band);
            if (op != null) {
                final Product[] sourceProducts = op.getSourceProducts();
                for (Product sourceProduct : sourceProducts) {
                    if (!productNodes.contains(sourceProduct)) {
                        productNodes.add(sourceProduct);
                        generateNodes(sourceProduct, handler);
                    }
                }
                if (!operatorNodes.contains(op)) {
                    operatorNodes.add(op);
                    handler.generateOpNode(op);
                }
            }

        }
        handler.generateProductNode(product);
    }

    private void generateEdges(Product product, GraphGenHandler handler) {
        final Band[] bands = product.getBands();
        for (Band band : bands) {
            Op targetOp = getOp(band);
            if (targetOp != null) {

                Op[] sourceOps = targetOp.getSourceOps();
                for (Op sourceOp : sourceOps) {
                    final OpOpKey opOpKey = new OpOpKey(targetOp, sourceOp);
                    if (!opOpKeys.contains(opOpKey)) {
                        opOpKeys.add(opOpKey);
                        handler.generateOp2OpEdge(sourceOp, targetOp);
                    }
                }

                Product[] sourceProducts = targetOp.getSourceProducts();
                for (Product sourceProduct : sourceProducts) {
                    final ProductOpKey productOpKey = new ProductOpKey(sourceProduct, targetOp);
                    if (!productOpKeys.contains(productOpKey)) {
                        productOpKeys.add(productOpKey);
                        generateEdges(sourceProduct, handler);
                        handler.generateProduct2OpEdge(sourceProduct, targetOp);
                    }
                }

                final OpBandKey opBandKey = new OpBandKey(targetOp, band);
                if (!opBandKeys.contains(opBandKey)) {
                    opBandKeys.add(opBandKey);
                    handler.generateOp2BandEdge(targetOp, band);
                }

                final OpProductKey opProductKey = new OpProductKey(targetOp, product);
                if (!opProductKeys.contains(opProductKey)) {
                    opProductKeys.add(opProductKey);
                    handler.generateOp2ProductEdge(targetOp, product);
                }
            } else {
                // scan for JAI image
            }
        }
    }

    private static Op getOp(Band band) {
        if (band.isSourceImageSet()) {
            return getOp(band.getSourceImage());
        }
        return null;
    }

    private static Op getOp(RenderedImage image0) {
        if (image0 instanceof MultiLevelImage) {
            MultiLevelImage multiLevelImage = (MultiLevelImage) image0;
            image0 = multiLevelImage.getImage(0);
        }
        final Operator operator = getOperator(image0);
        if (operator != null) {
            return new GpfOp(operator);
        } else if (image0 instanceof RenderedOp) {
            final RenderedOp renderedOp = (RenderedOp) image0;
            return new ImageOp(renderedOp.getOperationName(), renderedOp);
        } else if (image0 instanceof RasterDataNodeOpImage) {
            return new RdnOp((RasterDataNodeOpImage) image0);
        } else if (image0 instanceof VirtualBandOpImage) {
            return new VbOp((VirtualBandOpImage) image0);
        } else {
            return new ImageOp(image0.getClass().getSimpleName(), image0);
        }
    }

    private static Operator getOperator(RenderedImage image0) {
        try {
            final Field field = getOperatorContextField(image0.getClass());
            field.setAccessible(true);
            return ((OperatorContext) field.get(image0)).getOperator();
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private static Field getOperatorContextField(Class<?> aClass) throws NoSuchFieldException {

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
        private Op op;

        private ProductOpKey(Product product, Op op) {
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

        private Op op;
        private Band band;

        private OpBandKey(Op op, Band band) {
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

        private Op op;
        private Product band;

        private OpProductKey(Op op, Product band) {
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

            return band.equals(that.band) && op.equals(that.op);

        }

        @Override
        public int hashCode() {
            int result = op.hashCode();
            result = 31 * result + band.hashCode();
            return result;
        }
    }

    private class OpOpKey {

        private final Op op1;
        private final Op op2;

        public OpOpKey(Op op1, Op op2) {
            this.op1 = op1;
            this.op2 = op2;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            OpOpKey opOpKey = (OpOpKey) o;

            return op1.equals(opOpKey.op1) && op2.equals(opOpKey.op2);

        }

        @Override
        public int hashCode() {
            int result = op1.hashCode();
            result = 31 * result + op2.hashCode();
            return result;
        }
    }

    private static class GpfOp implements Op {

        private final Operator operator;

        public GpfOp(Operator operator) {
            this.operator = operator;
        }

        @Override
        public String getName() {
//            return operator.getSpi().getOperatorAlias();
            return operator.getClass().getSimpleName();
        }

        @Override
        public boolean isJAI() {
            return false;
        }

        @Override
        public Op[] getSourceOps() {
            Set<Op> ops = new HashSet<Op>();
            final Product[] sourceProducts = getSourceProducts();
            for (Product sourceProduct : sourceProducts) {
                for (Band band : sourceProduct.getBands()) {
                    Op sourceOp = getOp(band);
                    if (sourceOp != null) {
                        ops.add(sourceOp);
                    }
                }
            }
            return ops.toArray(new Op[ops.size()]);
        }

        @Override
        public Product[] getSourceProducts() {
            return operator.getSourceProducts();
        }

        @Override
        public boolean isTargetProduct(Product product) {
            return product == operator.getTargetProduct();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            GpfOp gpfOp = (GpfOp) o;

            return operator.equals(gpfOp.operator);

        }

        @Override
        public int hashCode() {
            return operator.hashCode();
        }
    }

    private static class ImageOp implements Op {

        private final String name;
        private final RenderedImage image;

        public ImageOp(String name, RenderedImage image) {
            this.name = name;
            this.image = image;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isJAI() {
            return true;
        }

        @Override
        public Op[] getSourceOps() {
            final Vector sources = image.getSources();
            if( sources == null ) {
                return new Op[0];
            }
            final Set<Op> result = new HashSet<Op>();
            for (Object source : sources) {
                if (source instanceof RenderedImage) {
                    result.add(getOp((RenderedImage) source));
                }
            }
            return result.toArray(new Op[result.size()]);
        }

        @Override
        public Product[] getSourceProducts() {
            final Vector sources = image.getSources();
            if( sources == null ) {
                return new Product[0];
            }
            final Set<Product> result = new HashSet<Product>();
            for (Object source : sources) {
                if (source instanceof RenderedImage) {
                    Op op = getOp((RenderedImage) source);
                    result.addAll(Arrays.asList(op.getSourceProducts()));
                }
            }
            return result.toArray(new Product[result.size()]);
        }

        @Override
        public boolean isTargetProduct(Product product) {
            return false;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ImageOp imageOp = (ImageOp) o;

            return image.equals(imageOp.image);

        }

        @Override
        public int hashCode() {
            return image.hashCode();
        }
    }

    private static class RdnOp extends ImageOp {

        private final RasterDataNodeOpImage rasterDataNodeOpImage;

        public RdnOp(RasterDataNodeOpImage rasterDataNodeOpImage) {
            super(rasterDataNodeOpImage.getRasterDataNode().getName(), rasterDataNodeOpImage);
            this.rasterDataNodeOpImage = rasterDataNodeOpImage;
        }

        @Override
        public boolean isTargetProduct(Product product) {
            return rasterDataNodeOpImage.getRasterDataNode().getProduct() == product;
        }

    }

    private static class VbOp extends ImageOp {

        private final VirtualBandOpImage virtualBandOpImage;

        public VbOp(VirtualBandOpImage virtualBandOpImage) {
            super(virtualBandOpImage.getExpression(), virtualBandOpImage);
            this.virtualBandOpImage = virtualBandOpImage;
        }

        @Override
        public Product[] getSourceProducts() {
            return virtualBandOpImage.getProducts();
        }

        @Override
        public boolean isTargetProduct(Product product) {
            return false;
        }

    }
}