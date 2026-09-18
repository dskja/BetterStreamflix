package com.dskja.betterstreamflix.extractors

/**
 * Firestream is a thin DE wrapper host; unpack packed player sources like other
 * GenericPackedSource extractors. Alias covers common TLD rotations.
 */
class FirestreamExtractor : GenericPackedSourceExtractor() {
    override val name = "Firestream"
    override val mainUrl = "https://firestream.to"
    override val aliasUrls = listOf(
        "https://firestream.click",
        "https://firestream.cam",
        "https://firestream.online",
        "https://firestream.live",
    )
}
