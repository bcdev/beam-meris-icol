package org.esa.beam.meris.icol.graphgen;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;

/**
 * Handles various events fired by the {@link GraphGen} class.
 *
 * @author Thomas Storm
 * @author Norman Fomferra
 */
public interface GraphGenHandler {

    void handleBeginGraph();

    void handleEndGraph();

    void generateOpNode(Op operator);

    void generateProductNode(Product product);

    void generateOp2BandEdge(Op operator, Band band);

    void generateOp2ProductEdge(Op operator, Product product);

    void generateProduct2OpEdge(Product sourceProduct, Op operator);

    void generateOp2OpEdge(Op source, Op target);
}
