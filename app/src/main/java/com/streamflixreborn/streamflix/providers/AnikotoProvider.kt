package com.streamflixreborn.streamflix.providers

import android.net.Uri
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.extractors.Extractor
import com.streamflixreborn.streamflix.models.Category
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Genre
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.People
import com.streamflixreborn.streamflix.models.Season
import com.streamflixreborn.streamflix.models.Show
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.DnsResolver
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

object AnikotoProvider : Provider {

    override val baseUrl = "https://anikototv.to"
    override val name = "Anikoto"
    override val logo = "$baseUrl/AnikotoTheme/assets/images/logo.png"
    override val language = "en"

    private val service = Service.build()

    override suspend fun getHome(): List<Category> {
        val document = service.getPage("$baseUrl/home")
        val categories = document.select("section").mapNotNull { section ->
            val title = section.selectFirst(".head .title, h2, .cat-heading")
                ?.text()
                ?.trim()
                .orEmpty()
            val items = parseCards(section.select(".item, .flw-item, .swiper-slide"))
                .distinctBy { it.itemId() }

            if (title.isBlank() || items.isEmpty()) null else Category(title, items)
        }.toMutableList()

        val featured = categories.firstOrNull()?.list?.take(10)?.map {
            when (it) {
                is Movie -> it.copy(banner = it.banner ?: it.poster)
                is TvShow -> it.copy(banner = it.banner ?: it.poster)
                else -> it
            }
        }.orEmpty()

        if (featured.isNotEmpty()) {
            categories.add(0, Category(Category.FEATURED, featured))
        }

        return categories
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank()) {
            val document = service.getPage("$baseUrl/filter")
            return document.select("#menu a[href^=/genre/], form.filters .genres li").mapNotNull {
                val link = it.selectFirst("a[href^=/genre/]")
                val label = it.selectFirst("label")
                val id = link?.attr("href")?.substringAfter("/genre/")
                    ?: label?.text()?.trim()?.lowercase()?.replace(" ", "-")
                    ?: return@mapNotNull null
                val name = link?.attr("title")?.ifBlank { link.text() }
                    ?: label?.text()?.trim()
                    ?: return@mapNotNull null
                Genre(id = id, name = name)
            }.distinctBy { it.id }.sortedBy { it.name }
        }

