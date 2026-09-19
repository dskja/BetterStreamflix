package com.dskja.betterstreamflix.platform.playerbackend

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.player.PlayerBuilderFactory
import com.dskja.betterstreamflix.utils.UserPreferences

object ExoPlayerBackend : PlayerBackend {
    override val kind = PlayerBackendKind.EXO
    override val displayName = "ExoPlayer (Media3)"

    fun build(
        context: Context,
        dataSourceFactory: DataSource.Factory,
        options: PlayerBuilderFactory.Options,
    ): ExoPlayer = PlayerBuilderFactory.build(context, dataSourceFactory, options)
}

/**
 * Secondary decoder path: hand off to an installed MPV-capable player.
 * Forwards title, auth headers, resume position, and subtitle URI when supported.
 */
object ExternalMpvBackend : PlayerBackend {
    override val kind = PlayerBackendKind.EXTERNAL_MPV
    override val displayName = "MPV (external)"

    private const val TAG = "ExternalMpv"
    val CANDIDATE_PACKAGES = listOf(
        "is.xyz.mpv",
        "com.brouken.player",
        "org.videolan.vlc",
    )

    data class Handoff(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
        val positionMs: Long = 0L,
        val title: String? = null,
        val subtitleUri: String? = null,
    )

    fun canResolve(context: Context): Boolean =
        preferredInstalledPackage(context) != null

    fun preferredInstalledPackage(context: Context): String? =
        CANDIDATE_PACKAGES.firstOrNull { pkg ->
            runCatching {
                context.packageManager.getPackageInfo(pkg, 0)
                true
            }.getOrDefault(false)
        }

    /** Pure intent builder for unit tests / shared handoff. */
    fun buildIntent(handoff: Handoff, packageName: String?): Intent {
        val uri = Uri.parse(handoff.url)
        val headerArray = handoff.headers.flatMap { listOf(it.key, it.value) }.toTypedArray()
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            if (!packageName.isNullOrBlank()) setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (headerArray.isNotEmpty()) {
                putExtra("headers", headerArray)
                putExtra(
                    "http_header_list",
                    handoff.headers.entries.joinToString("\r\n") { "${it.key}: ${it.value}" },
                )
            }
            if (handoff.positionMs > 0L) {
                putExtra("position", handoff.positionMs.toInt())
                putExtra("seek_position", handoff.positionMs)
            }
            if (!handoff.title.isNullOrBlank()) {
                putExtra("title", handoff.title)
                putExtra(Intent.EXTRA_TITLE, handoff.title)
            }
            if (!handoff.subtitleUri.isNullOrBlank()) {
                putExtra("subs", Uri.parse(handoff.subtitleUri))
                putExtra("subtitle", handoff.subtitleUri)
                putExtra("subtitles_location", handoff.subtitleUri)
            }
        }
    }

    fun open(
        context: Context,
        url: String,
        headers: Map<String, String> = emptyMap(),
        positionMs: Long = 0L,
        title: String? = null,
        subtitleUri: String? = null,
    ): Boolean {
        val handoff = Handoff(
            url = url,
            headers = headers,
            positionMs = positionMs,
            title = title,
            subtitleUri = subtitleUri,
        )
        for (pkg in CANDIDATE_PACKAGES) {
            val intent = buildIntent(handoff, pkg)
            if (intent.resolveActivity(context.packageManager) != null) {
                return try {
                    context.startActivity(intent)
                    true
                } catch (e: ActivityNotFoundException) {
                    Log.w(TAG, "MPV handoff failed for $pkg: ${e.message}")
                    false
                }
            }
        }
        return try {
            context.startActivity(buildIntent(handoff, null))
            true
        } catch (e: Exception) {
            Log.w(TAG, "No external player: ${e.message}")
            Toast.makeText(
                context,
                context.getString(R.string.platform_mpv_not_installed),
                Toast.LENGTH_LONG,
            ).show()
            false
        }
    }
}

object PlayerBackendSelector {
    fun preferredKind(): PlayerBackendKind =
        when (UserPreferences.playerBackend.lowercase()) {
            "mpv", "external_mpv" -> PlayerBackendKind.EXTERNAL_MPV
            else -> PlayerBackendKind.EXO
        }

    fun backend(): PlayerBackend = when (preferredKind()) {
        PlayerBackendKind.EXTERNAL_MPV -> ExternalMpvBackend
        PlayerBackendKind.EXO -> ExoPlayerBackend
    }

    fun shouldHandoffToExternal(): Boolean =
        preferredKind() == PlayerBackendKind.EXTERNAL_MPV
}
