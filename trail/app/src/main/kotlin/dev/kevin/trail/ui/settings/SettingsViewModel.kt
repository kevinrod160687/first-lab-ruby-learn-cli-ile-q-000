package dev.kevin.trail.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kevin.trail.core.export.TrailExporter
import dev.kevin.trail.core.model.PrivateZone
import dev.kevin.trail.core.model.Retention
import dev.kevin.trail.core.model.ZoneAction
import dev.kevin.trail.core.retention.RetentionPolicy
import dev.kevin.trail.core.security.UrlValidation
import dev.kevin.trail.core.security.WebhookUrlValidator
import dev.kevin.trail.data.repo.SettingsRepository
import dev.kevin.trail.data.repo.TrackingRepository
import dev.kevin.trail.upload.UploadScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val webhookUrl: String = "",
    val authorizationHeader: String = "",
    val certificatePin: String = "",
    val uploadIntervalMinutes: Int = RetentionPolicy.DEFAULT_UPLOAD_INTERVAL_MINUTES,
    val wifiOnly: Boolean = false,
    val deferOnLowBattery: Boolean = true,
    val retention: Retention = Retention.DAYS_90,
    val trackerId: String = RetentionPolicy.DEFAULT_TRACKER_ID,
    val zones: List<PrivateZone> = emptyList(),
    val latestLatitude: Double? = null,
    val latestLongitude: Double? = null,
    val webhookError: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val repository: TrackingRepository,
    private val scheduler: UploadScheduler,
) : ViewModel() {
    val state: StateFlow<SettingsUiState> = combine(
        settingsRepository.observe(),
        repository.observeZones(),
        repository.observeLatest(),
    ) { settings, zones, latest ->
        SettingsUiState(
            webhookUrl = settings.webhookUrl,
            authorizationHeader = settings.authorizationHeader,
            certificatePin = settings.certificatePin,
            uploadIntervalMinutes = settings.uploadIntervalMinutes,
            wifiOnly = settings.wifiOnly,
            deferOnLowBattery = settings.deferOnLowBattery,
            retention = settings.retention,
            trackerId = settings.trackerId,
            zones = zones,
            latestLatitude = latest?.latitude,
            latestLongitude = latest?.longitude,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun saveWebhook(url: String, authorization: String, pin: String): Boolean {
        if (url.isNotBlank()) {
            when (val validation = WebhookUrlValidator.validate(url)) {
                is UrlValidation.Invalid -> return false
                is UrlValidation.Ok -> validation.url
            }
        }
        if (WebhookUrlValidator.pinIsPresentButInvalid(pin)) return false
        settingsRepository.update {
            it.copy(
                webhookUrl = url.trim(),
                authorizationHeader = authorization,
                certificatePin = pin.trim(),
            )
        }
        scheduler.apply(settingsRepository.current())
        return true
    }

    fun forgetWebhook() {
        settingsRepository.update { it.copy(webhookUrl = "", authorizationHeader = "", certificatePin = "") }
    }

    fun setInterval(minutes: Int) {
        settingsRepository.update { it.copy(uploadIntervalMinutes = RetentionPolicy.clampUploadInterval(minutes)) }
        scheduler.apply(settingsRepository.current())
    }

    fun setWifiOnly(enabled: Boolean) {
        settingsRepository.update { it.copy(wifiOnly = enabled) }
        scheduler.apply(settingsRepository.current())
    }

    fun setDeferBattery(enabled: Boolean) {
        settingsRepository.update { it.copy(deferOnLowBattery = enabled) }
        scheduler.apply(settingsRepository.current())
    }

    fun setRetention(retention: Retention) {
        settingsRepository.update { it.copy(retention = retention) }
    }

    fun setTrackerId(value: String) {
        settingsRepository.update { it.copy(trackerId = RetentionPolicy.normalizeTrackerId(value)) }
    }

    fun addZone(name: String, latitude: Double, longitude: Double, radiusMeters: Double, action: ZoneAction) {
        viewModelScope.launch {
            repository.addZone(
                PrivateZone(
                    name = name.ifBlank { "Zone" },
                    latitude = latitude,
                    longitude = longitude,
                    radiusMeters = radiusMeters.coerceIn(25.0, 5_000.0),
                    action = action,
                ),
            )
        }
    }

    fun deleteZone(id: Long) {
        viewModelScope.launch { repository.deleteZone(id) }
    }

    fun wipe() {
        viewModelScope.launch { repository.wipe() }
    }

    suspend fun exportGpx(): String {
        val diary = repository.exportAll()
        return TrailExporter.gpx(diary.points, diary.visits)
    }

    suspend fun exportJson(): String {
        val diary = repository.exportAll()
        return TrailExporter.json(diary.points, diary.visits, diary.trips, System.currentTimeMillis())
    }
}
