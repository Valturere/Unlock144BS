package dev.leonid.unlock144bs.autofix

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.leonid.unlock144bs.data.AppPreferences
import dev.leonid.unlock144bs.data.DiagnosticStore
import dev.leonid.unlock144bs.system.BatteryOptimization
import dev.leonid.unlock144bs.system.UsageAccess

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val trigger = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> StartupTrigger.BOOT_COMPLETED
            Intent.ACTION_MY_PACKAGE_REPLACED -> StartupTrigger.MY_PACKAGE_REPLACED
            else -> return
        }

        val preferences = AppPreferences(context)
        val diagnostics = DiagnosticStore(context)
        val (receivedEvent, autoFixEvent) = when (trigger) {
            StartupTrigger.BOOT_COMPLETED -> "Boot completed" to "Boot Auto Fix"
            StartupTrigger.MY_PACKAGE_REPLACED -> "Package replaced" to "Update Auto Fix"
        }
        diagnostics.record(receivedEvent, "received")

        if (shouldStartAutoFix(
                trigger = trigger,
                autoFixEnabled = preferences.autoFixEnabled,
                startAfterBoot = preferences.startAfterBoot,
                usageAccessGranted = UsageAccess.isGranted(context),
                batteryOptimizationExempt = BatteryOptimization.isExempt(context),
            )
        ) {
            val started = AutoFixService.start(context)
            diagnostics.record(autoFixEvent, if (started) "start requested" else "start failed")
        } else {
            diagnostics.record(autoFixEvent, "skipped: disabled or setup incomplete")
        }
    }
}
