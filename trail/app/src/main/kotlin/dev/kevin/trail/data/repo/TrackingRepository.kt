package dev.kevin.trail.data.repo

import androidx.room.withTransaction
import dev.kevin.trail.core.detect.VisitTripDetector
import dev.kevin.trail.core.export.ExportPoint
import dev.kevin.trail.core.export.ExportTrip
import dev.kevin.trail.core.export.ExportVisit
import dev.kevin.trail.core.model.ActivityMode
import dev.kevin.trail.core.model.DetectedTrip
import dev.kevin.trail.core.model.DetectedVisit
import dev.kevin.trail.core.model.OutboxKind
import dev.kevin.trail.core.model.OutboxRecord
import dev.kevin.trail.core.model.PrivateZone
import dev.kevin.trail.core.model.Retention
import dev.kevin.trail.core.model.TrackPoint
import dev.kevin.trail.core.model.ZoneAction
import dev.kevin.trail.core.retention.RetentionPolicy
import dev.kevin.trail.core.upload.Outbox
import dev.kevin.trail.data.db.PointEntity
import dev.kevin.trail.data.db.SyncStateEntity
import dev.kevin.trail.data.db.TrailDatabase
import dev.kevin.trail.data.db.TripEntity
import dev.kevin.trail.data.db.UploadStatus
import dev.kevin.trail.data.db.VisitEntity
import dev.kevin.trail.data.db.ZoneEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class DaySnapshot(
    val points: List<PointEntity>,
    val visits: List<VisitEntity>,
    val trips: List<TripEntity>,
    val pendingCount: Int,
    val sync: SyncStateEntity?,
)

data class DiaryExport(
    val points: List<ExportPoint>,
    val visits: List<ExportVisit>,
    val trips: List<ExportTrip>,
)

