package dev.leonid.unlock144bs.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.PackageManagerCompat
import androidx.core.content.UnusedAppRestrictionsConstants
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import dev.leonid.unlock144bs.AppConstants
import dev.leonid.unlock144bs.R
import dev.leonid.unlock144bs.autofix.AutoFixService
import dev.leonid.unlock144bs.data.AppPreferences
import dev.leonid.unlock144bs.data.DiagnosticStore
import dev.leonid.unlock144bs.system.BatteryOptimization
import dev.leonid.unlock144bs.system.DeviceInfoProvider
import dev.leonid.unlock144bs.system.OverrideResult
import dev.leonid.unlock144bs.system.OverrideSender
import dev.leonid.unlock144bs.system.UsageAccess
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {
    private val appSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        render()
    }

    private lateinit var preferences: AppPreferences
    private lateinit var diagnostics: DiagnosticStore
    private lateinit var deviceInfo: DeviceInfoProvider
    private lateinit var overrideSender: OverrideSender

    private lateinit var autoFixSwitch: MaterialSwitch
    private lateinit var startAfterBootSwitch: MaterialSwitch
    private lateinit var rateGroup: MaterialButtonToggleGroup
    private lateinit var repeatGroup: MaterialButtonToggleGroup
    private lateinit var pollIntervalGroup: MaterialButtonToggleGroup
    private var rendering = false
    private var waitingForUsageAccess = false
    private var waitingForBatteryExemption = false
    private var enableAfterBatteryExemption = false
    private var unusedAppRestrictionsStatus = UnusedAppRestrictionsConstants.ERROR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preferences = AppPreferences(this)
        diagnostics = DiagnosticStore(this)
        deviceInfo = DeviceInfoProvider(this)
        overrideSender = OverrideSender(this)
        waitingForUsageAccess = savedInstanceState?.getBoolean(KEY_WAITING_FOR_USAGE) ?: false
        waitingForBatteryExemption =
            savedInstanceState?.getBoolean(KEY_WAITING_FOR_BATTERY) ?: false
        enableAfterBatteryExemption =
            savedInstanceState?.getBoolean(KEY_ENABLE_AFTER_BATTERY) ?: false

        bindViews()
        bindActions()
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_WAITING_FOR_USAGE, waitingForUsageAccess)
        outState.putBoolean(KEY_WAITING_FOR_BATTERY, waitingForBatteryExemption)
        outState.putBoolean(KEY_ENABLE_AFTER_BATTERY, enableAfterBatteryExemption)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (waitingForUsageAccess) {
            waitingForUsageAccess = false
            if (UsageAccess.isGranted(this)) continueAutoFixSetup()
            else showMessage(R.string.usage_access_not_granted)
        } else if (waitingForBatteryExemption) {
            waitingForBatteryExemption = false
            if (BatteryOptimization.isExempt(this)) {
                diagnostics.record("Battery optimization", "unrestricted")
                if (enableAfterBatteryExemption) enableAutoFix()
                else if (preferences.autoFixEnabled) AutoFixService.start(this)
            } else {
                showMessage(R.string.battery_exemption_not_granted)
            }
            enableAfterBatteryExemption = false
        }
        if (preferences.autoFixEnabled &&
            UsageAccess.isGranted(this) &&
            BatteryOptimization.isExempt(this)
        ) {
            AutoFixService.ensureRunning(this)
        }
        render()
        refreshUnusedAppRestrictionsStatus()
    }

    private fun bindViews() {
        autoFixSwitch = findViewById(R.id.auto_fix_switch)
        startAfterBootSwitch = findViewById(R.id.start_after_boot_switch)
        rateGroup = findViewById(R.id.rate_group)
        repeatGroup = findViewById(R.id.repeat_group)
        pollIntervalGroup = findViewById(R.id.poll_interval_group)
    }

    private fun bindActions() {
        autoFixSwitch.setOnCheckedChangeListener { _, checked ->
            if (rendering) return@setOnCheckedChangeListener
            if (checked) beginAutoFixSetup() else disableAutoFix()
        }
        startAfterBootSwitch.setOnCheckedChangeListener { _, checked ->
            if (!rendering) preferences.startAfterBoot = checked
        }
        rateGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!rendering && isChecked) preferences.targetRefreshRate = rateForButton(checkedId)
        }
        repeatGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!rendering && isChecked) preferences.repeatWhilePlaying = checkedId == R.id.repeat_auto
        }
        pollIntervalGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!rendering && isChecked) {
                val interval = pollIntervalForButton(checkedId)
                if (interval != preferences.foregroundPollIntervalMillis) {
                    preferences.foregroundPollIntervalMillis = interval
                    if (preferences.autoFixEnabled) {
                        AutoFixService.refreshPollingInterval(this)
                    }
                }
            }
        }
        findViewById<MaterialButton>(R.id.usage_access_button).setOnClickListener {
            waitingForUsageAccess = true
            openUsageSettings()
        }
        findViewById<MaterialButton>(R.id.open_app_info_button).setOnClickListener {
            openAppInfo()
        }
        findViewById<MaterialButton>(R.id.battery_access_button).setOnClickListener {
            requestBatteryExemption(enableAfterGrant = false)
        }
        findViewById<MaterialButton>(R.id.background_settings_button).setOnClickListener {
            openUnusedAppRestrictionsSettings()
        }
        findViewById<MaterialButton>(R.id.notification_settings_button).setOnClickListener {
            openNotificationSettings()
        }
        findViewById<MaterialButton>(R.id.apply_now_button).setOnClickListener { applyNow("Manual") }
        findViewById<MaterialButton>(R.id.launch_brawl_button).setOnClickListener { launchBrawl() }
        findViewById<MaterialButton>(R.id.diagnostics_button).setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }
    }

    private fun beginAutoFixSetup() {
        val status = deviceInfo.snapshot()
        if (!status.brawlStarsInstalled) {
            showMessage(R.string.brawl_missing)
            render()
            return
        }
        if (status.powerKeeperVersion == null) {
            showMessage(R.string.powerkeeper_missing)
            render()
            return
        }
        if (!UsageAccess.isGranted(this)) {
            waitingForUsageAccess = true
            openUsageSettings()
            showMessage(R.string.usage_settings_opened)
            render()
            return
        }
        continueAutoFixSetup()
    }

    private fun continueAutoFixSetup() {
        if (!BatteryOptimization.isExempt(this)) {
            requestBatteryExemption(enableAfterGrant = true)
            return
        }
        enableAutoFix()
    }

    private fun enableAutoFix() {
        preferences.autoFixEnabled = true
        if (AutoFixService.start(this)) {
            diagnostics.record("Auto Fix", "enabled by user")
            showMessage(R.string.auto_fix_started)
            autoFixSwitch.postDelayed({
                if (!isFinishing && !isDestroyed) render()
            }, STATUS_REFRESH_DELAY_MS)
        } else {
            preferences.autoFixEnabled = false
        }
        render()
    }

    private fun disableAutoFix() {
        preferences.autoFixEnabled = false
        AutoFixService.stop(this)
        showMessage(R.string.auto_fix_stopped)
        render()
    }

    private fun applyNow(source: String): Boolean {
        val status = deviceInfo.snapshot()
        if (!status.brawlStarsInstalled) {
            showMessage(R.string.brawl_missing)
            return false
        }
        if (status.powerKeeperVersion == null) {
            showMessage(R.string.powerkeeper_missing)
            return false
        }
        return when (val result = overrideSender.send(preferences.targetRefreshRate, source)) {
            is OverrideResult.Sent -> {
                showMessage(getString(R.string.override_sent, result.refreshRate))
                render()
                true
            }
            is OverrideResult.Failure -> {
                showMessage(getString(R.string.override_failed, result.error.message.orEmpty()))
                render()
                false
            }
        }
    }

    private fun launchBrawl() {
        val launchIntent = packageManager.getLaunchIntentForPackage(AppConstants.BRAWL_STARS_PACKAGE)
        if (launchIntent == null) {
            showMessage(R.string.brawl_missing)
            return
        }
        applyNow("Launch button")
        startActivity(launchIntent)
    }

    private fun openUsageSettings() {
        val intent = Intent(
            Settings.ACTION_USAGE_ACCESS_SETTINGS,
            Uri.parse("package:$packageName"),
        )
        startActivity(intent)
    }

    private fun openAppInfo() {
        val packageUri = Uri.parse("package:$packageName")
        try {
            appSettingsLauncher.launch(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
            )
        } catch (_: Exception) {
            appSettingsLauncher.launch(Intent(Settings.ACTION_APPLICATION_SETTINGS))
        }
    }

    private fun openUnusedAppRestrictionsSettings() {
        queryUnusedAppRestrictionsStatus { status ->
            unusedAppRestrictionsStatus = status
            if (status == UnusedAppRestrictionsConstants.FEATURE_NOT_AVAILABLE) {
                openAppInfo()
                return@queryUnusedAppRestrictionsStatus
            }
            try {
                appSettingsLauncher.launch(
                    IntentCompat.createManageUnusedAppRestrictionsIntent(this, packageName),
                )
            } catch (_: Exception) {
                openAppInfo()
            }
        }
    }

    private fun refreshUnusedAppRestrictionsStatus() {
        queryUnusedAppRestrictionsStatus { status ->
            unusedAppRestrictionsStatus = status
            if (!isFinishing && !isDestroyed) render()
        }
    }

    private fun queryUnusedAppRestrictionsStatus(onResult: (Int) -> Unit) {
        val statusFuture = PackageManagerCompat.getUnusedAppRestrictionsStatus(this)
        statusFuture.addListener(
            {
                val status = runCatching { statusFuture.get() }
                    .getOrDefault(UnusedAppRestrictionsConstants.ERROR)
                onResult(status)
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private fun openNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
        )
    }

    private fun requestBatteryExemption(enableAfterGrant: Boolean) {
        waitingForBatteryExemption = true
        enableAfterBatteryExemption = enableAfterGrant
        val packageUri = Uri.parse("package:$packageName")
        val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
        try {
            startActivity(requestIntent)
        } catch (_: Exception) {
            waitingForBatteryExemption = false
            enableAfterBatteryExemption = false
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
            showMessage(R.string.battery_settings_fallback)
        }
    }

    private fun render() {
        val status = deviceInfo.snapshot()
        val usageGranted = UsageAccess.isGranted(this)
        val batteryExempt = BatteryOptimization.isExempt(this)
        val serviceNotificationVisible = notificationsVisible()
        rendering = true

        autoFixSwitch.isChecked = preferences.autoFixEnabled
        startAfterBootSwitch.isChecked = preferences.startAfterBoot
        startAfterBootSwitch.isEnabled = preferences.autoFixEnabled
        rateGroup.check(buttonForRate(preferences.targetRefreshRate))
        repeatGroup.check(if (preferences.repeatWhilePlaying) R.id.repeat_auto else R.id.repeat_off)
        pollIntervalGroup.check(
            buttonForPollInterval(preferences.foregroundPollIntervalMillis),
        )

        findViewById<TextView>(R.id.auto_fix_status).setText(
            when {
                preferences.autoFixEnabled && !batteryExempt -> R.string.status_needs_battery
                preferences.autoFixEnabled && AutoFixService.isLikelyRunning(this) -> R.string.status_enabled
                preferences.autoFixEnabled -> R.string.status_starting
                else -> R.string.status_disabled
            },
        )
        findViewById<TextView>(R.id.brawl_value).setText(
            if (status.brawlStarsInstalled) R.string.installed else R.string.not_installed,
        )
        findViewById<TextView>(R.id.powerkeeper_value).text = status.powerKeeperVersion
            ?.let { getString(R.string.version_value, it) }
            ?: getString(R.string.not_found)
        findViewById<MaterialButton>(R.id.usage_access_button).setText(
            if (usageGranted) R.string.usage_access_granted else R.string.usage_access_required,
        )
        findViewById<MaterialButton>(R.id.battery_access_button).setText(
            if (batteryExempt) R.string.battery_access_granted else R.string.battery_access_required,
        )
        val onboarding = onboardingVisibility(
            autoFixEnabled = preferences.autoFixEnabled,
            usageGranted = usageGranted,
            batteryExempt = batteryExempt,
            unusedAppRestrictionsStatus = unusedAppRestrictionsStatus,
        )
        findViewById<View>(R.id.setup_steps).visibility =
            if (onboarding.showSetup) View.VISIBLE else View.GONE
        findViewById<View>(R.id.required_setup_steps).visibility =
            if (onboarding.showRequiredSteps) View.VISIBLE else View.GONE
        findViewById<View>(R.id.background_work_step_container).visibility =
            if (onboarding.showBackgroundStep) View.VISIBLE else View.GONE
        findViewById<View>(R.id.restricted_settings_help).visibility =
            if (usageGranted) View.GONE else View.VISIBLE
        findViewById<TextView>(R.id.background_work_status).setText(
            when (unusedAppRestrictionsStatus) {
                UnusedAppRestrictionsConstants.API_30_BACKPORT,
                UnusedAppRestrictionsConstants.API_30,
                UnusedAppRestrictionsConstants.API_31,
                -> R.string.background_work_status_enabled
                else -> R.string.background_work_status_unknown
            },
        )
        findViewById<MaterialButton>(R.id.notification_settings_button).setText(
            if (serviceNotificationVisible) {
                R.string.hide_service_notification
            } else {
                R.string.open_notification_settings
            },
        )
        findViewById<TextView>(R.id.notification_explanation).setText(
            if (serviceNotificationVisible) {
                R.string.notification_explanation_visible
            } else {
                R.string.notification_explanation_hidden
            },
        )
        findViewById<TextView>(R.id.last_applied_value).text = diagnostics.lastSuccessMillis
            .takeIf { it > 0L }
            ?.let { DATE_TIME_FORMAT.format(Instant.ofEpochMilli(it)) }
            ?: getString(R.string.never)

        val errorView = findViewById<TextView>(R.id.error_text)
        errorView.text = diagnostics.lastError.orEmpty()
        errorView.visibility = if (diagnostics.lastError == null) View.GONE else View.VISIBLE
        rendering = false
    }

    private fun rateForButton(buttonId: Int): Int = when (buttonId) {
        R.id.rate_60 -> 60
        R.id.rate_90 -> 90
        R.id.rate_120 -> 120
        else -> 144
    }

    private fun buttonForRate(rate: Int): Int = when (rate) {
        60 -> R.id.rate_60
        90 -> R.id.rate_90
        120 -> R.id.rate_120
        else -> R.id.rate_144
    }

    private fun pollIntervalForButton(buttonId: Int): Long = when (buttonId) {
        R.id.poll_interval_2500 -> 2_500L
        R.id.poll_interval_5000 -> 5_000L
        R.id.poll_interval_15000 -> 15_000L
        R.id.poll_interval_30000 -> 30_000L
        else -> 10_000L
    }

    private fun buttonForPollInterval(intervalMillis: Long): Int = when (intervalMillis) {
        2_500L -> R.id.poll_interval_2500
        5_000L -> R.id.poll_interval_5000
        15_000L -> R.id.poll_interval_15000
        30_000L -> R.id.poll_interval_30000
        else -> R.id.poll_interval_10000
    }

    private fun showMessage(messageRes: Int) = showMessage(getString(messageRes))

    private fun showMessage(message: String) {
        Snackbar.make(findViewById(R.id.main_root), message, Snackbar.LENGTH_LONG).show()
    }

    private fun notificationsVisible(): Boolean =
        NotificationManagerCompat.from(this).areNotificationsEnabled()

    private companion object {
        const val KEY_WAITING_FOR_USAGE = "waiting_for_usage"
        const val KEY_WAITING_FOR_BATTERY = "waiting_for_battery"
        const val KEY_ENABLE_AFTER_BATTERY = "enable_after_battery"
        const val STATUS_REFRESH_DELAY_MS = 500L
        val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter
            .ofPattern("dd.MM.yyyy · HH:mm:ss")
            .withZone(ZoneId.systemDefault())
    }
}
