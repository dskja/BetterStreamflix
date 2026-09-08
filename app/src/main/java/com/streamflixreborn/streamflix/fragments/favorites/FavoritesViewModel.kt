package com.streamflixreborn.streamflix.fragments.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamflixreborn.streamflix.StreamFlixApp
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.database.AppDatabase
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.ui.UserDataNotifier
import com.streamflixreborn.streamflix.utils.CrossProviderLibrary
import com.streamflixreborn.streamflix.utils.ProviderChangeNotifier
import com.streamflixreborn.streamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch

class FavoritesViewModel(
    database: AppDatabase,
    private val providerName: String,
) : ViewModel() {

    enum class Section(val key: String) {
        MOVIES("movies"),
        TV_SHOWS("tv_shows");

        companion object {
            fun fromKey(key: String): Section? = entries.firstOrNull { it.key == key }
        }
    }

    enum class SortMode(val key: String) {
        MANUAL("manual"),
        RECENTLY_ADDED("recently_added"),
        TITLE_ASCENDING("title_ascending"),
        TITLE_DESCENDING("title_descending");

        companion object {
            fun fromKey(key: String): SortMode = entries.firstOrNull { it.key == key } ?: MANUAL
        }
    }

    data class FavoriteSection(
        val section: Section,
        val items: List<AppAdapter.Item>,
    )

    private val prefsScope: String
        get() = if (UserPreferences.isCrossProviderLibrary) "__all__" else providerName

    private val order = MutableStateFlow(readOrder())
    private val sortMode = MutableStateFlow(SortMode.fromKey(UserPreferences.getFavoriteSortMode(prefsScope)))
    private val orderRevision = MutableStateFlow(0)
    private val libraryRefresh = MutableStateFlow(0)
    @Volatile
    private var currentSections: List<FavoriteSection> = emptyList()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val favorites = combine(
        database.movieDao().getFavorites().map { },
        database.tvShowDao().getFavorites().map { },
        libraryRefresh,
    ) { _, _, tick -> tick }
        .mapLatest {
            CrossProviderLibrary.loadHomeHistory(StreamFlixApp.instance.applicationContext)
        }
        .flowOn(Dispatchers.IO)

    val sections: Flow<List<FavoriteSection>> = combine(
        favorites,
        order,
        combine(sortMode, orderRevision) { mode, _ -> mode },
    ) { history, sectionOrder, mode ->
        sectionOrder.map { section ->
            when (section) {
                Section.MOVIES -> FavoriteSection(section, sortItems(section, history.favoriteMovies, mode))
                Section.TV_SHOWS -> FavoriteSection(section, sortItems(section, history.favoriteTvShows, mode))
            }
        }.also { currentSections = it }
    }.flowOn(Dispatchers.IO)

    init {
        viewModelScope.launch {
            UserDataNotifier.updates.collect { libraryRefresh.value += 1 }
        }
        viewModelScope.launch {
            ProviderChangeNotifier.providerChangeFlow.collect {
                order.value = readOrder()
                sortMode.value = SortMode.fromKey(UserPreferences.getFavoriteSortMode(prefsScope))
                libraryRefresh.value += 1
            }
        }
    }

    fun reverseCategoryOrder() {
        setCategoryOrder(order.value.reversed())
    }

    fun setCategoryOrder(newOrder: List<Section>) {
        val normalized = (newOrder + Section.entries).distinct()
        order.value = normalized
        UserPreferences.setFavoriteCategoryOrder(prefsScope, normalized.map { it.key })
    }

    fun setSortMode(mode: SortMode) {
        sortMode.value = mode
        UserPreferences.setFavoriteSortMode(prefsScope, mode.key)
    }

    fun moveItem(section: Section, itemId: String, delta: Int) {
        val ids = currentSections.firstOrNull { it.section == section }
            ?.items
            ?.mapNotNull(::itemId)
            ?.toMutableList()
            ?: return
        val from = ids.indexOf(itemId)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, ids.lastIndex)
        if (from == to) return
        val moved = ids.removeAt(from)
        ids.add(to, moved)
        UserPreferences.setFavoriteItemOrder(prefsScope, section.key, ids)
        UserPreferences.setFavoriteSortMode(prefsScope, SortMode.MANUAL.key)
        sortMode.value = SortMode.MANUAL
        orderRevision.value += 1
    }

    fun setManualItemOrder(section: Section, itemIds: List<String>) {
        UserPreferences.setFavoriteItemOrder(prefsScope, section.key, itemIds)
        UserPreferences.setFavoriteSortMode(prefsScope, SortMode.MANUAL.key)
        sortMode.value = SortMode.MANUAL
        orderRevision.value += 1
    }

    fun currentSortMode(): SortMode = sortMode.value

    private fun sortItems(
        section: Section,
        items: List<AppAdapter.Item>,
        mode: SortMode,
    ): List<AppAdapter.Item> = when (mode) {
        SortMode.MANUAL -> {
            val savedOrder = UserPreferences.getFavoriteItemOrder(prefsScope, section.key)
            val currentIds = items.mapNotNull(::itemId)
            val currentIdSet = currentIds.toSet()
            val normalizedOrder = (
                savedOrder.filter { it in currentIdSet } +
                    currentIds.filterNot { it in savedOrder }
                ).distinct()

            if (normalizedOrder != savedOrder) {
                UserPreferences.setFavoriteItemOrder(prefsScope, section.key, normalizedOrder)
            }

            val positions = normalizedOrder.withIndex().associate { it.value to it.index }
            items.sortedBy { positions[itemId(it)] ?: Int.MAX_VALUE }
        }
        SortMode.RECENTLY_ADDED -> items.sortedByDescending(::favoriteTime)
        SortMode.TITLE_ASCENDING -> items.sortedBy(::titleLowercase)
        SortMode.TITLE_DESCENDING -> items.sortedByDescending(::titleLowercase)
    }

    private fun itemId(item: AppAdapter.Item): String? = when (item) {
        is Movie -> libraryItemId(item.providerName, item.id)
        is TvShow -> libraryItemId(item.providerName, item.id)
        else -> null
    }

    private fun libraryItemId(provider: String?, id: String): String =
        if (UserPreferences.isCrossProviderLibrary) {
            "${provider.orEmpty()}:$id"
        } else {
            id
        }

    private fun favoriteTime(item: AppAdapter.Item): Long = when (item) {
        is Movie -> item.favoritedAtMillis ?: 0L
        is TvShow -> item.favoritedAtMillis ?: 0L
        else -> 0L
    }

    private fun titleLowercase(item: AppAdapter.Item): String = when (item) {
        is Movie -> item.title.lowercase()
        is TvShow -> item.title.lowercase()
        else -> ""
    }

    private fun readOrder(): List<Section> = UserPreferences
        .getFavoriteCategoryOrder(prefsScope)
        .mapNotNull(Section::fromKey)
        .let { (it + Section.entries).distinct() }
}
