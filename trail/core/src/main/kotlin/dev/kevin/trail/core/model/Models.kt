package dev.kevin.trail.core.model

/**
 * One location fix. Timestamps are Unix epoch milliseconds.
 * Speeds are meters per second, the unit Android's location API reports.
 */
data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val altitudeMeters: Double?,
    val speedMps: Double?,
    val bearingDegrees: Double?,
    val timestampMillis: Long,
    val batteryPercent: Int?,
    val connection: String?,
    val activity: ActivityMode?,
)

enum class ActivityMode {
    STILL,
    WALK,
    BIKE,
    DRIVE,
    UNKNOWN,
}

data class DetectedVisit(
    val latitude: Double,
    val longitude: Double,
    val arrivalMillis: Long,
    val departureMillis: Long,
    val pointCount: Int,
)

data class DetectedTrip(
    val startMillis: Long,
    val endMillis: Long,
    val mode: ActivityMode,
    val distanceMeters: Double,
    val pointCount: Int,
)

data class DetectionResult(
    val closedVisits: List<DetectedVisit>,
    val openVisit: DetectedVisit?,
    val closedTrips: List<DetectedTrip>,
    val openTrip: DetectedTrip?,
) {
    companion object {
        val EMPTY = DetectionResult(emptyList(), null, emptyList(), null)
    }
}

enum class ZoneAction {
    DROP,
    COARSEN,
}

data class PrivateZone(
    val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val action: ZoneAction,
)

enum class OutboxKind {
    LOCATION,
    ENTER,
    LEAVE,
}

/**
 * A record waiting to be uploaded. [tst] is Unix epoch seconds so it matches
 * OwnTracks and is the deduplication key together with [kind].
 */
data class OutboxRecord(
    val id: Long,
    val kind: OutboxKind,
    val tst: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Int,
    val altitudeMeters: Double?,
    val speedMps: Double?,
    val bearingDegrees: Double?,
    val batteryPercent: Int?,
    val connection: String?,
    val batchId: String?,
    val description: String? = null,
)

enum class Retention {
    DAYS_30,
    DAYS_90,
    DAYS_365,
    FOREVER,
}
