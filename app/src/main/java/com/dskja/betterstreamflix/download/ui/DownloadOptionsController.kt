package com.dskja.betterstreamflix.download.ui

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.download.DownloadConnectivityMonitor
import com.dskja.betterstreamflix.download.DownloadController
import com.dskja.betterstreamflix.download.DownloadEnqueueOutcome
import com.dskja.betterstreamflix.download.DownloadErrorCode
import com.dskja.betterstreamflix.download.DownloadPrepareResult
import com.dskja.betterstreamflix.download.DownloadQualityPreset
import com.dskja.betterstreamflix.download.DownloadStorage
import com.dskja.betterstreamflix.download.DownloadTrackOption
import com.dskja.betterstreamflix.models.Episode
import com.dskja.betterstreamflix.models.Movie
import com.dskja.betterstreamflix.models.TvShow
import com.dskja.betterstreamflix.models.Video
import com.dskja.betterstreamflix.utils.ExperimentalMobileDesign
import com.dskja.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object DownloadOptionsController {
    fun enqueueMovie(fragment: Fragment, movie: Movie) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            handleOutcome(fragment, withContext(Dispatchers.IO) {
                DownloadController.prepareMovie(fragment.requireContext(), movie)
            })
        }
    }

    fun enqueueEpisode(fragment: Fragment, episode: Episode) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            handleOutcome(fragment, withContext(Dispatchers.IO) {
                DownloadController.prepareEpisode(fragment.requireContext(), episode)
            })
        }
    }

    fun enqueueEpisode(context: Context, activity: Activity, episode: Episode, onNavigateDownloads: () -> Unit = {}) {
        activity.lifecycleScopeOrMain().launch {
            val outcome = withContext(Dispatchers.IO) {
                DownloadController.prepareEpisode(context, episode)
            }
            handleOutcomeActivity(activity, outcome, onNavigateDownloads)
        }
    }

    fun enqueueMovie(context: Context, activity: Activity, movie: Movie, onNavigateDownloads: () -> Unit = {}) {
        activity.lifecycleScopeOrMain().launch {
            val outcome = withContext(Dispatchers.IO) {
                DownloadController.prepareMovie(context, movie)
            }
            handleOutcomeActivity(activity, outcome, onNavigateDownloads)
        }
    }

    fun enqueueFromPlayer(
        fragment: Fragment,
        videoType: Video.Type,
        server: Video.Server,
        video: Video,
    ) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            handleOutcome(fragment, withContext(Dispatchers.IO) {
                DownloadController.prepareFromResolved(
                    fragment.requireContext(),
                    videoType,
                    server,
                    video,
                )
            })
        }
    }

    fun enqueueSeason(
        fragment: Fragment,
        tvShow: TvShow,
        seasonNumber: Int,
        episodes: List<Episode>,
    ) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            maybeRequestNotifications(fragment.requireActivity())
            val outcome = withContext(Dispatchers.IO) {
                DownloadController.enqueueSeason(
                    context = fragment.requireContext(),
                    tvShow = tvShow,
                    seasonNumber = seasonNumber,
                    episodes = episodes,
                ) { prepared ->
                    val trackIdx = when (UserPreferences.downloadQualityPreset) {
                        DownloadQualityPreset.DATA_SAVER ->
                            prepared.trackOptions.lastIndex.coerceAtLeast(0)
                        else -> 0
                    }
                    0 to trackIdx
                }
            }
            handleOutcome(fragment, outcome)
        }
    }

    private suspend fun handleOutcome(fragment: Fragment, outcome: DownloadEnqueueOutcome) {
        handleOutcomeActivity(fragment.requireActivity(), outcome) {
            runCatching {
                androidx.navigation.fragment.NavHostFragment.findNavController(fragment)
                    .navigate(R.id.downloads)
            }
        }
    }

    private suspend fun handleOutcomeActivity(
        activity: Activity,
        outcome: DownloadEnqueueOutcome,
        onNavigateDownloads: () -> Unit,
    ) {
        when (outcome) {
            is DownloadEnqueueOutcome.NeedsOptions -> {
                if (UserPreferences.downloadWifiOnly &&
                    DownloadConnectivityMonitor.isMetered(activity)
                ) {
                    val proceed = confirmCellular(activity)
                    if (!proceed) {
                        toast(activity, activity.getString(R.string.download_error_wifi))
                        return
                    }
                }
                maybeRequestNotifications(activity)
                showOptionsDialog(activity, outcome.prepared)
            }
            is DownloadEnqueueOutcome.Started -> {
                maybeRequestNotifications(activity)
                toast(activity, activity.getString(R.string.downloads_started))
            }
            is DownloadEnqueueOutcome.AlreadyActive -> {
                toast(activity, activity.getString(R.string.downloads_already_active))
                onNavigateDownloads()
            }
            is DownloadEnqueueOutcome.AlreadyCompleted -> {
                toast(activity, activity.getString(R.string.downloads_already_completed))
                onNavigateDownloads()
            }
            is DownloadEnqueueOutcome.Failed -> {
                toast(activity, localizedError(activity, outcome.code, outcome.message))
            }
        }
    }

    private fun showOptionsDialog(activity: Activity, prepared: DownloadPrepareResult) {
        val view = LayoutInflater.from(activity).inflate(
            ExperimentalMobileDesign.layout(
                R.layout.dialog_download_options,
                R.layout.dialog_download_options_exp,
            ),
            null,
        )
        val titleView = view.findViewById<TextView>(R.id.tv_download_options_title)
        val subtitleView = view.findViewById<TextView>(R.id.tv_download_options_subtitle)
        val storageView = view.findViewById<TextView>(R.id.tv_download_options_storage)
        val serverSpinner = view.findViewById<Spinner>(R.id.sp_download_server)
        val qualitySpinner = view.findViewById<Spinner>(R.id.sp_download_quality)
        val loading = view.findViewById<ProgressBar>(R.id.pb_download_options_loading)

        titleView.text = prepared.title
        subtitleView.text = prepared.subtitle
        storageView.text = activity.getString(
            R.string.download_options_storage,
            DownloadStorage.formatBytes(DownloadStorage.freeBytes(activity)),
        )

        var currentPrepared = prepared
        var trackOptions = prepared.trackOptions.toMutableList()
        var serverIndex = prepared.selectedServerIndex.coerceIn(0, prepared.servers.lastIndex.coerceAtLeast(0))
        var qualityIndex = defaultQualityIndex(trackOptions)
        var tracksJob: Job? = null

        val serverNames = prepared.servers.map { it.server.name }.ifEmpty { listOf("Auto") }
        serverSpinner.adapter = ArrayAdapter(
            activity,
            android.R.layout.simple_spinner_dropdown_item,
            serverNames,
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        serverSpinner.setSelection(serverIndex)

        fun bindQualitySpinner(options: List<DownloadTrackOption>, preferredIndex: Int) {
            val labels = options.map { it.label }.ifEmpty { listOf("Auto") }
            qualitySpinner.adapter = ArrayAdapter(
                activity,
                android.R.layout.simple_spinner_dropdown_item,
                labels,
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            qualityIndex = preferredIndex.coerceIn(0, labels.lastIndex.coerceAtLeast(0))
            qualitySpinner.setSelection(qualityIndex)
        }
        bindQualitySpinner(trackOptions, qualityIndex)

        qualitySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                qualityIndex = position
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        serverSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == serverIndex) return
                serverIndex = position
                val candidate = currentPrepared.servers.getOrNull(position) ?: return
                tracksJob?.cancel()
                tracksJob = activity.lifecycleScopeOrMain().launch {
                    loading.visibility = View.VISIBLE
                    qualitySpinner.isEnabled = false
                    val options = withContext(Dispatchers.IO) {
                        DownloadController.prepareTrackOptions(activity, candidate.video)
                    }
                    trackOptions.clear()
                    trackOptions.addAll(options)
                    currentPrepared = currentPrepared.copy(trackOptions = options, selectedServerIndex = position)
                    bindQualitySpinner(options, defaultQualityIndex(options))
                    qualitySpinner.isEnabled = true
                    loading.visibility = View.GONE
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        AlertDialog.Builder(activity)
            .setTitle(R.string.download_options_title)
            .setView(view)
            .setPositiveButton(R.string.download_options_start) { _, _ ->
                tracksJob?.cancel()
                activity.lifecycleScopeOrMain().launch {
                    val result = withContext(Dispatchers.IO) {
                        DownloadController.confirmEnqueue(
                            activity,
                            currentPrepared,
                            serverIndex,
                            qualityIndex,
                            trackOptions.getOrNull(qualityIndex)?.label ?: "Auto",
                        )
                    }
                    handleOutcomeActivity(activity, result) {}
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> tracksJob?.cancel() }
            .setOnCancelListener { tracksJob?.cancel() }
            .show()
    }

    private fun defaultQualityIndex(options: List<DownloadTrackOption>): Int {
        if (options.isEmpty()) return 0
        return when (UserPreferences.downloadQualityPreset) {
            DownloadQualityPreset.DATA_SAVER -> options.lastIndex
            DownloadQualityPreset.BEST,
            DownloadQualityPreset.ASK,
            -> 0
        }
    }

    private suspend fun confirmCellular(activity: Activity): Boolean {
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            activity.runOnUiThread {
                AlertDialog.Builder(activity)
                    .setTitle(R.string.download_options_cellular_confirm_title)
                    .setMessage(R.string.download_options_cellular_confirm_message)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        if (cont.isActive) cont.resume(true) {}
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ ->
                        if (cont.isActive) cont.resume(false) {}
                    }
                    .setOnCancelListener {
                        if (cont.isActive) cont.resume(false) {}
                    }
                    .show()
            }
        }
    }

    fun localizedError(context: Context, code: DownloadErrorCode, fallback: String): String {
        val res = when (code) {
            DownloadErrorCode.IPTV -> R.string.download_error_iptv
            DownloadErrorCode.DRM -> R.string.download_error_drm
            DownloadErrorCode.NO_SERVERS -> R.string.download_error_no_servers
            DownloadErrorCode.CLOUDFLARE -> R.string.download_error_cloudflare
            DownloadErrorCode.NETWORK -> R.string.download_error_network
            DownloadErrorCode.NOSPACE -> R.string.download_error_nospace
            DownloadErrorCode.WIFI_REQUIRED -> R.string.download_error_wifi
            DownloadErrorCode.EXPIRED -> R.string.download_error_expired
            DownloadErrorCode.FILE_MISSING -> R.string.download_error_file_missing
            DownloadErrorCode.UNSUPPORTED -> R.string.download_error_unsupported
            DownloadErrorCode.UNKNOWN -> null
        }
        return res?.let { context.getString(it) } ?: fallback.ifBlank {
            context.getString(R.string.downloads_failed_generic)
        }
    }

    private fun maybeRequestNotifications(activity: Activity) {
        if (Build.VERSION.SDK_INT < 33) return
        val permission = android.Manifest.permission.POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        ActivityCompat.requestPermissions(activity, arrayOf(permission), 4209)
    }

    private fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun Activity.lifecycleScopeOrMain() =
        (this as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope
            ?: throw IllegalStateException("Activity must be a LifecycleOwner")
}
