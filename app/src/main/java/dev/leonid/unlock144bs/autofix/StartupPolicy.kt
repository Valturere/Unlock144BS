package dev.leonid.unlock144bs.autofix

internal enum class StartupTrigger {
    BOOT_COMPLETED,
    MY_PACKAGE_REPLACED,
}

internal fun shouldStartAutoFix(
    trigger: StartupTrigger,
    autoFixEnabled: Boolean,
    startAfterBoot: Boolean,
    usageAccessGranted: Boolean,
    batteryOptimizationExempt: Boolean,
): Boolean =
    autoFixEnabled &&
        usageAccessGranted &&
        batteryOptimizationExempt &&
        (trigger != StartupTrigger.BOOT_COMPLETED || startAfterBoot)
