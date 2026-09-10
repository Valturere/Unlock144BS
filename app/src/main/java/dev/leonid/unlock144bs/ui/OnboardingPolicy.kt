package dev.leonid.unlock144bs.ui

internal fun shouldShowSetupSteps(
    autoFixEnabled: Boolean,
    usageGranted: Boolean,
    batteryExempt: Boolean,
): Boolean = !autoFixEnabled || !usageGranted || !batteryExempt
