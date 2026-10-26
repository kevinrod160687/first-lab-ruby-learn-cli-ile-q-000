package dev.kevin.trail

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration as OsmConfig
import javax.inject.Inject

@HiltAndroidApp
class TrailApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var scheduler: dev.kevin.trail.upload.UploadScheduler
    @Inject lateinit var settings: dev.kevin.trail.data.repo.SettingsRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createTrackingChannel()
        val osm = OsmConfig.getInstance()
        osm.userAgentValue = "Trail/${BuildConfig.VERSION_NAME} (personal diary tracker)"
        osm.load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        scheduler.apply(settings.current())
    }

    private fun createTrackingChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            dev.kevin.trail.location.TrackingService.CHANNEL_ID,
            getString(R.string.tracking_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.tracking_channel_description)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }
}
