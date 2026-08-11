package com.zziirt.ztv.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.zziirt.ztv.MainActivity
import com.zziirt.ztv.preferences.PreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            return
        }

        val enabled = runBlocking {
            PreferencesRepository(context.applicationContext).settings.first().bootAutostart
        }

        if (enabled) {
            runCatching {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(launchIntent)
            }.onFailure {
                Log.w(TAG, "Boot autostart was blocked by the device", it)
            }
        }
    }

    private companion object {
        const val TAG = "ZTVBootReceiver"
    }
}
