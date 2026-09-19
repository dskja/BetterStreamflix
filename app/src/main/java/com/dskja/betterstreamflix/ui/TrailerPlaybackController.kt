package com.dskja.betterstreamflix.ui

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.utils.ExperimentalMobileDesign

/**
 * Unified trailer playback for Movie/TV detail pages.
 * Supports in-app YouTube embed (experimental default), YouTube app, and SmartTube.
 */
object TrailerPlaybackController {
    private const val TAG = "TrailerPlayback"
    const val KEY_PREFERRED_PLAYER = "preferred_player"
    const val KEY_SMARTTUBE_PACKAGE = "preferred_smarttube_package"

    const val PLAYER_ASK = "ask"
    const val PLAYER_YOUTUBE = "youtube"
    const val PLAYER_IN_APP = "in_app"
    const val PLAYER_SMARTTUBE = "smarttube"
    const val PLAYER_SMARTTUBE_STABLE = "smarttube_stable"
    const val PLAYER_SMARTTUBE_BETA = "smarttube_beta"

    const val SMARTTUBE_STABLE_PACKAGE = "org.smarttube.stable"
    const val SMARTTUBE_BETA_PACKAGE = "org.smarttube.beta"

    fun play(fragment: Fragment, trailerUrl: String) {
        val context = fragment.requireContext()
        play(context, fragment.activity, trailerUrl, fragment.childFragmentManager)
    }

