package dev.kevin.trail.location

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kevin.trail.data.repo.SettingsRepository
import dev.kevin.trail.upload.UploadScheduler
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackingController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val scheduler: UploadScheduler,
) {
    fun startIfAllowed() {
        val current = settings.current()
        if (!current.trackingEnabled || current.isPaused(System.currentTimeMillis())) return
        TrackingService.start(context)
    }

    fun pause(hours: Int?) {
        val until = if (hours == null) {
            Long.MAX_VALUE
        } else {
            System.currentTimeMillis() + hours * MILLIS_PER_HOUR
        }
        settings.update { it.copy(pausedUntilEpochMs = until) }
        if (hours == null) scheduler.cancelResume() else scheduler.scheduleResume(until)
        context.stopService(Intent(context, TrackingService::class.java))
    }

    fun resume() {
        settings.update { it.copy(pausedUntilEpochMs = null, trackingEnabled = true) }
        scheduler.cancelResume()
        TrackingService.start(context)
    }

    fun uploadNow() {
        scheduler.uploadNow()
    }

    private companion object {
        const val MILLIS_PER_HOUR = 3_600_000L
    }
}
