package dev.kevin.trail.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityTest {
    @Test
    fun acceptsHttpsAndRejectsEverythingElse() {
        assertTrue(WebhookUrlValidator.validate("https://diary.example.com/trail") is UrlValidation.Ok)
        assertEquals(
            "Webhook URL must use HTTPS",
            (WebhookUrlValidator.validate("http://diary.example.com/trail") as UrlValidation.Invalid).reason,
        )
        assertTrue(WebhookUrlValidator.validate("https://user:secret@diary.example.com/hook") is UrlValidation.Invalid)
        assertTrue(WebhookUrlValidator.validate("") is UrlValidation.Invalid)
        assertTrue(WebhookUrlValidator.validate("ftp://diary.example.com/hook") is UrlValidation.Invalid)
    }

    @Test
    fun pinMustBeASha256Pin() {
        assertEquals("sha256/" + "a".repeat(20), WebhookUrlValidator.normalizePin("sha256/" + "a".repeat(20)))
        assertEquals(null, WebhookUrlValidator.normalizePin("  "))
        assertTrue(WebhookUrlValidator.pinIsPresentButInvalid("not-a-pin"))
        assertFalse(WebhookUrlValidator.pinIsPresentButInvalid(null))
    }

    @Test
    fun redactorRemovesUrlAndToken() {
        val url = "https://diary.example.com/hooks/trail-private"
        val token = "Bearer super-secret-token"
        val scrubbed = Redactor.scrub("failed $url with $token", listOf(url, token))
        assertFalse(scrubbed.contains("diary.example.com"))
        assertFalse(scrubbed.contains("super-secret-token"))
        assertTrue(scrubbed.contains(Redactor.REDACTED))
    }
}
