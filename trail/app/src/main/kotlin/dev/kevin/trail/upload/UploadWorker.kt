package dev.kevin.trail.upload

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.kevin.trail.BuildConfig
import dev.kevin.trail.core.upload.UploadConfig
import dev.kevin.trail.core.upload.UploadCoordinator
import dev.kevin.trail.core.upload.UploadOutcome
import dev.kevin.trail.data.repo.SettingsRepository
import dev.kevin.trail.data.repo.TrackingRepository
import dev.kevin.trail.location.DeviceStatus

@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val coordinator: UploadCoordinator,
    private val settingsRepository: SettingsRepository,
    private val repository: TrackingRepository,
    private val signals: DeviceStatus,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val settings = settingsRepository.current()
        val force = inputData.getBoolean(KEY_FORCE, false)
        val outcome = coordinator.upload(
            UploadConfig(
                webhookUrl = settings.webhookUrl,
                authorizationHeader = settings.authorizationHeader,
                deviceId = settings.deviceId,
                appVersion = BuildConfig.VERSION_NAME,
                trackerId = settings.trackerId,
                certificatePinSha256 = settings.certificatePin,
                zones = repository.zones(),
                wifiOnly = settings.wifiOnly,
                deferOnLowBattery = settings.deferOnLowBattery,
                batteryPercent = signals.batteryPercent(),
                charging = signals.charging(),
                networkConnected = signals.connected(),
                networkMetered = signals.metered(),
                force = force,
            ),
        )
        when (outcome) {
            is UploadOutcome.Success -> repository.recordSuccess(System.currentTimeMillis())
            is UploadOutcome.Retry -> repository.recordError(outcome.reason)
            is UploadOutcome.PermanentFailure -> repository.recordError(outcome.reason)
            UploadOutcome.NothingToSend, UploadOutcome.NotConfigured -> repository.clearError()
        }
        return resultFor(outcome)
    }

    companion object {
        const val KEY_FORCE = "force"

        fun resultFor(outcome: UploadOutcome): Result = when (outcome) {
            is UploadOutcome.Success, UploadOutcome.NothingToSend, UploadOutcome.NotConfigured -> Result.success()
            is UploadOutcome.Retry -> Result.retry()
            is UploadOutcome.PermanentFailure -> Result.failure()
        }
    }
}
