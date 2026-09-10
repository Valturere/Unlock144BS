package dev.leonid.unlock144bs.data

import android.content.Context
import dev.leonid.unlock144bs.AppConstants

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var autoFixEnabled: Boolean
        get() = preferences.getBoolean(KEY_AUTO_FIX, false)
        set(value) = preferences.edit().putBoolean(KEY_AUTO_FIX, value).apply()

    var targetRefreshRate: Int
        get() = preferences.getInt(KEY_TARGET_RATE, AppConstants.DEFAULT_REFRESH_RATE)
        set(value) = preferences.edit().putInt(KEY_TARGET_RATE, value).apply()

    var repeatWhilePlaying: Boolean
        get() = preferences.getBoolean(KEY_REPEAT, true)
        set(value) = preferences.edit().putBoolean(KEY_REPEAT, value).apply()

    var startAfterBoot: Boolean
        get() = preferences.getBoolean(KEY_START_AFTER_BOOT, true)
        set(value) = preferences.edit().putBoolean(KEY_START_AFTER_BOOT, value).apply()

    companion object {
        private const val FILE_NAME = "unlock144_preferences"
        private const val KEY_AUTO_FIX = "auto_fix"
        private const val KEY_TARGET_RATE = "target_rate"
        private const val KEY_REPEAT = "repeat_while_playing"
        private const val KEY_START_AFTER_BOOT = "start_after_boot"
    }
}
