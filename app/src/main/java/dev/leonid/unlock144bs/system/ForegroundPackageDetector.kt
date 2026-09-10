package dev.leonid.unlock144bs.system

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager

class ForegroundPackageDetector(context: Context) {
    private val usageStats = context.getSystemService(UsageStatsManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private var queryFromMillis = System.currentTimeMillis() - INITIAL_LOOKBACK_MS
    private var currentPackage: String? = null

    fun currentForegroundPackage(): String? {
        if (!powerManager.isInteractive) return null

        val now = System.currentTimeMillis()
        val events = usageStats.queryEvents(queryFromMillis, now)
        val event = UsageEvents.Event()
        var latestTimestamp = Long.MIN_VALUE

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == EVENT_ACTIVITY_RESUMED) {
                if (event.timeStamp >= latestTimestamp) {
                    latestTimestamp = event.timeStamp
                    currentPackage = event.packageName
                }
            }
        }

        queryFromMillis = (now - OVERLAP_MS).coerceAtLeast(queryFromMillis)
        return currentPackage
    }

    private companion object {
        const val INITIAL_LOOKBACK_MS = 10 * 60_000L
        const val OVERLAP_MS = 1_000L
        const val EVENT_ACTIVITY_RESUMED = 1
    }
}
