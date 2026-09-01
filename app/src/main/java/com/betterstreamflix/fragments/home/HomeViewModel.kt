package com.betterstreamflix.fragments.home

import android.util.Log
import com.betterstreamflix.StreamFlixApp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betterstreamflix.adapters.AppAdapter
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.models.Category
import com.betterstreamflix.models.Episode
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.TvShow
import com.betterstreamflix.metadata.RecommendableItem
import com.betterstreamflix.metadata.RecommendationEngineV2
import com.betterstreamflix.providers.AnimeOnlineNinjaProvider
import com.betterstreamflix.providers.Provider
import com.betterstreamflix.providers.ProviderDomainManager
import com.betterstreamflix.providers.ProviderHealthMonitor
import com.betterstreamflix.ui.UserDataNotifier
import com.betterstreamflix.utils.HomeCacheStore
import com.betterstreamflix.utils.ParentalControlUtils
import com.betterstreamflix.utils.ProviderChangeNotifier
import com.betterstreamflix.utils.UserDataCache
import com.betterstreamflix.utils.UserDataCache.toCached
import com.betterstreamflix.utils.UserDataCache.toEpisode
import com.betterstreamflix.utils.UserDataCache.toMovie
import com.betterstreamflix.utils.UserPreferences
import com.betterstreamflix.utils.combine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class HomeViewModel(database: AppDatabase) : ViewModel() {

    private data class HomeHistory(
        val continueWatching: List<AppAdapter.Item>,
        val recentlyWatched: List<AppAdapter.Item>,
        val favoritesMovies: List<Movie>,
        val favoriteTvShows: List<TvShow>
    )

    private fun <T> preserveCacheOrder(
        cached: List<T>,
        incoming: List<T>,
        idOf: (T) -> String,
    ): List<T> {
        val incomingById = incoming.associateBy(idOf)
        val orderedExisting = cached.mapNotNull { cachedItem -> incomingById[idOf(cachedItem)] }
        val appendedNew = incoming.filter { incomingItem ->
            cached.none { cachedItem -> idOf(cachedItem) == idOf(incomingItem) }
        }
        return orderedExisting + appendedNew
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    private val continueWatchingTvShowCache = ConcurrentHashMap<String, TvShow>()
    private val continueWatchingSeasonEpisodesCache = ConcurrentHashMap<String, List<Episode>>()
    private val _userDataCache = MutableStateFlow<UserDataCache.UserData?>(null)
    private var currentProvider: Provider? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: Flow<State> = combine(
        _state,

        combine(
            // CONTINUE WATCHING - Cache-first (faster on slow DB devices), falls back to DB
            combine(
            _userDataCache.transformLatest { cache ->
                if (cache != null && cache.continueWatchingMovies.isNotEmpty()) {
                    emit(cache.continueWatchingMovies.map { it.toMovie() })
                } else {
                    emitAll(database.movieDao().getWatchingMovies())
                }
            }.flowOn(Dispatchers.IO),
            _userDataCache.transformLatest { cache ->
                if (cache != null && cache.continueWatchingEpisodes.isNotEmpty()) {
                    emit(cache.continueWatchingEpisodes.map { it.toEpisode() })
                } else {
                    emitAll(database.episodeDao().getWatchingEpisodes())
                }
            }.flowOn(Dispatchers.IO),
            _userDataCache.transformLatest { cache ->
                if (cache != null && cache.continueWatchingEpisodes.isNotEmpty()) {
                    emit(cache.continueWatchingEpisodes.map { it.toEpisode() })
                } else {
                    emitAll(database.episodeDao().getNextEpisodesToWatch())
                }
            }.flowOn(Dispatchers.IO),
            database.tvShowDao().getAll().flowOn(Dispatchers.IO),
        ) { watchingMovies, watchingEpisodes, watchNextEpisodes, tvShows ->

            val allEpisodes = (watchingEpisodes + watchNextEpisodes)
                .distinctBy { it.id }

            val seasonIds = allEpisodes.mapNotNull { it.season?.id }.distinct()

            val tvShowsMap = tvShows.associateBy { it.id }

            val seasonsMap = if (seasonIds.isEmpty()) {
                emptyMap()
            } else {
                database.seasonDao()
                    .getByIds(seasonIds)
                    .associateBy { it.id }
            }

            val enrichedEpisodes = enrichContinueWatchingEpisodes(
                episodes = allEpisodes.map { episode ->
                    episode.copy(
                        tvShow = episode.tvShow?.id?.let { tvShowsMap[it] } ?: episode.tvShow,
                        season = episode.season?.id?.let { seasonsMap[it] } ?: episode.season,
                    ).apply {
                        merge(episode)
                    }
                }
            )

            val orderIndex = buildMap<String, Int> {
                _userDataCache.value?.continueWatchingMovies?.forEachIndexed { index, cached ->
                    put("movie:${cached.id}", index)
                }
                _userDataCache.value?.continueWatchingEpisodes?.forEachIndexed { index, cached ->
                    put("episode:${cached.id}", index)
                }
            }

            (watchingMovies + enrichedEpisodes)
                .sortedByDescending { item ->
                    when (item) {
                        is Movie -> item.watchHistory?.lastEngagementTimeUtcMillis
                            ?: item.watchedDate?.timeInMillis
                            ?: 0L
                        is Episode -> item.watchHistory?.lastEngagementTimeUtcMillis
                            ?: item.watchedDate?.timeInMillis
                            ?: 0L
                        else -> 0L
                    }
                } as List<AppAdapter.Item>
            }.flowOn(Dispatchers.IO),

            // RECENTLY WATCHED - Recorded immediately when playback starts.
            combine(
                database.movieDao().getRecentlyWatched(),
                database.tvShowDao().getRecentlyWatched(),
            ) { movies, tvShows ->
                val episodeIds = tvShows.mapNotNull { it.lastPlayedEpisodeId }.distinct()
                val episodesById = if (episodeIds.isEmpty()) {
                    emptyMap()
                } else {
                    database.episodeDao().getByIds(episodeIds).associateBy { it.id }
                }

                val recentlyWatchedTvShows = tvShows.map { tvShow ->
                    tvShow.copy().apply {
                        merge(tvShow)
                        lastPlayedEpisode = lastPlayedEpisodeId?.let(episodesById::get)
                    }
                }

                (movies + recentlyWatchedTvShows)
                    .sortedByDescending { item ->
                        when (item) {
                            is Movie -> item.lastPlayedAtMillis ?: 0L
                            is TvShow -> item.lastPlayedAtMillis ?: 0L
                            else -> 0L
                        }
                    } as List<AppAdapter.Item>
            }.flowOn(Dispatchers.IO),
            
            // FAVORITE MOVIES
            database.movieDao().getFavorites().flowOn(Dispatchers.IO),
            
            // FAVORITE TV SHOWS
            database.tvShowDao().getFavorites().flowOn(Dispatchers.IO),

        ) { continueWatching, recentlyWatched, favoritesMovies, favoriteTvShows ->
            HomeHistory(continueWatching, recentlyWatched, favoritesMovies, favoriteTvShows)
        }.flowOn(Dispatchers.IO),

        // MOVIES DB
        _state.transformLatest { state ->
            when (state) {
                is State.SuccessLoading -> {
                    val movies = state.categories
                        .flatMap { it.list }
                        .filterIsInstance<Movie>()
                    if (movies.isEmpty()) {
                        emit(emptyList())
                    } else {
                        emitAll(database.movieDao().getByIds(movies.map { it.id }))
                    }
                }
                else -> emit(emptyList<Movie>())
            }
        }.flowOn(Dispatchers.IO),

        // TV SHOWS DB
        _state.transformLatest { state ->
            when (state) {
                is State.SuccessLoading -> {
                    val tvShows = state.categories
                        .flatMap { it.list }
                        .filterIsInstance<TvShow>()
                    if (tvShows.isEmpty()) {
                        emit(emptyList())
                    } else {
                        emitAll(database.tvShowDao().getByIds(tvShows.map { it.id }))
                    }
                }
                else -> emit(emptyList<TvShow>())
            }
        }.flowOn(Dispatchers.IO),

        ) { state, history, moviesDb, tvShowsDb ->

        when (state) {
            is State.SuccessLoading -> {

                val moviesMap = moviesDb.associateBy { it.id }
                val tvShowsMap = tvShowsDb.associateBy { it.id }

                fun mergeItem(item: AppAdapter.Item): AppAdapter.Item {
                    return when (item) {
                        is Movie -> moviesMap[item.id]
                            ?.takeIf { !item.isSame(it) }
                            ?.let { item.copy().merge(it) }
                            ?: item

                        is TvShow -> tvShowsMap[item.id]
                            ?.takeIf { !item.isSame(it) }
                            ?.let { item.copy().merge(it) }
                            ?: item

                        else -> item
                    }
                }

                val categories = ParentalControlUtils.filterCategories(listOfNotNull(

                    // FEATURED
                    state.categories
                        .find { it.name == Category.FEATURED }
                        ?.let { category ->
                            category.copy(
                                list = category.list.map(::mergeItem)
                            )
                        },

                    // CONTINUE WATCHING
                    Category(
                        name = Category.CONTINUE_WATCHING,
                        list = history.continueWatching
                            .sortedByDescending {
                                when (it) {
                                    is Episode -> it.watchHistory?.lastEngagementTimeUtcMillis
                                        ?: it.watchedDate?.timeInMillis
                                        ?: 0L

                                    is Movie -> it.watchHistory?.lastEngagementTimeUtcMillis
                                        ?: it.watchedDate?.timeInMillis
                                        ?: 0L

                                    else -> 0L
                                }
                            }
                            .distinctBy {
                                when (it) {
                                    is Episode -> it.tvShow?.id
                                    is Movie -> it.id
                                    else -> null
                                }
                            },
                    ),

                    Category(
                        name = Category.RECENTLY_WATCHED,
                        list = history.recentlyWatched,
                    ),

                    runCatching { buildRecommendedCategory(history, state.categories) }
                        .onFailure { error ->
                            if (error is kotlinx.coroutines.CancellationException) throw error
                            Log.w("HomeViewModel", "buildRecommendedCategory failed", error)
                        }
                        .getOrNull(),

                    // FAVORITES
                    Category(
                        name = Category.FAVORITE_MOVIES,
                        list = history.favoritesMovies.sortedByDescending {
                            when (it) {
                                is Movie -> it.favoritedAtMillis ?: 0L
                                else -> 0L
                            }
                        },
                    ),
                    Category(
                        name = Category.FAVORITE_TV_SHOWS,
                        list = history.favoriteTvShows.sortedByDescending {
                            when (it) {
                                is TvShow -> it.favoritedAtMillis ?: 0L
                                else -> 0L
                            }
                        },
                    ),
                ) + state.categories
                    .filter { it.name != Category.FEATURED }
                    .map { category ->
                        category.copy(
                            list = category.list.map(::mergeItem)
                        )
                    })

                State.SuccessLoading(categories, state.isStaleCache)
            }

            else -> state
        }
    }.catch { error ->
        if (error is kotlinx.coroutines.CancellationException) throw error
        Log.e("HomeViewModel", "state flow failed", error)
        emit(State.FailedLoading(error as? Exception ?: Exception(error)))
    }.flowOn(Dispatchers.IO)

    private fun buildRecommendedCategory(
        history: HomeHistory,
        providerCategories: List<Category>,
    ): Category? {
        val pool = providerCategories
            .flatMap { it.list }
            .filter { it is Movie || it is TvShow }
            .distinctBy { item ->
                when (item) {
                    is Movie -> "movie:${item.id}"
                    is TvShow -> "tv:${item.id}"
                    else -> item.hashCode().toString()
                }
            }

        if (pool.isEmpty()) {
            val fallback = (history.favoritesMovies + history.favoriteTvShows + history.recentlyWatched)
                .distinctBy { item ->
                    when (item) {
                        is Movie -> "movie:${item.id}"
                        is TvShow -> "tv:${item.id}"
                        is Episode -> "episode:${item.id}"
                        else -> item.hashCode().toString()
                    }
                }
                .take(20)
            if (fallback.isEmpty()) return null
            return Category(name = Category.RECOMMENDED_FOR_YOU, list = fallback)
        }

        val pairs = pool.mapNotNull { item ->
            val recommendable = when (item) {
                is Movie -> RecommendableItem(
                    title = item.title,
                    type = "movie",
                    providerName = item.providerName.orEmpty(),
                    thumbnailUrl = item.poster,
                )
                is TvShow -> RecommendableItem(
                    title = item.title,
                    type = "tv",
                    providerName = item.providerName.orEmpty(),
                    thumbnailUrl = item.poster,
                )
                else -> null
            } ?: return@mapNotNull null
            item to recommendable
        }

        val scored = RecommendationEngineV2.scoreByWatchHistory(
            StreamFlixApp.instance.applicationContext,
            pairs.map { it.second },
        )

        val items = scored.mapNotNull { scoredItem ->
            pairs.firstOrNull { it.second == scoredItem }?.first
        }.take(20)

        if (items.isEmpty()) return null
        return Category(name = Category.RECOMMENDED_FOR_YOU, list = items)
    }

    sealed class State {
        data object Loading : State()
        data class SuccessLoading(val categories: List<Category>, val isStaleCache: Boolean = false) : State()
        data class FailedLoading(val error: Exception) : State()
    }

    init {
        val initialProvider = UserPreferences.currentProvider
        if (initialProvider != null) {
            currentProvider = initialProvider
            loadUserDataCache(initialProvider)
        }
        viewModelScope.launch {
            ProviderChangeNotifier.providerChangeFlow.collect {
                getHome()
            }
        }

        viewModelScope.launch {
            UserDataNotifier.updates.collect {
                val provider = UserPreferences.currentProvider ?: return@collect
                loadUserDataCache(provider)
            }
        }
        getHome()
    }

    private suspend fun enrichContinueWatchingEpisodes(episodes: List<Episode>): List<Episode> = coroutineScope {
        val provider = UserPreferences.currentProvider ?: return@coroutineScope episodes

        episodes.map { episode ->
            async {
                val tvShowId = episode.tvShow?.id ?: return@async episode
                val resolvedTvShow = continueWatchingTvShowCache[tvShowId] ?: runCatching {
                    provider.getTvShow(tvShowId)
                }.getOrNull()?.also { fetchedTvShow ->
                    continueWatchingTvShowCache[tvShowId] = fetchedTvShow
                }

                val mergedTvShow = resolvedTvShow?.copy().apply {
                    this?.let { show ->
                        episode.tvShow?.let { existingTvShow -> show.merge(existingTvShow) }
                    }
                } ?: episode.tvShow

                val resolvedSeason = episode.season?.let { season ->
                    mergedTvShow?.seasons?.firstOrNull { it.id == season.id || it.number == season.number }
                        ?: season
                }

                val resolvedEpisode = if (UserPreferences.enableTmdb) {
                    val seasonId = resolvedSeason?.id
                        ?: episode.season?.id
                    seasonId?.let { key ->
                        continueWatchingSeasonEpisodesCache[key] ?: runCatching {
                            provider.getEpisodesBySeason(key)
                        }.getOrDefault(emptyList()).also { fetchedEpisodes ->
                            if (fetchedEpisodes.isNotEmpty()) {
                                continueWatchingSeasonEpisodesCache[key] = fetchedEpisodes
                            }
                        }
                    }?.firstOrNull { seasonEpisode ->
                        seasonEpisode.id == episode.id || seasonEpisode.number == episode.number
                    }
                } else {
                    null
                }

                episode.copy(
                    title = resolvedEpisode?.title ?: episode.title,
                    overview = resolvedEpisode?.overview ?: episode.overview,
                    poster = resolvedEpisode?.poster ?: episode.poster,
                    tvShow = mergedTvShow,
                    season = resolvedSeason,
                ).apply {
                    merge(episode)
                }
            }
        }.awaitAll()
    }

    fun getHome() = viewModelScope.launch(Dispatchers.IO) {
        val provider = UserPreferences.currentProvider ?: run {
            _state.emit(State.FailedLoading(IllegalStateException("No provider selected")))
            return@launch
        }


        currentProvider = provider
        val appContext = StreamFlixApp.instance.applicationContext
        val cachedCategories = HomeCacheStore.read(appContext, provider)
        val deferCachedHomeForClearance =
                provider === AnimeOnlineNinjaProvider &&
                        !AnimeOnlineNinjaProvider.hasCurrentClearanceCookie()
        if (!cachedCategories.isNullOrEmpty() && !deferCachedHomeForClearance) {
            _state.emit(State.SuccessLoading(cachedCategories, isStaleCache = false))
        } else {
            _state.emit(State.Loading)
        }

        loadUserDataCache(provider)

        try {
            val categories = provider.getHome()
            HomeCacheStore.write(appContext, provider, categories)
            ProviderHealthMonitor.recordSuccess(provider.name)
            _state.emit(State.SuccessLoading(categories, isStaleCache = false))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e("HomeViewModel", "getHome: ", e)
            ProviderHealthMonitor.recordFailure(provider.name, e.message ?: e.javaClass.simpleName)
            if (ProviderDomainManager.tryFallbackDomain(provider.name)) {
                try {
                    val categories = provider.getHome()
                    HomeCacheStore.write(appContext, provider, categories)
                    ProviderHealthMonitor.recordSuccess(provider.name)
                    _state.emit(State.SuccessLoading(categories, isStaleCache = false))
                    return@launch
                } catch (retryError: Exception) {
                    if (retryError is kotlinx.coroutines.CancellationException) throw retryError
                    Log.e("HomeViewModel", "getHome after domain fallback: ", retryError)
                }
            }
            if (cachedCategories.isNullOrEmpty()) {
                _state.emit(State.FailedLoading(e))
            } else {
                Log.w("HomeViewModel", "Serving stale cache for ${provider.name} — provider fetch failed", e)
                _state.emit(State.SuccessLoading(cachedCategories, isStaleCache = true))
            }
        }
    }

    private fun loadUserDataCache(provider: Provider) {
        val appContext = StreamFlixApp.instance.applicationContext
        val cached = UserDataCache.read(appContext, provider)
        _userDataCache.value = cached

        viewModelScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(appContext)
            val moviesDeferred = async { db.movieDao().getFavorites().first() }
            val tvShowsDeferred = async { db.tvShowDao().getFavorites().first() }
            val watchingMoviesDeferred = async { db.movieDao().getWatchingMovies().first() }
            val watchingEpisodesDeferred = async { db.episodeDao().getWatchingEpisodes().first() }

            val movies = moviesDeferred.await()
            val tvShows = tvShowsDeferred.await()
            val watchingMovies = watchingMoviesDeferred.await()
            val watchingEpisodes = watchingEpisodesDeferred.await()

            val newData = UserDataCache.UserData(
                favoritesMovies = preserveCacheOrder(
                    cached = cached?.favoritesMovies ?: emptyList(),
                    incoming = movies.filter { it.isFavorite }.map { it.toCached() },
                    idOf = { it.id },
                ),
                favoritesTvShows = preserveCacheOrder(
                    cached = cached?.favoritesTvShows ?: emptyList(),
                    incoming = tvShows.filter { it.isFavorite }.map { it.toCached() },
                    idOf = { it.id },
                ),
                continueWatchingMovies = preserveCacheOrder(
                    cached = cached?.continueWatchingMovies ?: emptyList(),
                    incoming = (movies + watchingMovies)
                        .filter { it.watchHistory != null }
                        .map { it.toCached() },
                    idOf = { it.id },
                ),
                continueWatchingEpisodes = preserveCacheOrder(
                    cached = cached?.continueWatchingEpisodes ?: emptyList(),
                    incoming = watchingEpisodes
                        .filter { it.watchHistory != null }
                        .map { it.toCached() },
                    idOf = { it.id },
                ),
            )

            UserDataCache.write(appContext, provider, newData)

            if (_userDataCache.value != newData) {
                _userDataCache.value = newData
            }
        }
    }
}
