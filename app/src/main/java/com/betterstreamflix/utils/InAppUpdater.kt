package com.betterstreamflix.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.betterstreamflix.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlin.math.max

object InAppUpdater {

    private const val GITHUB_OWNER = "dskja"
    private const val GITHUB_REPO = "BetterStreamflix"

    private data class Version(val name: String) : Comparable<Version> {
        override operator fun compareTo(other: Version): Int {
            val thisParts = this.name.split(".").toTypedArray()
            val thatParts = other.name.split(".").toTypedArray()
            for (i in 0 until max(thisParts.size, thatParts.size)) {
                val thisPart = thisParts.getOrNull(i)?.toIntOrNull() ?: 0
                val thatPart = thatParts.getOrNull(i)?.toIntOrNull() ?: 0
                if (thisPart < thatPart) return -1
                if (thisPart > thatPart) return 1
            }
            return 0
        }
    }

    suspend fun getReleaseUpdate(): GitHub.Release? {
        val latestRelease = GitHub.Releases.getLatestRelease(GITHUB_OWNER, GITHUB_REPO)
        val currentVersion = BuildConfig.VERSION_NAME

        if (Version(latestRelease.tagName.substringAfter("v")) > Version(currentVersion)) {
            return latestRelease
        }
        return null
    }

    suspend fun getNewReleases(): List<GitHub.Release> {
        val releases = GitHub.Releases.getReleases(GITHUB_OWNER, GITHUB_REPO)
        val currentVersion = BuildConfig.VERSION_NAME

        val newReleases = releases
            .filter { Version(it.tagName.substringAfter("v")) > Version(currentVersion) }

        return newReleases
    }

    suspend fun downloadApk(
        context: Context,
        asset: GitHub.Release.Asset,
        onProgress: (Float) -> Unit = {},
    ): File {
        context.cacheDir.listFiles()
            ?.filter { it.extension == "apk" }
            ?.forEach { it.deleteOnExit() }

        val apk = withContext(Dispatchers.IO) {
            File.createTempFile(
                "${File(asset.name).nameWithoutExtension}-",
                ".${File(asset.name).extension}",
                context.cacheDir,
            )
        }

        try {
            withContext(Dispatchers.IO) {
                val connection = URL(asset.browserDownloadUrl).openConnection()
                connection.connectTimeout = 30000
                connection.readTimeout = 30000
                val totalSize = connection.contentLengthLong
                connection.getInputStream().use { input ->
                    FileOutputStream(apk).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (totalSize > 0) {
                                onProgress(totalRead.toFloat() / totalSize)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("InAppUpdater", "Download failed: ${asset.name}", e)
            apk.delete()
            throw e
        }

        return apk
    }

    fun installApk(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).also { intent ->
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            val filePath = uri.path ?: run {
                android.util.Log.e("InAppUpdater", "URI path is null: $uri")
                return
            }
            intent.data = FileProvider.getUriForFile(
                context,
                BuildConfig.APPLICATION_ID + ".provider",
                File(filePath)
            )
        }
        context.startActivity(intent)
    }
}