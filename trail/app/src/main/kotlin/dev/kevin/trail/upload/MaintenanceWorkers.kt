package dev.kevin.trail.upload

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.kevin.trail.data.repo.SettingsRepository
import dev.kevin.trail.data.repo.TrackingRepository
import dev.kevin.trail.location.TrackingService

@HiltWorker
class RetentionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val repository: TrackingRepository,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        repository.enforceRetention(settings.current().retention, System.currentTimeMillis() / 1_000L)
        return Result.success()
    }
}

@HiltWorker
class ResumeTrackingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val current = settings.current()
        val now = System.currentTimeMillis()
        val until = current.pausedUntilEpochMs
        if (until == Long.MAX_VALUE) return Result.success()
        if (until != null && until > now + GRACE_MS) return Result.success()
        settings.update { it.copy(pausedUntilEpochMs = null, trackingEnabled = true) }
        TrackingService.start(applicationContext)
        return Result.success()
    }

    private companion object {
        const val GRACE_MS = 60_000L
    }
}
