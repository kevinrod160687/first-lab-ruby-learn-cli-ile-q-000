package dev.kevin.trail.core.detect

import dev.kevin.trail.core.geo.Geo
import dev.kevin.trail.core.model.ActivityMode
import dev.kevin.trail.core.model.DetectedTrip
import dev.kevin.trail.core.model.DetectedVisit
import dev.kevin.trail.core.model.DetectionResult
import dev.kevin.trail.core.model.TrackPoint

/**
 * Groups fixes into stays and the trips between them.
 *
 * A stay is a run of points that remain within [radiusMeters] of the first
 * point in the run for at least [dwellMillis]. Everything else is movement.
 * A gap longer than [tripGapMillis] splits movement so a paused phone does not
 * invent a trip across the hole.
 *
 * The visit at the end of the input is left open: we have not seen a departure,
 * so a leave transition should not be uploaded yet.
 */
class VisitTripDetector(
    private val dwellMillis: Long = DEFAULT_DWELL_MILLIS,
    private val radiusMeters: Double = DEFAULT_RADIUS_METERS,
    private val tripGapMillis: Long = DEFAULT_TRIP_GAP_MILLIS,
) {
    fun detect(points: List<TrackPoint>): DetectionResult {
        if (points.isEmpty()) return DetectionResult.EMPTY
        return label(cluster(points.sortedBy { it.timestampMillis }))
    }

    private fun cluster(sorted: List<TrackPoint>): List<List<TrackPoint>> {
        val clusters = mutableListOf<List<TrackPoint>>()
        var current = mutableListOf(sorted.first())
        var anchor = sorted.first()
        for (point in sorted.drop(1)) {
            if (Geo.metersBetween(anchor, point) <= radiusMeters) {
                current += point
            } else {
                clusters += current
                current = mutableListOf(point)
                anchor = point
            }
        }
        clusters += current
        return clusters
    }

    private fun isVisit(cluster: List<TrackPoint>): Boolean {
        if (cluster.size < 2) return false
        return cluster.last().timestampMillis - cluster.first().timestampMillis >= dwellMillis
    }

    private fun label(clusters: List<List<TrackPoint>>): DetectionResult {
        val closedVisits = mutableListOf<DetectedVisit>()
        val closedTrips = mutableListOf<DetectedTrip>()
        var openVisit: DetectedVisit? = null
        var openTrip: DetectedTrip? = null
        var pendingMoves = mutableListOf<TrackPoint>()
        var departPoint: TrackPoint? = null

        fun emitMoves(nextArrival: TrackPoint?, closeTrip: Boolean) {
            val pieces = splitByGap(pendingMoves)
            if (pieces.isEmpty()) {
                val origin = departPoint
                if (origin != null && nextArrival != null) {
                    val gap = nextArrival.timestampMillis - origin.timestampMillis
                    val distance = Geo.metersBetween(origin, nextArrival)
                    if (gap in 0..tripGapMillis && distance > radiusMeters) {
                        val trip = buildTrip(listOf(origin, nextArrival), null, null)
                        if (closeTrip) closedTrips += trip else openTrip = trip
                    }
                }
            } else {
                pieces.forEachIndexed { pieceIndex, piece ->
                    val lastPiece = pieceIndex == pieces.lastIndex
                    val prefix = if (pieceIndex == 0) departPoint else null
                    val suffix = if (lastPiece) nextArrival else null
                    val linkedPrefix = prefix?.takeIf {
                        piece.first().timestampMillis - it.timestampMillis <= tripGapMillis
                    }
                    val linkedSuffix = suffix?.takeIf {
                        it.timestampMillis - piece.last().timestampMillis <= tripGapMillis
                    }
                    val trip = buildTrip(piece, linkedPrefix, linkedSuffix)
                    if (lastPiece && !closeTrip) {
                        openTrip = trip
                    } else {
                        closedTrips += trip
                    }
                }
            }
            pendingMoves = mutableListOf()
        }

        clusters.forEachIndexed { index, cluster ->
            val last = index == clusters.lastIndex
            if (isVisit(cluster)) {
                emitMoves(nextArrival = cluster.first(), closeTrip = true)
                val visit = cluster.toVisit()
                if (last) openVisit = visit else closedVisits += visit
                departPoint = cluster.last()
            } else {
                pendingMoves += cluster
                if (last) emitMoves(nextArrival = null, closeTrip = false)
            }
        }
        return DetectionResult(closedVisits, openVisit, closedTrips, openTrip)
    }

    private fun splitByGap(points: List<TrackPoint>): List<List<TrackPoint>> {
        if (points.isEmpty()) return emptyList()
        val pieces = mutableListOf<List<TrackPoint>>()
        var current = mutableListOf(points.first())
        for (point in points.drop(1)) {
            if (point.timestampMillis - current.last().timestampMillis > tripGapMillis) {
                pieces += current
                current = mutableListOf(point)
            } else {
                current += point
            }
        }
        pieces += current
        return pieces
    }

    private fun buildTrip(
        points: List<TrackPoint>,
        prefix: TrackPoint?,
        suffix: TrackPoint?,
    ): DetectedTrip {
        val start = prefix?.timestampMillis ?: points.first().timestampMillis
        val end = suffix?.timestampMillis ?: points.last().timestampMillis
        return DetectedTrip(
            startMillis = start,
            endMillis = end,
            mode = modeOf(points),
            distanceMeters = pathDistance(prefix, points, suffix),
            pointCount = points.size,
        )
    }

    private fun pathDistance(
        prefix: TrackPoint?,
        points: List<TrackPoint>,
        suffix: TrackPoint?,
    ): Double {
        val path = buildList {
            if (prefix != null) add(prefix)
            addAll(points)
            if (suffix != null) add(suffix)
        }
        return path.zipWithNext().sumOf { (from, to) ->
            val gap = to.timestampMillis - from.timestampMillis
            if (gap > tripGapMillis) 0.0 else Geo.metersBetween(from, to)
        }
    }

    private fun modeOf(points: List<TrackPoint>): ActivityMode {
        val labeled = points.mapNotNull { it.activity }.filter {
            it == ActivityMode.WALK || it == ActivityMode.BIKE || it == ActivityMode.DRIVE
        }
        if (labeled.isNotEmpty()) {
            return labeled.groupingBy { it }.eachCount().maxBy { it.value }.key
        }
        val speeds = points.mapNotNull { it.speedMps }.filter { it >= MOVING_SPEED_MPS }
        if (speeds.isEmpty()) return ActivityMode.UNKNOWN
        val average = speeds.average()
        return when {
            average < WALK_MAX_MPS -> ActivityMode.WALK
            average < BIKE_MAX_MPS -> ActivityMode.BIKE
            else -> ActivityMode.DRIVE
        }
    }

    private fun List<TrackPoint>.toVisit(): DetectedVisit = DetectedVisit(
        latitude = map { it.latitude }.average(),
        longitude = map { it.longitude }.average(),
        arrivalMillis = first().timestampMillis,
        departureMillis = last().timestampMillis,
        pointCount = size,
    )

    companion object {
        const val DEFAULT_DWELL_MILLIS = 5 * 60 * 1000L
        const val DEFAULT_RADIUS_METERS = 100.0
        const val DEFAULT_TRIP_GAP_MILLIS = 30 * 60 * 1000L
        const val WALK_MAX_MPS = 2.2
        const val BIKE_MAX_MPS = 8.0
        private const val MOVING_SPEED_MPS = 0.4
    }
}
