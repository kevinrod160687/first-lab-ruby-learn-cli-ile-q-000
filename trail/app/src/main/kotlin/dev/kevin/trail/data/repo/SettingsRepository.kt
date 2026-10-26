package dev.kevin.trail.data.repo

import dev.kevin.trail.core.model.Retention
import dev.kevin.trail.core.retention.RetentionPolicy
import dev.kevin.trail.data.security.SecretStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class TrailSettings(
    val webhookUrl: String,
    val authorizationHeader: String,
    val uploadIntervalMinutes: Int,
    val wifiOnly: Boolean,
    val deferOnLowBattery: Boolean,
    val retention: Retention,
    val pausedUntilEpochMs: Long?,
    val trackingEnabled: Boolean,
    val trackerId: String,
    val deviceId: String,
    val certificatePin: String,
) {
    fun isPaused(nowEpochMs: Long): Boolean = when (pausedUntilEpochMs) {
        null -> false
        Long.MAX_VALUE -> true
        else -> nowEpochMs < pausedUntilEpochMs
    }

    override fun toString(): String =
        "TrailSettings(webhook=[redacted], authorization=[redacted], pin=[redacted], " +
            "interval=$uploadIntervalMinutes, wifiOnly=$wifiOnly, retention=$retention, " +
            "tracking=$trackingEnabled, trackerId=$trackerId)"
}

@Singleton
class SettingsRepository @Inject constructor(
    private val secrets: SecretStore,
) {
    private val state = MutableStateFlow(read())
    fun observe(): StateFlow<TrailSettings> = state.asStateFlow()

    fun current(): TrailSettings = state.value

    fun update(transform: (TrailSettings) -> TrailSettings) {
        val next = transform(state.value)
        write(next)
        state.value = next
    }

    private fun read(): TrailSettings {
        val deviceId = secrets.getString(KEY_DEVICE) ?: UUID.randomUUID().toString().also {
            secrets.putString(KEY_DEVICE, it)
        }
        val pausedRaw = secrets.getLong(KEY_PAUSED, PAUSED_NONE)
        return TrailSettings(
            webhookUrl = secrets.getString(KEY_URL).orEmpty(),
            authorizationHeader = secrets.getString(KEY_AUTH).orEmpty(),
            uploadIntervalMinutes = RetentionPolicy.clampUploadInterval(
                secrets.getInt(KEY_INTERVAL, RetentionPolicy.DEFAULT_UPLOAD_INTERVAL_MINUTES),
            ),
            wifiOnly = secrets.getBoolean(KEY_WIFI, false),
            deferOnLowBattery = secrets.getBoolean(KEY_BATTERY, true),
            retention = retentionOf(secrets.getString(KEY_RETENTION)),
            pausedUntilEpochMs = when (pausedRaw) {
                PAUSED_NONE -> null
                PAUSED_INDEFINITE -> Long.MAX_VALUE
                else -> pausedRaw
            },
            trackingEnabled = secrets.getBoolean(KEY_TRACKING, true),
            trackerId = RetentionPolicy.normalizeTrackerId(secrets.getString(KEY_TID)),
            deviceId = deviceId,
            certificatePin = secrets.getString(KEY_PIN).orEmpty(),
        )
    }

    private fun write(settings: TrailSettings) {
        secrets.putString(KEY_URL, settings.webhookUrl.trim())
        secrets.putString(KEY_AUTH, settings.authorizationHeader)
        secrets.putInt(KEY_INTERVAL, RetentionPolicy.clampUploadInterval(settings.uploadIntervalMinutes))
        secrets.putBoolean(KEY_WIFI, settings.wifiOnly)
        secrets.putBoolean(KEY_BATTERY, settings.deferOnLowBattery)
        secrets.putString(KEY_RETENTION, settings.retention.name)
        secrets.putLong(
            KEY_PAUSED,
            when (settings.pausedUntilEpochMs) {
                null -> PAUSED_NONE
                Long.MAX_VALUE -> PAUSED_INDEFINITE
                else -> settings.pausedUntilEpochMs
            },
        )
        secrets.putBoolean(KEY_TRACKING, settings.trackingEnabled)
        secrets.putString(KEY_TID, RetentionPolicy.normalizeTrackerId(settings.trackerId))
        secrets.putString(KEY_PIN, settings.certificatePin.trim())
        secrets.putString(KEY_DEVICE, settings.deviceId)
    }

    private fun retentionOf(raw: String?): Retention =
        raw?.let { runCatching { Retention.valueOf(it) }.getOrNull() } ?: Retention.DAYS_90

    private companion object {
        const val KEY_URL = "webhook_url"
        const val KEY_AUTH = "auth_header"
        const val KEY_INTERVAL = "upload_interval_min"
        const val KEY_WIFI = "wifi_only"
        const val KEY_BATTERY = "defer_low_battery"
        const val KEY_RETENTION = "retention"
        const val KEY_PAUSED = "paused_until"
        const val KEY_TRACKING = "tracking_enabled"
        const val KEY_TID = "tracker_id"
        const val KEY_DEVICE = "device_id"
        const val KEY_PIN = "cert_pin"
        const val PAUSED_NONE = 0L
        const val PAUSED_INDEFINITE = -1L
    }
}
