package com.betterstreamflix.fragments.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.SubtitleView
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.betterstreamflix.R
import com.betterstreamflix.activities.tools.BypassWebViewActivity
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.download.DownloadActionHelper
import com.betterstreamflix.databinding.ContentExoControllerMobileBinding
import com.betterstreamflix.databinding.FragmentPlayerMobileBinding
import com.betterstreamflix.models.Episode
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.Season
import com.betterstreamflix.models.TvShow
import com.betterstreamflix.models.Video
import com.betterstreamflix.models.WatchItem
import com.betterstreamflix.notifications.NotificationPreferences
import com.betterstreamflix.sync.CloudSyncHooks
import com.betterstreamflix.ui.PlayerMobileView
import com.betterstreamflix.utils.MediaServer
import com.betterstreamflix.utils.SubtitleOffsetRenderersFactory
import com.betterstreamflix.utils.UserPreferences
import com.betterstreamflix.utils.UserDataCache
import com.betterstreamflix.utils.dp
import com.betterstreamflix.utils.getFileName
import com.betterstreamflix.utils.next
import com.betterstreamflix.utils.plus
import com.betterstreamflix.utils.setMediaServerId
import com.betterstreamflix.utils.setMediaServers
import com.betterstreamflix.utils.toSubtitleMimeType
import com.betterstreamflix.utils.viewModelsFactory
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import androidx.core.net.toUri
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.betterstreamflix.fragments.player.settings.PlayerSettingsView
import com.betterstreamflix.fragments.player.WatchProgressHelper.hasFinished
import com.betterstreamflix.fragments.player.WatchProgressHelper.hasReallyFinished
import com.betterstreamflix.fragments.player.WatchProgressHelper.hasStarted
import java.util.Base64 
import java.io.File
import java.io.FileOutputStream
import android.webkit.CookieManager
import androidx.core.content.FileProvider
import androidx.navigation.NavOptions
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.betterstreamflix.utils.DnsResolver
import com.betterstreamflix.utils.NetworkClient
import com.betterstreamflix.utils.EpisodeManager
import com.betterstreamflix.utils.PlayerGestureHelper
import com.betterstreamflix.utils.UserDataCache.toEpisode
import com.betterstreamflix.utils.UserDataCache.toMovie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.internal.userAgent
import java.util.Locale
import com.betterstreamflix.extractors.TokenManager

class PlayerMobileFragment : Fragment() {

    private var _binding: FragmentPlayerMobileBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("Binding is null. Fragment view has been destroyed.")
    private var isSetupDone = false

    private val PlayerControlView.binding
        get() = ContentExoControllerMobileBinding.bind(this.findViewById(R.id.cl_exo_controller))

    private val args by navArgs<PlayerMobileFragmentArgs>()
    private val database by lazy { AppDatabase.getInstance(requireContext()) }
    private val viewModel by viewModelsFactory { PlayerViewModel(args.videoType, args.id) }
    private val playbackController by viewModels<PlayerPlaybackController>()

    private lateinit var player: ExoPlayer
    private lateinit var nextEpisodeOverlayManager: NextEpisodeOverlayManager
    private lateinit var httpDataSource: HttpDataSource.Factory
    private lateinit var dataSourceFactory: DataSource.Factory
    private var mediaSession: MediaSession? = null
    private lateinit var progressHandler: android.os.Handler
    private lateinit var progressRunnable: Runnable
    private lateinit var gestureHelper: PlayerGestureHelper

    private var servers = listOf<Video.Server>()
    private var zoomToast: Toast? = null
    private val sleepTimer = com.betterstreamflix.player.advanced.SleepTimer()

    private var currentVideo: Video? = null
    private var currentServer: Video.Server? = null
    private var isIgnoringPip = false
    private var waitingForBypass = false
    private var bypassDone = false

    private val bypassWebViewLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val cookies =
                result.data?.getStringExtra(BypassWebViewActivity.EXTRA_COOKIE_HEADER)?.trim()

            if (result.resultCode != android.app.Activity.RESULT_OK || cookies.isNullOrBlank()) {
                waitingForBypass = false
                return@registerForActivityResult
            }

            val bypassUrl = servers.firstOrNull { BypassUrlHelper.isSerienStreamBypassUrl(it.id) }?.id
            if (bypassUrl.isNullOrBlank()) {
                waitingForBypass = false
                return@registerForActivityResult
            }

            BypassUrlHelper.applyBypassCookies(bypassUrl, cookies)
            waitingForBypass = false
            bypassDone = true

