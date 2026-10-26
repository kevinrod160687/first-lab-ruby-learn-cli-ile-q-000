package dev.kevin.trail.upload

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kevin.trail.core.retention.RetentionPolicy
import dev.kevin.trail.data.repo.TrailSettings
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    fun apply(settings: TrailSettings) {
        val minutes = RetentionPolicy.clampUploadInterval(settings.uploadIntervalMinutes).toLong()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .apply {
                if (settings.deferOnLowBattery) setRequiresBatteryNotLow(true)
            }
            .build()
        val upload = PeriodicWorkRequestBuilder<UploadWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RetryPolicyMinutes.INITIAL_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(UPLOAD, ExistingPeriodicWorkPolicy.UPDATE, upload)
        val retention = PeriodicWorkRequestBuilder<RetentionWorker>(1, TimeUnit.DAYS).build()
        workManager.enqueueUniquePeriodicWork(RETENTION, ExistingPeriodicWorkPolicy.KEEP, retention)
    }

    fun uploadNow() {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(workDataOf(UploadWorker.KEY_FORCE to true))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork(UPLOAD_NOW, ExistingWorkPolicy.REPLACE, request)
    }

    fun scheduleResume(untilEpochMs: Long) {
        val delay = (untilEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ResumeTrackingWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(RESUME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelResume() {
        workManager.cancelUniqueWork(RESUME)
    }

    private object RetryPolicyMinutes {
        const val INITIAL_SECONDS = 30L
    }

    companion object {
        const val UPLOAD = "trail-upload"
        const val UPLOAD_NOW = "trail-upload-now"
        const val RETENTION = "trail-retention"
        const val RESUME = "trail-resume"
    }
}
