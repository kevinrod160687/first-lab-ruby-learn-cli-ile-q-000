package dev.kevin.trail.core.batch

import kotlin.math.pow

enum class HttpDisposition {
    SUCCESS,
    RETRY,
    PERMANENT,
}

/**
 * Exponential backoff for a failed upload. Attempt 1 waits [initialMillis],
 * then the delay doubles until [maxMillis]. Only 2xx is success. 408, 429,
 * and 5xx are worth retrying. Other 4xx responses need a settings change,
 * usually a new token, so they are not retried by the scheduler's backoff.
 */
class RetryPolicy(
    private val initialMillis: Long = DEFAULT_INITIAL_MILLIS,
    private val multiplier: Double = DEFAULT_MULTIPLIER,
    private val maxMillis: Long = DEFAULT_MAX_MILLIS,
) {
    fun delayMillis(attempt: Int): Long {
        val safeAttempt = attempt.coerceAtLeast(1)
        val raw = initialMillis.toDouble() * multiplier.pow(safeAttempt - 1)
        return raw.coerceAtMost(maxMillis.toDouble()).toLong()
    }

    fun disposition(httpCode: Int): HttpDisposition = when (httpCode) {
        in HTTP_SUCCESS_START..HTTP_SUCCESS_END -> HttpDisposition.SUCCESS
        // 0 means the request never got an HTTP status (timeout, DNS, TLS).
        HTTP_NO_RESPONSE, HTTP_TIMEOUT, HTTP_TOO_MANY -> HttpDisposition.RETRY
        in HTTP_SERVER_START..HTTP_SERVER_END -> HttpDisposition.RETRY
        else -> HttpDisposition.PERMANENT
    }

    companion object {
        const val DEFAULT_INITIAL_MILLIS = 30_000L
        const val DEFAULT_MULTIPLIER = 2.0
        const val DEFAULT_MAX_MILLIS = 60 * 60 * 1000L
        private const val HTTP_NO_RESPONSE = 0
        private const val HTTP_SUCCESS_START = 200
        private const val HTTP_SUCCESS_END = 299
        private const val HTTP_TIMEOUT = 408
        private const val HTTP_TOO_MANY = 429
        private const val HTTP_SERVER_START = 500
        private const val HTTP_SERVER_END = 599
    }
}
