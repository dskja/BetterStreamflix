package com.betterstreamflix.activities

import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.betterstreamflix.download.Media3OfflineDownloads
import com.betterstreamflix.download.OfflineMediaPaths
import com.betterstreamflix.utils.AppConfig
import com.betterstreamflix.utils.Constants
import com.betterstreamflix.utils.ThemeManager
import com.betterstreamflix.utils.UserPreferences
import java.io.File

@UnstableApi
class OfflinePlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val themeRes = if (AppConfig.isTv) {
            ThemeManager.tvThemeRes(UserPreferences.selectedTheme)
        } else {
            ThemeManager.mobileThemeRes(UserPreferences.selectedTheme)
        }
        setTheme(themeRes)
        super.onCreate(savedInstanceState)

        val filePath = intent.getStringExtra(EXTRA_FILE_PATH).orEmpty()
        val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID)
            ?: OfflineMediaPaths.parseDownloadId(filePath)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val playerView = PlayerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            useController = true
        }
        setContentView(
            FrameLayout(this).apply {
                addView(playerView)
            },
        )

        if (!downloadId.isNullOrBlank()) {
            if (!prepareMedia3(downloadId, playerView)) {
                finish()
            }
            return
        }

        if (filePath.isNotBlank() && prepareProgressive(filePath, playerView)) {
            return
        }
        finish()
    }

    private fun prepareMedia3(downloadId: String, playerView: PlayerView): Boolean {
        val downloadManager = Media3OfflineDownloads.managerOrNull(this) ?: return false
        val cache = Media3OfflineDownloads.cacheOrNull(this) ?: return false
        val download = downloadManager.downloadIndex.getDownload(downloadId) ?: return false

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(Constants.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpFactory)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val dataSourceFactory = DefaultDataSource.Factory(this, cacheDataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .also { exoPlayer ->
                playerView.player = exoPlayer
                exoPlayer.setMediaItem(MediaItem.fromUri(download.request.uri))
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
        return true
    }

    private fun prepareProgressive(filePath: String, playerView: PlayerView): Boolean {
        val file = File(filePath)
        if (!file.exists() || file.length() <= 0L) return false
        val dataSourceFactory = DefaultDataSource.Factory(this)
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(android.net.Uri.fromFile(file)))
        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            playerView.player = exoPlayer
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
        return true
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_DOWNLOAD_ID = "download_id"
        const val EXTRA_FILE_PATH = "file_path"
    }
}
