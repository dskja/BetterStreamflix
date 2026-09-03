package com.betterstreamflix.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ParentalPinLogicTest {

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        UserPreferences.setup(context)
        UserPreferences.enableTmdb = true
        UserPreferences.parentalControlPin = ""
        UserPreferences.parentalControlAdminPin = ""
        UserPreferences.parentalControlMaxAge = null
        UserPreferences.unlockParentalControls()
    }

    @Test
    fun setParentalPin_rejectsShortPin() {
        assertEquals("TOO_SHORT", ParentalPinLogic.setParentalPin("12"))
        assertTrue(UserPreferences.parentalControlPin.isBlank())
    }

    @Test
    fun setParentalPin_savesValidPin() {
        assertNull(ParentalPinLogic.setParentalPin("1234"))
        assertEquals("1234", UserPreferences.parentalControlPin)
    }

    @Test
    fun verifyCurrentPin_requiresMatch() {
        ParentalPinLogic.setParentalPin("4321")
        assertEquals("INVALID", ParentalPinLogic.verifyCurrentPin("0000"))
        assertNull(ParentalPinLogic.verifyCurrentPin("4321"))
    }

    @Test
    fun setMaxAge_requiresPinFirst() {
        assertEquals("SET_PIN_FIRST", ParentalPinLogic.setMaxAge("13+"))
        ParentalPinLogic.setParentalPin("9999")
        assertNull(ParentalPinLogic.setMaxAge("13+"))
        assertEquals(13, UserPreferences.parentalControlMaxAge)
    }

    @Test
    fun isLocked_falseByDefault() {
        assertFalse(ParentalPinLogic.isLocked())
    }

    @Test
    fun setParentalPin_blankClearsExisting() {
        ParentalPinLogic.setParentalPin("5555")
        ParentalPinLogic.setMaxAge("16+")
        assertNull(ParentalPinLogic.setParentalPin(""))
        assertTrue(UserPreferences.parentalControlPin.isBlank())
        assertEquals(null, UserPreferences.parentalControlMaxAge)
    }

    @Test
    fun setAdminPin_andVerify() {
        assertEquals("NO_ADMIN", ParentalPinLogic.verifyAdminPin("1234"))
        assertNull(ParentalPinLogic.setAdminPin("2468"))
        assertEquals("INVALID_ADMIN", ParentalPinLogic.verifyAdminPin("0000"))
        assertNull(ParentalPinLogic.verifyAdminPin("2468"))
    }

    @Test
    fun setMaxAge_requiresTmdb() {
        ParentalPinLogic.setParentalPin("1111")
        UserPreferences.enableTmdb = false
        assertEquals("REQUIRES_TMDB", ParentalPinLogic.setMaxAge("13+"))
    }
}
