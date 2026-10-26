package dev.kevin.trail.core.batch

import org.junit.Assert.assertEquals
import org.junit.Test

class RetryPolicyTest {
    private val policy = RetryPolicy()

    @Test
    fun backoffDoublesAndCapsAtOneHour() {
        assertEquals(30_000L, policy.delayMillis(1))
        assertEquals(60_000L, policy.delayMillis(2))
        assertEquals(120_000L, policy.delayMillis(3))
        assertEquals(240_000L, policy.delayMillis(4))
        assertEquals(60 * 60 * 1000L, policy.delayMillis(20))
    }

    @Test
    fun successIsOnly2xx() {
        assertEquals(HttpDisposition.SUCCESS, policy.disposition(200))
        assertEquals(HttpDisposition.SUCCESS, policy.disposition(204))
        assertEquals(HttpDisposition.RETRY, policy.disposition(500))
        assertEquals(HttpDisposition.RETRY, policy.disposition(503))
        assertEquals(HttpDisposition.RETRY, policy.disposition(408))
        assertEquals(HttpDisposition.RETRY, policy.disposition(429))
        assertEquals(HttpDisposition.PERMANENT, policy.disposition(401))
        assertEquals(HttpDisposition.PERMANENT, policy.disposition(403))
        assertEquals(HttpDisposition.PERMANENT, policy.disposition(404))
        assertEquals(HttpDisposition.RETRY, policy.disposition(0))
    }
}
