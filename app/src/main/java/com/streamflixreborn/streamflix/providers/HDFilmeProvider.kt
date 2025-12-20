package com.streamflixreborn.streamflix.providers

import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.adapters.AppAdapter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.streamflixreborn.streamflix.models.Category
import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Genre
import com.streamflixreborn.streamflix.models.People
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.extractors.Extractor
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.models.Season
import com.streamflixreborn.streamflix.models.Show
import com.streamflixreborn.streamflix.utils.DnsResolver
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.Jsoup
import retrofit2.Retrofit
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit
import java.net.URLEncoder
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Url
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.POST
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded

object HDFilmeProvider : Provider {

    override val name: String = "HDFilme"
    override val baseUrl: String = "https://hdfilme.blog"
    override val logo: String = "$baseUrl/templates/hdfilme/images/apple-touch-icon.png"
    override val language: String = "de"

    private const val USER_AGENT = "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private interface HDFilmeService {
        companion object {
            fun build(baseUrl: String): HDFilmeService {
                val clientBuilder = OkHttpClient.Builder()
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)

                val client = clientBuilder.dns(DnsResolver.doh).build()

                return Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .client(client)
                    .build()
                    .create(HDFilmeService::class.java)
            }
        }

        @Headers(USER_AGENT)
        @GET(".")
        suspend fun getHome(): Document

        @Headers(USER_AGENT)
        @GET("filme1/")
        suspend fun getMovies(): Document

        @Headers(USER_AGENT)
        @GET("filme1/page/{page}/")
        suspend fun getMovies(@Path("page") page: Int): Document

        @Headers(USER_AGENT)
        @GET("serien/")
        suspend fun getTvShows(): Document

        @Headers(USER_AGENT)
        @GET("serien/page/{page}/")
        suspend fun getTvShows(@Path("page") page: Int): Document

        @Headers(USER_AGENT)
        @GET
        suspend fun getPage(@Url url: String): Document

        @Headers(USER_AGENT)
        @GET("{path}page/{page}/")
        suspend fun getPaged(
            @Path(value = "path", encoded = true) path: String,
            @Path("page") page: Int
        ): Document

        @Headers(USER_AGENT)
        @FormUrlEncoded
        @POST("index.php?do=search")
        suspend fun search(
            @Field("do") doParam: String = "search",
            @Field("subaction") subaction: String = "search",
            @Field("search_start") searchStart: Int,
            @Field("full_search") fullSearch: Int = 0,
            @Field("result_from") resultFrom: Int,
            @Field("story") story: String
        ): Document
    }

    private val service = HDFilmeService.build(baseUrl)

    override suspend fun getHome(): List<Category> {
        val doc = service.getHome()
        val categories = mutableListOf<Category>()

        val sliderItems = doc.select("ul.glide__slides li.glide__slide").mapNotNull { parseSliderItem(it) }
        
        if (sliderItems.isNotEmpty()) {
             categories.add(Category(name = Category.FEATURED, list = sliderItems))
        }

        val listingDiv = doc.selectFirst("div.listing.grid[id=dle-content]")
        if (listingDiv != null) {
            val items = listingDiv.select("div.item.relative.mt-3").mapNotNull { parseGridItem(it) }
            if (items.isNotEmpty()) {
                categories.add(Category(name = "Filme", list = items))
            }
        }

        val latestMoviesSection = doc.selectFirst("section.sidebar-section:has(h3:containsOwn(neueste Filme eingefügt))")
        val latestMoviesItems = latestMoviesSection?.select("div.listing > a")?.mapNotNull { parseSidebarItemAsMovie(it) } ?: emptyList()
        if (latestMoviesItems.isNotEmpty()) {
            categories.add(Category(name = "Neueste Filme Eingefügt", list = latestMoviesItems))
        }

        val latestSeriesSection = doc.selectFirst("section.sidebar-section:has(h3:containsOwn(neueste Serie eingefügt))")
        val latestSeriesItems = latestSeriesSection?.select("div.listing > a")?.mapNotNull { parseSidebarItemAsTvShow(it) } ?: emptyList()
        if (latestSeriesItems.isNotEmpty()) {
            categories.add(Category(name = "Neueste Serie Eingefügt", list = latestSeriesItems))
        }

        return categories
    }

    private fun parseSliderItem(el: Element): Movie? {
        val title = el.selectFirst("h3.title")?.text()?.trim() ?: return null
        
        val linkElement = el.selectFirst("div.actions a.watchnow") ?: return null
        val href = linkElement.attr("href").trim()
        
        val bannerUrl = el.selectFirst("img")?.attr("data-src") ?: ""
        val banner = normalizeUrl(bannerUrl)
        
        return Movie(
            id = href,
            title = title,
            banner = banner
        )
    }

    private fun parseGridItem(el: Element): Movie? {
        val titleElement = el.selectFirst("h3.line-clamp-2.text-sm.mt-1.font-light.leading-snug") ?: return null
        val title = titleElement.text().trim()
        
        val linkElement = el.selectFirst("a.block.relative[href]") ?: return null
        val href = linkElement.attr("href").trim()
        
        val posterUrl = el.selectFirst("img")?.attr("data-src") ?: ""
        val poster = normalizeUrl(posterUrl)
        
        val quality = el.selectFirst("span.absolute")?.text()?.trim()
        
        return Movie(
            id = href,
            title = title,
            poster = poster,
            quality = quality
        )
    }

    private fun parseGridItemAsTvShow(el: Element): TvShow? {
        val titleElement = el.selectFirst("h3.line-clamp-2.text-sm.mt-1.font-light.leading-snug") ?: return null
        val title = titleElement.text().trim()
        
        val linkElement = el.selectFirst("a.block.relative[href]") ?: return null
        val href = linkElement.attr("href").trim()
        
        val posterUrl = el.selectFirst("img")?.attr("data-src") ?: ""
        val poster = normalizeUrl(posterUrl)
        
        val quality = el.selectFirst("span.absolute")?.text()?.trim()
        
        return TvShow(
            id = href,
            title = title,
            poster = poster,
            quality = quality
        )
    }

    private fun parseSidebarItemAsMovie(el: Element): Movie? {
        val href = el.attr("href").trim()
        if (href.isBlank()) return null
        
        val title = el.selectFirst("figcaption.hidden")?.text()?.trim()
            ?: el.selectFirst("h4.movie-title")?.text()?.trim()
            ?: return null
            
        val posterUrl = el.selectFirst("img")?.attr("data-src") ?: ""
        val poster = normalizeUrl(posterUrl)
        
        return Movie(
            id = href,
            title = title,
            poster = poster
        )
    }

    private fun parseSidebarItemAsTvShow(el: Element): TvShow? {
        val href = el.attr("href").trim()
        if (href.isBlank()) return null
        
        val title = el.selectFirst("figcaption.hidden")?.text()?.trim()
            ?: el.selectFirst("h4.movie-title")?.text()?.trim()
            ?: return null
            
        val posterUrl = el.selectFirst("img")?.attr("data-src") ?: ""
        val poster = normalizeUrl(posterUrl)
        
        return TvShow(
            id = href,
            title = title,
            poster = poster
        )
    }

    private fun normalizeUrl(url: String): String {
        if (url.isBlank()) return ""
        if (url.startsWith("http")) return url
        if (url.startsWith("//")) return "https:$url"
        return baseUrl + (if (url.startsWith("/")) url else "/$url")
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank()) {
            if (page > 1) return emptyList()

            val doc = service.getHome()

            val genreContainer = doc.selectFirst("div.dropdown-hover:has(span:containsOwn(Genre))")
            val genreLinks = genreContainer?.select("div.dropdown-content a[href]") ?: emptyList()

            return genreLinks.mapNotNull { a ->
                val href = a.attr("href").trim()
                val text = a.text().trim()
                if (href.isBlank() || text.isBlank()) return@mapNotNull null

                Genre(
                    id = href,
                    name = text
                )
            }
        }

        val resultFrom = (page - 1) * 25 + 1
        
        return try {
            val doc = service.search(
                searchStart = page,
                resultFrom = resultFrom,
                story = query
            )

            val elements = doc.select("div.listing.grid[id=dle-content] div.item.relative.mt-3")

            coroutineScope {
                elements.map { el ->
                    async {
                        val href = el.selectFirst("a.block.relative[href]")?.attr("href")?.trim() 
                            ?: return@async null

                        try {
                            val itemDoc = service.getPage(href)
                            val hasSeasons = itemDoc.select("div#se-accordion").isNotEmpty()
                            
                            if (hasSeasons) {
                                parseGridItemAsTvShow(el)
                            } else {
                                parseGridItem(el)
                            }
                        } catch (e: Exception) {
                            parseGridItem(el)
                        }
                    }
                }.awaitAll().filterNotNull()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getMovies(page: Int): List<Movie> {
        return try {
            val doc = if (page > 1) service.getMovies(page) else service.getMovies()

            doc.select("div.listing.grid[id=dle-content] div.item.relative.mt-3")
                .mapNotNull { el -> parseGridItem(el) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getTvShows(page: Int): List<TvShow> {
        return try {
            val doc = if (page > 1) service.getTvShows(page) else service.getTvShows()

            doc.select("div.listing.grid[id=dle-content] div.item.relative.mt-3")
                .mapNotNull { el -> parseGridItemAsTvShow(el) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getMovie(id: String): Movie {
        val doc = service.getPage(id)
        
        val titleRaw = doc.selectFirst("h1.font-bold")?.text()?.trim() ?: ""
        val title = titleRaw.replace(Regex("\\s*hdfilme\\s*$", RegexOption.IGNORE_CASE), "").trim()
        
        val posterUrl = doc.selectFirst("figure.inline-block img")?.attr("data-src") ?: ""
        val poster = normalizeUrl(posterUrl)
        
        val overviewDiv = doc.selectFirst("div.font-extralight.prose.max-w-none")
        val overview = overviewDiv?.select("p")?.firstOrNull()?.let { p ->
            val fullText = p.text().trim()
            val refIndex = fullText.indexOf("Referenzen von")
            if (refIndex > 0) {
                fullText.substring(0, refIndex).trim()
            } else {
                fullText
            }
        }
        
        val trailer = doc.selectFirst("iframe[src*='youtube.com/embed']")?.attr("src")?.let { src ->
            src.replace("/embed/", "/watch?v=").replace("?autoplay=1", "")
        }
        
        val metadataDiv = doc.selectFirst("div.border-b.border-gray-700.font-extralight")
        
        val genres = metadataDiv?.let { div ->
            val firstSpan = div.selectFirst("span")
            firstSpan?.select("a")?.mapNotNull { a ->
                val genreName = a.text().trim()
                if (genreName.isNotBlank()) {
                     Genre(id = genreName, name = genreName)
                } else null
            }
        } ?: emptyList()
        
        val year = metadataDiv?.select("span")?.firstOrNull { span ->
            span.text().trim().matches(Regex("^\\d{4}$"))
        }?.text()?.trim()
        
        val duration = metadataDiv?.select("span")?.firstOrNull { span ->
            span.text().trim().contains("min")
        }?.text()?.trim()?.replace("min", "")?.trim()?.toIntOrNull()
        
        val quality = metadataDiv?.children()?.filter { it.tagName() == "span" && !it.hasClass("divider") }
            ?.lastOrNull()?.text()?.trim()?.let { text ->
                if (text.matches(Regex("^\\d{4}$")) || text.contains("min", ignoreCase = true)) null else text
            }

      
        val rating = metadataDiv?.selectFirst("p.imdb-badge span.imdb-rate")?.text()?.trim()?.toDoubleOrNull()
        
        val cast = doc.select("ul.space-y-1 li:has(span:containsOwn(Schauspieler:)) a[href*='/xfsearch/actors/']")
            .mapNotNull { a ->
                val actorName = a.text().trim()
                val actorUrl = a.attr("href").trim()
                if (actorName.isNotBlank() && actorName != "N/A") {
                    People(id = actorUrl, name = actorName)
                } else null
            }
        
        return Movie(
            id = id,
            title = title,
            poster = poster,
            trailer = trailer,
            rating = rating,
            overview = overview,
            released = year,
            runtime = duration,
            quality = quality,
            genres = genres,
            cast = cast
        )
    }

    override suspend fun getTvShow(id: String): TvShow {
        val doc = service.getPage(id)
        
        val titleRaw = doc.selectFirst("h1.font-bold")?.text()?.trim() ?: ""
        val title = titleRaw.replace(Regex("\\s*hdfilme\\s*$", RegexOption.IGNORE_CASE), "").trim()
        
        val posterUrl = doc.selectFirst("figure.inline-block img")?.attr("data-src") ?: ""
        val poster = normalizeUrl(posterUrl)
        
        val overviewDiv = doc.selectFirst("div.font-extralight.prose.max-w-none")
        val overview = overviewDiv?.select("p")?.firstOrNull()?.let { p ->
            val fullText = p.text().trim()
            val refIndex = fullText.indexOf("Referenzen von")
            if (refIndex > 0) {
                fullText.substring(0, refIndex).trim()
            } else {
                fullText
            }
        }
        
        val trailer = doc.selectFirst("iframe[src*='youtube.com/embed']")?.attr("src")?.let { src ->
            src.replace("/embed/", "/watch?v=").replace("?autoplay=1", "")
        }
        
        val metadataDiv = doc.selectFirst("div.border-b.border-gray-700.font-extralight")
        
        val genres = metadataDiv?.let { div ->
            val firstSpan = div.selectFirst("span")
            firstSpan?.select("a")?.mapNotNull { a ->
                val genreName = a.text().trim()
                if (genreName.isNotBlank()) {
                    Genre(id = genreName, name = genreName)
                } else null
            }
        } ?: emptyList()
        
        val year = metadataDiv?.select("span")?.firstOrNull { span ->
            span.text().trim().matches(Regex("^\\d{4}$"))
        }?.text()?.trim()
        
        val duration = metadataDiv?.select("span")?.firstOrNull { span ->
            span.text().trim().contains("min")
        }?.text()?.trim()?.replace("min", "")?.trim()?.toIntOrNull()
        
        val quality = metadataDiv?.children()?.filter { it.tagName() == "span" && !it.hasClass("divider") }
            ?.lastOrNull()?.text()?.trim()?.let { text ->
                if (text.matches(Regex("^\\d{4}$")) || text.contains("min", ignoreCase = true)) null else text
            }

   
        val rating = metadataDiv?.selectFirst("p.imdb-badge span.imdb-rate")?.text()?.trim()?.toDoubleOrNull()
        
        val seasons = mutableListOf<Season>()
        doc.select("div#se-accordion div.su-spoiler").forEach { spoiler ->
            val seasonTitle = spoiler.selectFirst("div.su-spoiler-title")?.text()?.trim() ?: return@forEach
            val seasonNumberMatch = Regex("Staffel\\s+(\\d+)").find(seasonTitle)
            val seasonNumber = seasonNumberMatch?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach
            
            val episodes = mutableListOf<Episode>()
            val content = spoiler.selectFirst("div.su-spoiler-content")?.html() ?: ""
            
            if (content.isNotBlank()) {
                val episodeRegex = Regex("""(\d+)x(\d+)\s+Episode\s+\d+""")
                val lines = content.split("<br>")
                
                lines.forEach { line ->
                    val match = episodeRegex.find(line) ?: return@forEach
                    val epNumber = match.groupValues[2].toIntOrNull() ?: return@forEach
                    
                    episodes.add(
                        Episode(
                            id = "$id#s${seasonNumber}e$epNumber",
                            number = epNumber,
                            title = "Episode $epNumber",
                            poster = null
                        )
                    )
                }
            }
            
            seasons.add(
                Season(
                    id = "$id#season-$seasonNumber",
                    number = seasonNumber,
                    episodes = episodes.distinctBy { it.number }.sortedBy { it.number }
                )
            )
        }

        val cast = doc.select("ul.space-y-1 li:has(span:containsOwn(Schauspieler:)) a[href*='/xfsearch/actors/']")
            .mapNotNull { a ->
                val actorName = a.text().trim()
                val actorUrl = a.attr("href").trim()
                if (actorName.isNotBlank() && actorName != "N/A") {
                    People(id = actorUrl, name = actorName)
                } else null
            }
        
        return TvShow(
            id = id,
            title = title,
            poster = poster,
            banner = null,
            trailer = trailer,
            rating = rating,
            overview = overview,
            released = year,
            runtime = duration,
            quality = quality,
            genres = genres,
            cast = cast,
            seasons = seasons
        )
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        val showUrl = seasonId.substringBefore("#")
        val seasonNumber = seasonId.substringAfter("#season-").toIntOrNull() ?: return emptyList()
        
        val doc = service.getPage(showUrl)
        
        val episodes = mutableListOf<Episode>()
        
        doc.select("div#se-accordion div.su-spoiler").forEach { spoiler ->
            val seasonTitle = spoiler.selectFirst("div.su-spoiler-title")?.text()?.trim() ?: return@forEach
            val seasonNumberMatch = Regex("Staffel\\s+(\\d+)").find(seasonTitle)
            val currentSeasonNum = seasonNumberMatch?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach
            
            if (currentSeasonNum == seasonNumber) {
                val content = spoiler.selectFirst("div.su-spoiler-content")?.html() ?: return@forEach
                
                val episodeRegex = Regex("""(\d+)x(\d+)\s+Episode\s+\d+""")
                val lines = content.split("<br>")
                
                lines.forEach { line ->
                    val match = episodeRegex.find(line) ?: return@forEach
                    val epNumber = match.groupValues[2].toIntOrNull() ?: return@forEach
                    
                    episodes.add(
                        Episode(
                            id = "$showUrl#s${seasonNumber}e$epNumber",
                            number = epNumber,
                            title = "Episode $epNumber",
                            poster = null
                        )
                    )
                }
            }
        }
        
        return episodes.distinctBy { it.number }.sortedBy { it.number }
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        return try {
            val doc = if (page <= 1) {
                service.getPage(id)
            } else {
                val path = id.substringAfter(baseUrl).trimStart('/')
                service.getPaged(path, page)
            }
            val title = doc.selectFirst("h1")?.text()?.trim() ?: ""

            val elements = doc.select("div.listing.grid[id=dle-content] div.item.relative.mt-3")

            val shows = when {
                id.contains("/serien/", ignoreCase = true) -> {
                    elements.mapNotNull { el -> parseGridItemAsTvShow(el) }
                }
                else -> {
                    coroutineScope {
                        elements.map { el ->
                            async {
                                val href = el.selectFirst("a.block.relative[href]")?.attr("href")?.trim()
                                    ?: return@async null

                                try {
                                    val itemDoc = service.getPage(href)
                                    val hasSeasons = itemDoc.select("div#se-accordion").isNotEmpty()
                                    
                                    if (hasSeasons) {
                                        parseGridItemAsTvShow(el)
                                    } else {
                                        parseGridItem(el)
                                    }
                                } catch (e: Exception) {
                                    parseGridItem(el)
                                }
                            }
                        }.awaitAll().filterNotNull()
                    }
                }
            }

            Genre(
                id = id,
                name = title,
                shows = shows
            )
        } catch (e: Exception) {
            Genre(id = id, name = "")
        }
    }

    override suspend fun getPeople(id: String, page: Int): People {
        val doc = service.getPage(id)
        
        val name = doc.selectFirst("h1")?.text()?.trim() ?: ""
        
        if (page > 1) {
            return People(
                id = id,
                name = name,
                filmography = emptyList()
            )
        }
        
        val elements = doc.select("div.listing.grid[id=dle-content] div.item.relative.mt-3")

        val filmography = coroutineScope {
            elements.map { el ->
                async {
                    val href = el.selectFirst("a.block.relative[href]")?.attr("href")?.trim()
                        ?: return@async null

                    try {
                        val itemDoc = service.getPage(href)
                        val hasSeasons = itemDoc.select("div#se-accordion").isNotEmpty()
                        
                        if (hasSeasons) {
                            parseGridItemAsTvShow(el)
                        } else {
                            parseGridItem(el)
                        }
                    } catch (e: Exception) {
                        parseGridItem(el)
                    }
                }
            }.awaitAll().filterNotNull()
        }
        
        return People(
            id = id,
            name = name,
            filmography = filmography
        )
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        val doc = service.getPage(id)
        
        if (videoType is Video.Type.Episode) {
            val showUrl = id.substringBefore('#')
            val episodePart = id.substringAfter('#', "")
            val seasonNum = episodePart.substringAfter('s').substringBefore('e').toIntOrNull() ?: return emptyList()
            val epNum = episodePart.substringAfter('e').toIntOrNull() ?: return emptyList()
            
            val showDoc = service.getPage(showUrl)
            val servers = mutableListOf<Video.Server>()
            
            showDoc.select("div#se-accordion div.su-spoiler").forEach { spoiler ->
                val seasonTitle = spoiler.selectFirst("div.su-spoiler-title")?.text()?.trim() ?: return@forEach
                val seasonNumberMatch = Regex("Staffel\\s+(\\d+)").find(seasonTitle)
                val currentSeasonNum = seasonNumberMatch?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach
                
                if (currentSeasonNum == seasonNum) {
                    val content = spoiler.selectFirst("div.su-spoiler-content") ?: return@forEach
                    
                    val episodeRegex = Regex("""${seasonNum}x${epNum}\s+Episode\s+\d+""")
                    val lines = content.html().split("<br>")
                    
                    lines.forEach { line ->
                        if (episodeRegex.find(line) != null) {
                            val linkElements = Jsoup.parse(line).select("a[href]")
                            linkElements.forEach { link ->
                                val serverName = link.text().trim()
                                val serverUrl = link.attr("href").trim()
                                
                                if (!serverUrl.contains("/engine/player.php") && 
                                    !serverName.contains("Player HD", ignoreCase = true) &&
                                    !serverName.contains("4K", ignoreCase = true) &&
                                    serverUrl.isNotBlank()) {
                                    
                                    val normalized = when {
                                        serverUrl.startsWith("http") -> serverUrl
                                        serverUrl.startsWith("//") -> "https:$serverUrl"
                                        else -> serverUrl
                                    }
                                    
                                    servers.add(
                                        Video.Server(
                                            id = normalized,
                                            name = serverName.ifBlank { "Server" },
                                            src = normalized
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            return servers.distinctBy { it.src }
        }
        
        val iframeSrc = doc.selectFirst("iframe[src*='meinecloud.click']")?.attr("src")
            ?: throw Exception("Embed iframe not found")
        
        val embedUrl = normalizeUrl(iframeSrc)
        val embedDoc = service.getPage(embedUrl)
        
        return embedDoc.select("ul._player-mirrors li[data-link]")
            .filterNot { li -> 
                li.hasClass("fullhd") || li.text().contains("4K Server", ignoreCase = true)
            }
            .mapNotNull { li ->
                val dataLink = li.attr("data-link").trim()
                if (dataLink.isBlank()) return@mapNotNull null
                
                val normalized = when {
                    dataLink.startsWith("//") -> "https:$dataLink"
                    dataLink.startsWith("http") -> dataLink
                    else -> "https://$dataLink"
                }
                
                val nameText = li.ownText().ifBlank { li.text() }.trim()
                val name = nameText.ifBlank { "Server" }
                
                Video.Server(id = normalized, name = name, src = normalized)
            }
            .filter { it.src.isNotBlank() }
    }

    override suspend fun getVideo(server: Video.Server): Video {
        return Extractor.extract(server.src)
    }
}
