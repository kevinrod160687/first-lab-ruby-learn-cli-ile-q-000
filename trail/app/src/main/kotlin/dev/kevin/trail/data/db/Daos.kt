package dev.kevin.trail.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PointDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(point: PointEntity): Long

    @Query("SELECT * FROM points WHERE tst >= :from AND tst < :to ORDER BY tst ASC")
    fun observeBetween(from: Long, to: Long): Flow<List<PointEntity>>

    @Query("SELECT * FROM points WHERE tst >= :since ORDER BY tst ASC")
    suspend fun pointsSince(since: Long): List<PointEntity>

    @Query("SELECT MAX(tst) FROM points")
    suspend fun newestTst(): Long?

    @Query("SELECT * FROM points WHERE uploadStatus = 'PENDING' ORDER BY tst ASC LIMIT :limit")
    suspend fun pending(limit: Int = 500): List<PointEntity>

    @Query("SELECT COUNT(*) FROM points WHERE uploadStatus = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("UPDATE points SET batchId = :batchId WHERE id IN (:ids)")
    suspend fun assignBatch(ids: List<Long>, batchId: String)

    @Query("UPDATE points SET uploadStatus = 'SENT', batchId = :batchId WHERE id IN (:ids)")
    suspend fun markSent(ids: List<Long>, batchId: String)

    @Query("UPDATE points SET uploadStatus = 'SUPPRESSED' WHERE id IN (:ids)")
    suspend fun markSuppressed(ids: List<Long>)

    @Query("DELETE FROM points WHERE tst < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("DELETE FROM points")
    suspend fun deleteAll()

    @Query("SELECT * FROM points ORDER BY tst ASC")
    suspend fun all(): List<PointEntity>

    @Query("SELECT * FROM points ORDER BY tst DESC LIMIT 1")
    suspend fun latest(): PointEntity?

    @Query("SELECT * FROM points ORDER BY tst DESC LIMIT 1")
    fun observeLatest(): Flow<PointEntity?>
}

@Dao
interface VisitDao {
    @Insert
    suspend fun insert(visit: VisitEntity): Long

    @Update
    suspend fun update(visit: VisitEntity)

    @Query("SELECT * FROM visits WHERE arrivalTst = :arrival LIMIT 1")
    suspend fun findByArrival(arrival: Long): VisitEntity?

    @Query("SELECT * FROM visits WHERE arrivalTst < :end AND departureTst >= :start ORDER BY arrivalTst ASC")
    fun observeOverlapping(start: Long, end: Long): Flow<List<VisitEntity>>

    @Query(
        """
        SELECT * FROM visits
        WHERE enterStatus = 'PENDING' OR (closed = 1 AND leaveStatus = 'PENDING')
        ORDER BY arrivalTst ASC
        LIMIT :limit
        """,
    )
    suspend fun pending(limit: Int = 200): List<VisitEntity>

    @Query(
        """
        SELECT
          (SELECT COUNT(*) FROM visits WHERE enterStatus = 'PENDING') +
          (SELECT COUNT(*) FROM visits WHERE closed = 1 AND leaveStatus = 'PENDING')
        """,
    )
    fun observePendingTransitions(): Flow<Int>

    @Query("UPDATE visits SET enterBatchId = :batchId WHERE id IN (:ids)")
    suspend fun assignEnterBatch(ids: List<Long>, batchId: String)

    @Query("UPDATE visits SET leaveBatchId = :batchId WHERE id IN (:ids)")
    suspend fun assignLeaveBatch(ids: List<Long>, batchId: String)

    @Query("UPDATE visits SET enterStatus = 'SENT', enterBatchId = :batchId WHERE id IN (:ids)")
    suspend fun markEnterSent(ids: List<Long>, batchId: String)

    @Query("UPDATE visits SET leaveStatus = 'SENT', leaveBatchId = :batchId WHERE id IN (:ids)")
    suspend fun markLeaveSent(ids: List<Long>, batchId: String)

    @Query("UPDATE visits SET enterStatus = 'SUPPRESSED' WHERE id IN (:ids)")
    suspend fun markEnterSuppressed(ids: List<Long>)

    @Query("UPDATE visits SET leaveStatus = 'SUPPRESSED' WHERE id IN (:ids)")
    suspend fun markLeaveSuppressed(ids: List<Long>)

    @Query(
        """
        DELETE FROM visits
        WHERE arrivalTst >= :since AND enterStatus = 'PENDING' AND leaveStatus = 'PENDING'
        """,
    )
    suspend fun deletePendingSince(since: Long)

    @Query(
        """
        DELETE FROM visits
        WHERE arrivalTst >= :since
          AND enterStatus = 'PENDING'
          AND leaveStatus = 'PENDING'
          AND arrivalTst NOT IN (:keep)
        """,
    )
    suspend fun deletePendingSinceExcept(since: Long, keep: List<Long>)

    @Query("DELETE FROM visits WHERE departureTst < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("DELETE FROM visits")
    suspend fun deleteAll()

    @Query("SELECT * FROM visits ORDER BY arrivalTst ASC")
    suspend fun all(): List<VisitEntity>
}

@Dao
interface TripDao {
    @Insert
    suspend fun insertAll(trips: List<TripEntity>)

    @Query("SELECT * FROM trips WHERE startTst < :end AND endTst >= :start ORDER BY startTst ASC")
    fun observeOverlapping(start: Long, end: Long): Flow<List<TripEntity>>

    @Query("DELETE FROM trips WHERE startTst >= :since")
    suspend fun deleteSince(since: Long)

    @Query("DELETE FROM trips WHERE endTst < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("DELETE FROM trips")
    suspend fun deleteAll()

    @Query("SELECT * FROM trips ORDER BY startTst ASC")
    suspend fun all(): List<TripEntity>
}

@Dao
interface ZoneDao {
    @Insert
    suspend fun insert(zone: ZoneEntity): Long

    @Query("SELECT * FROM zones ORDER BY name ASC")
    fun observe(): Flow<List<ZoneEntity>>

    @Query("SELECT * FROM zones ORDER BY name ASC")
    suspend fun all(): List<ZoneEntity>

    @Query("DELETE FROM zones WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM zones")
    suspend fun deleteAll()
}

@Dao
interface SyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: SyncStateEntity)

    @Query("SELECT * FROM sync_state WHERE id = 1")
    fun observe(): Flow<SyncStateEntity?>

    @Query("SELECT * FROM sync_state WHERE id = 1")
    suspend fun current(): SyncStateEntity?

    @Query("DELETE FROM sync_state")
    suspend fun delete()
}
