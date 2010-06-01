/*
 * $Id: NavigationUtils.java,v 1.1 2007/03/27 12:52:00 marcoz Exp $
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
package org.esa.beam.meris.icol.utils;

import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.util.math.MathUtils;

/**
 * Created by marcoz.
 *
 * @author marcoz
 * @version $Revision: 8078 $ $Date: 2010-01-22 17:24:28 +0100 (Fr, 22 Jan 2010) $
 */
public class NavigationUtils {

    private static final int MEAN_EARTH_RADIUS = 6371000;

    public static GeoPos lineWithAngle(GeoPos startPoint, double lengthInMeters, double azimuthAngleInRadiance) {
        //deltaX and deltaY are the corrections to apply to get the point
        final double deltaX = lengthInMeters * Math.sin(azimuthAngleInRadiance);
        final double deltaY = lengthInMeters * Math.cos(azimuthAngleInRadiance);

        // distLat and distLon are in degrees
        final float distLat = (float) (-(deltaY / MEAN_EARTH_RADIUS) * MathUtils.RTOD);
        final float distLon = (float) (-(deltaX / (MEAN_EARTH_RADIUS * Math
                .cos(startPoint.lat * MathUtils.DTOR))) * MathUtils.RTOD);
        final GeoPos geoPos = new GeoPos(startPoint.lat + distLat, startPoint.lon + distLon);

        return geoPos;
    }

    public static float distanceInMeters(GeoCoding geocoding, PixelPos p1, PixelPos p2) {
        final GeoPos geoPosH = geocoding.getGeoPos(p1, null);
        final GeoPos geoPosN = geocoding.getGeoPos(p2, null);
        final float lamH = (float) (MathUtils.DTOR * geoPosH.getLon());
        final float phiH = (float) (MathUtils.DTOR * geoPosH.getLat());
        final float lamN = (float) (MathUtils.DTOR * geoPosN.getLon());
        final float phiN = (float) (MathUtils.DTOR * geoPosN.getLat());
        final float distance = (float) (MEAN_EARTH_RADIUS * Math.acos(
                Math.sin(phiH) * Math.sin(phiN) + Math.cos(phiH) * Math.cos(phiN) * Math.cos(Math.abs(lamN - lamH))));
        return distance;
    }
}
