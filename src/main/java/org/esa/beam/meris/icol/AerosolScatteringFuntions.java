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

import com.bc.ceres.core.Assert;
import org.esa.beam.util.math.MathUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Created by marcoz.
 *
 * @author marcoz
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
public class AerosolScatteringFuntions {

   private static final double[] angle = {0, 2.84, 17.64, 28.77, 36.19,
    		43.61, 51.03, 58.46, 65.88, 69.59, 73.30, 77.01, 80.72};
   
   private static final int NUM_IAER = 26;
   private static final int NUM_IZA = 12;
   private static final int NUM_IAOT = 16;
   private static final int NTHETA = 83;
   
   private double[][][] transmittance;
   private double[][][][][] fourier;
   private double[][] albedo;
   private double[][] phase;
   
   public AerosolScatteringFuntions() {
	   transmittance = new double[NUM_IAER+1][][];
	   fourier = new double[NUM_IAER+1][][][][];
	   phase = new double[NUM_IAER+1][];
   }
   
//   private static int checkIndex(int index, int max) {
//	   if (index >= max) {
//		   index = max-1;
//	   }
//	   if (index < 0) {
//		   index = 0;
//	   }
//	   return index;
//   }
   
   static int selectIza(double teta) {
	   return selectIzaUpperIndex(teta);
   }

   static int selectIzaNearest(double teta) {
	   Assert.argument(teta>=0, "teta is negative");
	   Assert.argument(teta<=90, "teta bigger than 90");
	   
	   double smallestDiff = 90;
	   int selectedIndex = 0;
    	for (int i = 0; i < angle.length; i++) {
    		double diff = Math.abs(angle[i] - teta);
    		if (smallestDiff > diff) {
    			selectedIndex = i;
    			smallestDiff = diff;
    		}
		}
    	return selectedIndex;
    }
   
   static int selectIzaUpperIndex(double teta) {
	   Assert.argument(teta>=0, "teta is negative");
	   Assert.argument(teta<=90, "teta bigger than 90");
	   
	   //Search for upper index of input za in the list of angle values
	   int iza = 1;
	   while (angle[iza] <= teta && iza < angle.length-1) {
		   iza++;
	   }
	   return iza;
    }
   
   static double interZa(int iza, double y1, double y2, double x) {
//	   iza = checkIndex(iza, angle.length); overflow no possible
	   double x1 =  angle[iza];
	   double x2 =  angle[iza+1];
	   double slope = (y2-y1)/(x2-x1);
	   double y = y1 + slope*(x-x1);
	   return y;
   }
   
   double aerosolTranmittance(int isza, int iaot, int iaer) throws IOException {
//	   isza = checkIndex(isza, NUM_IZA);
//	   iaot = checkIndex(iaot, NUM_IAOT);
//	   iaer = checkIndex(iaer, NUM_IAER);
	   if (transmittance[iaer] == null) {
		   transmittance[iaer] = readAerosolTransmittanceFiles(iaer);
	   }
	   return transmittance[iaer][isza][iaot];
   }

	@SuppressWarnings("boxing")
	double[][] readAerosolTransmittanceFiles(int iaer) throws IOException {
		String fileName = String.format(
				"/auxdata/icol/File_Transmit/tr_aer%02d", iaer+26-1);
		InputStream inputStream = this.getClass().getResourceAsStream(fileName);
		InputStreamReader isr = new InputStreamReader(inputStream);
		BufferedReader bufferedReader = new BufferedReader(isr);
		int skipLines = 10 + 1 + 4 + 1 + 5;
		for (int i = 0; i < skipLines; i++) {
			bufferedReader.readLine();
		}
		double[][] buf = new double[NUM_IZA+1][NUM_IAOT+1];
		for (int i = 1; i <= NUM_IZA; i++) {
			String line = bufferedReader.readLine();
			line = line.trim();
			String[] strings = line.split("\\s+");
			for (int j = 0; j < strings.length; j++) {
				double v = Double.parseDouble(strings[j]);
				buf[i][j] = v;
			}
		}
		return buf;
	}
	
	private double aerosolAlbedo(int iaot, int iaer) throws IOException {
//		iaot = checkIndex(iaot, NUM_IAOT);
//		iaer = checkIndex(iaer, NUM_IAER);
		if (albedo == null) {
			albedo = readAerosolAlbedo();
		}
		return albedo[iaer][iaot];
	}
	