@Singleton
class TrackingRepository @Inject constructor(
    private val database: TrailDatabase,
) : Outbox {
    private val detector = VisitTripDetector()

    fun observeDay(startEpochSeconds: Long, endEpochSeconds: Long): Flow<DaySnapshot> {
        val places = combine(
            database.points().observeBetween(startEpochSeconds, endEpochSeconds),
            database.visits().observeOverlapping(startEpochSeconds, endEpochSeconds),
            database.trips().observeOverlapping(startEpochSeconds, endEpochSeconds),
        ) { points, visits, trips -> Triple(points, visits, trips) }
        val pending = combine(
            database.points().observePendingCount(),
            database.visits().observePendingTransitions(),
        ) { points, transitions -> points + transitions }
        return combine(places, pending, database.sync().observe()) { placesValue, pendingCount, sync ->
            DaySnapshot(placesValue.first, placesValue.second, placesValue.third, pendingCount, sync)
        }
    }

    suspend fun record(point: PointEntity) {
        database.withTransaction {
            database.points().insert(point)
            recompute()
        }
    }

    suspend fun latestPoint(): PointEntity? = database.points().latest()

    fun observeLatest(): Flow<PointEntity?> = database.points().observeLatest()

    suspend fun exportAll(): DiaryExport {
        val points = database.points().all()
        val visits = database.visits().all()
        val trips = database.trips().all()
        return DiaryExport(
            points = points.map { ExportPoint(it.tst, it.latitude, it.longitude, it.altitudeMeters) },
            visits = visits.map { ExportVisit(it.latitude, it.longitude, it.arrivalTst, it.departureTst, it.closed) },
            trips = trips.map {
                ExportTrip(
                    startTst = it.startTst,
                    endTst = it.endTst,
                    mode = it.mode.toActivity(),
                    distanceMeters = it.distanceMeters,
                )
            },
        )
    }

    fun observeZones(): Flow<List<PrivateZone>> =
        database.zones().observe().map { zones -> zones.map { it.toZone() } }

    suspend fun zones(): List<PrivateZone> = database.zones().all().map { it.toZone() }

    suspend fun addZone(zone: PrivateZone) {
        database.zones().insert(
            ZoneEntity(
                name = zone.name,
                latitude = zone.latitude,
                longitude = zone.longitude,
                radiusMeters = zone.radiusMeters,
                action = zone.action.name,
            ),
        )
    }

    suspend fun deleteZone(id: Long) {
        database.zones().delete(id)
    }

    suspend fun enforceRetention(retention: Retention, nowEpochSeconds: Long) {
        val cutoff = RetentionPolicy.cutoffEpochSeconds(retention, nowEpochSeconds) ?: return
        database.withTransaction {
            database.points().deleteOlderThan(cutoff)
            database.visits().deleteOlderThan(cutoff)
            database.trips().deleteOlderThan(cutoff)
        }
    }

    suspend fun wipe() {
        database.withTransaction {
            database.points().deleteAll()
            database.visits().deleteAll()
            database.trips().deleteAll()
            database.zones().deleteAll()
            database.sync().delete()
        }
    }

    suspend fun recordSuccess(epochMs: Long) {
        val previous = database.sync().current()
        database.sync().upsert(
            SyncStateEntity(
                lastSuccessEpochMs = epochMs,
                lastError = null,
            ).let { previous?.copy(lastSuccessEpochMs = epochMs, lastError = null) ?: it },
        )
    }

    suspend fun recordError(message: String) {
        val previous = database.sync().current()
        database.sync().upsert(
            SyncStateEntity(
                lastSuccessEpochMs = previous?.lastSuccessEpochMs,
                lastError = message.take(ERROR_LIMIT),
            ),
        )
    }

    suspend fun clearError() {
        val previous = database.sync().current() ?: return
        if (previous.lastError == null) return
        database.sync().upsert(previous.copy(lastError = null))
    }

    override suspend fun pending(): List<OutboxRecord> {
        val points = database.points().pending().map { it.toOutbox() }
        val transitions = database.visits().pending().flatMap { it.toOutbox() }
        return points + transitions
    }

    override suspend fun assignBatch(records: List<OutboxRecord>, batchId: String) {
        records.ids(OutboxKind.LOCATION).chunked(CHUNK).forEach {
            database.points().assignBatch(it, batchId)
        }
        records.ids(OutboxKind.ENTER).chunked(CHUNK).forEach {
            database.visits().assignEnterBatch(it, batchId)
        }
        records.ids(OutboxKind.LEAVE).chunked(CHUNK).forEach {
            database.visits().assignLeaveBatch(it, batchId)
        }
    }

    override suspend fun markSent(records: List<OutboxRecord>, batchId: String) {
        records.ids(OutboxKind.LOCATION).chunked(CHUNK).forEach {
            database.points().markSent(it, batchId)
        }
        records.ids(OutboxKind.ENTER).chunked(CHUNK).forEach {
            database.visits().markEnterSent(it, batchId)
        }
        records.ids(OutboxKind.LEAVE).chunked(CHUNK).forEach {
            database.visits().markLeaveSent(it, batchId)
        }
    }

    override suspend fun markSuppressed(records: List<OutboxRecord>) {
        records.ids(OutboxKind.LOCATION).chunked(CHUNK).forEach {
            database.points().markSuppressed(it)
        }
        records.ids(OutboxKind.ENTER).chunked(CHUNK).forEach {
            database.visits().markEnterSuppressed(it)
        }
        records.ids(OutboxKind.LEAVE).chunked(CHUNK).forEach {
            database.visits().markLeaveSuppressed(it)
        }
    }

    private suspend fun recompute() {
        val newest = database.points().newestTst() ?: return
        val since = newest - LOOKBACK_SECONDS
        val loaded = database.points().pointsSince(since)
        val result = detector.detect(loaded.map { it.toTrackPoint() })
        val saved = result.closedVisits.map { it to true } + listOfNotNull(result.openVisit).map { it to false }
        val arrivals = mutableListOf<Long>()
        saved.forEach { (visit, closed) ->
            val arrival = visit.arrivalMillis / MILLIS_PER_SECOND
            arrivals += arrival
            val existing = database.visits().findByArrival(arrival)
            if (existing == null) {
                database.visits().insert(visit.toEntity(closed))
            } else {
                database.visits().update(
                    existing.copy(
                        latitude = visit.latitude,
                        longitude = visit.longitude,
                        departureTst = visit.departureMillis / MILLIS_PER_SECOND,
                        pointCount = visit.pointCount,
                        closed = existing.closed || closed,
                    ),
                )
            }
        }
        if (arrivals.isEmpty()) {
            database.visits().deletePendingSince(since)
        } else {
            database.visits().deletePendingSinceExcept(since, arrivals)
        }
        database.trips().deleteSince(since)
        val trips = result.closedTrips.map { it.toEntity() } + listOfNotNull(result.openTrip).map { it.toEntity() }
        if (trips.isNotEmpty()) database.trips().insertAll(trips)
    }

    private fun List<OutboxRecord>.ids(kind: OutboxKind): List<Long> =
        filter { it.kind == kind }.map { it.id }

    private companion object {
        const val LOOKBACK_SECONDS = 36 * 60 * 60
        const val MILLIS_PER_SECOND = 1_000L
        const val CHUNK = 400
        const val ERROR_LIMIT = 200
    }
}

