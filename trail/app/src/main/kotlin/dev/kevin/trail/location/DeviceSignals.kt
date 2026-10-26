package dev.kevin.trail.location

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.BatteryManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kevin.trail.core.model.ActivityMode
import dev.kevin.trail.data.db.PointEntity
import javax.inject.Inject
import javax.inject.Singleton

interface DeviceStatus {
    fun batteryPercent(): Int?
    fun charging(): Boolean
    fun connected(): Boolean
    fun metered(): Boolean
}

@Singleton
class DeviceSignals @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceStatus {
    override fun batteryPercent(): Int? {
        val manager = context.getSystemService(BatteryManager::class.java) ?: return null
        val percent = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return percent.takeIf { it in 0..100 }
    }

    override fun charging(): Boolean {
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        val status = sticky.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    override fun connected(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun metered(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        return manager.isActiveNetworkMetered
    }

    fun connectionCode(): String {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return "o"
        val network = manager.activeNetwork ?: return "o"
        val capabilities = manager.getNetworkCapabilities(network) ?: return "o"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "w"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "w"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "m"
            else -> "o"
        }
    }
}

fun Location.toPoint(activity: ActivityMode?, batteryPercent: Int?, connection: String?): PointEntity {
    val timeMillis = if (time > 0) time else System.currentTimeMillis()
    return PointEntity(
        tst = timeMillis / 1_000L,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy.toInt().coerceAtLeast(0) else 0,
        altitudeMeters = if (hasAltitude()) altitude else null,
        speedMps = if (hasSpeed()) speed.toDouble() else null,
        bearingDegrees = if (hasBearing()) bearing.toDouble() else null,
        batteryPercent = batteryPercent,
        connection = connection,
        activity = activity?.name,
    )
}
