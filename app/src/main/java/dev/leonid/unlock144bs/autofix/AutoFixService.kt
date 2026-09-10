package dev.leonid.unlock144bs.autofix

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
    private val policy = AutoFixPolicy(AppConstants.BRAWL_STARS_PACKAGE)
    private var lastObservedPackage: String? = null

    private val pollRunnable = object : Runnable {
        override fun run() {
            poll()
            if (preferences.autoFixEnabled) {
                handler.postDelayed(this, AppConstants.FOREGROUND_POLL_INTERVAL_MS)
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
        createNotificationChannel()
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
        diagnostics.record("Auto Fix", "watcher started")
        handler.removeCallbacks(pollRunnable)
        handler.post(pollRunnable)
        return START_STICKY
    }

    private fun poll() {
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

        if (foregroundPackage != lastObservedPackage) {
            if (foregroundPackage == AppConstants.BRAWL_STARS_PACKAGE) {
                diagnostics.record("Brawl foreground detected", "active")
            } else if (lastObservedPackage == AppConstants.BRAWL_STARS_PACKAGE) {
                diagnostics.record("Brawl left foreground", "watching")
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
            val source = when (reason) {
                ApplyReason.ENTERED_GAME -> "Auto Fix: foreground"
                ApplyReason.PERIODIC_REPEAT -> "Auto Fix: periodic"
            }
            when (sender.send(preferences.targetRefreshRate, source)) {
                is OverrideResult.Sent -> updateNotification(
                    getString(R.string.notification_applied, preferences.targetRefreshRate),
                )
                is OverrideResult.Failure -> updateNotification(getString(R.string.notification_error))
            }
        } else if (foregroundPackage != AppConstants.BRAWL_STARS_PACKAGE) {
            updateNotification(getString(R.string.notification_waiting))
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
            .addAction(0, getString(R.string.disable_auto_fix), stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun stopSelfSafely() {
        handler.removeCallbacks(pollRunnable)
        diagnostics.markServiceStopped()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        diagnostics.markServiceStopped()
        startRequested = false
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "Unlock144BS"
        private const val CHANNEL_ID = "auto_fix"
        private const val NOTIFICATION_ID = 144
        private const val ACTION_STOP = "dev.leonid.unlock144bs.action.STOP_AUTO_FIX"
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
            return heartbeat > 0L &&
                System.currentTimeMillis() - heartbeat < AppConstants.FOREGROUND_POLL_INTERVAL_MS * 4
        }
    }
}
