package dev.kevin.trail.core.geo

import dev.kevin.trail.core.model.TrackPoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun metersBetween(start: TrackPoint, end: TrackPoint): Double =
        metersBetween(start.latitude, start.longitude, end.latitude, end.longitude)

    fun metersBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val originLat = Math.toRadians(lat1)
        val destLat = Math.toRadians(lat2)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(originLat) * cos(destLat) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /**
     * Snap a point to a grid so a private place is not uploaded at street precision.
     * One degree of latitude is about 111.32 km.
     */
    fun coarsen(latitude: Double, longitude: Double, gridMeters: Double): Pair<Double, Double> {
        val safeGrid = gridMeters.coerceAtLeast(1.0)
        val latStep = safeGrid / 111_320.0
        val lonScale = (111_320.0 * cos(Math.toRadians(latitude))).coerceAtLeast(1_000.0)
        val lonStep = safeGrid / lonScale
        val coarseLat = round(latitude / latStep) * latStep
        val coarseLon = round(longitude / lonStep) * lonStep
        return coarseLat to coarseLon
    }
}
