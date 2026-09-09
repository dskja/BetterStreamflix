package com.dskja.betterstreamflix.download

object DownloadContentKey {
    fun movie(providerName: String, movieId: String): String =
        "movie|$providerName|$movieId"

    fun episode(
        providerName: String,
        tvShowId: String,
        seasonNumber: Int,
        episodeNumber: Int,
        episodeId: String,
    ): String = "episode|$providerName|$tvShowId|$seasonNumber|$episodeNumber|$episodeId"

    fun seasonPack(
        providerName: String,
        tvShowId: String,
        seasonNumber: Int,
    ): String = "season|$providerName|$tvShowId|$seasonNumber"
}
