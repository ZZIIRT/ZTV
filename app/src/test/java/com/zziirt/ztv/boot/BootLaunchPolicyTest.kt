package com.zziirt.ztv.boot

import com.zziirt.ztv.preferences.AppSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootLaunchPolicyTest {
    @Test
    fun `accessibility fallback launches only shortly after device boot`() {
        assertTrue(AccessibilityBootPolicy.shouldLaunch(0L))
        assertTrue(AccessibilityBootPolicy.shouldLaunch(9 * 60_000L))
        assertFalse(AccessibilityBootPolicy.shouldLaunch(10 * 60_000L + 1L))
    }

    @Test
    fun `boot autostart is enabled by default`() {
        assertTrue(AppSettings().bootAutostart)
    }

    @Test
    fun `accepts Android and OEM boot broadcasts`() {
        assertTrue(BootActions.isSupported(BootActions.BOOT_COMPLETED))
        assertTrue(BootActions.isSupported(BootActions.QUICKBOOT_POWERON))
        assertTrue(BootActions.isSupported(BootActions.HTC_QUICKBOOT_POWERON))
    }

    @Test
    fun `rejects unrelated and locked boot broadcasts`() {
        assertFalse(BootActions.isSupported("android.intent.action.SCREEN_ON"))
        assertFalse(BootActions.isSupported("android.intent.action.LOCKED_BOOT_COMPLETED"))
        assertFalse(BootActions.isSupported(null))
    }
}
