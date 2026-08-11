package com.zziirt.ztv.boot

import com.zziirt.ztv.preferences.AppSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootLaunchPolicyTest {
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
