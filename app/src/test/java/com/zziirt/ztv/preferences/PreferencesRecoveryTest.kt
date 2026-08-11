package com.zziirt.ztv.preferences

import org.junit.Assert.assertTrue
import org.junit.Test

class PreferencesRecoveryTest {
    @Test
    fun `corrupt preferences are replaced with an empty store`() {
        assertTrue(PreferencesRecovery.replacement().asMap().isEmpty())
    }
}
