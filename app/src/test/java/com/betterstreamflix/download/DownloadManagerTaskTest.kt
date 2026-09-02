package com.betterstreamflix.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadManagerTaskTest {

    @Test
    fun progressFraction_usesFileSize() {
        val task = DownloadManager.DownloadTask(
            id = "1",
            videoId = "v",
            title = "t",
            providerName = "p",
            url = "https://example.com/a.mp4",
            filePath = "/tmp/a.mp4",
            fileSize = 1000,
            downloadedBytes = 250,
            status = DownloadManager.DownloadStatus.DOWNLOADING,
        )
        assertEquals(0.25f, task.progressFraction, 0.001f)
        assertTrue(task.isActive)
        assertFalse(task.canOpen)
    }

    @Test
    fun completed_withoutSize_isFullProgress() {
        val task = DownloadManager.DownloadTask(
            id = "1",
            videoId = "v",
            title = "t",
            providerName = "p",
            url = "https://example.com/a.mp4",
            filePath = "/tmp/a.mp4",
            status = DownloadManager.DownloadStatus.COMPLETED,
        )
        assertEquals(1f, task.progressFraction, 0.001f)
        assertTrue(task.canOpen)
    }
}
