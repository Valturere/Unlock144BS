package dev.leonid.unlock144bs.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import dev.leonid.unlock144bs.AppConstants
import dev.leonid.unlock144bs.data.DiagnosticStore

sealed interface OverrideResult {
    data class Sent(val refreshRate: Int) : OverrideResult
    data class Failure(val error: Throwable) : OverrideResult
}

class OverrideSender(context: Context) {
    private val appContext = context.applicationContext
    private val diagnostics = DiagnosticStore(appContext)

    fun send(refreshRate: Int, source: String): OverrideResult {
        if (refreshRate !in SUPPORTED_REFRESH_RATES) {
            return failure(IllegalArgumentException("Unsupported refresh rate: $refreshRate"))
        }
        if (!isPowerKeeperInstalled()) {
            return failure(IllegalStateException("PowerKeeper is not installed"))
        }

        val intent = Intent(AppConstants.ACTION_OVERRIDE_GAME_REFRESH_RATE).apply {
            setPackage(AppConstants.POWER_KEEPER_PACKAGE)
            putExtra(AppConstants.EXTRA_PACKAGE_NAME, AppConstants.BRAWL_STARS_PACKAGE)
            putExtra(AppConstants.EXTRA_REFRESH_RATE, refreshRate)
        }

        return try {
            appContext.sendBroadcast(intent)
            Log.i(TAG, "Override sent: package=${AppConstants.BRAWL_STARS_PACKAGE} rate=$refreshRate source=$source")
            diagnostics.recordOverrideSuccess(source, refreshRate)
            OverrideResult.Sent(refreshRate)
        } catch (error: Exception) {
            failure(error)
        }
    }

    private fun isPowerKeeperInstalled(): Boolean = try {
        appContext.packageManager.getPackageInfo(AppConstants.POWER_KEEPER_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun failure(error: Exception): OverrideResult.Failure {
        Log.e(TAG, "Override failed", error)
        diagnostics.recordError("Override failed", error)
        return OverrideResult.Failure(error)
    }

    private companion object {
        const val TAG = "Unlock144BS"
        val SUPPORTED_REFRESH_RATES = setOf(60, 90, 120, 144)
    }
}
