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

import java.awt.Rectangle;

import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.Tile;

/**
 * Created by marcoz.
 *
 * @author marcoz
 * @version $Revision: $ $Date: $
 */
public class WeightedMeanCalculator {
	
	private final int extend;
	private final int[][] distance;

	public WeightedMeanCalculator(int extend) {
		this.extend = extend;
		this.distance = computeDistanceMatrix();
	}

	private int[][] computeDistanceMatrix() {
		int[][] dist = new int[extend+1][extend+1];
		for (int y = 0; y <= extend; y++) {
			for (int x = 0; x <= extend; x++) {
				final int d = (int) Math.sqrt(x * x + y * y);
				if (d <= extend) {
					dist[x][y] = d;
				}
			}
		}
		return dist;
	}
	
	private static int convertToIndex(int x, int y, Rectangle rectangle) {
        final int index = (y - rectangle.y) * rectangle.width + (x - rectangle.x);
        return index;
    }

	public double compute(final int x, final int y, Tile srcTile, double[] weights) {
		final Rectangle sourceRect = srcTile.getRectangle();
		ProductData srcData = srcTile.getRawSamples();
		
       	int[] counts = new int[extend + 1];
        float[] sum = new float[extend + 1];
        
        for (int iy = -extend; iy <= extend; iy++) {
            int index = convertToIndex(x - extend, y + iy, sourceRect);
            final int yDist = Math.abs(iy);
            for (int ix = -extend; ix <= extend; ix++, index++) {
                final int xDist = Math.abs(ix);
                final int i = distance[xDist][yDist];
                if (i != 0) {
                	final float value = srcData.getElemFloatAt(index);
                	if (value != -1) {
                		counts[i]++;
                		sum[i] += value;
                	}
                }
            }
        }
        double mean = srcTile.getSampleFloat(x, y) * weights[0];
        for (int i = 1; i <= extend; i++) {
        	if (counts[i] > 0) {
        		mean += ((sum[i] * weights[i]) / counts[i]);
        	}
        }
        return mean;
	}
	
	public double[] computeAll(final int x, final int y, Tile srcRaster[], double[] weights) {
		final Rectangle sourceRect = srcRaster[0].getRectangle();
		ProductData[] srcData = new ProductData[srcRaster.length];
		final int numBands = srcData.length;
		for (int i = 0; i < numBands; i++) {
			srcData[i] = srcRaster[i].getRawSamples();
		}
		
       	int[] counts = new int[extend + 1];
        float[][] sum = new float[numBands][extend + 1];
        
        for (int iy = -extend; iy <= extend; iy++) {
            int index = convertToIndex(x - extend, y + iy, sourceRect);
            final int yDist = Math.abs(iy);
            for (int ix = -extend; ix <= extend; ix++, index++) {
                final int xDist = Math.abs(ix);
                final int i = distance[xDist][yDist];
                if (i != 0) {
                	final float value0 = srcData[0].getElemFloatAt(index);
                	if (value0 != -1) {
                		counts[i]++;
                		for (int b = 0; b < numBands; b++) {
                    		sum[b][i] += srcData[b].getElemFloatAt(index);
                        }
                	}
                }
            }
        }
        double[] means = new double[numBands];
        for (int b = 0; b < numBands; b++) {
        	means[b] = srcRaster[b].getSampleFloat(x, y) * weights[0];
        	for (int i = 1; i <= extend; i++) {
        		if (counts[i] > 0) {
        			means[b] += ((sum[b][i] * weights[i]) / counts[i]);
        		}
        	}
        }
        return means;
	}
	
	/**
	 * unused remove soon
	 * @param x
	 * @param y
	 * @param srcTile
	 * @param weights
	 * @return the weighted mean
	 */
	public double computeRhoMeanOld(final int x, final int y, Tile srcTile, double[] weights) {
		final Rectangle sourceRect = srcTile.getRectangle();
		ProductData srcData = srcTile.getRawSamples();
		
       	int[] nValue = new int[extend + 1];
        float[] rhoMean = new float[extend + 1];
        
    	final int iyStart = Math.max(y - extend, sourceRect.y);
        final int iyEnd = Math.min(y + extend, sourceRect.y+sourceRect.height);
        final int ixStart = Math.max(x - extend, sourceRect.x);
        final int ixEnd = Math.min(x + extend, sourceRect.x+sourceRect.width);
        
        for (int iy = iyStart; iy <= iyEnd; iy++) {
            if (iy == y) {
                continue;
            }
            int index = convertToIndex(ixStart, iy, sourceRect);
            final int yDist = Math.abs(iy - y);
            for (int ix = ixStart; ix <= ixEnd; ix++, index++) {
                if (ix == x) {
                    continue;
                }
                final int xDist = Math.abs(ix - x);
                final int i = distance[xDist][yDist];
                if (i != 0) {
                	final float value = srcData.getElemFloatAt(index);
                	if (value != -1) {
                		nValue[i]++;
                		rhoMean[i] += value;
                	}
                }
            }
        }
        double matrixCor = srcTile.getSampleFloat(x, y) * weights[0];
        for (int i = 1; i <= extend; i++) {
        	if (nValue[i] > 0) {
        		matrixCor += ((rhoMean[i] * weights[i]) / nValue[i]);
        	}
        }
        return matrixCor;
	}
}
