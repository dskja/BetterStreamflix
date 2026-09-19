package com.dskja.betterstreamflix.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentalMobileDesignTest {

    @Test
    fun accentFromKeyDefaultsToCrimson() {
        assertEquals(
            ExperimentalMobileDesign.Accent.CRIMSON,
            ExperimentalMobileDesign.Accent.fromKey(null),
        )
        assertEquals(
            ExperimentalMobileDesign.Accent.CRIMSON,
            ExperimentalMobileDesign.Accent.fromKey("unknown"),
        )
    }

    @Test
    fun accentFromKeyParsesPresets() {
        assertEquals(ExperimentalMobileDesign.Accent.EMBER, ExperimentalMobileDesign.Accent.fromKey("ember"))
        assertEquals(ExperimentalMobileDesign.Accent.AURORA, ExperimentalMobileDesign.Accent.fromKey("AURORA"))
        assertEquals(ExperimentalMobileDesign.Accent.SLATE, ExperimentalMobileDesign.Accent.fromKey("slate"))
        assertEquals(ExperimentalMobileDesign.Accent.CRIMSON, ExperimentalMobileDesign.Accent.fromKey("crimson"))
    }

    @Test
    fun accentKeysAreStable() {
        assertEquals("crimson", ExperimentalMobileDesign.Accent.CRIMSON.key)
        assertEquals("ember", ExperimentalMobileDesign.Accent.EMBER.key)
        assertEquals("aurora", ExperimentalMobileDesign.Accent.AURORA.key)
        assertEquals("slate", ExperimentalMobileDesign.Accent.SLATE.key)
        assertTrue(ExperimentalMobileDesign.Accent.entries.size >= 4)
        assertFalse(ExperimentalMobileDesign.Accent.entries.isEmpty())
    }
}
