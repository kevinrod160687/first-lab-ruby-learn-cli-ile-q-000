package dev.kevin.trail.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class UploadStatusConverter {
    @TypeConverter
    fun toStatus(value: String): UploadStatus = UploadStatus.valueOf(value)

    @TypeConverter
    fun fromStatus(value: UploadStatus): String = value.name
}

@Database(
    entities = [
        PointEntity::class,
        VisitEntity::class,
        TripEntity::class,
        ZoneEntity::class,
        SyncStateEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(UploadStatusConverter::class)
abstract class TrailDatabase : RoomDatabase() {
    abstract fun points(): PointDao
    abstract fun visits(): VisitDao
    abstract fun trips(): TripDao
    abstract fun zones(): ZoneDao
    abstract fun sync(): SyncDao
}
