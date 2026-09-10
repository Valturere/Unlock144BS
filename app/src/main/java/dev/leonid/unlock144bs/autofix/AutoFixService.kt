package dev.leonid.unlock144bs.autofix

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.leonid.unlock144bs.AppConstants
import dev.leonid.unlock144bs.R
import dev.leonid.unlock144bs.data.AppPreferences
import dev.leonid.unlock144bs.data.DiagnosticStore
import dev.leonid.unlock144bs.system.BatteryOptimization
import dev.leonid.unlock144bs.system.ForegroundPackageDetector
import dev.leonid.unlock144bs.system.OverrideResult
import dev.leonid.unlock144bs.system.OverrideSender
import dev.leonid.unlock144bs.system.UsageAccess
import dev.leonid.unlock144bs.ui.MainActivity

class AutoFixService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var preferences: AppPreferences
    private lateinit var diagnostics: DiagnosticStore
    private lateinit var detector: ForegroundPackageDetector
    private lateinit var sender: OverrideSender
    private lateinit var powerManager: PowerManager
    private val policy = AutoFixPolicy(AppConstants.BRAWL_STARS_PACKAGE)
    private val monitoring = AutoFixMonitoringStateMachine()
    private var lastObservedPackage: String? = null
    private var screenReceiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!preferences.autoFixEnabled) {
                disableMonitoring()
                return
            }

            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> handleScreenOff()
                Intent.ACTION_SCREEN_ON -> handleScreenOn()
                Intent.ACTION_USER_PRESENT -> handleUserPresent()
            }
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (monitoring.state != MonitoringState.RUNNING) return
            pollForegroundApp()
            if (preferences.autoFixEnabled && monitoring.state == MonitoringState.RUNNING) {
                handler.postDelayed(this, nextCheckDelayMillis())
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startRequested = false
        running = true
        preferences = AppPreferences(this)
        diagnostics = DiagnosticStore(this)
        detector = ForegroundPackageDetector(this)
        sender = OverrideSender(this)
        powerManager = getSystemService(PowerManager::class.java)
        createNotificationChannel()
        registerScreenReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            preferences.autoFixEnabled = false
            diagnostics.record("Auto Fix", "disabled from notification")
            stopSelfSafely()
            return START_NOT_STICKY
        }

        if (!preferences.autoFixEnabled ||
            !UsageAccess.isGranted(this) ||
            !BatteryOptimization.isExempt(this)
        ) {
            diagnostics.record("Auto Fix", "not started: setup incomplete or disabled")
            stopSelfSafely()
            return START_NOT_STICKY
        }

        startAsForeground(getString(R.string.notification_waiting))
        val command = monitoring.enable(powerManager.isInteractive)
        if (command != MonitoringCommand.NONE) {
            diagnostics.record("Auto Fix", "watcher started")
        }
        when (command) {
            MonitoringCommand.START_LOOP -> startMonitoring("service start", countResume = false)
            MonitoringCommand.STOP_LOOP -> suspendMonitoring("service start: screen off")
            MonitoringCommand.NONE,
            MonitoringCommand.CHECK_NOW,
            -> Unit
        }
        if (intent?.action == ACTION_REFRESH_POLL_INTERVAL &&
            command == MonitoringCommand.NONE &&
            monitoring.state == MonitoringState.RUNNING
        ) {
            val interval = preferences.foregroundPollIntervalMillis
            val result =
                "changed to ${formatInterval(interval)}; checks=${diagnostics.foregroundCheckCount}"
            Log.i(TAG, "Polling interval -> $result; immediate foreground check")
            diagnostics.record("Polling interval", result)
            requestImmediateCheck()
        }
        return START_STICKY
    }

    private fun pollForegroundApp() {
        if (!powerManager.isInteractive) {
            if (monitoring.onScreenOff() == MonitoringCommand.STOP_LOOP) {
                suspendMonitoring("interactive state changed before SCREEN_OFF delivery")
            }
            return
        }

        diagnostics.updateServiceHeartbeat()

        if (!UsageAccess.isGranted(this)) {
            diagnostics.record("Auto Fix", "stopped: Usage Access revoked")
            preferences.autoFixEnabled = false
            stopSelfSafely()
            return
        }

        if (!BatteryOptimization.isExempt(this)) {
            diagnostics.record("Auto Fix", "stopped: battery optimization enabled")
            stopSelfSafely()
            return
        }

        val foregroundPackage = try {
            detector.currentForegroundPackage()
        } catch (error: Exception) {
            diagnostics.recordError("Foreground detection failed", error)
            null
        }

        val stillInteractive = powerManager.isInteractive
        diagnostics.recordForegroundCheck(screenInteractive = stillInteractive)
        if (!stillInteractive) {
            if (monitoring.onScreenOff() == MonitoringCommand.STOP_LOOP) {
                suspendMonitoring("screen turned off during foreground check")
            }
            return
        }

        if (foregroundPackage != lastObservedPackage) {
            if (foregroundPackage == AppConstants.BRAWL_STARS_PACKAGE) {
                diagnostics.record("Brawl foreground detected", "active")
                diagnostics.markBrawlDetected(true)
            } else if (lastObservedPackage == AppConstants.BRAWL_STARS_PACKAGE) {
                Log.i(TAG, "Brawl exited -> repeat stopped")
                diagnostics.record("Brawl exited", "repeat stopped")
                diagnostics.markBrawlDetected(false)
            }
            lastObservedPackage = foregroundPackage
        }

        val reason = policy.evaluate(
            foregroundPackage = foregroundPackage,
            elapsedRealtime = SystemClock.elapsedRealtime(),
            repeatEnabled = preferences.repeatWhilePlaying,
            repeatIntervalMillis = AppConstants.DEFAULT_REPEAT_INTERVAL_MS,
        )

        if (reason != null) {
            if (!powerManager.isInteractive) {
                if (monitoring.onScreenOff() == MonitoringCommand.STOP_LOOP) {
                    suspendMonitoring("screen turned off before override")
                }
                return
            }
            val source = when (reason) {
                ApplyReason.ENTERED_GAME -> "Auto Fix: foreground"
                ApplyReason.PERIODIC_REPEAT -> "Auto Fix: periodic"
            }
            when (sender.send(preferences.targetRefreshRate, source)) {
                is OverrideResult.Sent -> {
                    Log.i(TAG, "Brawl foreground -> override ${preferences.targetRefreshRate} sent")
                    updateNotification(
                        getString(R.string.notification_applied, preferences.targetRefreshRate),
                    )
                }
                is OverrideResult.Failure -> updateNotification(getString(R.string.notification_error))
            }
        } else if (foregroundPackage != AppConstants.BRAWL_STARS_PACKAGE) {
            updateNotification(getString(R.string.notification_waiting))
        }
    }

    private fun handleScreenOff() {
        if (monitoring.onScreenOff() == MonitoringCommand.STOP_LOOP) {
            suspendMonitoring("SCREEN_OFF")
        }
    }

    private fun handleScreenOn() {
        if (!powerManager.isInteractive) return
        if (monitoring.onScreenOn() == MonitoringCommand.START_LOOP) {
            startMonitoring("SCREEN_ON", countResume = true)
        }
    }

    private fun handleUserPresent() {
        val command = monitoring.onUserPresent(powerManager.isInteractive)
        if (command == MonitoringCommand.NONE) return

        Log.i(TAG, "USER_PRESENT -> immediate foreground check")
        diagnostics.record("USER_PRESENT", "immediate foreground check")
        when (command) {
            MonitoringCommand.START_LOOP -> startMonitoring("USER_PRESENT", countResume = true)
            MonitoringCommand.CHECK_NOW -> requestImmediateCheck()
            MonitoringCommand.NONE,
            MonitoringCommand.STOP_LOOP,
            -> Unit
        }
    }

    private fun startMonitoring(reason: String, countResume: Boolean) {
        handler.removeCallbacks(pollRunnable)
        policy.reset()
        lastObservedPackage = null
        diagnostics.markBrawlDetected(false)
        diagnostics.markMonitoringActive()
        diagnostics.updateServiceHeartbeat()
        if (countResume) diagnostics.recordMonitoringResume()
        val result = "monitoring resumed; checks=${diagnostics.foregroundCheckCount} " +
            "overrides=${diagnostics.totalApplicationCount}"
        Log.i(TAG, "$reason -> $result")
        diagnostics.record(reason, result)
        handler.post(pollRunnable)
    }

    private fun requestImmediateCheck() {
        if (monitoring.state != MonitoringState.RUNNING) return
        handler.removeCallbacks(pollRunnable)
        policy.reset()
        handler.post(pollRunnable)
    }

    private fun nextCheckDelayMillis(): Long {
        val foregroundPollInterval = preferences.foregroundPollIntervalMillis
        val millisUntilRepeat = policy.millisUntilRepeat(
            elapsedRealtime = SystemClock.elapsedRealtime(),
            repeatEnabled = preferences.repeatWhilePlaying,
            repeatIntervalMillis = AppConstants.DEFAULT_REPEAT_INTERVAL_MS,
        )
        return millisUntilRepeat?.let { minOf(foregroundPollInterval, it) }
            ?: foregroundPollInterval
    }

    private fun suspendMonitoring(reason: String) {
        handler.removeCallbacks(pollRunnable)
        policy.reset()
        lastObservedPackage = null
        diagnostics.markMonitoringSuspended()
        val result = "monitoring suspended; checks=${diagnostics.foregroundCheckCount} " +
            "overrides=${diagnostics.totalApplicationCount}"
        Log.i(TAG, "$reason -> $result")
        diagnostics.record(reason, result)
    }

    private fun disableMonitoring() {
        monitoring.disable()
        handler.removeCallbacks(pollRunnable)
        policy.reset()
        lastObservedPackage = null
        diagnostics.markServiceStopped()
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        screenReceiverRegistered = true
    }

    private fun unregisterScreenReceiver() {
        if (!screenReceiverRegistered) return
        try {
            unregisterReceiver(screenReceiver)
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "Screen receiver was already unregistered", error)
        } finally {
            screenReceiverRegistered = false
        }
    }

    private fun startAsForeground(status: String) {
        val notification = buildNotification(status)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(status: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun buildNotification(status: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AutoFixService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_unlock)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(status)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .addAction(0, getString(R.string.disable_auto_fix), stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
    }

    private fun stopSelfSafely() {
        disableMonitoring()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        disableMonitoring()
        unregisterScreenReceiver()
        startRequested = false
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "Unlock144BS"
        private const val CHANNEL_ID = "auto_fix_service_v2"
        private const val LEGACY_CHANNEL_ID = "auto_fix"
        private const val NOTIFICATION_ID = 144
        private const val ACTION_STOP = "dev.leonid.unlock144bs.action.STOP_AUTO_FIX"
        private const val ACTION_REFRESH_POLL_INTERVAL =
            "dev.leonid.unlock144bs.action.REFRESH_POLL_INTERVAL"
        @Volatile
        private var running = false
        @Volatile
        private var startRequested = false

        fun start(context: Context): Boolean {
            if (running || startRequested) return true
            startRequested = true
            return try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, AutoFixService::class.java),
                )
                true
            } catch (error: Exception) {
                startRequested = false
                Log.e(TAG, "Unable to start Auto Fix service", error)
                DiagnosticStore(context).recordError("Auto Fix start failed", error)
                false
            }
        }

        fun ensureRunning(context: Context): Boolean = running || startRequested || start(context)

        fun refreshPollingInterval(context: Context): Boolean = try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AutoFixService::class.java).setAction(ACTION_REFRESH_POLL_INTERVAL),
            )
            true
        } catch (error: Exception) {
            Log.e(TAG, "Unable to refresh foreground polling interval", error)
            DiagnosticStore(context).recordError("Polling interval refresh failed", error)
            false
        }

        fun stop(context: Context) {
            startRequested = false
            context.stopService(Intent(context, AutoFixService::class.java))
            DiagnosticStore(context).apply {
                markServiceStopped()
                record("Auto Fix", "watcher stopped")
            }
        }

        fun isLikelyRunning(context: Context): Boolean {
            if (running) return true
            val heartbeat = DiagnosticStore(context).serviceHeartbeatMillis
            val interval = AppPreferences(context).foregroundPollIntervalMillis
            return heartbeat > 0L &&
                System.currentTimeMillis() - heartbeat < interval * 4
        }

        private fun formatInterval(intervalMillis: Long): String =
            if (intervalMillis % 1_000L == 0L) {
                "${intervalMillis / 1_000L} s"
            } else {
                "${intervalMillis / 1_000.0} s"
            }
    }
}
