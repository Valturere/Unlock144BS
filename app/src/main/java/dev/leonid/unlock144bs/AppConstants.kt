package dev.leonid.unlock144bs

object AppConstants {
    const val ACTION_OVERRIDE_GAME_REFRESH_RATE =
        "com.xiaomi.joyose.OVERRIDE_GAME_FRESHRATE"
    const val EXTRA_PACKAGE_NAME = "override_pkg_name"
    const val EXTRA_REFRESH_RATE = "override_freshrate"
    const val POWER_KEEPER_PACKAGE = "com.miui.powerkeeper"
    const val BRAWL_STARS_PACKAGE = "com.supercell.brawlstars"

    const val DEFAULT_REFRESH_RATE = 144
    const val DEFAULT_REPEAT_INTERVAL_MS = 45_000L
    const val FOREGROUND_POLL_INTERVAL_MS = 2_500L
}
