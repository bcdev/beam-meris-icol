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

public class GraphGen {

    public void generateGraph(Product product, Handler handler) {
        handler.handleBeginGraph();
        generateNodes(product, handler);
        generateEdges(product, handler);
        handler.handleEndGraph();
    }

    private void generateNodes(Product product, Handler handler) {
        final Band[] bands = product.getBands();
        boolean init = false;
        for (Band band : bands) {
            if (band.isSourceImageSet()) {
                final MultiLevelImage image = band.getSourceImage();
                final RenderedImage image0 = image.getImage(0);
                try {
                    final Field field = image0.getClass().getDeclaredField("operatorContext");
                    field.setAccessible(true);
                    OperatorContext operatorContext = (OperatorContext) field.get(image0);
                    Operator operator = operatorContext.getOperator();

                    if (!init) {
                        init = true;
                        final Product[] sourceProducts = operator.getSourceProducts();
                        for (Product sourceProduct : sourceProducts) {
                            generateNodes(sourceProduct, handler);
                        }
                        handler.handleOperator(operator);
                    }
                } catch (NoSuchFieldException e) {
                    // ok
                } catch (IllegalAccessException e) {
                    // ok
                }
            }
        }
        handler.handleProduct(product);
    }

    private void generateEdges(Product product, Handler handler) {
        final Band[] bands = product.getBands();
        boolean init = false;
        for (Band band : bands) {
            if (band.isSourceImageSet()) {
                final MultiLevelImage image = band.getSourceImage();
                final RenderedImage image0 = image.getImage(0);
                try {
                    final Field field = image0.getClass().getDeclaredField("operatorContext");
                    field.setAccessible(true);
                    OperatorContext operatorContext = (OperatorContext) field.get(image0);
                    Operator operator = operatorContext.getOperator();

                    if (!init) {
                        init = true;
                        final Product[] sourceProducts = operator.getSourceProducts();
                        for (Product sourceProduct : sourceProducts) {
                            handler.handleSourceProduct(sourceProduct, operator);
                        }
                    }

                    handler.handleBand(band, operator);
                } catch (NoSuchFieldException e) {
                    // ok
                } catch (IllegalAccessException e) {
                    // ok
                }
            }
        }
    }

    public interface Handler {

        void handleBeginGraph();

        void handleEndGraph();

        void handleOperator(Operator operator);

        void handleProduct(Product product);

        void handleBand(Band band, Operator operator);

        void handleSourceProduct(Product sourceProduct, Operator operator);
    }
}