	double[][] readAerosolAlbedo() throws IOException {
		String fileName = "/auxdata/icol/Albedo_AER";
		InputStream inputStream = this.getClass().getResourceAsStream(fileName);
		InputStreamReader isr = new InputStreamReader(inputStream);
		BufferedReader bufferedReader = new BufferedReader(isr);
		int skipLines = 14+32;
		for (int i = 0; i < skipLines; i++) {
			bufferedReader.readLine();
		}
		double[][] buf = new double[NUM_IAER+1][NUM_IAOT+1];
		for (int i = 1; i <= NUM_IAER; i++) {
			String line = bufferedReader.readLine();
			line = line.trim();
			String[] strings = line.split("\\s+");
			for (int j = 0; j < strings.length; j++) {
				double v = Double.parseDouble(strings[j]);
				buf[i][j] = v;
			}
		}
		return buf;
	}
	
	/**
	 * This subroutine computes the aerosol reflectance in primary scattering
	 * This computation is a paste and copy of the computation of the same terms
	 * for the land algorithm.
	 * 
	 * One limitation both over land and over water: the coupling between
	 * Rayleigh and aerosol scattering is not accounted for. The second
	 * limitation is over water for which the Fresnel coupling with scattering
	 * is not accounted for. The computation is done for any geometry and any
	 * AOT.
	 */
	double aerosolPrimaryReflectance(double sza, double vza, double aot, double pab) {
		final double szaR = sza * MathUtils.DTOR;
		final double vzaR = vza * MathUtils.DTOR;
		final double m = (1.0/Math.cos(szaR))+(1.0/Math.cos(vzaR));
	    double g = 1.0 / (4.0 * (Math.cos(szaR) + Math.cos(vzaR)));
	    g = g * ( 1.0-Math.exp(-aot*m));
	    final double roap = pab * g;	
	    return roap;
	}
	
	private double aerosolReflectanceFA(float sza, float vza, float phi, double aot, int iaer) throws IOException {
		final int NORDRE = 5;
		
		if (fourier[iaer] == null) {
			fourier[iaer] = readFourierAerosol(iaer+36);
		}
		int isza = selectIza(sza);
		int ivza = selectIza(vza);
        if (isza == 1) {
            isza += 1;
        }
        if (ivza == 1) {
            ivza += 1;
        }
		final double szamin = angle[isza-1];
		final double szamax = angle[isza];
		final double vzamin = angle[ivza-1];
		final double vzamax = angle[ivza];
		
		double fa = 0;
		double szaMinVzaMin;
		double szaMinVzaMax;
		double szaMaxVzaMin;
		double szaMaxVzaMax;
		for (int k = 0; k <= 3; k++) {

			if (isza <= ivza) {
	           szaMinVzaMin = fourier[iaer][isza-1][ivza-1][0][k];
	           szaMaxVzaMax = fourier[iaer][isza  ][ivza  ][0][k];
			} else {
	           szaMinVzaMin = fourier[iaer][ivza-1][isza-1][0][k];
	           szaMaxVzaMax = fourier[iaer][ivza  ][isza  ][0][k];
			}
	        if (isza-1 <= ivza) {
	           szaMinVzaMax = fourier[iaer][isza-1][ivza  ][0][k];
	        } else {
	           szaMinVzaMax = fourier[iaer][ivza  ][isza-1][0][k];
	        }
	        if (isza <= ivza-1) {
	           szaMaxVzaMin = fourier[iaer][isza  ][ivza-1][0][k];
	        } else {
	           szaMaxVzaMin = fourier[iaer][ivza-1][isza  ][0][k];
	        }
	        double fa1 = interpolateLin(szamin, szaMinVzaMin,
	        		szamax, szaMaxVzaMin, sza);
	        double fa2 = interpolateLin(szamin, szaMinVzaMax,
	        		szamax,szaMaxVzaMax, sza);
	        double faInt = interpolateLin(vzamin, fa1,
	        	    vzamax, fa2, vza);
	        fa += faInt * Math.pow(aot, k);
		}
		
		double[] f = new double[NORDRE+1];
		for (int s = 1; s <= NORDRE; s++) {
			for (int k = 0; k <= 3; k++) {
				if (isza <= ivza) {
		           szaMinVzaMin = fourier[iaer][isza-1][ivza-1][s][k];
		           szaMaxVzaMax = fourier[iaer][isza  ][ivza  ][s][k];
				} else {
		           szaMinVzaMin = fourier[iaer][ivza-1][isza-1][s][k];
		           szaMaxVzaMax = fourier[iaer][ivza  ][isza  ][s][k];
				}
		        if (isza-1 <= ivza) {
		           szaMinVzaMax = fourier[iaer][isza-1][ivza  ][s][k];
		        } else {
		           szaMinVzaMax = fourier[iaer][ivza  ][isza-1][s][k];
		        }
		        if (isza <= ivza-1) {
		           szaMaxVzaMin = fourier[iaer][isza  ][ivza-1][s][k];
		        } else {
		           szaMaxVzaMin = fourier[iaer][ivza-1][isza  ][s][k];
		        }
		        double fa1 = interpolateLin(szamin, szaMinVzaMin,
		        		szamax, szaMaxVzaMin, sza);
		        double fa2 = interpolateLin(szamin, szaMinVzaMax,
		        		szamax,szaMaxVzaMax, sza);
		        double faInt = interpolateLin(vzamin, fa1,
		        	    vzamax, fa2, vza);
		        f[s] += faInt * Math.pow(aot, k);
			}
			fa += 2.0 * f[s] * Math.cos( s * phi * MathUtils.DTOR);
		}
		return fa;
	}
	
