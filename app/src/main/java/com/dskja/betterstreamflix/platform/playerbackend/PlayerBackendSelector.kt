package com.dskja.betterstreamflix.platform.playerbackend

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
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
 * Native libmpv embedding can replace this later without changing call sites.
 */
object ExternalMpvBackend : PlayerBackend {
    override val kind = PlayerBackendKind.EXTERNAL_MPV
    override val displayName = "MPV (external)"

    private const val TAG = "ExternalMpv"
    private val CANDIDATE_PACKAGES = listOf(
        "is.xyz.mpv",
        "com.brouken.player",
        "org.videolan.vlc",
    )

    fun canResolve(context: Context): Boolean =
        CANDIDATE_PACKAGES.any { pkg ->
            runCatching {
                context.packageManager.getPackageInfo(pkg, 0)
                true
            }.getOrDefault(false)
        }

    fun open(context: Context, url: String, headers: Map<String, String> = emptyMap()): Boolean {
        val uri = Uri.parse(url)
        for (pkg in CANDIDATE_PACKAGES) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                headers["User-Agent"]?.let { putExtra("headers", arrayOf("User-Agent", it)) }
            }
            val resolved = intent.resolveActivity(context.packageManager) != null
            if (resolved) {
                return runCatching {
                    context.startActivity(intent)
                    true
                }.getOrElse {
                    Log.w(TAG, "MPV handoff failed for $pkg: ${it.message}")
                    false
                }
            }
        }
        // Generic video intent as last resort
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "video/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            true
        }.getOrElse {
            Log.w(TAG, "No external player: ${it.message}")
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
