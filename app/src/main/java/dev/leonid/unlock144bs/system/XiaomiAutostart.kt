package dev.leonid.unlock144bs.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object XiaomiAutostart {
    private val component = ComponentName(
        "com.miui.securitycenter",
        "com.miui.permcenter.autostart.AutoStartManagementActivity",
    )

    fun isAvailable(context: Context): Boolean {
        val activityInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getActivityInfo(
                    component,
                    PackageManager.ComponentInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getActivityInfo(component, 0)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: SecurityException) {
            null
        }
        return activityInfo?.exported == true && activityInfo.enabled
    }

    fun settingsIntent(): Intent = Intent().setComponent(component)
}
