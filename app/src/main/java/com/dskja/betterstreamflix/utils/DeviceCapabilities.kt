package com.dskja.betterstreamflix.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Lightweight device capability checks for playback tuning
 * (Fire TV Stick RAM/decoder limits, OEM audio quirks, etc.).
 */
object DeviceCapabilities {

    fun isAmazonFireTv(context: Context): Boolean {
        if (Build.MANUFACTURER.equals("Amazon", ignoreCase = true)) return true
        if (Build.MODEL.startsWith("AFT", ignoreCase = true)) return true
        if (Build.BRAND.equals("Amazon", ignoreCase = true)) return true
        return runCatching {
            context.packageManager.hasSystemFeature("amazon.hardware.fire_tv")
        }.getOrDefault(false)
    }

    fun isLowRamDevice(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        return activityManager?.isLowRamDevice == true
    }

    fun isLeanbackDevice(context: Context): Boolean =
        runCatching {
            context.packageManager.hasSystemFeature("android.software.leanback")
        }.getOrDefault(false)

    /** Xiaomi / OEM Android TV sticks that often crash on HW decoder init. */
    fun isFragileTvOem(): Boolean {
        val mfr = Build.MANUFACTURER.orEmpty()
        val model = Build.MODEL.orEmpty()
        return mfr.equals("Xiaomi", ignoreCase = true) ||
            mfr.contains("skyworth", ignoreCase = true) ||
            mfr.contains("amlogic", ignoreCase = true) ||
            model.contains("MIBOX", ignoreCase = true) ||
            model.contains("MiTV", ignoreCase = true) ||
            model.contains("Mi Box", ignoreCase = true)
    }

    /** Fire Stick / low-RAM / fragile leanback boxes struggle with huge buffers and HW-only decode. */
    fun shouldUseConstrainedPlayback(context: Context): Boolean {
        return isAmazonFireTv(context) ||
            isLowRamDevice(context) ||
            (isLeanbackDevice(context) && isFragileTvOem())
    }

    /** Prefer software/extension renderers from the first player build (TV crash mitigation). */
    fun shouldPreferSoftwareDecoder(context: Context): Boolean {
        return isAmazonFireTv(context) ||
            isLeanbackDevice(context) ||
            isFragileTvOem() ||
            isLowRamDevice(context)
    }

    /**
     * ISO-639 language tags ExoPlayer understands for preferred audio selection.
     * TMDb/HLS streams often label tracks as `eng`/`spa` rather than `en`/`es`.
     */
    fun preferredAudioLanguages(providerLanguage: String?): List<String> {
        val base = providerLanguage
            ?.substringBefore('-')
            ?.substringBefore('_')
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() }
            ?: return emptyList()

        return when (base) {
            "es", "spa", "esp" -> listOf("spa", "es", "esp")
            "en", "eng" -> listOf("eng", "en")
            "de", "deu", "ger" -> listOf("deu", "ger", "de")
            "fr", "fra", "fre" -> listOf("fra", "fre", "fr")
            "it", "ita" -> listOf("ita", "it")
            "pt", "por" -> listOf("por", "pt", "pb", "pt-br")
            "ar", "ara" -> listOf("ara", "ar")
            "ja", "jpn" -> listOf("jpn", "ja")
            "ko", "kor" -> listOf("kor", "ko")
            "zh", "zho", "chi" -> listOf("zho", "chi", "zh", "cmn")
            else -> listOf(base)
        }
    }
}