	private double aerosolReflectance(int isza, int ivza, float phi, double aot, int iaer) throws IOException {
		final int NORDRE = 5;
//		isza = checkIndex(isza, NUM_IZA);
//		ivza = checkIndex(ivza, NUM_IZA);
//		iaer = checkIndex(iaer, NUM_IAER);
		
		if (fourier[iaer] == null) {
			fourier[iaer] = readFourierAerosol(iaer+36);
		}
		int iszaUse = Math.min(isza, ivza);
		int ivzaUse = Math.max(isza, ivza);
		double fa = 0;
		for (int i = 0; i <= 3; i++) {
			fa += fourier[iaer][iszaUse][ivzaUse][0][i] * Math.pow(aot, i);
		}

		double[] f = new double[NORDRE+1];
		for (int s = 1; s <= NORDRE; s++) {
			f[s] = 0;
			for (int i = 0; i <= 3; i++) {
				f[s] += fourier[iaer][iszaUse][ivzaUse][s][i] * Math.pow(aot, i);
			}
			fa = fa + 2.0 *f[s]*Math.cos(s * 7.5 * phi * MathUtils.DTOR);
		}

		return fa;
	}
	
	@SuppressWarnings("boxing")
	double[][][][] readFourierAerosol(int iaer) throws IOException {
		final int NORDDRE = 6;
		final int NSVZA = 78;
		final int NCOEF = 4;
		String fileName = String.format(
				"/auxdata/icol/File_Fourier/Fourier_AER%02d", iaer);
		InputStream inputStream = this.getClass().getResourceAsStream(fileName);
		InputStreamReader isr = new InputStreamReader(inputStream);
		BufferedReader bufferedReader = new BufferedReader(isr);
		
		String skipLine;
		do {
			skipLine = bufferedReader.readLine();
		} while (!skipLine.startsWith(" Aerosol model"));
		bufferedReader.readLine();
		
		double[][] fread = new double[NSVZA+1][NCOEF];
		double[][][][] singleFourier = new double[NUM_IZA+1][NUM_IZA+1][NORDDRE][NCOEF];
		for (int s = 0; s < NORDDRE; s++) {
			//skip
			do {
				skipLine = bufferedReader.readLine();
			} while (!skipLine.startsWith("Fourier series term"));
			
			for (int j = 1; j <= NSVZA; j++) {
				String coefLine = bufferedReader.readLine();
				coefLine = coefLine.trim();
				String[] strings = coefLine.split("\\s+");
				for (int l = 0; l < strings.length; l++) {
					double v = Double.parseDouble(strings[l]);
					fread[j][l] = v;
				}
			}
			int indice=0;
			for (int isza = 1; isza <= NUM_IZA; isza++) {
				for (int ivza = isza; ivza <= NUM_IZA; ivza++) {
					indice++;
					for (int l = 0; l < 4; l++) {
						singleFourier[isza][ivza][s][l] = fread[indice][l];
					}
					
				}
			}
		}
		return singleFourier;
	}
	
	public double aerosolPhaseFB(double thetaf, double thetab, int iaer) throws IOException {
//		iaer = checkIndex(iaer, NUM_IAER);
		double paerF = aerosolPhase(thetaf,iaer);
        double paerB = aerosolPhase(thetab,iaer);
        double paerFB = paerF/paerB;
        return paerFB;
	}
	
	public double aerosolPhase(double theta, int iaer) throws IOException {
//		iaer = checkIndex(iaer, NUM_IAER);
		if (phase[iaer] == null) {
			phase[iaer] = readAerosolPhase(iaer);
		}
		int phaseIndex = getPhaesIndex(theta, iaer);
		double theTheta = phase[iaer][phaseIndex];
		double thePhase = phase[iaer][phaseIndex + NTHETA];
		double prevTheta = phase[iaer][phaseIndex - 1];
		double prevPhase = phase[iaer][phaseIndex + NTHETA - 1];
		double pa = interpolateLin(theTheta, thePhase,
				prevTheta, prevPhase,
				theta);
		return pa;
	}
	
	int getPhaesIndex(double theta, int iaer) {
		for (int i = 1; i < NTHETA; i++) {
			if (phase[iaer][i] <= theta) {
				return i;
			}
		}
		return -1;
	}
	
