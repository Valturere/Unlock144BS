package dev.leonid.unlock144bs.autofix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoFixPolicyTest {
    private val policy = AutoFixPolicy("com.supercell.brawlstars")

    @Test
    fun `applies immediately when game enters foreground`() {
        assertEquals(
            ApplyReason.ENTERED_GAME,
            policy.evaluate("com.supercell.brawlstars", 1_000, true, 45_000),
        )
    }

    @Test
    fun `does not apply while another package is foreground`() {
        assertNull(policy.evaluate("com.example.other", 1_000, true, 45_000))
    }

    @Test
    fun `repeats only after configured interval`() {
        policy.evaluate("com.supercell.brawlstars", 1_000, true, 45_000)
        assertNull(policy.evaluate("com.supercell.brawlstars", 45_999, true, 45_000))
        assertEquals(
            ApplyReason.PERIODIC_REPEAT,
            policy.evaluate("com.supercell.brawlstars", 46_000, true, 45_000),
        )
    }

    @Test
    fun `off mode never repeats`() {
        policy.evaluate("com.supercell.brawlstars", 1_000, false, 45_000)
        assertNull(policy.evaluate("com.supercell.brawlstars", 100_000, false, 45_000))
    }

    @Test
    fun `leaving and reentering applies again`() {
        policy.evaluate("com.supercell.brawlstars", 1_000, true, 45_000)
        policy.evaluate("com.example.other", 2_000, true, 45_000)
        assertEquals(
            ApplyReason.ENTERED_GAME,
            policy.evaluate("com.supercell.brawlstars", 3_000, true, 45_000),
        )
    }

    @Test
    fun `reset makes foreground game apply immediately again`() {
        policy.evaluate("com.supercell.brawlstars", 1_000, true, 45_000)
        policy.reset()

        assertEquals(
            ApplyReason.ENTERED_GAME,
            policy.evaluate("com.supercell.brawlstars", 2_000, true, 45_000),
        )
    }
}
