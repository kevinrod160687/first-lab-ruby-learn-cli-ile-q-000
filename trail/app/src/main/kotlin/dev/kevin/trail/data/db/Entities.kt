package dev.kevin.trail.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class UploadStatus {
    PENDING,
    SENT,
    SUPPRESSED,
}

@Entity(
    tableName = "points",
    indices = [Index(value = ["tst"], unique = true)],
)
data class PointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tst: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Int,
    val altitudeMeters: Double?,
    val speedMps: Double?,
    val bearingDegrees: Double?,
    val batteryPercent: Int?,
    val connection: String?,
    val activity: String?,
    val uploadStatus: UploadStatus = UploadStatus.PENDING,
    val batchId: String? = null,
)

@Entity(
    tableName = "visits",
    indices = [Index(value = ["arrivalTst"], unique = true)],
)
data class VisitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val arrivalTst: Long,
    val departureTst: Long,
    val pointCount: Int,
    val closed: Boolean,
    val enterStatus: UploadStatus = UploadStatus.PENDING,
    val leaveStatus: UploadStatus = UploadStatus.PENDING,
    val enterBatchId: String? = null,
    val leaveBatchId: String? = null,
)

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTst: Long,
    val endTst: Long,
    val mode: String,
    val distanceMeters: Double,
    val pointCount: Int,
)

@Entity(tableName = "zones")
data class ZoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val action: String,
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = SINGLETON,
    val lastSuccessEpochMs: Long? = null,
    val lastError: String? = null,
) {
    companion object {
        const val SINGLETON = 1
    }
}