            lifecycleScope.launch {
                delay(300)
                viewModel.reloadServersAfterBypass()
            }
        }

    private val chooserReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val clickedComponent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent?.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT, android.content.ComponentName::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent?.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT)
                }
                Log.i("ExternalPlayer", "Mobile - App selezionata: ${clickedComponent?.packageName ?: "Sconosciuta"}")
            }
        }
    }

    private val pickLocalSubtitle = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        requireContext().contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )

        val fileName = uri.getFileName(requireContext()) ?: uri.toString()

        val currentPosition = player.currentPosition
        val currentSubtitleConfigurations =
            player.currentMediaItem?.localConfiguration?.subtitleConfigurations?.map {
                MediaItem.SubtitleConfiguration.Builder(it.uri)
                    .setMimeType(it.mimeType)
                    .setLabel(it.label)
                    .setLanguage(it.language)
                    .setSelectionFlags(0)
                    .build()
            } ?: listOf()
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(player.currentMediaItem?.localConfiguration?.uri)
                .setMimeType(player.currentMediaItem?.localConfiguration?.mimeType)
                .setSubtitleConfigurations(
                    currentSubtitleConfigurations
                            + MediaItem.SubtitleConfiguration.Builder(uri)
                        .setMimeType(fileName.toSubtitleMimeType())
                        .setLabel(fileName)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                )
                .setMediaMetadata(player.mediaMetadata)
                .build()
        )
        player.seekTo(currentPosition)
        player.play()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerMobileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        if (!isSetupDone) {
            requireActivity().requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            
            val window = requireActivity().window
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            
            val insetsController = WindowInsetsControllerCompat(window, window.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            isSetupDone = true
        }
        isIgnoringPip = false
        if (::player.isInitialized) {
            binding.pvPlayer.useController = true
            // Resume playback after returning from bypass or any pause
            if (!player.isPlaying) {
                player.play()
            }
        }
        
        try {
            val filter = IntentFilter("ACTION_PLAYER_CHOSEN")
            ContextCompat.registerReceiver(
                requireContext(),
                chooserReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            android.util.Log.w("PlayerMobileFragment", "Failed to register chooser receiver", e)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializePlayer(false)
        initializeVideo()
        gestureHelper = PlayerGestureHelper(
            requireContext(), 
            binding.pvPlayer, 
            binding.llBrightness, 
            binding.pbBrightness, 
            binding.tvBrightnessPercentage,
            binding.llVolume, 
            binding.pbVolume, 
            binding.tvVolumePercentage
        )

        // Stato Video
        viewLifecycleOwner.lifecycleScope.launch { 
            viewModel.state.flowWithLifecycle(lifecycle, Lifecycle.State.CREATED).collect { state ->
                when (state) {
                    PlayerViewModel.State.LoadingServers -> {}
                    is PlayerViewModel.State.SuccessLoadingServers -> {
                        servers = state.servers
                        val sToServer = servers.firstOrNull {
                            BypassUrlHelper.isSerienStreamBypassUrl(it.id)
                        }

                        if (sToServer != null && !waitingForBypass && !bypassDone) {
                            val bypassUrl = BypassUrlHelper.buildSerienStreamBypassUrl(args.videoType)
                            if (bypassUrl.isNullOrBlank()) {
                                waitingForBypass = false
                                Toast.makeText(requireContext(), "Unable to open s.to bypass page.", Toast.LENGTH_SHORT).show()
                                return@collect
                            }

                            waitingForBypass = true
                            bypassWebViewLauncher.launch(
                                Intent(requireContext(), BypassWebViewActivity::class.java)
                                    .putExtra(BypassWebViewActivity.EXTRA_URL, bypassUrl)
                            )
                        } else {
                            val providerName = UserPreferences.currentProvider?.name ?: ""
                            val isTmdb = providerName.contains("TMDb", ignoreCase = true)
                            val isAD = providerName.contains("AfterDark", ignoreCase = true)

                            if (servers.isEmpty()) {
                                val message = if (isTmdb || isAD) {
                                    val langCode = providerName.substringAfter("(").substringBefore(")")
                                    val locale = Locale.forLanguageTag(langCode)
                                    val langDisplayName = locale.getDisplayLanguage(Locale.getDefault())
                                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

                                    if (isTmdb) getString(R.string.player_not_available_lang_message, langDisplayName)
                                    else getString(R.string.player_retry_later_message)
                                } else {
                                    "No servers found for this content."
                                }
                                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                                findNavController().navigateUp()
                                return@collect
                            }

                            player.playlistMetadata = MediaMetadata.Builder()
                                .setTitle(state.toString())
                                .setMediaServers(state.servers.map {
                                    MediaServer(
                                        id = it.id,
                                        name = it.name,
                                    )
                                })
                                .build()
                            binding.settings.setOnServerSelectedListener { server ->
                                val matchedServer = state.servers.find { server.id == it.id }
                                if (matchedServer != null) {
                                    viewModel.getVideo(matchedServer)
                                }
                            }
                            val preferredServer = state.servers.firstOrNull {
                                it.name.equals(args.preferredServerName, ignoreCase = true)
                            } ?: state.servers.firstOrNull()
                            if (preferredServer != null) {
                                viewModel.getVideo(preferredServer)
                            }
                        }

                    }

                    is PlayerViewModel.State.FailedLoadingServers -> {
                        Toast.makeText(
                            requireContext(),
                            state.error.message ?: "",
                            Toast.LENGTH_LONG
                        ).show()
                        findNavController().navigateUp()
                    }

                    is PlayerViewModel.State.LoadingVideo -> {
                        player.setMediaItem(
                            MediaItem.Builder()
                                .setUri("".toUri())
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setMediaServerId(state.server.id)
                                        .build()
                                )
                                .build()
                        )
                    }

                    is PlayerViewModel.State.SuccessLoadingVideo -> {
                        PlayerSettingsView.Settings.ExtraBuffering.init(state.video.extraBuffering)
                        PlayerSettingsView.Settings.SoftwareDecoder.init(false)
                        displayVideo(state.video, state.server)
                    }

                    is PlayerViewModel.State.FailedLoadingVideo -> {
                        val nextServer = servers.getOrNull(servers.indexOf(state.server) + 1)
                        if (nextServer != null) {
                            viewModel.getVideo(nextServer)
                        } else {
                            val providerName = UserPreferences.currentProvider?.name ?: ""
                            val isTmdb = providerName.contains("TMDb", ignoreCase = true)
                            val isAD = providerName.contains("AfterDark", ignoreCase = true)

                            val message = if (isTmdb || isAD) {
                                val langCode = providerName.substringAfter("(").substringBefore(")")
                                val locale = Locale.forLanguageTag(langCode)
                                val langDisplayName = locale.getDisplayLanguage(Locale.getDefault())
                                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                                if (isTmdb) getString(R.string.player_not_available_lang_message, langDisplayName)
                                else getString(R.string.player_retry_later_message)
                            } else {
                                "All servers failed to load the video."
                            }
                            
                            Toast.makeText(
                                requireContext(),
                                message,
                                Toast.LENGTH_LONG
                            ).show()
                            findNavController().navigateUp()
                        }
                    }
                }
            }
        }

        // Stato Sottotitoli
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.subtitleState.flowWithLifecycle(lifecycle, Lifecycle.State.CREATED).collect { state ->
                when (state) {
                    PlayerViewModel.SubtitleState.Loading -> {}
                    is PlayerViewModel.SubtitleState.SuccessOpenSubtitles -> {
                        binding.settings.openSubtitles = state.subtitles
                    }
                    is PlayerViewModel.SubtitleState.FailedOpenSubtitles -> {}

                    PlayerViewModel.SubtitleState.DownloadingOpenSubtitle -> {}
                    is PlayerViewModel.SubtitleState.SuccessDownloadingOpenSubtitle -> {
                        val fileName = state.uri.getFileName(requireContext()) ?: state.uri.toString()
                        val currentPosition = player.currentPosition
                        val currentSubtitleConfigurations = player.currentMediaItem?.localConfiguration?.subtitleConfigurations?.map {
                            MediaItem.SubtitleConfiguration.Builder(it.uri)
                                .setMimeType(it.mimeType)
                                .setLabel(it.label)
                                .setLanguage(it.language)
                                .setSelectionFlags(0)
                                .build()
                        } ?: listOf()
                        player.setMediaItem(
                            MediaItem.Builder()
                                .setUri(player.currentMediaItem?.localConfiguration?.uri)
                                .setMimeType(player.currentMediaItem?.localConfiguration?.mimeType)
                                .setSubtitleConfigurations(
                                    currentSubtitleConfigurations + MediaItem.SubtitleConfiguration.Builder(state.uri)
                                        .setMimeType(fileName.toSubtitleMimeType())
                                        .setLabel(fileName)
                                        .setLanguage(state.subtitle.languageName)
                                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                        .build()
                                )
                                .setMediaMetadata(player.mediaMetadata)
                                .build()
                        )
                        UserPreferences.subtitleName = (state.subtitle.languageName ?: fileName).substringBefore(" ")
                        player.seekTo(currentPosition)
                        player.play()
                    }
                    is PlayerViewModel.SubtitleState.FailedDownloadingOpenSubtitle -> {
                        Toast.makeText(requireContext(), "${state.subtitle.subFileName}: ${state.error.message}", Toast.LENGTH_LONG).show()
                    }

                    is PlayerViewModel.SubtitleState.SuccessSubDLSubtitles -> {
                        binding.settings.subDLSubtitles = state.subtitles
                    }
                    is PlayerViewModel.SubtitleState.FailedSubDLSubtitles -> {}

                    PlayerViewModel.SubtitleState.DownloadingSubDLSubtitle -> {}
                    is PlayerViewModel.SubtitleState.SuccessDownloadingSubDLSubtitle -> {
                        val fileName = state.uri.getFileName(requireContext()) ?: state.uri.toString()
                        val currentPosition = player.currentPosition
                        val currentSubtitleConfigurations = player.currentMediaItem?.localConfiguration?.subtitleConfigurations?.map {
                            MediaItem.SubtitleConfiguration.Builder(it.uri)
                                .setMimeType(it.mimeType)
                                .setLabel(it.label)
                                .setLanguage(it.language)
                                .setSelectionFlags(0)
                                .build()
                        } ?: listOf()
                        player.setMediaItem(
                            MediaItem.Builder()
                                .setUri(player.currentMediaItem?.localConfiguration?.uri)
                                .setMimeType(player.currentMediaItem?.localConfiguration?.mimeType)
                                .setSubtitleConfigurations(
                                    currentSubtitleConfigurations + MediaItem.SubtitleConfiguration.Builder(state.uri)
                                        .setMimeType(fileName.toSubtitleMimeType())
                                        .setLabel(state.subtitle.releaseName ?: state.subtitle.name ?: fileName)
                                        .setLanguage(state.subtitle.lang ?: state.subtitle.language ?: "Unknown")
                                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                        .build()
                                )
                                .setMediaMetadata(player.mediaMetadata)
                                .build()
                        )
                        UserPreferences.subtitleName = (state.subtitle.releaseName ?: state.subtitle.name ?: fileName).substringBefore(" ")
                        player.seekTo(currentPosition)
                        player.play()
                    }
                    is PlayerViewModel.SubtitleState.FailedDownloadingSubDLSubtitle -> {
                        Toast.makeText(requireContext(), "${state.subtitle.name}: ${state.error.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.playPreviousOrNextEpisode.collect { nextEpisode ->
                    releasePlayer()
                    isSetupDone = false
                    val action = PlayerMobileFragmentDirections
                        .actionPlayerMobileFragmentSelf(
                            id = nextEpisode.id,
                            videoType = nextEpisode,
                            title = nextEpisode.tvShow.title,
                            subtitle = "S${nextEpisode.season.number} E${nextEpisode.number}  •  ${nextEpisode.title}",
                            preferredServerName = currentServer?.name,
                        )

                    nextEpisodeOverlayManager.hideOverlay()
                    findNavController().navigate(
                        action,
                        NavOptions.Builder()
                            .setPopUpTo(
                                findNavController().currentDestination?.id ?: return@collect, true
                            )
                            .setLaunchSingleTop(false) 
                            .build()
                    )
                }
            }
        }


    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        _binding?.pvPlayer?.useController = !isInPictureInPictureMode
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
    }

    fun onUserLeaveHint() {
        if (!isIgnoringPip && ::player.isInitialized && player.isPlaying) {
            enterPIPMode()
        }
    }

    override fun onStop() {
        super.onStop()
        if (::player.isInitialized && _binding != null) {
            saveWatchProgress()
            player.pause()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::nextEpisodeOverlayManager.isInitialized) {
            nextEpisodeOverlayManager.cancelPrefetch()
        }
        if (::gestureHelper.isInitialized) gestureHelper.release()
        val window = requireActivity().window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        }
        WindowCompat.getInsetsController(
            window,
            window.decorView
        ).run {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            show(WindowInsetsCompat.Type.systemBars())
        }
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        saveWatchProgress()
        releasePlayer()
        try {
            requireContext().unregisterReceiver(chooserReceiver)
        } catch (e: Exception) {
            android.util.Log.w("PlayerMobileFragment", "Failed to unregister chooser receiver", e)
        }
        _binding = null
        isSetupDone = false
    }

    fun onBackPressed(): Boolean = when {
        _binding == null -> false
        binding.pvPlayer.isManualZoomEnabled -> {
            binding.pvPlayer.exitManualZoomMode()
            true
        }
        binding.settings.isVisible -> {
            binding.settings.onBackPressed()
        }
        else -> false
    }


    private fun initializeVideo() {
        WindowCompat.getInsetsController(
            requireActivity().window,
            requireActivity().window.decorView
        ).run {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        when (val type = args.videoType) {
            is Video.Type.Episode -> {
                nextEpisodeOverlayManager.resetDismissed()
                if (EpisodeManager.listIsEmpty(type)) {
                    EpisodeManager.clearEpisodes()
                    lifecycleScope.launch(Dispatchers.IO) {
                        EpisodeManager.addEpisodesFromDb(type, database)
                        withContext(Dispatchers.Main) {
                            EpisodeManager.setCurrentEpisode(type)
                            updatePlayerHeader(type)
                            setupEpisodeNavigationButtons()
                            refreshEpisodeNavigation(type)
                        }
                    }
                } else {
                    EpisodeManager.setCurrentEpisode(type)
                    setupEpisodeNavigationButtons()
                    refreshEpisodeNavigation(type)
                }
            }
            is Video.Type.Movie -> {
                nextEpisodeOverlayManager.resetDismissed()
                EpisodeManager.clearEpisodes()
                nextEpisodeOverlayManager.hideOverlay()
            }
        }


        binding.settings.onSubtitlesClicked = {
            viewModel.getSubtitles(args.videoType)
        }
        binding.settings.setOnExtraBufferingSelectedListener {
            displayVideo(
                currentVideo ?: return@setOnExtraBufferingSelectedListener,
                currentServer ?: return@setOnExtraBufferingSelectedListener
            )
        }
        binding.settings.setOnSoftwareDecoderSelectedListener { useSoftware ->
            currentSoftwareDecoder = useSoftware
            displayVideo(
                currentVideo ?: return@setOnSoftwareDecoderSelectedListener,
                currentServer ?: return@setOnSoftwareDecoderSelectedListener
            )
        }
        binding.pvPlayer.resizeMode = UserPreferences.playerResize.resizeMode
        binding.pvPlayer.subtitleView?.apply {
            setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * UserPreferences.captionTextSize)
            setStyle(UserPreferences.captionStyle)
            setPadding(0, 0, 0, UserPreferences.captionMargin.dp(context))
        }
        setupEpisodeNavigationButtons()

        binding.pvPlayer.controller.binding.btnExoBack.setOnClickListener {
            findNavController().navigateUp()
        }

        updatePlayerHeader()

        binding.pvPlayer.controller.binding.btnExoExternalPlayer.setOnClickListener {
            Toast.makeText(
                requireContext(),
                getString(R.string.player_external_player_error_video),
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.pvPlayer.controller.binding.exoReplay.setOnClickListener {
            player.seekTo(0)
        }

        binding.pvPlayer.controller.binding.btnExoLock.setOnClickListener {
            binding.pvPlayer.controller.binding.gControlsLock.isGone = true
            binding.pvPlayer.controller.binding.btnExoUnlock.isVisible = true
        }

        binding.pvPlayer.controller.binding.btnExoUnlock.setOnClickListener {
            binding.pvPlayer.controller.binding.gControlsLock.isVisible = true
            binding.pvPlayer.controller.binding.btnExoUnlock.isGone = true
        }

        binding.pvPlayer.controller.binding.btnExoPictureInPicture.setOnClickListener {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.player_picture_in_picture_not_supported),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                enterPIPMode()
            }
        }

        binding.pvPlayer.controller.binding.btnExoAspectRatio.setOnClickListener {
            val newResize = UserPreferences.playerResize.next()
            zoomToast?.cancel()
            zoomToast = Toast.makeText(requireContext(), newResize.stringRes, Toast.LENGTH_SHORT)
            zoomToast?.show()

            UserPreferences.playerResize = newResize
            binding.pvPlayer.controllerShowTimeoutMs = binding.pvPlayer.controllerShowTimeoutMs
            updatePlayerScale()
        }

        binding.pvPlayer.controller.binding.exoSettings.setOnClickListener {
            binding.pvPlayer.controllerShowTimeoutMs = binding.pvPlayer.controllerShowTimeoutMs
            binding.settings.show()
        }
        binding.pvPlayer.controller.binding.exoSettings.setOnLongClickListener {
            showSleepTimerDialog()
            true
        }

        binding.settings.setOnLocalSubtitlesClickedListener {
            isIgnoringPip = true
            pickLocalSubtitle.launch(
                arrayOf(
                    "text/plain",
                    "text/str",
                    "application/octet-stream",
                    MimeTypes.TEXT_UNKNOWN,
                    MimeTypes.TEXT_VTT,
                    MimeTypes.TEXT_SSA,
                    MimeTypes.APPLICATION_TTML,
                    MimeTypes.APPLICATION_MP4VTT,
                    MimeTypes.APPLICATION_SUBRIP,
                )
            )
        }

        binding.settings.setOnOpenSubtitleSelectedListener { subtitle ->
            viewModel.downloadSubtitle(subtitle.openSubtitle)
        }

        binding.settings.setOnSubDLSubtitleSelectedListener { subtitle ->
            viewModel.downloadSubDLSubtitle(subtitle.subDLSubtitle)
        }

        binding.settings.setOnExtraBufferingSelectedListener {
            displayVideo(
                currentVideo ?: return@setOnExtraBufferingSelectedListener,
                currentServer ?: return@setOnExtraBufferingSelectedListener
            )
        }

        binding.pvPlayer.controller.binding.btnSkipIntro.setOnClickListener {
            player.seekTo(player.currentPosition + 85000)
            it.isGone = true
        }

        binding.btnNextEpisodeAction.setOnClickListener {
            nextEpisodeOverlayManager.hideOverlay()
            playNextEpisodeAcrossSeasons()
        }
        binding.btnNextEpisodeDismiss.setOnClickListener {
            nextEpisodeOverlayManager.dismissOverlay()
        }

        binding.settings.onManualZoomClicked = {
            binding.settings.hide()
            binding.pvPlayer.hideController()
            binding.pvPlayer.enterManualZoomMode()
        }

        binding.settings.onDownloadClicked = {
            enqueueCurrentStreamDownload()
        }

        setupPlayerComposeOverlay(
            composeView = binding.composePlaybackOverlay,
            playerView = binding.pvPlayer,
            player = player,
            playbackController = playbackController,
            onBack = {
                runCatching { findNavController().navigateUp() }
            },
            onSettings = { binding.settings.show() },
            onPip = {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.player_picture_in_picture_not_supported),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    enterPIPMode()
                }
            },
            onAspectRatio = {
                val newResize = UserPreferences.playerResize.next()
                zoomToast?.cancel()
                zoomToast = Toast.makeText(requireContext(), newResize.stringRes, Toast.LENGTH_SHORT)
                zoomToast?.show()
                UserPreferences.playerResize = newResize
                updatePlayerScale()
            },
            onExternalPlayer = {
                binding.pvPlayer.controller.binding.btnExoExternalPlayer.performClick()
            },
            onPreviousEpisode = {
                binding.pvPlayer.controller.binding.btnCustomPrev.performClick()
            },
            onNextEpisode = {
                binding.pvPlayer.controller.binding.btnCustomNext.performClick()
            },
            onSkipIntro = {
                binding.pvPlayer.controller.binding.btnSkipIntro.performClick()
            },
        )
    }

    private fun enqueueCurrentStreamDownload() {
        val video = currentVideo
        val streamUrl = video?.source
        if (streamUrl.isNullOrBlank()) {
            Toast.makeText(requireContext(), R.string.download_error_no_url, Toast.LENGTH_SHORT).show()
            return
        }
        if (!::player.isInitialized) {
            Toast.makeText(requireContext(), R.string.download_error_no_url, Toast.LENGTH_SHORT).show()
            return
        }
        DownloadActionHelper.enqueueCurrentVideo(
            context = requireContext(),
            videoType = args.videoType,
            streamUrl = streamUrl,
        )
    }

 private fun updatePlayerScale() {
        val videoSurfaceView = binding.pvPlayer.videoSurfaceView
        val playerResize = UserPreferences.playerResize 

        binding.pvPlayer.resizeMode = playerResize.resizeMode 

        when (playerResize) { 
            UserPreferences.PlayerResize.Stretch43 -> {
                val scale = 1.33f 
                videoSurfaceView?.scaleX = scale
                videoSurfaceView?.scaleY = 1f
            }
            UserPreferences.PlayerResize.StretchVertical -> {
                videoSurfaceView?.scaleX = 1f
                videoSurfaceView?.scaleY = 1.25f
            }
            UserPreferences.PlayerResize.SuperZoom -> {
                videoSurfaceView?.scaleX = 1.5f
                videoSurfaceView?.scaleY = 1.5f
            }
            else -> {
                videoSurfaceView?.scaleX = 1f
                videoSurfaceView?.scaleY = 1f
            }
        }
    }

    fun setupEpisodeNavigationButtons() {
        val btnPrevious = binding.pvPlayer.controller.binding.btnCustomPrev
        val btnNext = binding.pvPlayer.controller.binding.btnCustomNext

        fun handleNavigationButton(
            button: ImageView,
            hasEpisode: () -> Boolean,
            playEpisode: () -> Unit
        ) {
            if (!hasEpisode()) {
                button.isGone = true
                return
            }

            button.isGone = false
            button.setOnClickListener listener@{
                if (!hasEpisode()) return@listener

                val videoType = args.videoType

                val watchItem: WatchItem? = when (videoType) {
                    is Video.Type.Movie -> database.movieDao().getById(videoType.id)
                    is Video.Type.Episode -> database.episodeDao().getById(videoType.id)
                }

                when (videoType) {
                    is Video.Type.Movie -> {
                        val provider = UserPreferences.currentProvider ?: return@listener
                        val movie = watchItem as? Movie
                        movie?.let { database.movieDao().update(it) }
                        movie?.let { UserDataCache.addMovieToContinueWatching(requireContext(), provider, it) }
                    }

                    is Video.Type.Episode -> {
                        val provider = UserPreferences.currentProvider ?: return@listener
                        val episode = watchItem as? Episode
                        episode?.let {
                            if (player.hasFinished()) {
                                database.episodeDao().resetProgressionFromEpisode(videoType.id)
                                UserDataCache.removeEpisodeFromContinueWatching(requireContext(), provider, it.id)
                            }
                            database.episodeDao().update(it)

                            if (!player.hasFinished()) {
                                UserDataCache.addEpisodeToContinueWatching(requireContext(), provider, it)
                            }

                            it.tvShow?.let { tvShow ->
                                database.tvShowDao().getById(tvShow.id)
                            }?.let { tvShow ->
                                val episodeDao = database.episodeDao()
                                val isStillWatching = episodeDao.hasAnyWatchHistoryForTvShow(tvShow.id)

                                val updatedTvShow = tvShow.copy().apply {
                                    merge(tvShow)
                                    isWatching = !player.hasReallyFinished() || isStillWatching
                                }
                                database.tvShowDao().update(updatedTvShow)
                                CloudSyncHooks.tvShow(requireContext(), provider, updatedTvShow)
                            }
                        }
                    }
                }

                playEpisode()
            }
        }

        handleNavigationButton(
            btnPrevious,
            EpisodeManager::hasPreviousEpisode,
            viewModel::playPreviousEpisode
        )
        handleNavigationButton(btnNext, EpisodeManager::hasNextEpisode, ::playNextEpisodeAcrossSeasons)
        playbackController.setEpisodeNavigation(
            canGoPrevious = EpisodeManager.hasPreviousEpisode(),
            canGoNext = EpisodeManager.hasNextEpisode(),
        )
    }

    private fun refreshEpisodeNavigation(type: Video.Type.Episode) {
        lifecycleScope.launch(Dispatchers.IO) {
            EpisodeManager.ensureNextEpisodeAvailable(type, database)
            withContext(Dispatchers.Main) {
                setupEpisodeNavigationButtons()
            }
        }
    }

    private fun playNextEpisodeAcrossSeasons(autoplay: Boolean = false) {
        val type = args.videoType as? Video.Type.Episode ?: return

        lifecycleScope.launch {
            val hasNextEpisode = withContext(Dispatchers.IO) {
                EpisodeManager.ensureNextEpisodeAvailable(type, database)
            }

            setupEpisodeNavigationButtons()

            if (!hasNextEpisode) return@launch
            if (autoplay && !UserPreferences.autoplay) return@launch

            viewModel.playNextEpisode()
        }
    }

    private fun displayVideo(video: Video, server: Video.Server) {
        currentVideo = video
        currentServer = server
        updatePlayerHeader()

        val extraBuffering = PlayerSettingsView.Settings.ExtraBuffering.isEnabled

        val softwareDecoder = PlayerSettingsView.Settings.SoftwareDecoder.isEnabled
        val needsReinit =
            extraBuffering != currentExtraBuffering || softwareDecoder != currentSoftwareDecoder
        if (needsReinit) {
            initializePlayer(extraBuffering, softwareDecoder)
            player.playlistMetadata = MediaMetadata.Builder()
                .setTitle(resolvePlayerTitle())
                .setMediaServers(servers.map {
                    MediaServer(
                        id = it.id,
                        name = it.name,
                    )
                })
                .build()
        }

        val currentPosition = player.currentPosition

        httpDataSource.setDefaultRequestProperties(
            mapOf(
                "User-Agent" to userAgent,
            ) + (video.headers ?: emptyMap())
        )

        player.setMediaItem(
            MediaItem.Builder()
                .setUri(video.source.toUri())
                .setMimeType(video.type)
                .setSubtitleConfigurations(video.subtitles.map { subtitle ->
                    MediaItem.SubtitleConfiguration.Builder(subtitle.file.toUri())
                        .setMimeType(subtitle.file.toSubtitleMimeType())
                        .setLabel(subtitle.label)
                        .setSelectionFlags(if (subtitle.default) C.SELECTION_FLAG_DEFAULT else 0)
                        .build()
                })
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setMediaServerId(server.id)
                        .build()
                )
                .build()
        )

        binding.pvPlayer.controller.binding.btnExoExternalPlayer.setOnClickListener {
            isIgnoringPip = true
            
            val videoTitle = when (val type = args.videoType) {
                is Video.Type.Movie -> type.title
                is Video.Type.Episode -> "${type.tvShow.title} • S${type.season.number} E${type.number}"
            }
            
            var sourceUri: Uri
            val mimeType = "video/*"
            
            val initialSource = video.source

            if (initialSource.startsWith("data:application/vnd.apple.mpegurl;base64,")) {
                val playlistContent = VideoUrlUtils.decodeBase64Uri(initialSource)
                val extractedUrl = if (playlistContent != null) VideoUrlUtils.extractUrlFromPlaylist(playlistContent) else null
                
                if (extractedUrl != null) {
                    sourceUri = extractedUrl.toUri()
                    Log.i("ExternalPlayer", "Link reale estratto: $sourceUri")
                } else {
                    try {
                        val file = File(requireContext().cacheDir, "stream.m3u8")
                        FileOutputStream(file).use { it.write(playlistContent?.toByteArray() ?: ByteArray(0)) }
                        sourceUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", file)
                    } catch (ignored: Exception) {
                        sourceUri = initialSource.toUri()
                    }
                }
            } else {
                sourceUri = initialSource.toUri()
            }

            Log.i("ExternalPlayer", "Avvio intent con URI: $sourceUri e MIME: $mimeType")

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(sourceUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                
                putExtra("title", videoTitle)
                putExtra("position", player.currentPosition.toInt())
                putExtra("return_result", true)
                
                putExtra("extra_headers", video.headers?.map { "${it.key}: ${it.value}" }?.toTypedArray())
                
                if (video.headers != null) {
                    val headersArray = video.headers.flatMap { listOf(it.key, it.value) }.toTypedArray()
                    putExtra("headers", headersArray)
                }
            }

            try {
                val receiverIntent = Intent("ACTION_PLAYER_CHOSEN").apply {
                    setPackage(requireContext().packageName)
                }
                
                val pendingIntent = PendingIntent.getBroadcast(
                    requireContext(), 0, receiverIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    startActivity(
                        Intent.createChooser(
                            intent,
                            getString(R.string.player_external_player_title),
                            pendingIntent.intentSender
                        )
                    )
                } else {
                    startActivity(Intent.createChooser(intent, getString(R.string.player_external_player_title)))
                }
            } catch (e: Exception) {
                Log.e("ExternalPlayer", "Errore selettore app", e)
                startActivity(Intent.createChooser(intent, getString(R.string.player_external_player_title)))
            }
        }
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                playbackController.setPlaying(isPlaying)
                binding.pvPlayer.keepScreenOn = isPlaying || UserPreferences.keepScreenOnWhenPaused

                if (isPlaying) {
                    recordRecentlyWatchedStart()
                    startProgressHandler()
                } else {
                    stopProgressHandler()
                }

                val hasUri = player.currentMediaItem?.localConfiguration?.uri
                    ?.toString()?.isNotEmpty()
                    ?: false

                if (!isPlaying && hasUri) {
                    val videoType = args.videoType
                    val watchItem: WatchItem? = when (videoType) {
                        is Video.Type.Movie -> database.movieDao().getById(videoType.id)
                        is Video.Type.Episode -> database.episodeDao().getById(videoType.id)
                    }

                    when {
                        player.hasStarted() && !player.hasFinished() -> {
                            watchItem?.isWatched = false
                            watchItem?.watchedDate = null
                            watchItem?.watchHistory = WatchItem.WatchHistory(
                                lastEngagementTimeUtcMillis = System.currentTimeMillis(),
                                lastPlaybackPositionMillis = player.currentPosition,
                                durationMillis = player.duration,
                            )
                        }

                        player.hasFinished() -> {
                            watchItem?.isWatched = true
                            watchItem?.watchedDate = Calendar.getInstance()
                            watchItem?.watchHistory = null
                        }
                    }

                            when (videoType) {
                                is Video.Type.Movie -> {
                                    val provider = UserPreferences.currentProvider ?: return
                                    val movie = watchItem as? Movie
                                    movie?.let {
                                        database.movieDao().update(it)
                                        UserDataCache.syncMovieToCache(requireContext(), provider, it)
                                    }
                                }

                                is Video.Type.Episode -> {
                                    val provider = UserPreferences.currentProvider ?: return
                                    val episode = watchItem as? Episode
                                    episode?.let {
                                        if (player.hasFinished()) {
                                            database.episodeDao().resetProgressionFromEpisode(videoType.id)
                                            UserDataCache.removeEpisodeFromContinueWatching(requireContext(), provider, it.id)
                                            queueNextEpisodeForContinueWatching(provider)
                                        }
                                        database.episodeDao().update(it)
                                        if (!player.hasFinished()) {
                                            UserDataCache.syncEpisodeToCache(requireContext(), provider, it)
                                        }

                                        it.tvShow?.let { tvShow ->
                                            database.tvShowDao().getById(tvShow.id)
                                        }?.let { tvShow ->
                                            val episodeDao = database.episodeDao()
                                            val isStillWatching = episodeDao.hasAnyWatchHistoryForTvShow(tvShow.id)
                                            
                                            val updatedTvShow = tvShow.copy().apply {
                                                merge(tvShow)
                                                isWatching = !player.hasReallyFinished() || isStillWatching
                                            }
                                            database.tvShowDao().update(updatedTvShow)
                                            CloudSyncHooks.tvShow(
                                                requireContext(),
                                                provider,
                                                updatedTvShow,
                                            )
                                        }
                                    }
                                }
                            }
                    if (player.hasReallyFinished()) {
                        if (UserPreferences.autoplay) {
                            playNextEpisodeAcrossSeasons(autoplay = true)
                        }
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                playbackController.setBuffering(playbackState == Player.STATE_BUFFERING)
                playbackController.updatePosition(player.currentPosition, player.duration)
            }

            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                Log.e("PlayerMobileFragment", "onPlayerError: ", error)
                
                val nextServer = servers.getOrNull(servers.indexOf(currentServer) + 1)
                if (nextServer != null) {
                    Log.i("PlayerMobileFragment", "Playback failed, trying next server: ${nextServer.name}")
                    viewModel.getVideo(nextServer)
                }
            }
        })

        if (currentPosition == 0L) {
            val videoType = args.videoType
            val provider = UserPreferences.currentProvider
            
            val watchItem: WatchItem? = when (videoType) {
                is Video.Type.Movie -> {
                    // Try cache first, then DB
                    var movie = if (provider != null) {
                        UserDataCache.read(requireContext(), provider)?.continueWatchingMovies
                            ?.find { it.id == videoType.id }?.toMovie()
                    } else null
                    movie ?: database.movieDao().getById(videoType.id)
                }
                is Video.Type.Episode -> {
                    // Try cache first, then DB
                    var episode = if (provider != null) {
                        UserDataCache.read(requireContext(), provider)?.continueWatchingEpisodes
                            ?.find { it.id == videoType.id }?.toEpisode()
                    } else null
                    episode ?: database.episodeDao().getById(videoType.id)
                }
            }
            
            val lastPlaybackPositionMillis = watchItem?.watchHistory
                ?.let { it.lastPlaybackPositionMillis - 10.seconds.inWholeMilliseconds }

            player.seekTo(lastPlaybackPositionMillis ?: 0)
        } else {
            player.seekTo(currentPosition)
        }

        player.prepare()
        player.play()
    }

    private fun enterPIPMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding.pvPlayer.useController = false
            requireActivity().enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .build()
            )
        }
    }

    private fun recordRecentlyWatchedStart() {
        val playedAtMillis = System.currentTimeMillis()
        when (val videoType = currentVideoTypeForUi()) {
            is Video.Type.Movie -> {
                if (database.movieDao().markRecentlyWatched(videoType.id, playedAtMillis) == 0) {
                    database.movieDao().insert(
                        Movie(
                            id = videoType.id,
                            title = videoType.title,
                            released = videoType.releaseDate,
                            poster = videoType.poster,
                            imdbId = videoType.imdbId,
                        ).apply {
                            lastPlayedAtMillis = playedAtMillis
                        }
                    )
                }
            }

            is Video.Type.Episode -> {
                val storedTvShow = database.tvShowDao().getById(videoType.tvShow.id)
                    ?: TvShow(
                        id = videoType.tvShow.id,
                        title = videoType.tvShow.title,
                        released = videoType.tvShow.releaseDate,
                        poster = videoType.tvShow.poster,
                        banner = videoType.tvShow.banner,
                        imdbId = videoType.tvShow.imdbId,
                    ).apply {
                        lastPlayedAtMillis = playedAtMillis
                        lastPlayedEpisodeId = videoType.id
                        database.tvShowDao().insert(this)
                    }

                if (database.episodeDao().getById(videoType.id) == null) {
                    database.episodeDao().insert(
                        Episode(
                            id = videoType.id,
                            number = videoType.number,
                            title = videoType.title,
                            poster = videoType.poster,
                            overview = videoType.overview,
                            tvShow = storedTvShow,
                            season = Season(
                                number = videoType.season.number,
                                title = videoType.season.title.orEmpty(),
                            ),
                        )
                    )
                }
                database.tvShowDao().markRecentlyWatched(
                    id = videoType.tvShow.id,
                    episodeId = videoType.id,
                    playedAtMillis = playedAtMillis,
                )
            }
        }
    }

    private fun currentVideoTypeForUi(): Video.Type = when (val type = args.videoType) {
        is Video.Type.Episode -> EpisodeManager.getCurrentEpisode()
            ?.takeIf { currentEpisode -> currentEpisode.id == type.id }
            ?: type
        is Video.Type.Movie -> type
    }

    private fun resolvePlayerTitle(videoType: Video.Type = currentVideoTypeForUi()): String {
        return when (videoType) {
            is Video.Type.Movie -> videoType.title
            is Video.Type.Episode -> videoType.tvShow.title.ifBlank { args.title }
        }
    }

    private fun resolvePlayerSubtitle(videoType: Video.Type = currentVideoTypeForUi()): String {
        return when (videoType) {
            is Video.Type.Movie -> args.subtitle
            is Video.Type.Episode -> {
                val episodeTitle = videoType.title?.takeUnless { it.isBlank() } ?: args.subtitle
                "S${videoType.season.number} E${videoType.number}  •  $episodeTitle"
            }
        }
    }

    private fun updatePlayerHeader(videoType: Video.Type = currentVideoTypeForUi()) {
        val title = resolvePlayerTitle(videoType)
        val subtitle = resolvePlayerSubtitle(videoType)
        binding.pvPlayer.controller.binding.tvExoTitle.text = title
        binding.pvPlayer.controller.binding.tvExoSubtitle.text = subtitle
        playbackController.setMetadata(title, subtitle)
    }

    private fun queueNextEpisodeForContinueWatching(provider: com.betterstreamflix.providers.Provider) {
        val nextEpisode = EpisodeManager.peekNextEpisode() ?: return
        val episodeDao = database.episodeDao()
        val persistedNextEpisode = episodeDao.getById(nextEpisode.id)?.apply {
            isWatched = false
            watchedDate = null
            watchHistory = WatchItem.WatchHistory(
                lastEngagementTimeUtcMillis = System.currentTimeMillis(),
                lastPlaybackPositionMillis = 0L,
                durationMillis = 0L,
            )
        } ?: Episode(
            id = nextEpisode.id,
            number = nextEpisode.number,
            title = nextEpisode.title,
            poster = nextEpisode.poster,
            overview = nextEpisode.overview,
            tvShow = database.tvShowDao().getById(nextEpisode.tvShow.id) ?: TvShow(
                id = nextEpisode.tvShow.id,
                title = nextEpisode.tvShow.title,
                poster = nextEpisode.tvShow.poster,
                banner = nextEpisode.tvShow.banner,
            ),
            season = Season(
                number = nextEpisode.season.number,
                title = nextEpisode.season.title,
            ),
        ).apply {
            isWatched = false
            watchedDate = null
            watchHistory = WatchItem.WatchHistory(
                lastEngagementTimeUtcMillis = System.currentTimeMillis(),
                lastPlaybackPositionMillis = 0L,
                durationMillis = 0L,
            )
        }

        episodeDao.save(persistedNextEpisode)
        UserDataCache.syncEpisodeToCache(requireContext(), provider, persistedNextEpisode)
    }
    private fun startProgressHandler() {
        progressHandler = android.os.Handler(android.os.Looper.getMainLooper())
        progressRunnable = Runnable {
            if (player.isPlaying) {
                playbackController.updatePosition(player.currentPosition, player.duration)
                nextEpisodeOverlayManager.updateSkipIntroButton()
                nextEpisodeOverlayManager.updateOverlay(currentVideoTypeForUi())
                if (sleepTimer.checkAndStop(player)) {
                    Toast.makeText(requireContext(), R.string.player_settings_title, Toast.LENGTH_SHORT).show()
                }
            }
            progressHandler.postDelayed(progressRunnable, 1000)
        }
        progressHandler.post(progressRunnable)
    }

    private fun stopProgressHandler() {
        if (::progressHandler.isInitialized) {
            progressHandler.removeCallbacks(progressRunnable)
        }
    }

    private fun saveWatchProgress() {
        if (!::player.isInitialized) return
        val hasUri = player.currentMediaItem?.localConfiguration?.uri
            ?.toString()?.isNotEmpty()
            ?: false
        if (!hasUri) return

        val videoType = args.videoType
        val provider = UserPreferences.currentProvider ?: return
        val watchItem: WatchItem? = when (videoType) {
            is Video.Type.Movie -> database.movieDao().getById(videoType.id)
            is Video.Type.Episode -> database.episodeDao().getById(videoType.id)
        }

        when {
            player.hasStarted() && !player.hasFinished() -> {
                watchItem?.isWatched = false
                watchItem?.watchedDate = null
                watchItem?.watchHistory = WatchItem.WatchHistory(
                    lastEngagementTimeUtcMillis = System.currentTimeMillis(),
                    lastPlaybackPositionMillis = player.currentPosition,
                    durationMillis = player.duration,
                )
            }
            player.hasFinished() -> {
                watchItem?.isWatched = true
                watchItem?.watchedDate = Calendar.getInstance()
                watchItem?.watchHistory = null
            }
        }

        when (videoType) {
            is Video.Type.Movie -> {
                val movie = watchItem as? Movie
                movie?.let {
                    database.movieDao().update(it)
                    UserDataCache.syncMovieToCache(requireContext(), provider, it)
                }
            }
            is Video.Type.Episode -> {
                val episode = watchItem as? Episode
                episode?.let {
                    if (player.hasFinished()) {
                        database.episodeDao().resetProgressionFromEpisode(videoType.id)
                        UserDataCache.removeEpisodeFromContinueWatching(requireContext(), provider, it.id)
                    } else {
                        database.episodeDao().update(it)
                        UserDataCache.syncEpisodeToCache(requireContext(), provider, it)
                    }
                    it.tvShow?.let { tvShow ->
                        database.tvShowDao().getById(tvShow.id)
                    }?.let { tvShow ->
                        val episodeDao = database.episodeDao()
                        val isStillWatching = episodeDao.hasAnyWatchHistoryForTvShow(tvShow.id)
                        val updatedTvShow = tvShow.copy().apply {
                            merge(tvShow)
                            isWatching = !player.hasReallyFinished() || isStillWatching
                        }
                        database.tvShowDao().update(updatedTvShow)
                        CloudSyncHooks.tvShow(requireContext(), provider, updatedTvShow)
                    }
                }
            }
        }

        WatchProgressHelper.upsertWatchNext(
            context = requireContext().applicationContext,
            videoType = videoType,
            positionMillis = player.currentPosition,
            durationMillis = player.duration,
            finished = player.hasFinished(),
        )
    }

    override fun onPause() {
        super.onPause()
        stopProgressHandler()
        if (::nextEpisodeOverlayManager.isInitialized) {
            nextEpisodeOverlayManager.hideOverlay()
        }
    }

    private var currentExtraBuffering = false
    private var currentSoftwareDecoder = false

    private fun initializePlayer(extraBuffering: Boolean, softwareDecoder: Boolean = currentSoftwareDecoder) {
        releasePlayer()
        currentExtraBuffering = extraBuffering
        currentSoftwareDecoder = softwareDecoder

        var tokenLogged = false
        val okHttpClient = NetworkClient.default.newBuilder()
            .addInterceptor { chain ->
                var request = chain.request()
                
                if (currentVideo?.maintainToken == true) {
                    val latestQuery = TokenManager.latestQuery
                    if (latestQuery != null) {
                        val origHttpUrl = request.url
                        val updatedHttpUrl = origHttpUrl.newBuilder().query(latestQuery).build()
                        request = request.newBuilder().url(updatedHttpUrl).build()
                        if (!tokenLogged) {
                            if (com.betterstreamflix.BuildConfig.DEBUG) {
                                android.util.Log.d("TokenManager", "[MOBILE-INTERCEPTOR] Token successfully injected (applied to all segments)")
                            }
                            tokenLogged = true
                        }
                    } else {
                        android.util.Log.w("TokenManager", "[MOBILE-INTERCEPTOR] maintainToken=true but latestQuery is null! URL: ${request.url.host}")
                    }
                }
                
                chain.proceed(request)
            }
            .build()
        httpDataSource = OkHttpDataSource.Factory(okHttpClient)

        dataSourceFactory = DefaultDataSource.Factory(requireContext(), httpDataSource)

        player = PlayerBuilderFactory.buildPlayer(
            context = requireContext(),
            dataSourceFactory = dataSourceFactory,
            extraBuffering = extraBuffering,
            softwareDecoder = softwareDecoder,
        ).also { builtPlayer ->
            PlayerBuilderFactory.applyPlayerSettings(builtPlayer)
            if (NotificationPreferences.isPlaybackNotificationsEnabled(requireContext())) {
                mediaSession = PlayerBuilderFactory.createMediaSession(requireContext(), builtPlayer)
            }
        }

        nextEpisodeOverlayManager = NextEpisodeOverlayManager(this, player, database, playbackController).apply {
            onPrefetchComplete = {
                if (isAdded && _binding != null) {
                    setupEpisodeNavigationButtons()
                    if (player.isPlaying) {
                        updateOverlay(currentVideoTypeForUi())
                    }
                }
            }
        }

        binding.pvPlayer.player = player
        binding.settings.player = player
        binding.settings.subtitleView = binding.pvPlayer.subtitleView
        binding.settings.onSubtitlesClicked = {
            viewModel.getSubtitles(args.videoType)
        }
    }

    private fun releasePlayer() {
        stopProgressHandler()
        _binding?.pvPlayer?.player = null
        _binding?.settings?.player = null
        _binding?.settings?.subtitleView = null
        if (::player.isInitialized) {
            player.release()
        }
        mediaSession?.release()
        mediaSession = null
    }

    private fun showSleepTimerDialog() {
        val labels = com.betterstreamflix.player.advanced.SleepTimer.PRESET_DURATIONS
            .map { "$it min" }
            .toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.player_settings_title)
            .setItems(labels) { _, which ->
                val minutes = com.betterstreamflix.player.advanced.SleepTimer.PRESET_DURATIONS[which]
                sleepTimer.start(minutes)
                Toast.makeText(requireContext(), "$minutes min", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> sleepTimer.cancel() }
            .show()
    }
}

