package com.zziirt.ztv.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.zziirt.ztv.boot.AccessibilityBootPolicy
import com.zziirt.ztv.boot.BootLaunchScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ZtvAutostartAccessibilityService : AccessibilityService() {
    private val exceptionHandler = CoroutineExceptionHandler { _, error ->
        Log.e(TAG, "Autostart service failed", error)
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + exceptionHandler)
    private var launchJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (!AccessibilityBootPolicy.shouldLaunch(SystemClock.elapsedRealtime())) return

        launchJob?.cancel()
        launchJob = serviceScope.launch {
            for (retryInterval in AccessibilityBootPolicy.retryIntervalsMillis) {
                delay(retryInterval)
                if (BootLaunchScheduler.wasActivityStarted()) return@launch
                BootLaunchScheduler.launchNow(this@ZtvAutostartAccessibilityService)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "ZTVAccessibilityBoot"
    }
}
