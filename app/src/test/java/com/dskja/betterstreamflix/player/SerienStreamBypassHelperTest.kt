package com.dskja.betterstreamflix.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SerienStreamBypassHelperTest {

    @Test
    fun rejectsEmptyOrSessionOnlyCookies() {
        assertFalse(SerienStreamBypassHelper.looksLikeBypassSolved(""))
        assertFalse(SerienStreamBypassHelper.looksLikeBypassSolved("PHPSESSID=abc; path=/"))
    }

    @Test
    fun acceptsClearanceStyleCookies() {
        assertTrue(SerienStreamBypassHelper.looksLikeBypassSolved("cf_clearance=xyz; path=/"))
        assertTrue(SerienStreamBypassHelper.looksLikeBypassSolved("__ddg1=1; other=2"))
    }
}
