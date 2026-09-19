package com.dskja.betterstreamflix.platform.playerbackend

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerBackendSelectorTest {
    @Test
    fun candidatePackagesPreferMpvFirst() {
        assertEquals("is.xyz.mpv", ExternalMpvBackend.CANDIDATE_PACKAGES.first())
        assertEquals(3, ExternalMpvBackend.CANDIDATE_PACKAGES.size)
    }
}