	@SuppressWarnings("boxing")
	double[] readAerosolPhase(int iaer) throws IOException {
//		iaer = checkIndex(iaer, NUM_IAER);
		
		double[] buf = new double[NTHETA*2];
		String fileName = String.format(
				"/auxdata/icol/File_scamat/JungeModel_k000/sc_land%02d", iaer+26);
		InputStream inputStream = this.getClass().getResourceAsStream(fileName);
		InputStreamReader isr = new InputStreamReader(inputStream);
		BufferedReader bufferedReader = new BufferedReader(isr);
		int skipLines = 10;
		for (int i = 0; i < skipLines; i++) {
			bufferedReader.readLine();
		}
		for (int i = 0; i < NTHETA; i++) {
			String line = bufferedReader.readLine();
			line = line.trim();
			String[] strings = line.split("\\s+");
			double aTheta = Double.parseDouble(strings[0]);
			double aPhase = Double.parseDouble(strings[1]);
			buf[i] = aTheta;
			buf[i + NTHETA] = aPhase;
		}
		
		return buf;
	}
	
	//call        AEROSOL_F(taua,iaer,pab,ROA,TUA,TDA,sa)
	//SUBROUTINE  AEROSOL_F(aot, iaer,pab,ROA,TUA,TDA,sa) 
	//phi = saa-vaa
	public RV aerosol_f(double aot, int iaer, double pab, float sza, float vza, float phi) throws IOException {
	    double y1, y2, y3, y4;
	    RV rv = new RV();
	    //compute the discrete value of the AOT 
	    int iaot = MathUtils.floorInt(10 * aot);
	    if(iaot>15) {
	    	iaot=15;
	    }
	    final double aot_inf = 0.1 * iaot;
	    final double aot_sup = 0.1 * iaot + 0.1;
	    
	    //compute the downward transmittance
	    int isza = selectIza(sza);
	    y1 = aerosolTranmittance(isza, iaot, iaer);
	    y2 = aerosolTranmittance(isza+1, iaot, iaer);
	    y3 = interZa(isza,y1,y2,sza);
	    
	    y1 = aerosolTranmittance(isza, iaot+1, iaer);
	    y2 = aerosolTranmittance(isza+1, iaot+1, iaer);
	    y4 = interZa(isza,y1,y2,sza);
	    rv.tds = interpolateLin(aot_inf, y3, aot_sup, y4, aot);  // returnvalue

	    //compute the upward transmittance
	    int ivza = selectIza(vza);
	    y1 = aerosolTranmittance(ivza, iaot, iaer);
	    y2 = aerosolTranmittance(ivza+1, iaot, iaer);
	    y3 = interZa(ivza,y1,y2,vza);
	    
	    y1 = aerosolTranmittance(ivza, iaot+1, iaer);
	    y2 = aerosolTranmittance(ivza+1, iaot+1, iaer);
	    y4 = interZa(ivza,y1,y2,sza);
	    rv.tus = interpolateLin(aot_inf, y3, aot_sup, y4, aot);  // returnvalue
	    
	    //compute the aerosol spherical albedo
	    y1 = aerosolAlbedo(iaot, iaer);
	    y2 = aerosolAlbedo(iaot+1, iaer);
	    rv.sa = interpolateLin(aot_inf, y1, aot_sup, y2, aot); //returnvalue

	    //compute the aerosol reflectance in primary
	    double roap = aerosolPrimaryReflectance(sza, vza, aot, pab);
	    
	    //compute the multiple scattering factor
//	    y1 = aerosolReflectance(isza, ivza, phi, aot, iaer);
//	    y2 = aerosolReflectance(isza+1, ivza, phi, aot, iaer);
//	    y3 = interZa(isza,y1,y2,sza);
//	    y1 = aerosolReflectance(isza, ivza+1, phi, aot, iaer);
//	    y2 = aerosolReflectance(isza+1, ivza+1, phi, aot, iaer);
//	    y4 = interZa(isza,y1,y2,sza);
//	    double faOld = interZa(ivza,y3,y4,vza); //returnvalue
	    
	    double faNew = aerosolReflectanceFA(sza, vza, phi, aot, iaer);
	    
	    rv.fa = faNew;
	    
	    rv.rhoa = roap * rv.fa;
	    return rv;
	}
	
	

	public double interpolateLin(double x1, double y1, double x2, double y2, double x3) {
		double a = (y2-y1)/(x2-x1);
		double b = y1-(a*x1); 
	    double y3= (x3*a)+b;
		
		return y3;
	}
	
	public static class RV {
		public double tds;
		public double tus;
		public double sa;
		double fa;
		public double rhoa;
	}
}