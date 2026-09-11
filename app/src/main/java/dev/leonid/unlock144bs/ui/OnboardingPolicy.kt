package dev.leonid.unlock144bs.ui

import androidx.core.content.UnusedAppRestrictionsConstants

internal data class OnboardingVisibility(
    val showSetup: Boolean,
    val showRequiredSteps: Boolean,
    val showBackgroundStep: Boolean,
)

internal fun onboardingVisibility(
    autoFixEnabled: Boolean,
    usageGranted: Boolean,
    batteryExempt: Boolean,
    unusedAppRestrictionsStatus: Int,
): OnboardingVisibility {
    val requiredSetupComplete = autoFixEnabled && usageGranted && batteryExempt
    val backgroundStepComplete = unusedAppRestrictionsStatus ==
        UnusedAppRestrictionsConstants.DISABLED ||
        unusedAppRestrictionsStatus == UnusedAppRestrictionsConstants.FEATURE_NOT_AVAILABLE
    return OnboardingVisibility(
        showSetup = !requiredSetupComplete || !backgroundStepComplete,
        showRequiredSteps = !requiredSetupComplete,
        showBackgroundStep = !backgroundStepComplete,
    )
}
