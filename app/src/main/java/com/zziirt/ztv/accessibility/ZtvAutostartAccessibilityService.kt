package com.zziirt.ztv.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.zziirt.ztv.boot.AccessibilityBootPolicy
import com.zziirt.ztv.boot.BootLaunchScheduler
import com.zziirt.ztv.preferences.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ZtvAutostartAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var launchJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (!AccessibilityBootPolicy.shouldLaunch(SystemClock.elapsedRealtime())) return

        launchJob?.cancel()
        launchJob = serviceScope.launch {
            delay(LAUNCH_DELAY_MILLIS)
            val bootAutostartEnabled = withContext(Dispatchers.IO) {
                PreferencesRepository(applicationContext).settings.first().bootAutostart
            }
            if (bootAutostartEnabled) {
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
        const val LAUNCH_DELAY_MILLIS = 5_000L
    }
}
