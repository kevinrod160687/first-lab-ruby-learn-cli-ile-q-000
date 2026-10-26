package dev.kevin.trail.core.retention

import dev.kevin.trail.core.model.Retention

object RetentionPolicy {
    const val MIN_UPLOAD_INTERVAL_MINUTES = 15
    const val MAX_UPLOAD_INTERVAL_MINUTES = 120
    const val DEFAULT_UPLOAD_INTERVAL_MINUTES = 60
    const val DEFAULT_TRACKER_ID = "KV"
    const val LOW_BATTERY_PERCENT = 15

    fun clampUploadInterval(minutes: Int): Int =
        minutes.coerceIn(MIN_UPLOAD_INTERVAL_MINUTES, MAX_UPLOAD_INTERVAL_MINUTES)

    fun normalizeTrackerId(raw: String?): String {
        val cleaned = raw.orEmpty().trim().uppercase().filter { it.isLetterOrDigit() }
        return if (cleaned.length >= 2) cleaned.take(2) else DEFAULT_TRACKER_ID
    }

    /** Seconds-since-epoch cutoff, or null when history is kept forever. */
    fun cutoffEpochSeconds(retention: Retention, nowEpochSeconds: Long): Long? = when (retention) {
        Retention.DAYS_30 -> nowEpochSeconds - 30L * SECONDS_PER_DAY
        Retention.DAYS_90 -> nowEpochSeconds - 90L * SECONDS_PER_DAY
        Retention.DAYS_365 -> nowEpochSeconds - 365L * SECONDS_PER_DAY
        Retention.FOREVER -> null
    }

    private const val SECONDS_PER_DAY = 86_400L
}