    fun play(
        context: Context,
        activity: FragmentActivity?,
        trailerUrl: String,
        fragmentManager: androidx.fragment.app.FragmentManager? = activity?.supportFragmentManager,
    ) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        var preferred = prefs.getString(KEY_PREFERRED_PLAYER, PLAYER_ASK) ?: PLAYER_ASK
        // Experimental detail pages prefer in-app trailer when user hasn't chosen.
        if (preferred == PLAYER_ASK && ExperimentalMobileDesign.enabled()) {
            preferred = PLAYER_IN_APP
        }
        when (preferred) {
            PLAYER_IN_APP -> openInApp(context, activity, fragmentManager, trailerUrl)
            PLAYER_YOUTUBE -> openYoutube(context, trailerUrl)
            PLAYER_SMARTTUBE_STABLE -> launchSmartTube(context, SMARTTUBE_STABLE_PACKAGE, trailerUrl)
            PLAYER_SMARTTUBE_BETA -> launchSmartTube(context, SMARTTUBE_BETA_PACKAGE, trailerUrl)
            PLAYER_SMARTTUBE -> handleSmartTube(context, trailerUrl)
            else -> showChooser(context, activity, fragmentManager, trailerUrl)
        }
    }

    fun youtubeVideoId(trailerUrl: String): String? {
        val uri = runCatching { Uri.parse(trailerUrl) }.getOrNull() ?: return null
        val host = uri.host.orEmpty().lowercase()
        return when {
            host.contains("youtu.be") -> uri.lastPathSegment?.takeIf { it.length >= 6 }
            host.contains("youtube") -> uri.getQueryParameter("v")
                ?: uri.pathSegments?.let { segs ->
                    val idx = segs.indexOf("embed")
                    if (idx >= 0 && idx + 1 < segs.size) segs[idx + 1] else null
                }
            else -> Regex("""(?:v=|youtu\.be/|embed/)([A-Za-z0-9_-]{6,})""")
                .find(trailerUrl)?.groupValues?.getOrNull(1)
        }
    }

    private fun showChooser(
        context: Context,
        activity: FragmentActivity?,
        fragmentManager: androidx.fragment.app.FragmentManager?,
        trailerUrl: String,
    ) {
        val st = installedSmartTube(context)
        val items = buildList {
            add(context.getString(R.string.trailer_player_in_app))
            add(context.getString(R.string.youtube))
            if (st.isNotEmpty()) add(context.getString(R.string.smarttube))
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.watch_trailer_with)
            .setItems(items.toTypedArray()) { _, which ->
                when (which) {
                    0 -> openInApp(context, activity, fragmentManager, trailerUrl)
                    1 -> openYoutube(context, trailerUrl)
                    else -> {
                        if (st.size > 1) {
                            showSmartTubeVersionDialog(context, st, trailerUrl, save = false)
                        } else {
                            launchSmartTube(context, st.first(), trailerUrl)
                        }
                    }
                }
            }
            .show()
    }

    private fun openInApp(
        context: Context,
        activity: FragmentActivity?,
        fragmentManager: androidx.fragment.app.FragmentManager?,
        trailerUrl: String,
    ) {
        val id = youtubeVideoId(trailerUrl)
        if (id.isNullOrBlank() || fragmentManager == null || activity == null) {
            openYoutube(context, trailerUrl)
            return
        }
        InAppTrailerDialog.newInstance(id, trailerUrl)
            .show(fragmentManager, "in_app_trailer")
    }

    private fun openYoutube(context: Context, trailerUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trailerUrl))
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.trailer_player_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleSmartTube(context: Context, trailerUrl: String) {
        val installed = installedSmartTube(context)
        when {
            installed.isEmpty() -> openYoutube(context, trailerUrl)
            installed.size == 1 -> launchSmartTube(context, installed[0], trailerUrl)
            else -> {
                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                val saved = prefs.getString(KEY_SMARTTUBE_PACKAGE, null)
                if (saved != null && installed.contains(saved)) {
                    launchSmartTube(context, saved, trailerUrl)
                } else {
                    showSmartTubeVersionDialog(context, installed, trailerUrl, save = true)
                }
            }
        }
    }

    private fun showSmartTubeVersionDialog(
        context: Context,
        packages: List<String>,
        trailerUrl: String,
        save: Boolean,
    ) {
        val labels = packages.map { pkg ->
            if (pkg == SMARTTUBE_STABLE_PACKAGE) context.getString(R.string.smarttube_stable)
            else context.getString(R.string.smarttube_beta)
        }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(R.string.smarttube)
            .setItems(labels) { _, which ->
                val pkg = packages[which]
                if (save) {
                    PreferenceManager.getDefaultSharedPreferences(context).edit()
                        .putString(KEY_SMARTTUBE_PACKAGE, pkg)
                        .apply()
                }
                launchSmartTube(context, pkg, trailerUrl)
            }
            .show()
    }

    private fun launchSmartTube(context: Context, packageName: String, trailerUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trailerUrl)).apply {
            setPackage(packageName)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "SmartTube launch failed: ${e.message}")
            openYoutube(context, trailerUrl)
        }
    }

    private fun installedSmartTube(context: Context): List<String> {
        val pm = context.packageManager
        return listOf(SMARTTUBE_STABLE_PACKAGE, SMARTTUBE_BETA_PACKAGE).filter { pkg ->
            runCatching { pm.getPackageInfo(pkg, 0); true }.getOrDefault(false)
        }
    }

    class InAppTrailerDialog : androidx.fragment.app.DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val videoId = requireArguments().getString(ARG_ID).orEmpty()
            val fallback = requireArguments().getString(ARG_URL).orEmpty()
            val web = WebView(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    (220 * resources.displayMetrics.density).toInt(),
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
                loadDataWithBaseURL(
                    "https://www.youtube-nocookie.com",
                    embedHtml(videoId),
                    "text/html",
                    "utf-8",
                    null,
                )
            }
            return AlertDialog.Builder(requireContext())
                .setTitle(R.string.movie_trailer)
                .setView(web)
                .setPositiveButton(R.string.youtube) { _, _ ->
                    openYoutube(requireContext(), fallback.ifBlank { "https://www.youtube.com/watch?v=$videoId" })
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
                .also { dialog ->
                    dialog.setOnDismissListener {
                        web.loadUrl("about:blank")
                        web.destroy()
                    }
                }
        }

        private fun embedHtml(videoId: String): String = """
            <!DOCTYPE html><html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1"/>
            <style>html,body{margin:0;padding:0;background:#000;height:100%;}
            .wrap{position:relative;padding-bottom:56.25%;height:0;overflow:hidden;}
            iframe{position:absolute;top:0;left:0;width:100%;height:100%;border:0;}</style>
            </head><body><div class="wrap">
            <iframe src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&rel=0&modestbranding=1"
              allow="autoplay; encrypted-media; picture-in-picture" allowfullscreen></iframe>
            </div></body></html>
        """.trimIndent()

        companion object {
            private const val ARG_ID = "id"
            private const val ARG_URL = "url"
            fun newInstance(videoId: String, trailerUrl: String) = InAppTrailerDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_ID, videoId)
                    putString(ARG_URL, trailerUrl)
                }
            }
        }
    }
}
