package dev.kevin.trail.core.privacy

import dev.kevin.trail.core.geo.Geo
import dev.kevin.trail.core.model.PrivateZone
import dev.kevin.trail.core.model.ZoneAction
import kotlin.math.roundToInt

sealed interface ZoneEffect {
    data object Keep : ZoneEffect

    data object Drop : ZoneEffect

    data class Coarsen(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Int,
    ) : ZoneEffect
}

/**
 * Decides what an upload is allowed to say about a point that falls in a
 * private zone. Raw points stay on the device either way.
 * Drop wins when more than one zone matches.
 */
class PrivateZoneFilter(
    private val gridMeters: Double = DEFAULT_GRID_METERS,
) {
    fun effect(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Int,
        zones: List<PrivateZone>,
    ): ZoneEffect {
        val matches = zones.filter { zone ->
            Geo.metersBetween(latitude, longitude, zone.latitude, zone.longitude) <= zone.radiusMeters
        }
        if (matches.isEmpty()) return ZoneEffect.Keep
        if (matches.any { it.action == ZoneAction.DROP }) return ZoneEffect.Drop
        val (lat, lon) = Geo.coarsen(latitude, longitude, gridMeters)
        return ZoneEffect.Coarsen(
            latitude = lat,
            longitude = lon,
            accuracyMeters = maxOf(accuracyMeters, gridMeters.roundToInt()),
        )
    }

    companion object {
        const val DEFAULT_GRID_METERS = 1_000.0
    }
}
