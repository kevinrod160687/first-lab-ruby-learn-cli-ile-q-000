package dev.kevin.trail.core.payload

import dev.kevin.trail.core.model.OutboxKind
import dev.kevin.trail.core.model.OutboxRecord
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

@Serializable
data class TrailMeta(
    @SerialName("device_id") val deviceId: String,
    @SerialName("batch_id") val batchId: String,
    @SerialName("app_version") val appVersion: String,
)

@Serializable
data class LocationMessage(
    @SerialName("_type") val type: String = "location",
    val lat: Double,
    val lon: Double,
    val acc: Int,
    val alt: Int,
    val vel: Int,
    val cog: Int,
    val tst: Long,
    val batt: Int,
    val conn: String,
    val tid: String,
)

@Serializable
data class TransitionMessage(
    @SerialName("_type") val type: String = "transition",
    val event: String,
    val lat: Double,
    val lon: Double,
    val acc: Int,
    val tst: Long,
    val tid: String,
    val desc: String,
)

/**
 * OwnTracks-compatible messages plus a trail_meta block.
 *
 * A bare JSON array cannot carry the batch metadata, so the body is an object
 * whose `messages` array holds `_type: location` and `_type: transition`
 * objects. Velocity is kilometers per hour, the OwnTracks unit. `tst` is
 * Unix seconds.
 */
object OwnTracksPayload {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(meta: TrailMeta, records: List<OutboxRecord>, trackerId: String): String {
        val messages = records.map { record ->
            when (record.kind) {
                OutboxKind.LOCATION -> json.encodeToJsonElement(record.toLocation(trackerId))
                OutboxKind.ENTER -> json.encodeToJsonElement(record.toTransition("enter", trackerId))
                OutboxKind.LEAVE -> json.encodeToJsonElement(record.toTransition("leave", trackerId))
            }
        }
        val root = buildJsonObject {
            put("trail_meta", json.encodeToJsonElement(meta))
            put("messages", JsonArray(messages))
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    fun speedKmh(speedMps: Double?): Int =
        ((speedMps ?: 0.0) * METERS_PER_SECOND_TO_KMH).roundToInt().coerceAtLeast(0)

    fun courseDegrees(bearing: Double?): Int {
        if (bearing == null || bearing.isNaN()) return 0
        val normalized = ((bearing % DEGREES) + DEGREES) % DEGREES
        val rounded = normalized.roundToInt()
        return if (rounded >= DEGREES.toInt()) 0 else rounded.coerceAtLeast(0)
    }

    private fun OutboxRecord.toLocation(trackerId: String) = LocationMessage(
        lat = latitude,
        lon = longitude,
        acc = accuracyMeters.coerceAtLeast(0),
        alt = (altitudeMeters ?: 0.0).roundToInt(),
        vel = speedKmh(speedMps),
        cog = courseDegrees(bearingDegrees),
        tst = tst,
        batt = batteryPercent ?: 0,
        conn = connection?.takeIf { it == "w" || it == "m" || it == "o" } ?: "o",
        tid = trackerId,
    )

    private fun OutboxRecord.toTransition(event: String, trackerId: String) = TransitionMessage(
        event = event,
        lat = latitude,
        lon = longitude,
        acc = accuracyMeters.coerceAtLeast(0),
        tst = tst,
        tid = trackerId,
        desc = description?.takeIf { it.isNotBlank() } ?: "visit",
    )

    private const val METERS_PER_SECOND_TO_KMH = 3.6
    private const val DEGREES = 360.0
}
