package dev.kevin.trail.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kevin.trail.core.export.TrailExporter
import dev.kevin.trail.core.model.ActivityMode
import dev.kevin.trail.data.db.PointEntity
import dev.kevin.trail.data.db.TripEntity
import dev.kevin.trail.data.db.VisitEntity
import dev.kevin.trail.data.repo.SettingsRepository
import dev.kevin.trail.data.repo.TrackingRepository
import dev.kevin.trail.location.TrackingController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlin.math.roundToInt

enum class TimelineKind {
    STAY,
    HERE,
    WALK,
    BIKE,
    DRIVE,
    OUTING,
}

data class TimelineRow(
    val key: String,
    val kind: TimelineKind,
    val detail: String,
)

data class TodayUiState(
    val points: List<PointEntity> = emptyList(),
    val visits: List<VisitEntity> = emptyList(),
    val timeline: List<TimelineRow> = emptyList(),
    val pendingCount: Int = 0,
    val lastSyncLabel: String = "",
    val lastError: String? = null,
    val paused: Boolean = false,
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repository: TrackingRepository,
    private val settingsRepository: SettingsRepository,
    private val controller: TrackingController,
) : ViewModel() {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zone)

    val state: StateFlow<TodayUiState> = combine(
        repository.observeDay(startOfTodaySeconds(), endOfTodaySeconds()),
        settingsRepository.observe(),
    ) { day, settings ->
        TodayUiState(
            points = day.points,
            visits = day.visits,
            timeline = timeline(day.visits, day.trips),
            pendingCount = day.pendingCount,
            lastSyncLabel = day.sync?.lastSuccessEpochMs?.let { timeFormat.format(Instant.ofEpochMilli(it)) }.orEmpty(),
            lastError = day.sync?.lastError,
            paused = settings.isPaused(System.currentTimeMillis()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun ensureTracking() = controller.startIfAllowed()

    fun pause(hours: Int?) = controller.pause(hours)

    fun resume() = controller.resume()

    fun uploadNow() = controller.uploadNow()

    suspend fun exportGpx(): String {
        val diary = repository.exportAll()
        return TrailExporter.gpx(diary.points, diary.visits)
    }

    suspend fun exportJson(): String {
        val diary = repository.exportAll()
        return TrailExporter.json(diary.points, diary.visits, diary.trips, System.currentTimeMillis())
    }

    private fun startOfTodaySeconds(): Long = LocalDate.now(zone).atStartOfDay(zone).toEpochSecond()

    private fun endOfTodaySeconds(): Long = startOfTodaySeconds() + SECONDS_PER_DAY

    private fun timeline(visits: List<VisitEntity>, trips: List<TripEntity>): List<TimelineRow> {
        val rows = visits.map { visit ->
            TimelineRow(
                key = "v-${visit.arrivalTst}",
                kind = if (visit.closed) TimelineKind.STAY else TimelineKind.HERE,
                detail = "${range(visit.arrivalTst, visit.departureTst)} · ${visit.pointCount}",
            )
        } + trips.map { trip ->
            val mode = runCatching { ActivityMode.valueOf(trip.mode) }.getOrDefault(ActivityMode.UNKNOWN)
            TimelineRow(
                key = "t-${trip.startTst}",
                kind = when (mode) {
                    ActivityMode.WALK -> TimelineKind.WALK
                    ActivityMode.BIKE -> TimelineKind.BIKE
                    ActivityMode.DRIVE -> TimelineKind.DRIVE
                    else -> TimelineKind.OUTING
                },
                detail = "${range(trip.startTst, trip.endTst)} · ${formatDistance(trip.distanceMeters)}",
            )
        }
        return rows.sortedBy { it.key.substringAfter('-').toLongOrNull() ?: 0L }
    }

    private fun range(startTst: Long, endTst: Long): String {
        val start = timeFormat.format(Instant.ofEpochSecond(startTst))
        val end = timeFormat.format(Instant.ofEpochSecond(endTst))
        return "$start – $end"
    }

    private fun formatDistance(meters: Double): String =
        if (meters < 1_000) "${meters.roundToInt()} m" else "${"%.1f".format(meters / 1_000.0)} km"

    private companion object {
        const val SECONDS_PER_DAY = 86_400L
    }
}
