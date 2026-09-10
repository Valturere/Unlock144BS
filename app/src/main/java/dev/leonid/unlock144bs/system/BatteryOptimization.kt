package dev.leonid.unlock144bs.system

import android.content.Context
import android.os.PowerManager

object BatteryOptimization {
    fun isExempt(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)
}
