package com.betterstreamflix.download

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadStorageManagerFormatTest {

    @Test
    fun formatSizeMb_floorsToMegabytes() {
        assertEquals(0L, DownloadStorageManager.formatSizeMb(500_000))
        assertEquals(1L, DownloadStorageManager.formatSizeMb(1_048_576))
        assertEquals(10L, DownloadStorageManager.formatSizeMb(10L * 1024L * 1024L))
    }
}
