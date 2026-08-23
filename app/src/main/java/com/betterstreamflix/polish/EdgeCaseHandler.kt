package com.betterstreamflix.polish

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.core.content.edit

/**
 * Edge case handler — handles edge cases like empty states, error states,
 * and unusual conditions gracefully.
 */
object EdgeCaseHandler {

    /**
     * Get the appropriate empty state message.
     */
    fun getEmptyStateMessage(state: EmptyState): String {
        return when (state) {
            EmptyState.NO_SEARCH_RESULTS -> "No results found. Try a different search term."
            EmptyState.NO_FAVORITES -> "No favorites yet. Tap the heart icon to add content."
            EmptyState.NO_WATCH_HISTORY -> "No watch history. Start watching to see your history here."
            EmptyState.NO_DOWNLOADS -> "No downloads. Download content to watch offline."
            EmptyState.NO_PROVIDER -> "No provider configured. Select a provider in settings."
            EmptyState.PROVIDER_ERROR -> "Provider is currently unavailable. Try again later."
            EmptyState.NETWORK_ERROR -> "No internet connection. Check your network settings."
            EmptyState.EMPTY_LIST -> "Nothing to show here yet."
        }
    }

    /**
     * Get the appropriate empty state icon.
     */
    fun getEmptyStateIcon(state: EmptyState): Int {
        return when (state) {
            EmptyState.NO_SEARCH_RESULTS -> android.R.drawable.ic_menu_search
            EmptyState.NO_FAVORITES -> android.R.drawable.btn_star_big_off
            EmptyState.NO_WATCH_HISTORY -> android.R.drawable.ic_menu_recent_history
            EmptyState.NO_DOWNLOADS -> android.R.drawable.stat_sys_download
            EmptyState.NO_PROVIDER -> android.R.drawable.ic_menu_manage
            EmptyState.PROVIDER_ERROR -> android.R.drawable.stat_notify_error
            EmptyState.NETWORK_ERROR -> android.R.drawable.stat_sys_warning
            EmptyState.EMPTY_LIST -> android.R.drawable.ic_menu_gallery
        }
    }

    /**
     * Determine the appropriate empty state for a given context.
     */
    fun determineEmptyState(
        isOnline: Boolean,
        hasProvider: Boolean,
        itemCount: Int,
        isSearchResult: Boolean = false,
    ): EmptyState? {
        if (itemCount > 0) return null

        if (!isOnline) return EmptyState.NETWORK_ERROR
        if (!hasProvider) return EmptyState.NO_PROVIDER
        if (isSearchResult) return EmptyState.NO_SEARCH_RESULTS
        return EmptyState.EMPTY_LIST
    }

    enum class EmptyState {
        NO_SEARCH_RESULTS,
        NO_FAVORITES,
        NO_WATCH_HISTORY,
        NO_DOWNLOADS,
        NO_PROVIDER,
        PROVIDER_ERROR,
        NETWORK_ERROR,
        EMPTY_LIST,
    }
}
