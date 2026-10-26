package dev.kevin.trail.core.payload

import dev.kevin.trail.core.model.OutboxKind
import dev.kevin.trail.core.model.OutboxRecord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OwnTracksPayloadTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun encodesLocationTransitionAndTrailMeta() {
        val body = OwnTracksPayload.encode(
            meta = TrailMeta(deviceId = "device-1", batchId = "batch-9", appVersion = "1.0.0"),
            records = listOf(
                record(kind = OutboxKind.LOCATION, tst = 1_700_000_000, speedMps = 10.0, bearing = 359.6),
                record(kind = OutboxKind.ENTER, tst = 1_700_000_100),
                record(kind = OutboxKind.LEAVE, tst = 1_700_000_500),
            ),
            trackerId = "KV",
        )
        val root = json.parseToJsonElement(body).jsonObject
        val meta = root.getValue("trail_meta").jsonObject
        assertEquals("device-1", meta.getValue("device_id").jsonPrimitive.content)
        assertEquals("batch-9", meta.getValue("batch_id").jsonPrimitive.content)
        assertEquals("1.0.0", meta.getValue("app_version").jsonPrimitive.content)
        val messages = root.getValue("messages").jsonArray
        assertEquals(3, messages.size)
        val location = messages[0].jsonObject
        assertEquals("location", location.getValue("_type").jsonPrimitive.content)
        assertEquals("37.5", location.getValue("lat").jsonPrimitive.content)
        assertEquals("-122.25", location.getValue("lon").jsonPrimitive.content)
        assertEquals("36", location.getValue("vel").jsonPrimitive.content)
        assertEquals("0", location.getValue("cog").jsonPrimitive.content)
        assertEquals("1700000000", location.getValue("tst").jsonPrimitive.content)
        assertEquals("81", location.getValue("batt").jsonPrimitive.content)
        assertEquals("w", location.getValue("conn").jsonPrimitive.content)
        assertEquals("KV", location.getValue("tid").jsonPrimitive.content)
        assertEquals("enter", messages[1].jsonObject.getValue("event").jsonPrimitive.content)
        assertEquals("transition", messages[1].jsonObject.getValue("_type").jsonPrimitive.content)
        assertEquals("leave", messages[2].jsonObject.getValue("event").jsonPrimitive.content)
        assertFalse(body.contains("webhook"))
        assertFalse(body.contains("authorization"))
    }

    @Test
    fun speedConversionUsesKilometersPerHour() {
        assertEquals(36, OwnTracksPayload.speedKmh(10.0))
        assertEquals(0, OwnTracksPayload.speedKmh(null))
        assertEquals(90, OwnTracksPayload.courseDegrees(90.2))
        assertEquals(1, OwnTracksPayload.courseDegrees(361.0))
    }

    private fun record(kind: OutboxKind, tst: Long, speedMps: Double? = 0.0, bearing: Double? = 10.0) = OutboxRecord(
        id = tst,
        kind = kind,
        tst = tst,
        latitude = 37.5,
        longitude = -122.25,
        accuracyMeters = 6,
        altitudeMeters = 15.2,
        speedMps = speedMps,
        bearingDegrees = bearing,
        batteryPercent = 81,
        connection = "w",
        batchId = "batch-9",
        description = "visit",
    )
}
