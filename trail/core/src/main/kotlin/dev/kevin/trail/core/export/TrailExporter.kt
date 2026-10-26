package dev.kevin.trail.core.export

import dev.kevin.trail.core.model.ActivityMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

data class ExportPoint(
    val tst: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
)

data class ExportVisit(
    val latitude: Double,
    val longitude: Double,
    val arrivalTst: Long,
    val departureTst: Long,
    val closed: Boolean,
)

data class ExportTrip(
    val startTst: Long,
    val endTst: Long,
    val mode: ActivityMode,
    val distanceMeters: Double,
)

/**
 * Local export of the on-device diary. This is full precision. Private-zone
 * filtering applies to webhook uploads, not to an export the person shares
 * themselves.
 */
object TrailExporter {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun gpx(points: List<ExportPoint>, visits: List<ExportVisit>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append('\n')
        append("""<gpx version="1.1" creator="Trail" xmlns="http://www.topografix.com/GPX/1/1">""")
        append('\n')
        visits.forEach { visit ->
            append("""  <wpt lat="${visit.latitude}" lon="${visit.longitude}">""")
            append('\n')
            append("    <name>Stay</name>\n")
            append("    <time>${iso(visit.arrivalTst)}</time>\n")
            append("  </wpt>\n")
        }
        append("  <trk>\n    <name>Trail</name>\n    <trkseg>\n")
        points.sortedBy { it.tst }.forEach { point ->
            append("""      <trkpt lat="${point.latitude}" lon="${point.longitude}">""")
            append('\n')
            point.altitudeMeters?.let { append("        <ele>$it</ele>\n") }
            append("        <time>${iso(point.tst)}</time>\n")
            append("      </trkpt>\n")
        }
        append("    </trkseg>\n  </trk>\n</gpx>\n")
    }

    fun json(points: List<ExportPoint>, visits: List<ExportVisit>, trips: List<ExportTrip>, exportedAtEpochMs: Long): String {
        val document = ExportDocument(
            exportedAtEpochMs = exportedAtEpochMs,
            points = points.sortedBy { it.tst }.map {
                JsonPoint(it.tst, it.latitude, it.longitude, it.altitudeMeters)
            },
            visits = visits.map {
                JsonVisit(it.latitude, it.longitude, it.arrivalTst, it.departureTst, it.closed)
            },
            trips = trips.map {
                JsonTrip(it.startTst, it.endTst, it.mode.name, it.distanceMeters)
            },
        )
        return json.encodeToString(document)
    }

    private fun iso(tstSeconds: Long): String = Instant.ofEpochSecond(tstSeconds).toString()

    @Serializable
    private data class ExportDocument(
        val exportedAtEpochMs: Long,
        val points: List<JsonPoint>,
        val visits: List<JsonVisit>,
        val trips: List<JsonTrip>,
    )

    @Serializable
    private data class JsonPoint(
        val tst: Long,
        val lat: Double,
        val lon: Double,
        val alt: Double? = null,
    )

    @Serializable
    private data class JsonVisit(
        val lat: Double,
        val lon: Double,
        val arrivalTst: Long,
        val departureTst: Long,
        val closed: Boolean,
    )

    @Serializable
    private data class JsonTrip(
        val startTst: Long,
        val endTst: Long,
        val mode: String,
        val distanceMeters: Double,
    )
}
