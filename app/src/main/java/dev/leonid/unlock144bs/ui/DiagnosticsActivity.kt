package dev.leonid.unlock144bs.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import dev.leonid.unlock144bs.BuildConfig
import dev.leonid.unlock144bs.R
import dev.leonid.unlock144bs.autofix.AutoFixService
import dev.leonid.unlock144bs.data.DiagnosticStore
import dev.leonid.unlock144bs.system.BatteryOptimization
import dev.leonid.unlock144bs.system.DeviceInfoProvider
import dev.leonid.unlock144bs.system.UsageAccess

class DiagnosticsActivity : AppCompatActivity() {
    private lateinit var diagnostics: DiagnosticStore
    private lateinit var deviceInfo: DeviceInfoProvider
    private var reportText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostics)
        diagnostics = DiagnosticStore(this)
        deviceInfo = DeviceInfoProvider(this)

        findViewById<MaterialToolbar>(R.id.diagnostics_toolbar)
            .setNavigationOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.copy_diagnostics_button)
            .setOnClickListener { copyDiagnostics() }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        reportText = buildReport()
        val status = deviceInfo.snapshot()
        val notificationGranted = notificationPermissionGranted()

        findViewById<TextView>(R.id.runtime_text).text = listOf(
            "${getString(R.string.device_label)}: ${status.deviceName}",
            "${getString(R.string.android_label)}: ${status.androidVersion}",
            "${getString(R.string.brawl_label)}: ${if (status.brawlStarsInstalled) getString(R.string.installed) else getString(R.string.not_installed)}",
            "${getString(R.string.powerkeeper_label)}: ${status.powerKeeperVersion ?: getString(R.string.not_found)}",
            "${getString(R.string.usage_access_label)}: ${if (UsageAccess.isGranted(this)) getString(R.string.granted) else getString(R.string.denied)}",
            "${getString(R.string.service_notification_title)}: ${if (notificationGranted) getString(R.string.visible) else getString(R.string.hidden)}",
            "${getString(R.string.battery_access_label)}: ${if (BatteryOptimization.isExempt(this)) getString(R.string.unrestricted) else getString(R.string.restricted)}",
            "${getString(R.string.watcher_label)}: ${if (AutoFixService.isLikelyRunning(this)) getString(R.string.active) else getString(R.string.inactive)}",
        ).joinToString("\n")

        findViewById<TextView>(R.id.stats_text).text = listOf(
            "${getString(R.string.session_count)}: ${DiagnosticStore.sessionApplicationCount}",
            "${getString(R.string.total_count)}: ${diagnostics.totalApplicationCount}",
            "${getString(R.string.last_error)}: ${diagnostics.lastError ?: getString(R.string.no_errors)}",
        ).joinToString("\n")

        val entries = diagnostics.entries()
        findViewById<TextView>(R.id.log_text).text = if (entries.isEmpty()) {
            getString(R.string.empty_log)
        } else {
            entries.joinToString("\n") { it.displayText() }
        }
    }

    private fun buildReport(): String {
        val status = deviceInfo.snapshot()
        val entries = diagnostics.entries()
        return buildString {
            appendLine("Unlock144BS diagnostics")
            appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Method: Direct / PowerKeeper broadcast")
            appendLine("Device: ${status.deviceName}")
            appendLine("OS: ${status.androidVersion}")
            appendLine("Brawl installed: ${status.brawlStarsInstalled}")
            appendLine("PowerKeeper: ${status.powerKeeperVersion ?: "missing"}")
            appendLine("Usage Access: ${UsageAccess.isGranted(this@DiagnosticsActivity)}")
            appendLine("Service notification visible: ${notificationPermissionGranted()}")
            appendLine("Battery unrestricted: ${BatteryOptimization.isExempt(this@DiagnosticsActivity)}")
            appendLine("Watcher active: ${AutoFixService.isLikelyRunning(this@DiagnosticsActivity)}")
            appendLine("Session applications: ${DiagnosticStore.sessionApplicationCount}")
            appendLine("Total applications: ${diagnostics.totalApplicationCount}")
            appendLine("Last error: ${diagnostics.lastError ?: "none"}")
            appendLine("Events:")
            if (entries.isEmpty()) appendLine("(empty)")
            else entries.asReversed().forEach { appendLine(it.displayText()) }
        }.trimEnd()
    }

    private fun copyDiagnostics() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Unlock144BS diagnostics", reportText))
        Snackbar.make(findViewById(R.id.diagnostics_root), R.string.copied, Snackbar.LENGTH_SHORT).show()
    }

    private fun notificationPermissionGranted(): Boolean =
        NotificationManagerCompat.from(this).areNotificationsEnabled()
}
