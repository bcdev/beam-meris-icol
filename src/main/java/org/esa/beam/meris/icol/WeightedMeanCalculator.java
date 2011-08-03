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

import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.Tile;

import java.awt.Rectangle;

/**
 * Created by marcoz.
 *
 * @author marcoz
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
public class WeightedMeanCalculator {

    private final int extend;
    private final int[][] distances;

    public WeightedMeanCalculator(int extend) {
        this.extend = extend;
        this.distances = computeDistanceMatrix(extend);
    }

    static int[][] computeDistanceMatrix(int extend) {
        int[][] dist = new int[2 * extend + 1][2 * extend + 1];
        for (int y = -extend; y <= extend; y++) {
            for (int x = -extend; x <= extend; x++) {
                final int d = (int) Math.sqrt(x * x + y * y);
                if (d <= extend) {
                    dist[y + extend][x + extend] = d;
                }
            }
        }
        return dist;
    }

    private static int convertToIndex(int x, int y, Rectangle rectangle) {
        return (y - rectangle.y) * rectangle.width + (x - rectangle.x);
    }

    public double compute(final int x, final int y, Tile srcTile, double[] weights) {
        final Rectangle sourceRect = srcTile.getRectangle();
        ProductData srcData = srcTile.getRawSamples();

        if (x == 124 && y == 142) {
            System.out.println("x,y = " + x + "," + y);
        }

        int[] counts = new int[extend + 1];
        float[] sum = new float[extend + 1];

        for (int iy = 0; iy <= 2 * extend; iy++) {
            int index = convertToIndex(x - extend, y + iy - extend, sourceRect);
            final int[] distancesY = distances[iy];
            for (int ix = 0; ix <= 2 * extend; ix++, index++) {
                final int distance = distancesY[ix];
                if (distance != 0) {
                    final float value = srcData.getElemFloatAt(index);
                    if (value != -1) {
                        counts[distance]++;
                        sum[distance] += value;
                    }
                }
            }
        }
        double mean = srcTile.getSampleFloat(x, y) * weights[0];
        for (int distance = 1; distance <= extend; distance++) {
            if (counts[distance] > 0) {
                mean += ((sum[distance] * weights[distance]) / counts[distance]);
            }
        }
        return mean;
    }

    public double[] computeAll(final int x, final int y, Tile[] srcTiles, double[] weights) {
        final Rectangle sourceRect = srcTiles[0].getRectangle();
        ProductData[] srcData = new ProductData[srcTiles.length];
        final int numBands = srcData.length;
        for (int i = 0; i < numBands; i++) {
            if (srcTiles[i] != null) {
                srcData[i] = srcTiles[i].getRawSamples();
            }
        }

        if (x == 124 && y == 142) {
            System.out.println("x,y = " + x + "," + y);
        }

        int[] counts = new int[extend + 1];
        float[][] sum = new float[numBands][extend + 1];

        for (int iy = 0; iy <= 2 * extend; iy++) {
            int index = convertToIndex(x - extend, y + iy - extend, sourceRect);
            final int[] distancesY = distances[iy];
            for (int ix = 0; ix <= 2 * extend; ix++, index++) {
                final int distance = distancesY[ix];
                if (distance != 0) {
                    final float value0 = srcData[0].getElemFloatAt(index);
                    if (value0 != -1) {
                        counts[distance]++;
                        for (int bandIdx = 0; bandIdx < numBands; bandIdx++) {
                            if (srcData[bandIdx] != null) {
                                sum[bandIdx][distance] += srcData[bandIdx].getElemFloatAt(index);
                            }
                        }
                    }
                }
            }
        }
        double[] means = new double[numBands];
        for (int bandIdx = 0; bandIdx < numBands; bandIdx++) {
            if (srcTiles[bandIdx] != null) {
                means[bandIdx] = srcTiles[bandIdx].getSampleFloat(x, y) * weights[0];
                for (int distance = 1; distance <= extend; distance++) {
                    if (counts[distance] > 0) {
                        means[bandIdx] += ((sum[bandIdx][distance] * weights[distance]) / counts[distance]);
                    }
                }
            }
        }
        return means;
    }

    public double computeBoolean(final int x, final int y, Tile srcTile, double[] weights) {
        final Rectangle sourceRect = srcTile.getRectangle();
        ProductData srcData = srcTile.getRawSamples();

        if (x == 124 && y == 142) {
            System.out.println("x,y = " + x + "," + y);
        }

        int[] counts = new int[extend + 1];
        float[] sum = new float[extend + 1];

        for (int iy = 0; iy <= 2 * extend; iy++) {
            int index = convertToIndex(x - extend, y + iy - extend, sourceRect);
            final int[] distancesY = distances[iy];
            for (int ix = 0; ix <= 2 * extend; ix++, index++) {
                final int distance = distancesY[ix];
                if (distance != 0) {
                    final boolean value = srcData.getElemBooleanAt(index);
                    if (value) {
                        counts[distance]++;
                        sum[distance] += 1.0;
                    }
                }
            }
        }
        double mean = (srcTile.getSampleBoolean(x, y) == true) ? weights[0] : 0.0;
        for (int distance = 1; distance <= extend; distance++) {
            if (counts[distance] > 0) {
                mean += ((sum[distance] * weights[distance]) / counts[distance]);
            }
        }
        return mean;
    }

}