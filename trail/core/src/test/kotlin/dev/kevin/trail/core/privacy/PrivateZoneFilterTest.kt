package dev.kevin.trail.core.privacy

import dev.kevin.trail.core.geo.Geo
import dev.kevin.trail.core.model.PrivateZone
import dev.kevin.trail.core.model.ZoneAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateZoneFilterTest {
    private val filter = PrivateZoneFilter(gridMeters = 1_000.0)
    private val home = PrivateZone(
        name = "Home",
        latitude = 37.0,
        longitude = -122.0,
        radiusMeters = 150.0,
        action = ZoneAction.DROP,
    )

    @Test
    fun dropsPointsInsideADropZone() {
        val effect = filter.effect(37.0003, -122.0002, 10, listOf(home))
        assertEquals(ZoneEffect.Drop, effect)
    }

    @Test
    fun keepsPointsOutsideTheZone() {
        val effect = filter.effect(37.01, -122.0, 10, listOf(home))
        assertEquals(ZoneEffect.Keep, effect)
    }

    @Test
    fun coarsensInsideACoarsenZoneAndLowersAccuracy() {
        val zone = home.copy(action = ZoneAction.COARSEN, radiusMeters = 200.0)
        val effect = filter.effect(37.0004, -122.0004, 5, listOf(zone))
        assertTrue(effect is ZoneEffect.Coarsen)
        val coarsened = effect as ZoneEffect.Coarsen
        assertTrue(Geo.metersBetween(37.0004, -122.0004, coarsened.latitude, coarsened.longitude) <= 1_000.0)
        assertTrue(coarsened.accuracyMeters >= 1_000)
        assertTrue(coarsened.latitude != 37.0004 || coarsened.longitude != -122.0004)
    }

    @Test
    fun dropWinsWhenZonesOverlap() {
        val coarsen = home.copy(name = "Neighborhood", radiusMeters = 500.0, action = ZoneAction.COARSEN)
        val effect = filter.effect(37.0, -122.0, 10, listOf(coarsen, home))
        assertEquals(ZoneEffect.Drop, effect)
    }

    @Test
    fun boundaryPointIsInside() {
        val north = 37.0 + (150.0 / 111_320.0)
        val effect = filter.effect(north, -122.0, 10, listOf(home.copy(radiusMeters = 150.0)))
        assertEquals(ZoneEffect.Drop, effect)
    }
}
