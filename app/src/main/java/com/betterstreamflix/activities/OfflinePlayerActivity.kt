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
import androidx.media3.ui.PlayerView
import com.betterstreamflix.download.Media3OfflineDownloads
import com.betterstreamflix.download.OfflineMediaPaths
import com.betterstreamflix.utils.Constants
import com.betterstreamflix.utils.ThemeManager

@UnstableApi
class OfflinePlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeManager.mobileThemeRes(com.betterstreamflix.utils.UserPreferences.selectedTheme))
        super.onCreate(savedInstanceState)

        val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID)
            ?: OfflineMediaPaths.parseDownloadId(intent.getStringExtra(EXTRA_FILE_PATH).orEmpty())
        if (downloadId.isNullOrBlank()) {
            finish()
            return
        }

        val downloadManager = Media3OfflineDownloads.managerOrNull(this)
        val cache = Media3OfflineDownloads.cacheOrNull(this)
        val download = downloadManager?.downloadIndex?.getDownload(downloadId)
        if (downloadManager == null || cache == null || download == null) {
            finish()
            return
        }

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

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(Constants.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpFactory)
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
