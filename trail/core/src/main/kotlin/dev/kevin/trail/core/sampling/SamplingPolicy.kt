package dev.kevin.trail.core.sampling

enum class SamplingMode {
    MOVING,
    STILL,
}

data class SamplingSpec(
    val intervalMillis: Long,
    val minIntervalMillis: Long,
    val highAccuracy: Boolean,
)

/**
 * Moving fixes are requested about every 20 seconds (inside the 15–30 second
 * window). Still fixes back off to 8 minutes (inside the 5–10 minute window).
 */
object SamplingPolicy {
    const val MOVING_INTERVAL_MILLIS = 20_000L
    const val MOVING_MIN_INTERVAL_MILLIS = 15_000L
    const val STILL_INTERVAL_MILLIS = 8 * 60_000L
    const val STILL_MIN_INTERVAL_MILLIS = 5 * 60_000L

    fun spec(mode: SamplingMode): SamplingSpec = when (mode) {
        SamplingMode.MOVING -> SamplingSpec(
            intervalMillis = MOVING_INTERVAL_MILLIS,
            minIntervalMillis = MOVING_MIN_INTERVAL_MILLIS,
            highAccuracy = true,
        )
        SamplingMode.STILL -> SamplingSpec(
            intervalMillis = STILL_INTERVAL_MILLIS,
            minIntervalMillis = STILL_MIN_INTERVAL_MILLIS,
            highAccuracy = false,
        )
    }
}
