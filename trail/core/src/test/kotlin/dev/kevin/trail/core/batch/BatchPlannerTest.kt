package dev.kevin.trail.core.batch

import dev.kevin.trail.core.model.OutboxKind
import dev.kevin.trail.core.model.OutboxRecord
import dev.kevin.trail.core.model.PrivateZone
import dev.kevin.trail.core.model.ZoneAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchPlannerTest {
    private var ids = 0
    private val planner = BatchPlanner { "batch-${++ids}" }

    @Test
    fun emptyPendingProducesNoPlan() {
        assertNull(planner.plan(emptyList(), emptyList()))
    }

    @Test
    fun dedupesSameKindAndTimestamp() {
        val plan = planner.plan(
            listOf(
                record(id = 1, tst = 100),
                record(id = 2, tst = 100),
                record(id = 3, tst = 200),
            ),
            emptyList(),
        )!!
        assertEquals(listOf(2L, 3L), plan.toSend.map { it.id })
        assertEquals(listOf(1L), plan.suppressed.map { it.id })
        assertEquals("batch-1", plan.batchId)
        assertEquals(false, plan.reusedBatchId)
    }

    @Test
    fun retriesReuseTheExistingBatchId() {
        val plan = planner.plan(
            listOf(record(id = 4, tst = 100, batchId = "already"), record(id = 5, tst = 300)),
            emptyList(),
        )!!
        assertEquals("already", plan.batchId)
        assertTrue(plan.reusedBatchId)
        assertEquals(listOf(4L), plan.toSend.map { it.id })
    }

    @Test
    fun dropZoneSuppressesAndCoarsenChangesOnlyThePayloadCopy() {
        val zones = listOf(
            PrivateZone(name = "Home", latitude = 37.0, longitude = -122.0, radiusMeters = 100.0, action = ZoneAction.DROP),
            PrivateZone(name = "Park", latitude = 38.0, longitude = -121.0, radiusMeters = 100.0, action = ZoneAction.COARSEN),
        )
        val plan = planner.plan(
            listOf(
                record(id = 1, tst = 1, latitude = 37.0, longitude = -122.0),
                record(id = 2, tst = 2, latitude = 38.0, longitude = -121.0),
                record(id = 3, tst = 3, latitude = 40.0, longitude = -70.0),
            ),
            zones,
        )!!
        assertEquals(listOf(1L), plan.suppressed.map { it.id })
        val coarsened = plan.toSend.first { it.id == 2L }
        assertTrue(coarsened.latitude != 38.0 || coarsened.longitude != -121.0)
        assertTrue(coarsened.accuracyMeters >= 1000)
        assertEquals(40.0, plan.toSend.first { it.id == 3L }.latitude, 0.0)
    }

    private fun record(
        id: Long,
        tst: Long,
        latitude: Double = 10.0,
        longitude: Double = 20.0,
        batchId: String? = null,
        kind: OutboxKind = OutboxKind.LOCATION,
    ) = OutboxRecord(
        id = id,
        kind = kind,
        tst = tst,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = 12,
        altitudeMeters = 3.0,
        speedMps = 1.0,
        bearingDegrees = 10.0,
        batteryPercent = 77,
        connection = "m",
        batchId = batchId,
    )
}
