package dev.leonid.unlock144bs.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppPreferencesPolicyTest {
    @Test
    fun `all supported polling intervals are preserved`() {
        listOf(2_500L, 5_000L, 10_000L, 15_000L, 30_000L).forEach { interval ->
            assertEquals(interval, normalizeForegroundPollInterval(interval))
        }
    }

    @Test
    fun `unsupported polling interval falls back to ten seconds`() {
        assertEquals(10_000L, normalizeForegroundPollInterval(1_000L))
    }
}
