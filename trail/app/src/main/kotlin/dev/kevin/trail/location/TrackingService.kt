package dev.kevin.trail.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import dev.kevin.trail.R
import dev.kevin.trail.core.sampling.SamplingMode
import dev.kevin.trail.core.sampling.SamplingPolicy
import dev.kevin.trail.data.repo.SettingsRepository
import dev.kevin.trail.data.repo.TrackingRepository
import dev.kevin.trail.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TrackingService : Service() {
    @Inject lateinit var repository: TrackingRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var sampling: SamplingModeStore
    @Inject lateinit var signals: DeviceSignals

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val activityClient by lazy { ActivityRecognition.getClient(this) }
    private var samplingJob: Job? = null
    private var updatesStarted = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val activity = sampling.currentActivity()
            val battery = signals.batteryPercent()
            val connection = signals.connectionCode()
            scope.launch(Dispatchers.IO) {
                repository.record(location.toPoint(activity, battery, connection))
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification(sampling.currentMode())
        if (!promoteToForeground(notification)) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_PAUSE -> {
                scope.launch(Dispatchers.IO) {
                    settings.update { it.copy(pausedUntilEpochMs = Long.MAX_VALUE) }
                    launch(Dispatchers.Main) {
                        stopTracking()
                        stopSelf()
                    }
                }
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                stopTracking()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        val current = settings.current()
        if (!current.trackingEnabled || current.isPaused(System.currentTimeMillis()) || !hasLocationPermission()) {
            stopTracking()
            stopSelf()
            return START_NOT_STICKY
        }
        registerActivityTransitions()
        samplingJob?.cancel()
        samplingJob = scope.launch {
            sampling.mode.collect { mode ->
                requestUpdates(mode)
                refreshNotification(mode)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopTracking()
        scope.cancel()
        super.onDestroy()
    }

    private fun requestUpdates(mode: SamplingMode) {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) return
        val spec = SamplingPolicy.spec(mode)
        val priority = if (spec.highAccuracy) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        val request = LocationRequest.Builder(priority, spec.intervalMillis)
            .setMinUpdateIntervalMillis(spec.minIntervalMillis)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            fused.removeLocationUpdates(locationCallback)
            fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            updatesStarted = true
        } catch (_: SecurityException) {
            updatesStarted = false
        }
    }

    private fun registerActivityTransitions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val transitions = listOf(
            DetectedActivity.STILL,
            DetectedActivity.WALKING,
            DetectedActivity.ON_FOOT,
            DetectedActivity.RUNNING,
            DetectedActivity.ON_BICYCLE,
            DetectedActivity.IN_VEHICLE,
        ).flatMap { type ->
            listOf(ActivityTransition.ACTIVITY_TRANSITION_ENTER, ActivityTransition.ACTIVITY_TRANSITION_EXIT).map { transition ->
                ActivityTransition.Builder()
                    .setActivityType(type)
                    .setActivityTransition(transition)
                    .build()
            }
        }
        val pending = PendingIntent.getBroadcast(
            this,
            REQUEST_ACTIVITY,
            Intent(this, ActivityTransitionReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        activityClient.requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), pending)
            .addOnFailureListener {
                // Play Services can be missing or out of date. Sampling stays on the last known mode.
            }
    }

    private fun stopTracking() {
        samplingJob?.cancel()
        if (updatesStarted) {
            fused.removeLocationUpdates(locationCallback)
            updatesStarted = false
        }
        val pending = PendingIntent.getBroadcast(
            this,
            REQUEST_ACTIVITY,
            Intent(this, ActivityTransitionReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            try {
                activityClient.removeActivityTransitionUpdates(pending)
            } catch (_: SecurityException) {
                // Permission was revoked between the check and the call.
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun promoteToForeground(notification: Notification): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun refreshNotification(mode: SamplingMode) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(mode))
    }

    private fun buildNotification(mode: SamplingMode): Notification {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val pause = PendingIntent.getService(
            this,
            REQUEST_PAUSE,
            Intent(this, TrackingService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (mode == SamplingMode.STILL) {
            getString(R.string.tracking_notification_still)
        } else {
            getString(R.string.tracking_notification_moving)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(R.drawable.ic_notification, getString(R.string.tracking_notification_pause), pause)
            .build()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.tracking_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.tracking_channel_description)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val CHANNEL_ID = "trail_tracking"
        const val NOTIFICATION_ID = 42
        const val ACTION_PAUSE = "dev.kevin.trail.PAUSE"
        const val ACTION_STOP = "dev.kevin.trail.STOP"
        private const val REQUEST_OPEN = 1
        private const val REQUEST_PAUSE = 2
        private const val REQUEST_ACTIVITY = 3

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java))
        }
    }
}
