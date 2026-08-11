package com.zziirt.ztv.boot

import android.app.ActivityOptions
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.zziirt.ztv.MainActivity

object BootLaunchScheduler {
    const val EXTRA_BOOT_AUTOSTART = "com.zziirt.ztv.extra.BOOT_AUTOSTART"

    fun launch(context: Context) {
        scheduleFallback(context)
        runCatching { context.startActivity(launchIntent(context)) }
            .onFailure { error -> Log.w(TAG, "Immediate boot launch was blocked", error) }
    }

    fun cancel(context: Context) {
        val pendingIntent = findPendingIntent(context) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun scheduleFallback(context: Context) {
        val triggerAt = SystemClock.elapsedRealtime() + FALLBACK_DELAY_MILLIS
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAt,
            createPendingIntent(context),
        )
    }

    private fun launchIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_BOOT_AUTOSTART
            putExtra(EXTRA_BOOT_AUTOSTART, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

    private fun createPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            launchIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
            activityOptions(),
        )

    private fun findPendingIntent(context: Context): PendingIntent? =
        PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            launchIntent(context),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
            activityOptions(),
        )

    private fun activityOptions() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic()
                .setPendingIntentCreatorBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                )
                .toBundle()
        } else {
            null
        }

    private const val ACTION_BOOT_AUTOSTART = "com.zziirt.ztv.action.BOOT_AUTOSTART"
    private const val REQUEST_CODE = 1401
    private const val FALLBACK_DELAY_MILLIS = 15_000L
    private const val TAG = "ZTVBootLaunch"
}
