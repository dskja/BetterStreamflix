package com.dskja.betterstreamflix.fragments.season

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.database.AppDatabase
import com.dskja.betterstreamflix.models.Season
import com.dskja.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal object SeasonSwitcher {
    fun bind(
        fragment: Fragment,
        spinner: Spinner,
        database: AppDatabase,
        tvShowId: String,
        tvShowTitle: String,
        tvShowPoster: String?,
        tvShowBanner: String?,
        currentSeasonId: String,
        currentSeasonNumber: Int,
        currentSeasonTitle: String?,
    ) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val seasons = withContext(Dispatchers.IO) {
                var list = database.seasonDao().getByTvShowId(tvShowId).sortedBy { it.number }
                if (list.isEmpty()) {
                    runCatching {
                        UserPreferences.currentProvider?.getTvShow(tvShowId)?.seasons.orEmpty()
                    }.getOrDefault(emptyList()).also { fetched ->
                        if (fetched.isNotEmpty()) {
                            fetched.forEach { season ->
                                season.tvShow = season.tvShow ?: com.dskja.betterstreamflix.models.TvShow(
                                    id = tvShowId,
                                    title = tvShowTitle,
                                )
                            }
                            database.seasonDao().insertAll(fetched)
                            list = fetched.sortedBy { it.number }
                        }
                    }
                }
                if (list.none { it.id == currentSeasonId }) {
                    list = (list + Season(
                        id = currentSeasonId,
                        number = currentSeasonNumber,
                        title = currentSeasonTitle,
                    )).sortedBy { it.number }
                }
                list
            }

            if (seasons.size <= 1) {
                spinner.visibility = View.GONE
                return@launch
            }

            spinner.visibility = View.VISIBLE
            val labels = seasons.map { season ->
                season.title?.takeIf { it.isNotBlank() }
                    ?: fragment.getString(R.string.season_number, season.number)
            }
            spinner.adapter = ArrayAdapter(
                fragment.requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                labels,
            )
            val selectedIndex = seasons.indexOfFirst { it.id == currentSeasonId }
                .takeIf { it >= 0 }
                ?: seasons.indexOfFirst { it.number == currentSeasonNumber }
                    .coerceAtLeast(0)
            spinner.setSelection(selectedIndex, false)

            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    val season = seasons.getOrNull(position) ?: return
                    if (season.id == currentSeasonId) return
                    val title = season.title?.takeIf { it.isNotBlank() }
                        ?: fragment.getString(R.string.season_number, season.number)
                    fragment.findNavController().navigate(
                        R.id.season,
                        bundleOf(
                            "tvShowId" to tvShowId,
                            "tvShowTitle" to tvShowTitle,
                            "tvShowPoster" to tvShowPoster,
                            "tvShowBanner" to tvShowBanner,
                            "seasonId" to season.id,
                            "seasonNumber" to season.number,
                            "seasonTitle" to title,
                        ),
                        NavOptions.Builder()
                            .setPopUpTo(R.id.season, true)
                            .build(),
                    )
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
    }
}