private fun PointEntity.toTrackPoint(): TrackPoint = TrackPoint(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracyMeters.toDouble(),
    altitudeMeters = altitudeMeters,
    speedMps = speedMps,
    bearingDegrees = bearingDegrees,
    timestampMillis = tst * 1_000L,
    batteryPercent = batteryPercent,
    connection = connection,
    activity = activity?.toActivity(),
)

private fun PointEntity.toOutbox(): OutboxRecord = OutboxRecord(
    id = id,
    kind = OutboxKind.LOCATION,
    tst = tst,
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracyMeters,
    altitudeMeters = altitudeMeters,
    speedMps = speedMps,
    bearingDegrees = bearingDegrees,
    batteryPercent = batteryPercent,
    connection = connection,
    batchId = batchId,
)

private fun VisitEntity.toOutbox(): List<OutboxRecord> = buildList {
    if (enterStatus == UploadStatus.PENDING) {
        add(transition(OutboxKind.ENTER, arrivalTst, enterBatchId))
    }
    if (closed && leaveStatus == UploadStatus.PENDING) {
        add(transition(OutboxKind.LEAVE, departureTst, leaveBatchId))
    }
}

private fun VisitEntity.transition(kind: OutboxKind, tst: Long, batchId: String?) = OutboxRecord(
    id = id,
    kind = kind,
    tst = tst,
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = 50,
    altitudeMeters = null,
    speedMps = null,
    bearingDegrees = null,
    batteryPercent = null,
    connection = null,
    batchId = batchId,
    description = "visit",
)

private fun DetectedVisit.toEntity(closed: Boolean) = VisitEntity(
    latitude = latitude,
    longitude = longitude,
    arrivalTst = arrivalMillis / 1_000L,
    departureTst = departureMillis / 1_000L,
    pointCount = pointCount,
    closed = closed,
)

private fun DetectedTrip.toEntity() = TripEntity(
    startTst = startMillis / 1_000L,
    endTst = endMillis / 1_000L,
    mode = mode.name,
    distanceMeters = distanceMeters,
    pointCount = pointCount,
)

private fun ZoneEntity.toZone() = PrivateZone(
    id = id,
    name = name,
    latitude = latitude,
    longitude = longitude,
    radiusMeters = radiusMeters,
    action = runCatching { ZoneAction.valueOf(action) }.getOrDefault(ZoneAction.COARSEN),
)

private fun String.toActivity(): ActivityMode =
    runCatching { ActivityMode.valueOf(this) }.getOrDefault(ActivityMode.UNKNOWN)
