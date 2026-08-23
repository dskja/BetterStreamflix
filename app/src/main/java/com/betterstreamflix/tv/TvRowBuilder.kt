package com.betterstreamflix.tv

import android.content.Context
import android.view.KeyEvent
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.ObjectAdapter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter

/**
 * TV row builder — constructs rows for Leanback browse and search fragments.
 */
object TvRowBuilder {

    /**
     * Build a category row with items.
     */
    fun buildCategoryRow(
        categoryId: String,
        categoryTitle: String,
        items: List<TvCardPresenter.TvCardItem>,
        cardPresenter: TvCardPresenter,
    ): ListRow {
        val header = HeaderItem(categoryId.toLong(), categoryTitle)
        val adapter = ArrayObjectAdapter(cardPresenter)
        adapter.addAll(0, items)
        return ListRow(header, adapter)
    }

    /**
     * Build multiple category rows.
     */
    fun buildRows(
        categories: List<Pair<String, List<TvCardPresenter.TvCardItem>>>,
        cardPresenter: TvCardPresenter,
    ): ArrayObjectAdapter {
        val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        categories.forEach { (title, items) ->
            rowsAdapter.add(buildCategoryRow(title.hashCode().toString(), title, items, cardPresenter))
        }
        return rowsAdapter
    }

    /**
     * Build a search results row.
     */
    fun buildSearchRow(
        query: String,
        results: List<TvCardPresenter.TvCardItem>,
        cardPresenter: TvCardPresenter,
    ): ListRow {
        val header = HeaderItem("Search: $query".hashCode().toLong(), "Results for \"$query\"")
        val adapter = ArrayObjectAdapter(cardPresenter)
        adapter.addAll(0, results)
        return ListRow(header, adapter)
    }
}
