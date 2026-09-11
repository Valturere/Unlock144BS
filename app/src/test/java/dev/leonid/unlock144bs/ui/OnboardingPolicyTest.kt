package dev.leonid.unlock144bs.ui

import androidx.core.content.UnusedAppRestrictionsConstants.API_30
import androidx.core.content.UnusedAppRestrictionsConstants.API_30_BACKPORT
import androidx.core.content.UnusedAppRestrictionsConstants.API_31
import androidx.core.content.UnusedAppRestrictionsConstants.DISABLED
import androidx.core.content.UnusedAppRestrictionsConstants.ERROR
import androidx.core.content.UnusedAppRestrictionsConstants.FEATURE_NOT_AVAILABLE
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPolicyTest {
    @Test
    fun `required steps stay visible until Auto Fix and required access are ready`() {
        val visibility = onboardingVisibility(
            autoFixEnabled = true,
            usageGranted = false,
            batteryExempt = true,
            unusedAppRestrictionsStatus = DISABLED,
        )

        assertTrue(visibility.showSetup)
        assertTrue(visibility.showRequiredSteps)
        assertFalse(visibility.showBackgroundStep)
    }

    @Test
    fun `enabled unused app restrictions leave only background step visible`() {
        listOf(API_30_BACKPORT, API_30, API_31).forEach { status ->
            val visibility = completedRequiredSetup(status)

            assertTrue(visibility.showSetup)
            assertFalse(visibility.showRequiredSteps)
            assertTrue(visibility.showBackgroundStep)
        }
    }

    @Test
    fun `unknown status does not claim background step is complete`() {
        val visibility = completedRequiredSetup(ERROR)

        assertTrue(visibility.showSetup)
        assertFalse(visibility.showRequiredSteps)
        assertTrue(visibility.showBackgroundStep)
    }

    @Test
    fun `setup is hidden when unused app restrictions are disabled or unavailable`() {
        listOf(DISABLED, FEATURE_NOT_AVAILABLE).forEach { status ->
            val visibility = completedRequiredSetup(status)

            assertFalse(visibility.showSetup)
            assertFalse(visibility.showRequiredSteps)
            assertFalse(visibility.showBackgroundStep)
        }
    }

    private fun completedRequiredSetup(unusedAppRestrictionsStatus: Int) =
        onboardingVisibility(
            autoFixEnabled = true,
            usageGranted = true,
            batteryExempt = true,
            unusedAppRestrictionsStatus = unusedAppRestrictionsStatus,
        )
}
