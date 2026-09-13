package dev.leonid.unlock144bs.autofix

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupPolicyTest {
    @Test
    fun `boot starts when enabled for boot and setup is complete`() {
        assertTrue(shouldStart(StartupTrigger.BOOT_COMPLETED))
    }

    @Test
    fun `boot does not start when start after boot is disabled`() {
        assertFalse(
            shouldStart(
                trigger = StartupTrigger.BOOT_COMPLETED,
                startAfterBoot = false,
            ),
        )
    }

    @Test
    fun `package replacement ignores start after boot setting`() {
        assertTrue(
            shouldStart(
                trigger = StartupTrigger.MY_PACKAGE_REPLACED,
                startAfterBoot = false,
            ),
        )
    }

    @Test
    fun `package replacement does not start when Auto Fix is disabled`() {
        assertFalse(
            shouldStart(
                trigger = StartupTrigger.MY_PACKAGE_REPLACED,
                autoFixEnabled = false,
            ),
        )
    }

    @Test
    fun `package replacement does not start without Usage Access`() {
        assertFalse(
            shouldStart(
                trigger = StartupTrigger.MY_PACKAGE_REPLACED,
                usageAccessGranted = false,
            ),
        )
    }

    @Test
    fun `package replacement does not start without battery exemption`() {
        assertFalse(
            shouldStart(
                trigger = StartupTrigger.MY_PACKAGE_REPLACED,
                batteryOptimizationExempt = false,
            ),
        )
    }

    private fun shouldStart(
        trigger: StartupTrigger,
        autoFixEnabled: Boolean = true,
        startAfterBoot: Boolean = true,
        usageAccessGranted: Boolean = true,
        batteryOptimizationExempt: Boolean = true,
    ): Boolean = shouldStartAutoFix(
        trigger = trigger,
        autoFixEnabled = autoFixEnabled,
        startAfterBoot = startAfterBoot,
        usageAccessGranted = usageAccessGranted,
        batteryOptimizationExempt = batteryOptimizationExempt,
    )
}
