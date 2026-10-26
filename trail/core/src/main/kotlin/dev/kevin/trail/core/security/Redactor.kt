package dev.kevin.trail.core.security

object Redactor {
    const val REDACTED = "[redacted]"

    fun scrub(message: String, secrets: Collection<String>): String {
        var cleaned = message
        secrets
            .map { it.trim() }
            .filter { it.length >= MIN_SECRET_LENGTH }
            .distinct()
            .sortedByDescending { it.length }
            .forEach { secret ->
                cleaned = cleaned.replace(secret, REDACTED)
            }
        return cleaned
    }

    private const val MIN_SECRET_LENGTH = 4
}
