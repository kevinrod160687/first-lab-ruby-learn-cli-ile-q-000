package dev.kevin.trail.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.kevin.trail.core.model.ActivityMode
import dev.kevin.trail.core.model.Retention
import dev.kevin.trail.data.db.PointEntity
import dev.kevin.trail.data.db.TrailDatabase
import dev.kevin.trail.data.db.UploadStatus
import dev.kevin.trail.data.repo.TrackingRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestTrailApplication::class)
class TrailDatabaseTest {
    private lateinit var database: TrailDatabase
    private lateinit var repository: TrackingRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(context, TrailDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TrackingRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun pointTimestampIsUniqueAndPendingUntilMarkedSent() = runBlocking {
        val first = point(tst = 1_700_000_000)
        assertTrue(database.points().insert(first) > 0)
        assertEquals(-1, database.points().insert(first.copy(latitude = 1.0)))
        assertEquals(1, database.points().pending().size)
        val stored = database.points().all().single()
        database.points().assignBatch(listOf(stored.id), "batch-a")
        database.points().markSent(listOf(stored.id), "batch-a")
        assertEquals(0, database.points().pending().size)
        assertEquals(UploadStatus.SENT, database.points().all().single().uploadStatus)
        assertEquals("batch-a", database.points().all().single().batchId)
    }

    @Test
    fun retentionDeletesOldPointsAndLeavesRecentOnes() = runBlocking {
        val now = 1_800_000_000L
        val day = 86_400L
        val olderThanRetention = now - 40 * day
        val recent = now - day
        database.points().insert(point(tst = olderThanRetention))
        database.points().insert(point(tst = recent))
        repository.enforceRetention(Retention.DAYS_30, nowEpochSeconds = now)
        assertEquals(listOf(recent), database.points().all().map { it.tst })
    }

    @Test
    fun stayThenWalkThenStayIsStoredAsVisitsAndATrip() = runBlocking {
        val start = 1_800_000_000L
        stay(start, minutes = 10, latitude = 37.0).forEach { repository.record(it) }
        (1..8).forEach { step ->
            repository.record(
                point(
                    tst = start + (10 + step) * 60,
                    latitude = 37.0 + step * 0.0015,
                    activity = ActivityMode.WALK.name,
                    speedMps = 1.4,
                ),
            )
        }
        stay(start + 30 * 60, minutes = 10, latitude = 37.0).forEach { repository.record(it) }
        val visits = database.visits().all()
        val trips = database.trips().all()
        assertEquals(2, visits.size)
        assertTrue(visits.first().closed)
        assertTrue(!visits.last().closed)
        assertEquals(1, trips.size)
        assertEquals(ActivityMode.WALK.name, trips.single().mode)
        assertTrue(trips.single().distanceMeters > 400)
    }

    @Test
    fun wipeRemovesLocationData() = runBlocking {
        repository.record(point(tst = 10))
        repository.addZone(
            dev.kevin.trail.core.model.PrivateZone(
                name = "Home",
                latitude = 1.0,
                longitude = 2.0,
                radiusMeters = 100.0,
                action = dev.kevin.trail.core.model.ZoneAction.DROP,
            ),
        )
        repository.wipe()
        assertTrue(database.points().all().isEmpty())
        assertTrue(database.zones().all().isEmpty())
    }

    private fun stay(startTst: Long, minutes: Int, latitude: Double): List<PointEntity> =
        (0..minutes).map { offset -> point(tst = startTst + offset * 60, latitude = latitude) }

    private fun point(
        tst: Long,
        latitude: Double = 37.0,
        longitude: Double = -122.0,
        activity: String? = ActivityMode.STILL.name,
        speedMps: Double? = 0.0,
    ) = PointEntity(
        tst = tst,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = 8,
        altitudeMeters = 12.0,
        speedMps = speedMps,
        bearingDegrees = 10.0,
        batteryPercent = 80,
        connection = "w",
        activity = activity,
    )
}

class TestTrailApplication : Application()
