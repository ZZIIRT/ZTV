package com.zziirt.ztv.boot

internal object AccessibilityBootPolicy {
    fun shouldLaunch(elapsedRealtimeMillis: Long): Boolean =
        elapsedRealtimeMillis in 0L..BOOT_WINDOW_MILLIS

    private const val BOOT_WINDOW_MILLIS = 10 * 60_000L
}
