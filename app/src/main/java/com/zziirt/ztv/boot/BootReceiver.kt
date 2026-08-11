package com.zziirt.ztv.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.zziirt.ztv.preferences.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!BootActions.isSupported(intent.action)) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val enabled = PreferencesRepository(appContext).settings.first().bootAutostart
                if (enabled) BootLaunchScheduler.launch(appContext)
            } catch (error: Exception) {
                Log.w(TAG, "Unable to prepare boot autostart", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "ZTVBootReceiver"
    }
}

internal object BootActions {
    const val BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED"
    const val QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    const val HTC_QUICKBOOT_POWERON = "com.htc.intent.action.QUICKBOOT_POWERON"

    private val supportedActions = setOf(
        BOOT_COMPLETED,
        QUICKBOOT_POWERON,
        HTC_QUICKBOOT_POWERON,
    )

    fun isSupported(action: String?): Boolean = action in supportedActions
}
