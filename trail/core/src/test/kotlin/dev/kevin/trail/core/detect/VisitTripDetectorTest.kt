package dev.kevin.trail.core.detect

import dev.kevin.trail.core.model.ActivityMode
import dev.kevin.trail.core.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitTripDetectorTest {
    private val detector = VisitTripDetector()

    @Test
    fun stayOfFiveMinutesWithinOneHundredMetersIsAVisit() {
        val result = detector.detect(
            listOf(
                point(minute = 0),
                point(minute = 5, latitude = 37.0004),
            ),
        )
        assertEquals(0, result.closedVisits.size)
        assertNotNull(result.openVisit)
        assertEquals(2, result.openVisit!!.pointCount)
        assertNull(result.openTrip)
    }

    @Test
    fun fourMinuteDwellIsNotAVisit() {
        val result = detector.detect(
            listOf(
                point(minute = 0),
                point(minute = 4),
                point(minute = 5, latitude = 37.01),
            ),
        )
        assertTrue(result.closedVisits.isEmpty())
        assertNull(result.openVisit)
    }

    @Test
    fun movingPointsBecomeATripAndNotAStay() {
        val points = (0..8).map { minute ->
            point(minute = minute, latitude = 37.0 + minute * 0.002, activity = ActivityMode.WALK)
        }
        val result = detector.detect(points)
        assertTrue(result.closedVisits.isEmpty())
        assertNull(result.openVisit)
        assertNotNull(result.openTrip)
        assertEquals(ActivityMode.WALK, result.openTrip!!.mode)
        assertTrue(result.openTrip!!.distanceMeters > 500)
    }

    @Test
    fun dogWalkBetweenTwoStaysIsOneWalkTrip() {
        val home = mutableListOf<TrackPoint>()
        home += stay(startMinute = 0, minutes = 10, latitude = 37.0)
        home += (1..8).map { step ->
            point(
                minute = 10 + step,
                latitude = 37.0 + step * 0.0015,
                activity = ActivityMode.WALK,
                speedMps = 1.4,
            )
        }
        home += (1..8).map { step ->
            point(
                minute = 18 + step,
                latitude = 37.012 - step * 0.0015,
                activity = ActivityMode.WALK,
                speedMps = 1.4,
            )
        }
        home += stay(startMinute = 30, minutes = 10, latitude = 37.0)
        val result = detector.detect(home)
        assertEquals(1, result.closedVisits.size)
        assertNotNull(result.openVisit)
        assertEquals(1, result.closedTrips.size)
        assertEquals(ActivityMode.WALK, result.closedTrips.single().mode)
        assertTrue(result.closedTrips.single().distanceMeters > 400)
        assertNull(result.openTrip)
    }

    @Test
    fun speedFallbackClassifiesDriveBikeAndWalk() {
        assertEquals(ActivityMode.DRIVE, modeOf(speed = 15.0))
        assertEquals(ActivityMode.BIKE, modeOf(speed = 5.0))
        assertEquals(ActivityMode.WALK, modeOf(speed = 1.2))
    }

    @Test
    fun labeledActivityBeatsSpeed() {
        val points = (0..6).map {
            point(minute = it, latitude = 37.0 + it * 0.002, activity = ActivityMode.BIKE, speedMps = 20.0)
        }
        assertEquals(ActivityMode.BIKE, detector.detect(points).openTrip!!.mode)
    }

    @Test
    fun longGapSplitsTrips() {
        val points = listOf(
            point(minute = 0, latitude = 37.0),
            point(minute = 2, latitude = 37.01),
            point(minute = 40, latitude = 37.02),
            point(minute = 42, latitude = 37.03),
        )
        val result = detector.detect(points)
        assertNotNull(result.openTrip)
        assertEquals(1, result.closedTrips.size)
    }

    @Test
    fun pointJustInsideRadiusStaysInTheCluster() {
        val inside = detector.detect(
            listOf(point(minute = 0), point(minute = 6, latitude = 37.0 + (90.0 / 111_320.0))),
        )
        assertNotNull(inside.openVisit)
        val outside = detector.detect(
            listOf(point(minute = 0), point(minute = 6, latitude = 37.0 + (150.0 / 111_320.0))),
        )
        assertNull(outside.openVisit)
    }

    private fun modeOf(speed: Double): ActivityMode {
        val points = (0..4).map { point(minute = it, latitude = 37.0 + it * 0.01, speedMps = speed) }
        return detector.detect(points).openTrip!!.mode
    }

    private fun stay(startMinute: Int, minutes: Int, latitude: Double): List<TrackPoint> =
        (0..minutes).map { offset -> point(minute = startMinute + offset, latitude = latitude) }

    private fun point(
        minute: Int,
        latitude: Double = 37.0,
        longitude: Double = -122.0,
        activity: ActivityMode? = ActivityMode.STILL,
        speedMps: Double? = 0.0,
    ) = TrackPoint(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = 8.0,
        altitudeMeters = 12.0,
        speedMps = speedMps,
        bearingDegrees = 90.0,
        timestampMillis = minute * 60_000L,
        batteryPercent = 80,
        connection = "w",
        activity = activity,
    )
}
