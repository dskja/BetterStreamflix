package com.dskja.betterstreamflix.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSubsystemTest {

    @Test
    fun storageLocation_fromKey_defaultsToInternal() {
        assertEquals(DownloadStorageLocation.INTERNAL, DownloadStorageLocation.fromKey(null))
        assertEquals(DownloadStorageLocation.INTERNAL, DownloadStorageLocation.fromKey(""))
        assertEquals(DownloadStorageLocation.INTERNAL, DownloadStorageLocation.fromKey("nope"))
    }

    @Test
    fun storageLocation_fromKey_parsesKnownValues() {
        assertEquals(DownloadStorageLocation.INTERNAL, DownloadStorageLocation.fromKey("internal"))
        assertEquals(DownloadStorageLocation.APP_EXTERNAL, DownloadStorageLocation.fromKey("APP_EXTERNAL"))
        assertEquals(DownloadStorageLocation.PUBLIC_MOVIES, DownloadStorageLocation.fromKey("public_movies"))
    }

    @Test
    fun qualityPreset_fromKey_defaultsToBest() {
        assertEquals(DownloadQualityPreset.BEST, DownloadQualityPreset.fromKey(null))
        assertEquals(DownloadQualityPreset.DATA_SAVER, DownloadQualityPreset.fromKey("data_saver"))
        assertEquals(DownloadQualityPreset.ASK, DownloadQualityPreset.fromKey("ASK"))
    }

    @Test
    fun itemState_isActive_coversQueueAndDownload() {
        assertTrue(DownloadItemState.QUEUED.isActive)
        assertTrue(DownloadItemState.DOWNLOADING.isActive)
        assertTrue(DownloadItemState.PAUSED.isActive)
        assertFalse(DownloadItemState.COMPLETED.isActive)
        assertFalse(DownloadItemState.FAILED.isActive)
    }

    @Test
    fun itemState_fromKey_defaultsToFailed() {
        assertEquals(DownloadItemState.FAILED, DownloadItemState.fromKey(null))
        assertEquals(DownloadItemState.COMPLETED, DownloadItemState.fromKey("completed"))
    }
}
