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

    fun markServiceStopped() {
        preferences.edit().putLong(KEY_SERVICE_HEARTBEAT, 0L).apply()
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
        private const val KEY_LOG = "bounded_log"
        private const val SEPARATOR = "\t"
        private const val MAX_ENTRIES = 80
        private val SESSION_COUNT = AtomicInteger(0)

        val sessionApplicationCount: Int
            get() = SESSION_COUNT.get()

        private fun sanitize(value: String): String =
            value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').take(240)
    }
}
