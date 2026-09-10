package dev.leonid.unlock144bs.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPolicyTest {
    @Test
    fun `setup stays visible until Auto Fix and required access are ready`() {
        assertTrue(shouldShowSetupSteps(false, usageGranted = false, batteryExempt = false))
        assertTrue(shouldShowSetupSteps(true, usageGranted = false, batteryExempt = true))
        assertTrue(shouldShowSetupSteps(true, usageGranted = true, batteryExempt = false))
    }

    @Test
    fun `setup is hidden after successful configuration`() {
        assertFalse(shouldShowSetupSteps(true, usageGranted = true, batteryExempt = true))
    }
}
