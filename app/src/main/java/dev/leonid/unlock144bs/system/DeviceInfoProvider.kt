package dev.leonid.unlock144bs.system

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dev.leonid.unlock144bs.AppConstants

data class DeviceStatus(
    val brawlStarsInstalled: Boolean,
    val powerKeeperVersion: String?,
    val deviceName: String,
    val androidVersion: String,
)

class DeviceInfoProvider(private val context: Context) {
    fun snapshot(): DeviceStatus = DeviceStatus(
        brawlStarsInstalled = packageVersion(AppConstants.BRAWL_STARS_PACKAGE) != null,
        powerKeeperVersion = packageVersion(AppConstants.POWER_KEEPER_PACKAGE),
        deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
        androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    )

    private fun packageVersion(packageName: String): String? = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(packageName, 0).versionName
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
