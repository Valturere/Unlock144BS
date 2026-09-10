package dev.leonid.unlock144bs.data

import android.content.Context
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger

data class DiagnosticEntry(
    val timestampMillis: Long,
    val event: String,
    val result: String,
) {
    fun displayText(): String =
        "${TIME_FORMAT.format(Instant.ofEpochMilli(timestampMillis))}  $event — $result"

    private companion object {
        val TIME_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
    }
}

class DiagnosticStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    val lastSuccessMillis: Long
        get() = preferences.getLong(KEY_LAST_SUCCESS, 0L)

    val lastError: String?
        get() = preferences.getString(KEY_LAST_ERROR, null)?.takeIf { it.isNotBlank() }

    val totalApplicationCount: Int
        get() = preferences.getInt(KEY_TOTAL_COUNT, 0)

    val serviceHeartbeatMillis: Long
        get() = preferences.getLong(KEY_SERVICE_HEARTBEAT, 0L)

    val monitoringState: String
        get() = preferences.getString(KEY_MONITORING_STATE, MONITORING_DISABLED)
            ?: MONITORING_DISABLED

    val foregroundPollingActive: Boolean
        get() = preferences.getBoolean(KEY_FOREGROUND_POLLING_ACTIVE, false)

    val brawlDetected: Boolean
        get() = preferences.getBoolean(KEY_BRAWL_DETECTED, false)

    val foregroundCheckCount: Int
        get() = preferences.getInt(KEY_FOREGROUND_CHECK_COUNT, 0)

    val checksWhileScreenOffCount: Int
        get() = preferences.getInt(KEY_CHECKS_WHILE_SCREEN_OFF_COUNT, 0)

    val screenOffSuspendCount: Int
        get() = preferences.getInt(KEY_SCREEN_OFF_SUSPEND_COUNT, 0)

    val screenOnResumeCount: Int
        get() = preferences.getInt(KEY_SCREEN_ON_RESUME_COUNT, 0)

    @Synchronized
    fun record(event: String, result: String) {
        val entry = listOf(
            System.currentTimeMillis().toString(),
            sanitize(event),
            sanitize(result),
        ).joinToString(SEPARATOR)
        val updated = (preferences.getString(KEY_LOG, "").orEmpty().lineSequence()
            .filter { it.isNotBlank() }
            .toList() + entry)
            .takeLast(MAX_ENTRIES)
            .joinToString("\n")
        preferences.edit().putString(KEY_LOG, updated).apply()
    }

    @Synchronized
    fun recordOverrideSuccess(source: String, refreshRate: Int) {
        val now = System.currentTimeMillis()
        val total = totalApplicationCount + 1
        SESSION_COUNT.incrementAndGet()
        preferences.edit()
            .putLong(KEY_LAST_SUCCESS, now)
            .putInt(KEY_TOTAL_COUNT, total)
            .remove(KEY_LAST_ERROR)
            .apply()
        record("Override $refreshRate sent", source)
    }

    @Synchronized
    fun recordError(event: String, error: Throwable) {
        val message = "${error.javaClass.simpleName}: ${error.message.orEmpty()}".trim()
        preferences.edit().putString(KEY_LAST_ERROR, message).apply()
        record(event, message)
    }

    fun updateServiceHeartbeat() {
        preferences.edit().putLong(KEY_SERVICE_HEARTBEAT, System.currentTimeMillis()).apply()
    }

    @Synchronized
    fun markMonitoringActive() {
        preferences.edit()
            .putString(KEY_MONITORING_STATE, MONITORING_ACTIVE)
            .putBoolean(KEY_FOREGROUND_POLLING_ACTIVE, true)
            .apply()
    }

    @Synchronized
    fun markMonitoringSuspended() {
        preferences.edit()
            .putString(KEY_MONITORING_STATE, MONITORING_SUSPENDED)
            .putBoolean(KEY_FOREGROUND_POLLING_ACTIVE, false)
            .putBoolean(KEY_BRAWL_DETECTED, false)
            .putInt(KEY_SCREEN_OFF_SUSPEND_COUNT, screenOffSuspendCount + 1)
            .apply()
    }

    @Synchronized
    fun recordMonitoringResume() {
        preferences.edit()
            .putInt(KEY_SCREEN_ON_RESUME_COUNT, screenOnResumeCount + 1)
            .apply()
    }

    @Synchronized
    fun recordForegroundCheck(screenInteractive: Boolean) {
        val editor = preferences.edit()
        if (screenInteractive) {
            editor.putInt(KEY_FOREGROUND_CHECK_COUNT, foregroundCheckCount + 1)
        } else {
            editor.putInt(
                KEY_CHECKS_WHILE_SCREEN_OFF_COUNT,
                checksWhileScreenOffCount + 1,
            )
        }
        editor.apply()
    }

    fun markBrawlDetected(detected: Boolean) {
        preferences.edit().putBoolean(KEY_BRAWL_DETECTED, detected).apply()
    }

    fun markServiceStopped() {
        preferences.edit()
            .putLong(KEY_SERVICE_HEARTBEAT, 0L)
            .putString(KEY_MONITORING_STATE, MONITORING_DISABLED)
            .putBoolean(KEY_FOREGROUND_POLLING_ACTIVE, false)
            .putBoolean(KEY_BRAWL_DETECTED, false)
            .apply()
    }

    fun entries(): List<DiagnosticEntry> = preferences.getString(KEY_LOG, "").orEmpty()
        .lineSequence()
        .mapNotNull { line ->
            val parts = line.split(SEPARATOR, limit = 3)
            val timestamp = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            DiagnosticEntry(timestamp, parts.getOrElse(1) { "Event" }, parts.getOrElse(2) { "" })
        }
        .toList()
        .asReversed()

    companion object {
        private const val FILE_NAME = "unlock144_diagnostics"
        private const val KEY_LAST_SUCCESS = "last_success"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_TOTAL_COUNT = "total_count"
        private const val KEY_SERVICE_HEARTBEAT = "service_heartbeat"
        private const val KEY_MONITORING_STATE = "monitoring_state"
        private const val KEY_FOREGROUND_POLLING_ACTIVE = "foreground_polling_active"
        private const val KEY_BRAWL_DETECTED = "brawl_detected"
        private const val KEY_FOREGROUND_CHECK_COUNT = "foreground_check_count"
        private const val KEY_CHECKS_WHILE_SCREEN_OFF_COUNT = "checks_while_screen_off_count"
        private const val KEY_SCREEN_OFF_SUSPEND_COUNT = "screen_off_suspend_count"
        private const val KEY_SCREEN_ON_RESUME_COUNT = "screen_on_resume_count"
        private const val KEY_LOG = "bounded_log"
        private const val SEPARATOR = "\t"
        private const val MAX_ENTRIES = 80
        private val SESSION_COUNT = AtomicInteger(0)

        const val MONITORING_ACTIVE = "ACTIVE"
        const val MONITORING_SUSPENDED = "SUSPENDED"
        const val MONITORING_DISABLED = "DISABLED"

        val sessionApplicationCount: Int
            get() = SESSION_COUNT.get()

        private fun sanitize(value: String): String =
            value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').take(240)
    }
}
