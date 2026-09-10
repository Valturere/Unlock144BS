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
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val preferences = AppPreferences(context)
        val diagnostics = DiagnosticStore(context)
        diagnostics.record("Boot completed", "received")

        if (preferences.autoFixEnabled &&
            preferences.startAfterBoot &&
            UsageAccess.isGranted(context) &&
            BatteryOptimization.isExempt(context)
        ) {
            val started = AutoFixService.start(context)
            diagnostics.record("Boot Auto Fix", if (started) "start requested" else "start failed")
        } else {
            diagnostics.record("Boot Auto Fix", "skipped by settings or incomplete setup")
        }
    }
}
