package com.dskja.betterstreamflix.download

/**
 * Where offline Media3 downloads and sidecar files are stored.
 * Changing location does not migrate existing files — restart downloads after switching.
 */
enum class DownloadStorageLocation {
    /** App-private internal storage (default, most secure). */
    INTERNAL,

    /** App-specific external storage (Android/data/… — visible in Files on many devices). */
    APP_EXTERNAL,

    /** Shared Movies/BetterStreamflix folder (easier to find; may need legacy permission on old APIs). */
    PUBLIC_MOVIES;

    companion object {
        fun fromKey(raw: String?): DownloadStorageLocation {
            val key = raw?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.name == key } ?: INTERNAL
        }
    }
}
