package dev.leonid.unlock144bs.autofix

enum class ApplyReason {
    ENTERED_GAME,
    PERIODIC_REPEAT,
}

class AutoFixPolicy(private val targetPackage: String) {
    private var targetWasForeground = false
    private var lastApplicationAt = Long.MIN_VALUE

    fun evaluate(
        foregroundPackage: String?,
        elapsedRealtime: Long,
        repeatEnabled: Boolean,
        repeatIntervalMillis: Long,
    ): ApplyReason? {
        val targetIsForeground = foregroundPackage == targetPackage
        if (!targetIsForeground) {
            targetWasForeground = false
            return null
        }

        if (!targetWasForeground) {
            targetWasForeground = true
            lastApplicationAt = elapsedRealtime
            return ApplyReason.ENTERED_GAME
        }

        if (repeatEnabled && elapsedRealtime - lastApplicationAt >= repeatIntervalMillis) {
            lastApplicationAt = elapsedRealtime
            return ApplyReason.PERIODIC_REPEAT
        }

        return null
    }

    fun reset() {
        targetWasForeground = false
        lastApplicationAt = Long.MIN_VALUE
    }

    fun millisUntilRepeat(
        elapsedRealtime: Long,
        repeatEnabled: Boolean,
        repeatIntervalMillis: Long,
    ): Long? {
        if (!targetWasForeground || !repeatEnabled) return null
        return (repeatIntervalMillis - (elapsedRealtime - lastApplicationAt)).coerceAtLeast(0L)
    }
}