        val document = service.getPage("$baseUrl/filter?keyword=${query.replace(" ", "+")}&page=$page")
        return parseCards(document.select(".item, .flw-item"))
            .distinctBy { it.itemId() }
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        return parseCards(service.getPage("$baseUrl/type/movie?page=$page").select(".item, .flw-item"))
            .map {
                when (it) {
                    is Movie -> it
                    is TvShow -> Movie(
                        id = it.id,
                        title = it.title,
                        overview = it.overview,
                        runtime = it.runtime,
                        quality = it.quality,
                        rating = it.rating,
                        poster = it.poster,
                        banner = it.banner,
                        genres = it.genres,
                        recommendations = it.recommendations,
                    )
                    else -> null
                }
            }
            .filterNotNull()
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        return parseCards(service.getPage("$baseUrl/type/tv?page=$page").select(".item, .flw-item"))
            .map {
                when (it) {
                    is TvShow -> it
                    is Movie -> TvShow(
                        id = it.id,
                        title = it.title,
                        overview = it.overview,
                        runtime = it.runtime,
                        quality = it.quality,
                        rating = it.rating,
                        poster = it.poster,
                        banner = it.banner,
                        genres = it.genres,
                        recommendations = it.recommendations,
                    )
                    else -> null
                }
            }
            .filterNotNull()
    }

    override suspend fun getMovie(id: String): Movie {
        val document = service.getPage(id.toAbsoluteUrl())
        val detail = parseDetail(document)

        return Movie(
            id = id,
            title = detail.title,
            overview = detail.overview,
            released = detail.released,
            runtime = detail.runtime,
            quality = detail.quality,
            rating = detail.rating,
            poster = detail.poster,
            banner = detail.banner,
            genres = detail.genres,
            recommendations = detail.recommendations,
        )
    }

    override suspend fun getTvShow(id: String): TvShow {
        val document = service.getPage(id.toAbsoluteUrl())
        val detail = parseDetail(document)
        val watchUrl = id.toAbsoluteUrl()
        val animeId = document.selectFirst("#watch-main[data-id]")?.attr("data-id").orEmpty()
        val seasons = runCatching {
            val seasonsResponse = service.getSeasons(animeId, referer = watchUrl)
            Jsoup.parse(seasonsResponse.result.orEmpty())
                .select(".season a[href]")
                .mapIndexedNotNull { index, element ->
                    val seasonUrl = element.absUrl("href").ifBlank { element.attr("href").toAbsoluteUrl() }
                    val title = element.selectFirst(".name")?.text()?.trim()?.ifBlank { null }
                    val seasonNumber = Regex("""(\d+)""")
                        .find(title.orEmpty())
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull()
                        ?: index + 1
                    Season(
                        id = seasonUrl,
                        number = seasonNumber,
                        title = title?.takeUnless { isGenericSeasonTitle(it) },
                        poster = Regex("""background-image:\s*url\(['"]?([^'")]+)""")
                            .find(element.attr("style"))
                            ?.groupValues
                            ?.getOrNull(1),
                    )
                }
        }.getOrDefault(emptyList())

        return TvShow(
            id = id,
            title = detail.title,
            overview = detail.overview,
            released = detail.released,
            runtime = detail.runtime,
            quality = detail.quality,
            rating = detail.rating,
            poster = detail.poster,
            banner = detail.banner,
            seasons = seasons.ifEmpty {
                listOf(Season(id = watchUrl, number = inferSeasonNumber(detail.title)))
            },
            genres = detail.genres,
            recommendations = detail.recommendations,
        )
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        if (seasonId.isBlank()) return emptyList()

        val watchUrl = seasonId.toAbsoluteUrl()
        val animeId = service.getPage(watchUrl)
            .selectFirst("#watch-main[data-id]")
            ?.attr("data-id")
            .orEmpty()
        if (animeId.isBlank()) return emptyList()

        val response = service.getEpisodes(animeId, referer = watchUrl)
        val document = Jsoup.parse(response.result.orEmpty())

        return document.select(".episodes a[data-ids], a[data-id][data-ids]").mapNotNull {
            val token = it.attr("data-ids").ifBlank { return@mapNotNull null }
            Episode(
                id = "$token|$watchUrl",
                number = it.attr("data-num").toIntOrNull() ?: it.selectFirst("b")?.text()?.toIntOrNull() ?: 0,
                title = it.selectFirst(".d-title")?.text()?.trim()
                    ?: it.parent()?.attr("title")?.trim()
            )
        }
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        val path = if (id.startsWith("http")) Uri.parse(id).path.orEmpty().removePrefix("/") else "genre/$id"
        val document = service.getPage("$baseUrl/$path?page=$page")
        val name = document.selectFirst(".head .title, h1, h2")?.text()?.trim()
            ?: id.substringAfterLast("/").replace("-", " ").replaceFirstChar { it.titlecase() }

        return Genre(
            id = id,
            name = name,
            shows = parseCards(document.select(".item, .flw-item")).mapNotNull { it as? Show },
        )
    }

    override suspend fun getPeople(id: String, page: Int): People {
        throw Exception("People pages are not supported by Anikoto")
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        val (episodeToken, referer) = when (videoType) {
            is Video.Type.Movie -> {
                val document = service.getPage(id.toAbsoluteUrl())
                val firstEpisodeId = getEpisodesBySeason(id.toAbsoluteUrl()).firstOrNull()?.id.orEmpty()
                firstEpisodeId.substringBefore("|") to firstEpisodeId.substringAfter("|", id.toAbsoluteUrl())
            }
            is Video.Type.Episode -> id.substringBefore("|") to id.substringAfter("|", baseUrl)
        }

        if (episodeToken.isBlank()) return emptyList()

        val response = service.getServers(episodeToken, referer = referer)
        val document = Jsoup.parse(response.result.orEmpty())

        return document.select(".servers .type").flatMap { type ->
            val typeName = type.attr("data-type").uppercase()
            type.select("li[data-link-id]").mapNotNull {
                val linkId = it.attr("data-link-id").ifBlank { return@mapNotNull null }
                Video.Server(
                    id = "$linkId|$referer",
                    name = listOf(it.text().trim(), typeName)
                        .filter { part -> part.isNotBlank() }
                        .joinToString(" - "),
                )
            }
        }
    }

    override suspend fun getVideo(server: Video.Server): Video {
        val linkId = server.id.substringBefore("|")
        val referer = server.id.substringAfter("|", baseUrl)
        val link = service.getServerLink(linkId, referer = referer).result?.url
            ?: throw Exception("Anikoto server did not return a stream page")
        return Extractor.extract(link.toAbsoluteUrl(), server)
    }

    private fun parseDetail(document: Document): Detail {
        val info = document.selectFirst("#w-info") ?: document
        val metaRows = info.select(".bmeta .meta > div")
        fun meta(label: String): String? = metaRows.firstOrNull {
            it.ownText().trim().startsWith(label, ignoreCase = true)
        }?.selectFirst("span")?.text()?.trim()

        val recommendations = parseCards(document.select("#watch-order .item, aside.sidebar .item"))
            .mapNotNull { it as? Show }
            .distinctBy {
                when (it) {
                    is Movie -> it.id
                    is TvShow -> it.id
                }
            }

        return Detail(
            title = info.selectFirst("[itemprop=name], h1.title, .title.d-title")
                ?.text()
                ?.trim()
                .orEmpty(),
            overview = info.selectFirst(".synopsis .content, .synopsis, [itemprop=description]")
                ?.text()
                ?.trim(),
            released = meta("Aired:")?.substringBefore(" to")?.trim(),
            runtime = meta("Duration:")?.substringBefore("min")?.trim()?.toIntOrNull(),
            quality = info.selectFirst(".meta.icons .quality")?.text()?.trim(),
            rating = info.selectFirst("#w-rating[data-score]")?.attr("data-score")?.toDoubleOrNull()
                ?: meta("MAL:")?.toDoubleOrNull(),
            poster = info.selectFirst(".poster img, img[itemprop=image]")?.absUrl("src")?.ifBlank { null },
            banner = Regex("""background-image:\s*url\(['"]?([^'")]+)""")
                .find(document.selectFirst("#player[style]")?.attr("style").orEmpty())
                ?.groupValues?.getOrNull(1),
            genres = metaRows.firstOrNull {
                it.ownText().trim().startsWith("Genres:", ignoreCase = true)
            }?.select("a[href*=/genre/]")?.map {
                Genre(
                    id = it.attr("href").substringAfter("/genre/"),
                    name = it.text().trim(),
                )
            }.orEmpty(),
            recommendations = recommendations,
        )
    }

    private fun parseCards(elements: List<Element>): List<AppAdapter.Item> {
        return elements.mapNotNull { element ->
            val link = element.selectFirst("a[href*=/watch/]")
                ?: return@mapNotNull null
            val id = link.absUrl("href").ifBlank { link.attr("href").toAbsoluteUrl() }
            val title = element.selectFirst(".name, .film-name, .d-title")
                ?.text()
                ?.trim()
                ?.ifBlank { null }
                ?: link.attr("title").ifBlank { null }
                ?: element.selectFirst("img[alt]")?.attr("alt")?.trim()
                ?: return@mapNotNull null
            val poster = element.selectFirst(".poster img, img")
                ?.let { image -> image.absUrl("src").ifBlank { image.attr("data-src") } }
                ?.ifBlank { null }
            val metaText = element.select(".meta, .fd-infor").text()
            val rating = element.selectFirst(".score")?.text()
                ?.replace(Regex("""[^\d.]"""), "")
                ?.toDoubleOrNull()
            val runtime = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE)
                .find(metaText)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
            val episodes = Regex("""(\d+)\s*Eps?""", RegexOption.IGNORE_CASE)
                .find(metaText)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()

            if (metaText.contains("Movie", ignoreCase = true)) {
                Movie(
                    id = id,
                    title = title,
                    runtime = runtime,
                    rating = rating,
                    poster = poster,
                )
            } else {
                TvShow(
                    id = id,
                    title = title,
                    runtime = runtime,
                    rating = rating,
                    poster = poster,
                    seasons = episodes?.let {
                        listOf(
                            Season(
                                id = "",
                                number = 1,
                                episodes = listOf(Episode(number = it))
                            )
                        )
                    }.orEmpty(),
                )
            }
        }
    }

    private fun AppAdapter.Item.itemId(): String = when (this) {
        is Movie -> id
        is TvShow -> id
        is Genre -> id
        else -> hashCode().toString()
    }

    private fun inferSeasonNumber(title: String): Int {
        return Regex("""Season\s+(\d+)""", RegexOption.IGNORE_CASE)
            .find(title)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 1
    }

    private fun isGenericSeasonTitle(title: String): Boolean {
        return Regex("""^\s*Season\s+\d+\s*$""", RegexOption.IGNORE_CASE).matches(title)
    }

    private fun String.toAbsoluteUrl(): String {
        if (startsWith("http")) return this
        return "$baseUrl/${removePrefix("/")}"
    }

    private data class Detail(
        val title: String,
        val overview: String?,
        val released: String?,
        val runtime: Int?,
        val quality: String?,
        val rating: Double?,
        val poster: String?,
        val banner: String?,
        val genres: List<Genre>,
        val recommendations: List<Show>,
    )

    private interface Service {
        companion object {
            fun build(): Service {
                val client = OkHttpClient.Builder()
                    .dns(DnsResolver.doh)
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header("User-Agent", USER_AGENT)
                            .header("Accept-Language", "de,en-US;q=0.9,en;q=0.8")
                            .header("Cookie", COUNTRY_COOKIE)
                            .build()
                        chain.proceed(request)
                    }
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .build()

                return Retrofit.Builder()
                    .baseUrl("$baseUrl/")
                    .addConverterFactory(JsoupConverterFactory.create())
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build()
                    .create(Service::class.java)
            }
        }

        @GET
        suspend fun getPage(@Url url: String): Document

        @GET("ajax/episode/list/{id}")
        suspend fun getEpisodes(
            @Path("id") animeId: String,
            @Query("vrf") vrf: String = "",
            @Header("Accept") accept: String = AJAX_ACCEPT,
            @Header("Referer") referer: String,
            @Header("X-Requested-With") requestedWith: String = "XMLHttpRequest",
            @Header("Cookie") cookie: String = COUNTRY_COOKIE,
        ): AjaxHtmlResponse

        @GET("ajax/server/list")
        suspend fun getServers(
            @Query("servers") episodeToken: String,
            @Header("Accept") accept: String = AJAX_ACCEPT,
            @Header("Referer") referer: String,
            @Header("X-Requested-With") requestedWith: String = "XMLHttpRequest",
            @Header("Cookie") cookie: String = COUNTRY_COOKIE,
        ): AjaxHtmlResponse

        @GET("ajax/server")
        suspend fun getServerLink(
            @Query("get") linkId: String,
            @Header("Accept") accept: String = AJAX_ACCEPT,
            @Header("Referer") referer: String,
            @Header("X-Requested-With") requestedWith: String = "XMLHttpRequest",
            @Header("Cookie") cookie: String = COUNTRY_COOKIE,
        ): ServerLinkResponse

        @GET("api/seasons/{id}")
        suspend fun getSeasons(
            @Path("id") animeId: String,
            @Header("Accept") accept: String = AJAX_ACCEPT,
            @Header("Referer") referer: String,
            @Header("X-Requested-With") requestedWith: String = "XMLHttpRequest",
            @Header("Cookie") cookie: String = COUNTRY_COOKIE,
        ): AjaxHtmlResponse

        @GET
        suspend fun getStreamSources(
            @Url url: String,
            @retrofit2.http.Header("Referer") referer: String,
        ): StreamSourcesResponse
    }

    private data class AjaxHtmlResponse(
        val status: Int? = null,
        val result: String? = null,
    )

    private data class ServerLinkResponse(
        val status: Int? = null,
        val result: ServerLink? = null,
    ) {
        data class ServerLink(
            val url: String? = null,
        )
    }

    private data class StreamSourcesResponse(
        val sources: Sources? = null,
        val tracks: List<Track>? = null,
    ) {
        data class Sources(
            val file: String? = null,
        )

        data class Track(
            val file: String,
            val label: String? = null,
            val kind: String? = null,
            val default: Boolean? = null,
        )
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private const val AJAX_ACCEPT = "application/json, text/javascript, */*; q=0.01"
    private const val COUNTRY_COOKIE = "country_code=US"
}
