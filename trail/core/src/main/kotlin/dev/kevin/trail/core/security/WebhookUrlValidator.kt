package dev.kevin.trail.core.security

import java.net.URI

sealed interface UrlValidation {
    data class Ok(val url: String) : UrlValidation

    data class Invalid(val reason: String) : UrlValidation
}

object WebhookUrlValidator {
    fun validate(raw: String?): UrlValidation {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return UrlValidation.Invalid("Webhook URL is empty")
        val uri = try {
            URI(trimmed)
        } catch (_: IllegalArgumentException) {
            return UrlValidation.Invalid("Webhook URL is not a valid address")
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return UrlValidation.Invalid("Webhook URL must use HTTPS")
        }
        if (uri.host.isNullOrBlank()) {
            return UrlValidation.Invalid("Webhook URL is missing a host")
        }
        if (!uri.userInfo.isNullOrBlank()) {
            return UrlValidation.Invalid("Put the token in the Authorization header, not in the URL")
        }
        return UrlValidation.Ok(trimmed)
    }

    fun normalizePin(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val looksLikePin = trimmed.startsWith("sha256/") && trimmed.length >= PIN_PREFIX.length + MIN_PIN_BODY
        if (!looksLikePin) return null
        return trimmed
    }

    fun pinIsPresentButInvalid(raw: String?): Boolean {
        val trimmed = raw?.trim().orEmpty()
        return trimmed.isNotEmpty() && normalizePin(trimmed) == null
    }

    private const val PIN_PREFIX = "sha256/"
    private const val MIN_PIN_BODY = 16
}
