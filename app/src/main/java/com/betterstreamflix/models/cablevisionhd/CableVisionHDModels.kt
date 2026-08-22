package com.betterstreamflix.models.cablevisionhd

import com.betterstreamflix.models.TvShow
import com.betterstreamflix.providers.CableVisionHDProvider
import org.jsoup.nodes.Document

fun Document.toTvShows(providerName: String): List<TvShow> {

    val listaNegra = listOf(
        "Mundo Latam 🌐",
        "Donar con Paypal"
    )

    val channels = this.select("div.channels > div")

    return channels.mapNotNull { channelElement ->
        val linkElement = channelElement.selectFirst("a.channel-link")

        val href = linkElement?.attr("href")
        val name = linkElement?.selectFirst("img")?.attr("alt")
        var poster = linkElement?.selectFirst("img")?.attr("src")

        if (name in listaNegra) {
            return@mapNotNull null
        }

        if (href.isNullOrEmpty() || name.isNullOrEmpty() || poster.isNullOrEmpty()) {
            return@mapNotNull null
        }

        if (!poster.startsWith("http")) {
            poster = "https://www.cablevisionhd.com/${poster.removePrefix("/")}"
        }

        TvShow(
            id = href,
            title = name,
            poster = poster,
            providerName = providerName
        )
    }
}
