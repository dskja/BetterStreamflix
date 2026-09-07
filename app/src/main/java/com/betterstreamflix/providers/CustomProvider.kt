package com.betterstreamflix.providers

import com.betterstreamflix.adapters.AppAdapter
import com.betterstreamflix.models.Category
import com.betterstreamflix.models.Episode
import com.betterstreamflix.models.Genre
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.People
import com.betterstreamflix.models.TvShow
import com.betterstreamflix.models.Video
import com.betterstreamflix.utils.UserPreferences
import org.json.JSONObject

/**
 * A user-defined, configurable provider.
 *
 * This is the foundation for the **Custom Provider (Beta)** feature. Users can
 * add their own provider entry through the provider picker. In this beta stage
 * the provider is persisted, displayed, can be favorited and selected, but does
 * not yet fetch remote content.
 */
class CustomProvider private constructor(
    override val name: String,
    override val baseUrl: String,
    override val logo: String,
    override val language: String,
    val supportsMovies: Boolean,
    val supportsTvShows: Boolean,
) : Provider {

    /**
     * Whether this provider is still in beta. Displayed in the UI as a badge.
     */
    val isBeta: Boolean = true

    override suspend fun getHome(): List<Category> = emptyList()

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> = emptyList()

    override suspend fun getMovies(page: Int): List<Movie> = emptyList()

    override suspend fun getTvShows(page: Int): List<TvShow> = emptyList()

    override suspend fun getMovie(id: String): Movie = Movie(id = id, title = name).also {
        it.itemType = AppAdapter.Type.MOVIE_MOBILE
    }

    override suspend fun getTvShow(id: String): TvShow = TvShow(id = id, title = name).also {
        it.itemType = AppAdapter.Type.TV_SHOW_MOBILE
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> = emptyList()

    override suspend fun getGenre(id: String, page: Int): Genre = Genre(id = id, name = name).also {
        it.itemType = AppAdapter.Type.GENRE_GRID_MOBILE_ITEM
    }

    override suspend fun getPeople(id: String, page: Int): People = People(id = id, name = name).also {
        it.itemType = AppAdapter.Type.PEOPLE_MOBILE_ITEM
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> = emptyList()

    override suspend fun getVideo(server: Video.Server): Video = Video(source = "")


    fun toConfigJson(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("baseUrl", baseUrl)
            put("logo", logo)
            put("language", language)
            put("supportsMovies", supportsMovies)
            put("supportsTvShows", supportsTvShows)
        }
    }

    fun toDisplayString(): String = toConfigJson().toString()

    companion object {

        /**
         * Creates a new [CustomProvider] from raw user input.
         * Returns `null` if the input does not satisfy basic validation.
         */
        fun create(
            name: String,
            baseUrl: String,
            logo: String,
            language: String,
            supportsMovies: Boolean,
            supportsTvShows: Boolean,
        ): CustomProvider? {
            val trimmedName = name.trim()
            val normalizedUrl = ProviderUrlHelper.normalizeBaseUrl(baseUrl) ?: return null

            if (trimmedName.isEmpty() || trimmedName.length > 60) return null
            if (language.isBlank()) return null

            return CustomProvider(
                name = trimmedName,
                baseUrl = normalizedUrl,
                logo = logo.trim().ifEmpty { "" },
                language = language.trim().lowercase(),
                supportsMovies = supportsMovies,
                supportsTvShows = supportsTvShows,
            )
        }

        fun fromConfigJson(json: JSONObject): CustomProvider? {
            return runCatching {
                CustomProvider(
                    name = json.getString("name"),
                    baseUrl = json.getString("baseUrl"),
                    logo = json.optString("logo", ""),
                    language = json.getString("language").lowercase(),
                    supportsMovies = json.optBoolean("supportsMovies", true),
                    supportsTvShows = json.optBoolean("supportsTvShows", true),
                )
            }.getOrNull()
        }

        /**
         * Identifier used by [UserPreferences] to distinguish custom providers
         * when selecting the current provider.
         */
        const val CUSTOM_PROVIDER_PREFIX = "custom:"

        fun encodeName(name: String): String = "$CUSTOM_PROVIDER_PREFIX$name"
    }
}
