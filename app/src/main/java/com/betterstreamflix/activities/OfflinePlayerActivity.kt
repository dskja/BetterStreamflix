package com.betterstreamflix.activities

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.betterstreamflix.R
import com.betterstreamflix.compose.screens.PlayerControlsOverlay
import com.betterstreamflix.compose.theme.BetterStreamflixTheme
import com.betterstreamflix.download.Media3OfflineDownloads
import com.betterstreamflix.download.OfflineMediaPaths
import com.betterstreamflix.fragments.player.PlayerPlaybackController
import com.betterstreamflix.utils.AppConfig
import com.betterstreamflix.utils.Constants
import com.betterstreamflix.utils.ThemeManager
import com.betterstreamflix.utils.UserPreferences
import java.io.File

@UnstableApi
class OfflinePlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private val playbackController = PlayerPlaybackController()
    private var composeOverlay: ComposeView? = null
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            val exo = player ?: return
            if (exo.isPlaying) {
                syncPlaybackPosition(exo)
            }
            progressHandler.postDelayed(this, POSITION_POLL_MS)
        }
    }

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

        val root = FrameLayout(this)
        val playerView = PlayerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            useController = true
            controllerShowTimeoutMs = 4_000
        }
        val overlay = ComposeView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            )
            elevation = 12f
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        composeOverlay = overlay
        root.addView(playerView)
        root.addView(overlay)
        setContentView(root)

        if (!downloadId.isNullOrBlank()) {
            if (!prepareMedia3(downloadId, playerView)) {
                finish()
            } else {
                bindOverlay(playerView)
            }
            return
        }

        if (filePath.isNotBlank() && prepareProgressive(filePath, playerView)) {
            bindOverlay(playerView)
            return
        }
        finish()
    }

    private fun bindOverlay(playerView: PlayerView) {
        val exo = player ?: return
        val overlay = composeOverlay ?: return
        val title = intent.getStringExtra(EXTRA_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.player_title_offline)
        playbackController.setMetadata(title)
        com.betterstreamflix.fragments.player.hideLegacyPlayerChrome(playerView)
        overlay.setContent {
            BetterStreamflixTheme {
                val state by playbackController.state.collectAsStateWithLifecycle()
                PlayerControlsOverlay(
                    state = state,
                    onPlayPause = {
                        if (exo.isPlaying) exo.pause() else exo.play()
                    },
                    onSeek = { positionMs ->
                        exo.seekTo(positionMs)
                        playbackController.updatePosition(
                            positionMs,
                            exo.duration.coerceAtLeast(0L),
                        )
                    },
                    onBack = { finish() },
                )
            }
        }
        playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                overlay.visibility = visibility
                if (visibility == android.view.View.VISIBLE) {
                    com.betterstreamflix.fragments.player.hideLegacyPlayerChrome(playerView)
                }
            },
        )
        playerView.showController()
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playbackController.setPlaying(isPlaying)
                syncPlaybackPosition(exo)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                playbackController.setBuffering(playbackState == Player.STATE_BUFFERING)
                syncPlaybackPosition(exo)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                syncPlaybackPosition(exo)
            }
        })
        playbackController.setPlaying(exo.isPlaying)
        syncPlaybackPosition(exo)
        progressHandler.removeCallbacks(progressRunnable)
        progressHandler.post(progressRunnable)
    }

    private fun syncPlaybackPosition(exo: ExoPlayer) {
        playbackController.updatePosition(
            exo.currentPosition,
            exo.duration.coerceAtLeast(0L),
        )
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

    override fun onStart() {
        super.onStart()
        if (player != null) {
            progressHandler.removeCallbacks(progressRunnable)
            progressHandler.post(progressRunnable)
        }
    }

    override fun onStop() {
        super.onStop()
        progressHandler.removeCallbacks(progressRunnable)
        player?.pause()
    }

    override fun onDestroy() {
        progressHandler.removeCallbacks(progressRunnable)
        player?.release()
        player = null
        composeOverlay = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_DOWNLOAD_ID = "download_id"
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_TITLE = "title"
        private const val POSITION_POLL_MS = 500L
    }
}
