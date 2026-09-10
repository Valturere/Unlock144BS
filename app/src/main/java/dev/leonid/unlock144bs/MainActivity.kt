package dev.leonid.unlock144bs

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        statusView = TextView(this).apply {
            text = getString(R.string.ready)
            textSize = 16f
        }

        val button = Button(this).apply {
            text = getString(R.string.apply_144)
            setOnClickListener { sendRefreshRateOverride() }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(48), dp(24), dp(24))
            addView(TextView(context).apply {
                text = getString(R.string.app_name)
                textSize = 30f
                gravity = Gravity.CENTER
            }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(TextView(context).apply {
                text = getString(R.string.poc_description)
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(0, dp(16), 0, dp(24))
            }, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(button, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(statusView.apply { setPadding(0, dp(20), 0, 0) },
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        setContentView(content)
    }

    private fun sendRefreshRateOverride() {
        val intent = Intent(ACTION_OVERRIDE_GAME_REFRESH_RATE).apply {
            setPackage(POWER_KEEPER_PACKAGE)
            putExtra(EXTRA_PACKAGE_NAME, BRAWL_STARS_PACKAGE)
            putExtra(EXTRA_REFRESH_RATE, TARGET_REFRESH_RATE)
        }

        try {
            Log.i(TAG, "Sending refresh-rate override: package=$BRAWL_STARS_PACKAGE rate=$TARGET_REFRESH_RATE")
            sendBroadcast(intent)
            statusView.text = getString(R.string.sent)
            Log.i(TAG, "sendBroadcast returned without exception")
        } catch (error: Exception) {
            statusView.text = getString(R.string.failed, error.javaClass.simpleName, error.message.orEmpty())
            Log.e(TAG, "Refresh-rate override failed", error)
        }
    }

    private companion object {
        const val TAG = "Unlock144BS"
        const val ACTION_OVERRIDE_GAME_REFRESH_RATE =
            "com.xiaomi.joyose.OVERRIDE_GAME_FRESHRATE"
        const val EXTRA_PACKAGE_NAME = "override_pkg_name"
        const val EXTRA_REFRESH_RATE = "override_freshrate"
        const val POWER_KEEPER_PACKAGE = "com.miui.powerkeeper"
        const val BRAWL_STARS_PACKAGE = "com.supercell.brawlstars"
        const val TARGET_REFRESH_RATE = 144
    }
}
