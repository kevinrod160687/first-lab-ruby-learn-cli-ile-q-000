package dev.kevin.trail.core.export

import dev.kevin.trail.core.model.ActivityMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailExporterTest {
    @Test
    fun gpxContainsTrackAndStayAndEscapesNothingSecret() {
        val gpx = TrailExporter.gpx(
            points = listOf(ExportPoint(tst = 1_700_000_000, latitude = 37.1, longitude = -122.2, altitudeMeters = 4.0)),
            visits = listOf(ExportVisit(37.1, -122.2, 1_700_000_000, 1_700_000_300, closed = true)),
        )
        assertTrue(gpx.contains("<trkpt lat=\"37.1\" lon=\"-122.2\">"))
        assertTrue(gpx.contains("<wpt lat=\"37.1\" lon=\"-122.2\">"))
        assertTrue(gpx.contains("2023-11-14T22:13:20Z"))
        assertFalse(gpx.contains("webhook"))
        assertFalse(gpx.contains("Authorization"))
    }

    @Test
    fun jsonExportHasPointsVisitsAndTripsWithoutSecrets() {
        val body = TrailExporter.json(
            points = listOf(ExportPoint(10, 1.5, 2.5, null)),
            visits = listOf(ExportVisit(1.5, 2.5, 10, 20, true)),
            trips = listOf(ExportTrip(10, 20, ActivityMode.WALK, 320.0)),
            exportedAtEpochMs = 99,
        )
        assertTrue(body.contains("\"mode\": \"WALK\""))
        assertTrue(body.contains("\"lat\": 1.5"))
        assertFalse(body.contains("authorization"))
        assertFalse(body.contains("webhook"))
    }
}
