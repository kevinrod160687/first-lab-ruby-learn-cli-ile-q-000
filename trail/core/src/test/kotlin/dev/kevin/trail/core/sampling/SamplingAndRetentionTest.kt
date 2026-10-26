package dev.kevin.trail.core.sampling

import dev.kevin.trail.core.model.Retention
import dev.kevin.trail.core.retention.RetentionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SamplingAndRetentionTest {
    @Test
    fun movingIntervalIsBetweenFifteenAndThirtySeconds() {
        val spec = SamplingPolicy.spec(SamplingMode.MOVING)
        assertTrue(spec.intervalMillis in 15_000..30_000)
        assertTrue(spec.minIntervalMillis in 15_000..spec.intervalMillis)
        assertTrue(spec.highAccuracy)
    }

    @Test
    fun stillIntervalIsBetweenFiveAndTenMinutes() {
        val spec = SamplingPolicy.spec(SamplingMode.STILL)
        assertTrue(spec.intervalMillis in 5 * 60_000..10 * 60_000)
        assertTrue(spec.minIntervalMillis in 5 * 60_000..spec.intervalMillis)
        assertTrue(!spec.highAccuracy)
    }

    @Test
    fun uploadIntervalClampsToTheAllowedWindow() {
        assertEquals(15, RetentionPolicy.clampUploadInterval(1))
        assertEquals(120, RetentionPolicy.clampUploadInterval(500))
        assertEquals(60, RetentionPolicy.clampUploadInterval(60))
    }

    @Test
    fun trackerIdIsTwoCharacters() {
        assertEquals("KV", RetentionPolicy.normalizeTrackerId(null))
        assertEquals("AB", RetentionPolicy.normalizeTrackerId("ab-extra"))
    }

    @Test
    fun retentionCutoffUsesDaysOrForever() {
        val now = 1_700_000_000L
        assertEquals(now - 30 * 86_400L, RetentionPolicy.cutoffEpochSeconds(Retention.DAYS_30, now))
        assertEquals(now - 90 * 86_400L, RetentionPolicy.cutoffEpochSeconds(Retention.DAYS_90, now))
        assertEquals(now - 365 * 86_400L, RetentionPolicy.cutoffEpochSeconds(Retention.DAYS_365, now))
        assertNull(RetentionPolicy.cutoffEpochSeconds(Retention.FOREVER, now))
    }
}
