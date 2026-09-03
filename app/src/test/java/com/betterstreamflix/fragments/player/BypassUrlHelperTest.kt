package com.betterstreamflix.fragments.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.betterstreamflix.utils.UserPreferences

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BypassUrlHelperTest {

    @Before
    fun setUp() {
        UserPreferences.setup(RuntimeEnvironment.getApplication())
        UserPreferences.serienstreamDomain = "s.to"
    }

    @Test
    fun detectsSerienStreamHost() {
        assertTrue(BypassUrlHelper.isSerienStreamBypassUrl("https://serienstream.to/serie/foo"))
        assertTrue(BypassUrlHelper.isSerienStreamBypassUrl("https://s.to/serie/foo"))
        assertFalse(BypassUrlHelper.isSerienStreamBypassUrl("https://example.com/serie/foo"))
    }
}
