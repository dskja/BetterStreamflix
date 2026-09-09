package com.dskja.betterstreamflix.download

enum class DownloadQualityPreset {
    BEST,
    DATA_SAVER,
    ASK,
    ;

    companion object {
        fun fromKey(key: String?): DownloadQualityPreset =
            entries.find { it.name.equals(key, ignoreCase = true) } ?: BEST
    }
}

enum class DownloadItemState {
    QUEUED,
    PREPARING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    REMOVING,
    ;

    val isActive: Boolean
        get() = this == QUEUED || this == PREPARING || this == DOWNLOADING || this == PAUSED

    companion object {
        fun fromKey(key: String?): DownloadItemState =
            entries.find { it.name.equals(key, ignoreCase = true) } ?: FAILED
    }
}

enum class DownloadKind {
    MOVIE,
    EPISODE,
}

enum class DownloadErrorCode {
    IPTV,
    DRM,
    NO_SERVERS,
    CLOUDFLARE,
    NETWORK,
    NOSPACE,
    WIFI_REQUIRED,
    EXPIRED,
    FILE_MISSING,
    UNSUPPORTED,
    UNKNOWN,
}
