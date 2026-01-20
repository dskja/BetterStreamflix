package com.streamflixreborn.streamflix.adapters.viewholders

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.navigation.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.streamflixreborn.streamflix.R
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.database.AppDatabase
import com.streamflixreborn.streamflix.databinding.ContentTvShowCastMobileBinding
import com.streamflixreborn.streamflix.databinding.ContentTvShowCastTvBinding
import com.streamflixreborn.streamflix.databinding.ContentTvShowMobileBinding
import com.streamflixreborn.streamflix.databinding.ContentTvShowRecommendationsMobileBinding
import com.streamflixreborn.streamflix.databinding.ContentTvShowRecommendationsTvBinding
import com.streamflixreborn.streamflix.databinding.ContentTvShowSeasonsMobileBinding
import com.streamflixreborn.streamflix.databinding.ContentTvShowSeasonsTvBinding
import com.streamflixreborn.streamflix.databinding.ContentTvShowTvBinding
import com.streamflixreborn.streamflix.databinding.ItemCategorySwiperMobileBinding
import com.streamflixreborn.streamflix.databinding.ItemTvShowGridBinding
import com.streamflixreborn.streamflix.databinding.ItemTvShowGridMobileBinding
import com.streamflixreborn.streamflix.databinding.ItemTvShowMobileBinding
import com.streamflixreborn.streamflix.databinding.ItemTvShowTvBinding
import com.streamflixreborn.streamflix.fragments.genre.GenreMobileFragment
import com.streamflixreborn.streamflix.fragments.genre.GenreMobileFragmentDirections
import com.streamflixreborn.streamflix.fragments.genre.GenreTvFragment
import com.streamflixreborn.streamflix.fragments.genre.GenreTvFragmentDirections
import com.streamflixreborn.streamflix.fragments.home.HomeMobileFragment
import com.streamflixreborn.streamflix.fragments.home.HomeMobileFragmentDirections
import com.streamflixreborn.streamflix.fragments.home.HomeTvFragment
import com.streamflixreborn.streamflix.fragments.home.HomeTvFragmentDirections
import com.streamflixreborn.streamflix.fragments.movie.MovieMobileFragment
import com.streamflixreborn.streamflix.fragments.movie.MovieMobileFragmentDirections
import com.streamflixreborn.streamflix.fragments.movie.MovieTvFragment
import com.streamflixreborn.streamflix.fragments.movie.MovieTvFragmentDirections
import com.streamflixreborn.streamflix.fragments.people.PeopleMobileFragment
import com.streamflixreborn.streamflix.fragments.people.PeopleMobileFragmentDirections
import com.streamflixreborn.streamflix.fragments.people.PeopleTvFragment
import com.streamflixreborn.streamflix.fragments.people.PeopleTvFragmentDirections
import com.streamflixreborn.streamflix.fragments.search.SearchMobileFragment
import com.streamflixreborn.streamflix.fragments.search.SearchMobileFragmentDirections
import com.streamflixreborn.streamflix.fragments.search.SearchTvFragment
import com.streamflixreborn.streamflix.fragments.search.SearchTvFragmentDirections
import com.streamflixreborn.streamflix.fragments.tv_show.TvShowMobileFragment
import com.streamflixreborn.streamflix.fragments.tv_show.TvShowMobileFragmentDirections
import com.streamflixreborn.streamflix.fragments.tv_show.TvShowTvFragment
import com.streamflixreborn.streamflix.fragments.tv_show.TvShowTvFragmentDirections
import com.streamflixreborn.streamflix.fragments.tv_shows.TvShowsMobileFragment
import com.streamflixreborn.streamflix.fragments.tv_shows.TvShowsMobileFragmentDirections
import com.streamflixreborn.streamflix.fragments.tv_shows.TvShowsTvFragment
import com.streamflixreborn.streamflix.fragments.tv_shows.TvShowsTvFragmentDirections
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.ui.ShowOptionsMobileDialog
import com.streamflixreborn.streamflix.ui.ShowOptionsTvDialog
import com.streamflixreborn.streamflix.ui.SpacingItemDecoration
import com.streamflixreborn.streamflix.utils.dp
import androidx.preference.Preference
import com.streamflixreborn.streamflix.utils.format
import com.streamflixreborn.streamflix.utils.getCurrentFragment
import com.streamflixreborn.streamflix.utils.toActivity
import java.util.Locale
import com.streamflixreborn.streamflix.utils.UserPreferences
import com.streamflixreborn.streamflix.providers.Provider
import android.view.KeyEvent
import com.streamflixreborn.streamflix.databinding.ContentTvShowDirectorsMobileBinding
import com.streamflixreborn.streamflix.databinding.ContentTvShowDirectorsTvBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TvShowViewHolder(
    private val _binding: ViewBinding
) : RecyclerView.ViewHolder(
    _binding.root
) {

    private val context = itemView.context
    private val database = AppDatabase.getInstance(context)
    private lateinit var tvShow: TvShow
    private val TAG = "TrailerChoiceDebug" // Logging Tag

    companion object {
        private const val KEY_PREFERRED_PLAYER = "preferred_player"
        private const val KEY_SMARTTUBE_PACKAGE = "preferred_smarttube_package" // New key for saving the exact package
        private const val PLAYER_YOUTUBE = "youtube"
        private const val PLAYER_SMARTTUBE = "smarttube"
        private const val PLAYER_ASK = "ask"
        private const val SMARTTUBE_STABLE_PACKAGE = "org.smarttube.stable"
        private const val SMARTTUBE_BETA_PACKAGE = "org.smarttube.beta"
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        private const val YOUTUBE_TV_PACKAGE = "com.google.android.tv.youtube"
    }

    val childRecyclerView: RecyclerView?
        get() = when (_binding) {
            is ContentTvShowSeasonsMobileBinding -> _binding.rvTvShowSeasons
            is ContentTvShowSeasonsTvBinding -> _binding.hgvTvShowSeasons
            is ContentTvShowCastMobileBinding -> _binding.rvTvShowCast
            is ContentTvShowCastTvBinding -> _binding.hgvTvShowCast
            is ContentTvShowRecommendationsMobileBinding -> _binding.rvTvShowRecommendations
            is ContentTvShowRecommendationsTvBinding -> _binding.hgvTvShowRecommendations
            else -> null
        }

    fun bind(tvShow: TvShow) {
        this.tvShow = tvShow

        when (_binding) {
            is ItemTvShowMobileBinding -> displayMobileItem(_binding)
            is ItemTvShowTvBinding -> displayTvItem(_binding)
            is ItemTvShowGridMobileBinding -> displayGridMobileItem(_binding)
            is ItemTvShowGridBinding -> displayGridTvItem(_binding)
            is ItemCategorySwiperMobileBinding -> displaySwiperMobileItem(_binding)

            is ContentTvShowMobileBinding -> displayTvShowMobile(_binding)
            is ContentTvShowTvBinding -> displayTvShowTv(_binding)
            is ContentTvShowSeasonsMobileBinding -> displaySeasonsMobile(_binding)
            is ContentTvShowSeasonsTvBinding -> displaySeasonsTv(_binding)
            is ContentTvShowDirectorsMobileBinding -> displayDirectorsMobile(_binding)
            is ContentTvShowDirectorsTvBinding -> displayDirectorsTv(_binding)
            is ContentTvShowCastMobileBinding -> displayCastMobile(_binding)
            is ContentTvShowCastTvBinding -> displayCastTv(_binding)
            is ContentTvShowRecommendationsMobileBinding -> displayRecommendationsMobile(_binding)
            is ContentTvShowRecommendationsTvBinding -> displayRecommendationsTv(_binding)
        }
    }
    private fun checkProviderAndRun(action: () -> Unit) {
        if (!tvShow.providerName.isNullOrBlank() && tvShow.providerName != UserPreferences.currentProvider?.name) {
            Provider.providers.keys.find { it.name == tvShow.providerName }?.let {
                UserPreferences.currentProvider = it
                AppDatabase.setup(itemView.context)
            }
        }
        action()
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun getInstalledSmartTubePackages(): List<String> {
        val installed = mutableListOf<String>()
        if (isPackageInstalled(SMARTTUBE_STABLE_PACKAGE)) installed.add(SMARTTUBE_STABLE_PACKAGE)
        if (isPackageInstalled(SMARTTUBE_BETA_PACKAGE)) installed.add(SMARTTUBE_BETA_PACKAGE)
        return installed
    }

    private fun launchSmartTube(packageName: String, trailerUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW, trailerUrl.toUri())
        intent.setPackage(packageName)
        context.startActivity(intent)
    }

    private fun showSmartTubeVersionDialog(packages: List<String>, trailerUrl: String, shouldSavePreference: Boolean) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        
        val items = packages.map { pkg ->
            if (pkg == SMARTTUBE_STABLE_PACKAGE) context.getString(R.string.smarttube_stable)
            else context.getString(R.string.smarttube_beta)
        }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.choose_smarttube_version))
            .setItems(items) { _, which ->
                val selectedPackage = packages[which]
                
                if (shouldSavePreference) {
                    // Salva la scelta dell'utente se la preferenza principale è "smarttube"
                    editor.putString(KEY_SMARTTUBE_PACKAGE, selectedPackage).apply()
                    Log.d(TAG, "SmartTube version saved: $selectedPackage")
                }
                
                launchSmartTube(selectedPackage, trailerUrl)
            }.show()
    }

    private fun handleSmartTubeSelection(trailerUrl: String, logPrefix: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val savedPackage = prefs.getString(KEY_SMARTTUBE_PACKAGE, null)
        val stPackages = getInstalledSmartTubePackages()

        Log.d(TAG, "$logPrefix: SmartTube packages found: ${stPackages.size}. Saved package: $savedPackage")
        
        if (stPackages.isEmpty()) {
            // Caso 1: Nessuna SmartTube installata. Fallback su YouTube.
            Log.d(TAG, "$logPrefix: No SmartTube installed, falling back to YouTube")
            context.startActivity(Intent(Intent.ACTION_VIEW, trailerUrl.toUri()))
            return
        }

        if (stPackages.size == 1) {
            // Caso 2: Una sola SmartTube installata. Avvia direttamente.
            Log.d(TAG, "$logPrefix: Only one SmartTube installed: ${stPackages[0]}. Launching directly.")
            launchSmartTube(stPackages[0], trailerUrl)
            return
        }
        
        // Caso 3: Stable e Beta installate.
        if (savedPackage != null && stPackages.contains(savedPackage)) {
            // Caso 3a: Versione preferita è installata. Avvia direttamente la versione salvata.
            Log.d(TAG, "$logPrefix: Saved SmartTube version found: $savedPackage. Launching directly.")
            launchSmartTube(savedPackage, trailerUrl)
        } else {
            // Caso 3b: Nessuna preferenza salvata O la versione salvata non è più installata. Chiedi all'utente e salva la nuova scelta.
            Log.d(TAG, "$logPrefix: Saved version invalid or missing. Asking user which version to use.")
            showSmartTubeVersionDialog(stPackages, trailerUrl, true)
        }
    }

    private fun handleTrailerClick(trailer: String, logPrefix: String) {
        Log.d(TAG, "$logPrefix: Clicked. Trailer URL: $trailer")

        val youtubeIntent = Intent(Intent.ACTION_VIEW, trailer.toUri())
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val preferredPlayer = prefs.getString(KEY_PREFERRED_PLAYER, PLAYER_ASK)
        Log.d(TAG, "$logPrefix: Preferred player from settings: $preferredPlayer")

        when (preferredPlayer) {
            PLAYER_SMARTTUBE -> {
                handleSmartTubeSelection(trailer, logPrefix)
            }
            PLAYER_YOUTUBE -> {
                Log.d(TAG, "$logPrefix: Launching YouTube (Preferred)")
                context.startActivity(youtubeIntent)
            }
            else -> { // PLAYER_ASK or nothing set
                val stPackages = getInstalledSmartTubePackages()
                if (stPackages.isNotEmpty()) {
                    Log.d(TAG, "$logPrefix: Showing choice dialog (Ask)")
                    AlertDialog.Builder(context)
                        .setTitle(context.getString(R.string.watch_trailer_with))
                        .setItems(arrayOf(context.getString(R.string.youtube), context.getString(R.string.smarttube))) { _, which ->
                            if (which == 0) {
                                Log.d(TAG, "$logPrefix: Dialog (Ask): YouTube selected")
                                context.startActivity(youtubeIntent)
                            } else {
                                Log.d(TAG, "$logPrefix: Dialog (Ask): SmartTube selected")
                                // Qui, non salvare la preferenza per la versione SmartTube,
                                // ma chiedi quale usare se ci sono due installazioni.
                                if (stPackages.size > 1) {
                                    showSmartTubeVersionDialog(stPackages, trailer, false)
                                } else {
                                    launchSmartTube(stPackages[0], trailer)
                                }
                            }
                        }.show()
                } else {
                    Log.d(TAG, "$logPrefix: SmartTube not found, launching YouTube directly")
                    context.startActivity(youtubeIntent)
                }
            }
        }
    }

    private fun displayMobileItem(binding: ItemTvShowMobileBinding) {
        binding.root.apply {
            setOnClickListener {
                checkProviderAndRun {
                    when (context.toActivity()?.getCurrentFragment()) {
                        is HomeMobileFragment -> findNavController().navigate(
                            HomeMobileFragmentDirections.actionHomeToTvShow(id = tvShow.id)
                        )
                        is MovieMobileFragment -> findNavController().navigate(
                            MovieMobileFragmentDirections.actionMovieToTvShow(id = tvShow.id)
                        )
                        is TvShowMobileFragment -> findNavController().navigate(
                            TvShowMobileFragmentDirections.actionTvShowToTvShow(id = tvShow.id)
                        )
                    }
                }
            }
            setOnLongClickListener {
                ShowOptionsMobileDialog(context, tvShow)
                    .show()
                true
            }
        }

        Glide.with(context)
            .load(tvShow.poster)
            .centerCrop()
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(binding.ivTvShowPoster)

        binding.tvTvShowQuality.apply {
            text = tvShow.quality ?: ""
            visibility = when {
                tvShow.quality.isNullOrEmpty() -> View.GONE
                else -> View.VISIBLE
            }
        }

        binding.tvTvShowLastEpisode.text = tvShow.seasons.lastOrNull()?.let { season ->
            season.episodes.lastOrNull()?.let { episode ->
                if (season.number != 0) {
                    context.getString(
                        R.string.tv_show_item_season_number_episode_number,
                        season.number,
                        episode.number
                    )
                } else {
                    context.getString(
                        R.string.tv_show_item_episode_number,
                        episode.number
                    )
                }
            }
        } ?: context.getString(R.string.tv_show_item_type)

        binding.tvTvShowTitle.text = tvShow.title
    }

    private fun displayTvItem(binding: ItemTvShowTvBinding) {
        binding.root.apply {
            isFocusable = true
            setOnClickListener {
                checkProviderAndRun {
                    when (context.toActivity()?.getCurrentFragment()) {
                        is HomeTvFragment -> findNavController().navigate(HomeTvFragmentDirections.actionHomeToTvShow(id = tvShow.id))
                        is TvShowsTvFragment -> findNavController().navigate(TvShowsTvFragmentDirections.actionTvShowsToTvShow(id = tvShow.id))
                        is GenreTvFragment -> findNavController().navigate(GenreTvFragmentDirections.actionGenreToTvShow(id = tvShow.id))
                        is SearchTvFragment -> findNavController().navigate(SearchTvFragmentDirections.actionSearchToTvShow(id = tvShow.id))
                        is MovieTvFragment -> findNavController().navigate(MovieTvFragmentDirections.actionMovieToTvShow(id = tvShow.id))
                        is TvShowTvFragment -> findNavController().navigate(TvShowTvFragmentDirections.actionTvShowToTvShow(id = tvShow.id))
                        is PeopleTvFragment -> findNavController().navigate(PeopleTvFragmentDirections.actionPeopleToTvShow(id = tvShow.id))
                    }
                }
            }
            setOnLongClickListener {
                ShowOptionsTvDialog(context, tvShow).show()
                true
            }
            setOnFocusChangeListener { _, hasFocus ->
                val animation = when {
                    hasFocus -> AnimationUtils.loadAnimation(context, R.anim.zoom_in)
                    else -> AnimationUtils.loadAnimation(context, R.anim.zoom_out)
                }
                binding.root.startAnimation(animation)
                animation.fillAfter = true

                if (hasFocus) {
                    when (val fragment = context.toActivity()?.getCurrentFragment()) {
                        is HomeTvFragment -> fragment.updateBackground(tvShow.banner)
                    }
                }
            }
        }

        Glide.with(context)
            .load(tvShow.poster)
            .fallback(R.drawable.glide_fallback_cover)
            .centerCrop()
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(binding.ivTvShowPoster)
        binding.tvTvShowQuality.apply {
            text = tvShow.quality ?: ""
            visibility = when {
                tvShow.quality.isNullOrEmpty() -> View.GONE
                else -> View.VISIBLE
            }
        }
        binding.tvTvShowLastEpisode.text = tvShow.seasons.lastOrNull()?.let { season ->
            season.episodes.lastOrNull()?.let { episode ->
                if (season.number != 0) {
                    context.getString(R.string.tv_show_item_season_number_episode_number, season.number, episode.number)
                } else {
                    context.getString(R.string.tv_show_item_episode_number, episode.number)
                }
            }
        } ?: context.getString(R.string.tv_show_item_type)
        binding.tvTvShowTitle.text = tvShow.title
    }

    private fun displayGridMobileItem(binding: ItemTvShowGridMobileBinding) {
        binding.root.apply {
            setOnClickListener {
                checkProviderAndRun {
                    when (context.toActivity()?.getCurrentFragment()) {
                        is GenreMobileFragment -> findNavController().navigate(
                            GenreMobileFragmentDirections.actionGenreToTvShow(id = tvShow.id)
                        )
                        is PeopleMobileFragment -> findNavController().navigate(
                            PeopleMobileFragmentDirections.actionPeopleToTvShow(id = tvShow.id)
                        )
                        is SearchMobileFragment -> findNavController().navigate(
                            SearchMobileFragmentDirections.actionSearchToTvShow(id = tvShow.id)
                        )
                        is TvShowsMobileFragment -> findNavController().navigate(
                            TvShowsMobileFragmentDirections.actionTvShowsToTvShow(id = tvShow.id)
                        )
                    }
                }
            }
            setOnLongClickListener {
                ShowOptionsMobileDialog(context, tvShow)
                    .show()
                true
            }
        }

        Glide.with(context)
            .load(tvShow.poster)
            .centerCrop()
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(binding.ivTvShowPoster)

        binding.tvTvShowQuality.apply {
            text = tvShow.quality ?: ""
            visibility = when {
                tvShow.quality.isNullOrEmpty() -> View.GONE
                else -> View.VISIBLE
            }
        }

        binding.tvTvShowLastEpisode.text = tvShow.seasons.lastOrNull()?.let { season ->
            season.episodes.lastOrNull()?.let { episode ->
                if (season.number != 0) {
                    context.getString(
                        R.string.tv_show_item_season_number_episode_number,
                        season.number,
                        episode.number
                    )
                } else {
                    context.getString(
                        R.string.tv_show_item_episode_number,
                        episode.number
                    )
                }
            }
        } ?: context.getString(R.string.tv_show_item_type)

        binding.tvTvShowTitle.text = tvShow.title
    }

    private fun displayGridTvItem(binding: ItemTvShowGridBinding) {
        binding.root.apply {
            isFocusable = true
            setOnClickListener {
                checkProviderAndRun {
                    when (context.toActivity()?.getCurrentFragment()) {
                        is HomeTvFragment -> findNavController().navigate(HomeTvFragmentDirections.actionHomeToTvShow(id = tvShow.id))
                        is TvShowsTvFragment -> findNavController().navigate(TvShowsTvFragmentDirections.actionTvShowsToTvShow(id = tvShow.id))
                        is GenreTvFragment -> findNavController().navigate(GenreTvFragmentDirections.actionGenreToTvShow(id = tvShow.id))
                        is SearchTvFragment -> findNavController().navigate(SearchTvFragmentDirections.actionSearchToTvShow(id = tvShow.id))
                        is PeopleTvFragment -> findNavController().navigate(PeopleTvFragmentDirections.actionPeopleToTvShow(id = tvShow.id))
                    }
                }
            }
            setOnLongClickListener {
                ShowOptionsTvDialog(context, tvShow).show()
                true
            }
            setOnFocusChangeListener { _, hasFocus ->
                val animation = when {
                    hasFocus -> AnimationUtils.loadAnimation(context, R.anim.zoom_in)
                    else -> AnimationUtils.loadAnimation(context, R.anim.zoom_out)
                }
                binding.root.startAnimation(animation)
                animation.fillAfter = true
            }
        }

        Glide.with(context)
            .load(tvShow.poster)
            .fallback(R.drawable.glide_fallback_cover)
            .centerCrop()
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(binding.ivTvShowPoster)
        binding.tvTvShowQuality.apply {
            text = tvShow.quality ?: ""
            visibility = when {
                tvShow.quality.isNullOrEmpty() -> View.GONE
                else -> View.VISIBLE
            }
        }
        binding.tvTvShowLastEpisode.text = tvShow.seasons.lastOrNull()?.let { season ->
            season.episodes.lastOrNull()?.let { episode ->
                if (season.number != 0) {
                    context.getString(R.string.tv_show_item_season_number_episode_number, season.number, episode.number)
                } else {
                    context.getString(R.string.tv_show_item_episode_number, episode.number)
                }
            }
        } ?: context.getString(R.string.tv_show_item_type)
        binding.tvTvShowTitle.text = tvShow.title
    }


    private fun displaySwiperMobileItem(binding: ItemCategorySwiperMobileBinding) {
        Glide.with(context)
            .load(tvShow.banner)
            .centerCrop()
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(binding.ivSwiperBackground)

        binding.tvSwiperTitle.text = tvShow.title

        binding.tvSwiperTvShowLastEpisode.apply {
            text = tvShow.seasons.lastOrNull()?.let { season ->
                season.episodes.lastOrNull()?.let { episode ->
                    if (season.number != 0) {
                        context.getString(
                            R.string.tv_show_item_season_number_episode_number,
                            season.number,
                            episode.number
                        )
                    } else {
                        context.getString(
                            R.string.tv_show_item_episode_number,
                            episode.number
                        )
                    }
                }
            } ?: context.getString(R.string.tv_show_item_type)
        }

        binding.tvSwiperQuality.apply {
            text = tvShow.quality
            visibility = when {
                text.isNullOrEmpty() -> View.GONE
                else -> View.VISIBLE
            }
        }

        binding.tvSwiperReleased.apply {
            text = tvShow.released?.format("yyyy")
            visibility = when {
                text.isNullOrEmpty() -> View.GONE
                else -> View.VISIBLE
            }
        }

        binding.tvSwiperRating.apply {
            text = tvShow.rating?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "N/A"
            visibility = when {
                text.isNullOrEmpty() -> View.GONE
                else -> View.VISIBLE
            }
        }

        binding.ivSwiperRatingIcon.visibility = binding.tvSwiperRating.visibility

        binding.tvSwiperOverview.apply {
            setOnClickListener {
                maxLines = when (maxLines) {
                    2 -> Int.MAX_VALUE
                    else -> 2
                }
            }

            text = tvShow.overview
        }

        binding.btnSwiperWatchNow.apply {
            setOnClickListener {
                handler.removeCallbacksAndMessages(null)
                findNavController().navigate(
                    HomeMobileFragmentDirections.actionHomeToTvShow(
                        id = tvShow.id,
                    )
                )
            }
        }

        binding.pbSwiperProgress.visibility = View.GONE
    }


    private fun displayTvShowMobile(binding: ContentTvShowMobileBinding) {
        binding.ivTvShowPoster.run {
            Glide.with(context)
                .load(tvShow.poster)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(this)
            visibility = when {
                tvShow.poster.isNullOrEmpty() -> View.GONE
                else -> View.VISIBLE
            }
        }

        binding.tvTvShowTitle.text = tvShow.title

        binding.tvTvShowRating.text = tvShow.rating?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "N/A"

        binding.tvTvShowQuality.apply {
            text = tvShow.quality
            visibility = when {
                text.isNullOrEmpty() -> View.GONE
                else -> View.VISIBLE
            }
        }

        binding.tvTvShowReleased.apply {
            text = tvShow.released?.format("yyyy")
            visibility = when {
                text.isNullOrEmpty() -> View.GONE
                else -> View.VISIBLE
            }
        }

        binding.tvTvShowRuntime.apply {
            text = tvShow.runtime?.let {
                val hours = it / 60
                val minutes = it % 60
                when {
                    hours > 0 -> context.getString(
                        R.string.tv_show_runtime_hours_minutes,
                        hours,
                        minutes
                    )
                    else -> context.getString(R.string.tv_show_runtime_minutes, minutes)
                }
            }
            visibility = when {
                text.isNullOrEmpty() -> View.GONE
                else -> View.VISIBLE
            }
        }

        binding.tvTvShowGenres.apply {
            text = tvShow.genres.joinToString(", ") { it.name }
            visibility = when {
                tvShow.genres.isEmpty() -> View.GONE
                else -> View.VISIBLE
            }
        }

        binding.tvTvShowOverview.text = tvShow.overview

        val episodeToWatch = tvShow.episodeToWatch
        binding.btnTvShowWatchEpisode.apply {
            setOnClickListener {
                if (episodeToWatch == null) return@setOnClickListener
                checkProviderAndRun { // <-- LÓGICA APLICADA
                    findNavController().navigate(
                        TvShowMobileFragmentDirections.actionTvShowToPlayer(
                        id = episodeToWatch.id,
                        title = tvShow.title,
                        subtitle = episodeToWatch.season?.takeIf { it.number != 0 }?.let { season ->
                            context.getString(
                                R.string.player_subtitle_tv_show,
                                season.number,
                                episodeToWatch.number,
                                episodeToWatch.title ?: context.getString(
                                    R.string.episode_number,
                                    episodeToWatch.number
                                )
                            )
                        } ?: context.getString(
                            R.string.player_subtitle_tv_show_episode_only,
                            episodeToWatch.number,
                            episodeToWatch.title ?: context.getString(
                                R.string.episode_number,
                                episodeToWatch.number
                            )
                        ),
                        videoType = Video.Type.Episode(
                            id = episodeToWatch.id,
                            number = episodeToWatch.number,
                            title = episodeToWatch.title,
                            poster = episodeToWatch.poster,
                            overview = episodeToWatch.overview,
                            tvShow = Video.Type.Episode.TvShow(
                                id = tvShow.id,
                                title = tvShow.title,
                                poster = tvShow.poster,
                                banner = tvShow.banner,
                                releaseDate = tvShow.released?.format("yyyy-MM-dd"),
                                imdbId = tvShow.imdbId,
                            ),
                            season = Video.Type.Episode.Season(
                                number = episodeToWatch.season?.number ?: 0,
                                title = episodeToWatch.season?.title,
                            ),
                        ),
                    )
                )
            }}

            text = if (episodeToWatch != null) {
                episodeToWatch.season?.takeIf { it.number != 0 }?.let { season ->
                    context.getString(
                        R.string.tv_show_watch_season_episode,
                        season.number,
                        episodeToWatch.number
                    )
                } ?: context.getString(
                    R.string.tv_show_watch_episode,
                    episodeToWatch.number
                )
            } else ""
            visibility = when {
                episodeToWatch != null -> View.VISIBLE
                else -> View.GONE
            }
        }

        binding.pbTvShowWatchEpisodeLoading.apply {
            visibility = when {
                episodeToWatch != null -> View.GONE
                else -> View.VISIBLE
            }
        }

        binding.pbTvShowProgressEpisode.apply {
            val watchHistory = episodeToWatch?.watchHistory

            progress = when {
                watchHistory != null -> (watchHistory.lastPlaybackPositionMillis * 100 / watchHistory.durationMillis.toDouble()).toInt()
                else -> 0
            }
            visibility = when {
                watchHistory != null -> View.VISIBLE
                else -> View.GONE
            }
        }

        binding.btnTvShowTrailer.apply {
            val trailer = tvShow.trailer
            setOnClickListener {
                if (trailer != null) handleTrailerClick(trailer, "TvShowMobile")
            }
            visibility = if (trailer != null) View.VISIBLE else View.GONE
        }
        binding.btnTvShowFavorite.apply {

            fun Boolean.drawable() = when (this) {
                true -> R.drawable.ic_favorite_enable
                false -> R.drawable.ic_favorite_disable
            }

            setOnClickListener {
                CoroutineScope(Dispatchers.IO).launch {
                    val dao = database.tvShowDao()
                    val current = dao.getById(tvShow.id)?.isFavorite ?: false
                    val newValue = !current

                    dao.setFavorite(tvShow.id, newValue)

                    withContext(Dispatchers.Main) {
                        tvShow.isFavorite = newValue
                        setImageDrawable(
                            ContextCompat.getDrawable(context, newValue.drawable())
                        )
                    }
                }
            }

            setImageDrawable(
                ContextCompat.getDrawable(context, tvShow.isFavorite.drawable())
            )
        }
    }

    private fun displayTvShowTv(binding: ContentTvShowTvBinding) {
        binding.ivTvShowPoster.run {
            Glide.with(context)
                .load(tvShow.poster)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(this)
            visibility = when {
                tvShow.poster.isNullOrEmpty() -> View.GONE
                else -> View.VISIBLE
            }
        }

        binding.tvTvShowTitle.text = tvShow.title

        binding.tvTvShowRating.text = tvShow.rating?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "N/A"

        binding.tvTvShowQuality.apply {
            text = tvShow.quality
            visibility = when {
                text.isNullOrEmpty() -> View.GONE
                else -> View.VISIBLE
            }
        }

        binding.tvTvShowReleased.apply {
            text = tvShow.released?.format("yyyy")
            visibility = when {
                text.isNullOrEmpty() -> View.GONE
                else -> View.VISIBLE
            }
        }

        binding.tvTvShowRuntime.apply {
            text = tvShow.runtime?.let {
                val hours = it / 60
                val minutes = it % 60
                when {
                    hours > 0 -> context.getString(
                        R.string.tv_show_runtime_hours_minutes,
                        hours,
                        minutes
                    )
                    else -> context.getString(R.string.tv_show_runtime_minutes, minutes)
                }
            }
            visibility = when {
                text.isNullOrEmpty() -> View.GONE
                else -> View.VISIBLE
            }
        }

        binding.tvTvShowGenres.apply {
            text = tvShow.genres.joinToString(", ") { it.name }
            visibility = when {
                tvShow.genres.isEmpty() -> View.GONE
                else -> View.VISIBLE
            }
        }

        binding.tvTvShowOverview.text = tvShow.overview

        val episodeToWatch = tvShow.episodeToWatch
        binding.btnTvShowWatchEpisode.apply {
            setOnClickListener {
                if (episodeToWatch == null) return@setOnClickListener
                checkProviderAndRun { // <-- LÓGICA APLICADA
                    findNavController().navigate(
                        TvShowTvFragmentDirections.actionTvShowToPlayer(
                        id = episodeToWatch.id,
                        title = tvShow.title,
                        subtitle = episodeToWatch.season?.takeIf { it.number != 0 }?.let { season ->
                            context.getString(
                                R.string.player_subtitle_tv_show,
                                season.number,
                                episodeToWatch.number,
                                episodeToWatch.title ?: context.getString(
                                    R.string.episode_number,
                                    episodeToWatch.number
                                )
                            )
                        } ?: context.getString(
                            R.string.player_subtitle_tv_show_episode_only,
                            episodeToWatch.number,
                            episodeToWatch.title ?: context.getString(
                                R.string.episode_number,
                                episodeToWatch.number
                            )
                        ),
                        videoType = Video.Type.Episode(
                            id = episodeToWatch.id,
                            number = episodeToWatch.number,
                            title = episodeToWatch.title,
                            poster = episodeToWatch.poster,
                            overview = episodeToWatch.overview,
                            tvShow = Video.Type.Episode.TvShow(
                                id = tvShow.id,
                                title = tvShow.title,
                                poster = tvShow.poster,
                                banner = tvShow.banner,
                                releaseDate = tvShow.released?.format("yyyy-MM-dd"),
                                imdbId = tvShow.imdbId,
                            ),
                            season = Video.Type.Episode.Season(
                                number = episodeToWatch.season?.number ?: 0,
                                title = episodeToWatch.season?.title,
                            ),
                        ),
                    )
                )
            }
            }
            text = if (episodeToWatch != null) {
                episodeToWatch.season?.takeIf { it.number != 0 }?.let { season ->
                    context.getString(
                        R.string.tv_show_watch_season_episode,
                        season.number,
                        episodeToWatch.number
                    )
                } ?: context.getString(
                    R.string.tv_show_watch_episode,
                    episodeToWatch.number
                )
            } else ""
            visibility = when {
                episodeToWatch != null -> View.VISIBLE
                else -> View.GONE
            }
        }

        binding.pbTvShowWatchEpisodeLoading.apply {
            visibility = when {
                episodeToWatch != null -> View.GONE
                else -> View.VISIBLE
            }
        }

        binding.pbTvShowProgressEpisode.apply {
            val watchHistory = episodeToWatch?.watchHistory

            progress = when {
                watchHistory != null -> (watchHistory.lastPlaybackPositionMillis * 100 / watchHistory.durationMillis.toDouble()).toInt()
                else -> 0
            }
            visibility = when {
                watchHistory != null -> View.VISIBLE
                else -> View.GONE
            }
        }

        binding.btnTvShowTrailer.apply {
            val trailer = tvShow.trailer
            setOnClickListener {
                if (trailer != null) handleTrailerClick(trailer, "TvShowTv")
            }
            visibility = if (trailer != null) View.VISIBLE else View.GONE
        }
        binding.btnTvShowFavorite.apply {

            fun Boolean.drawable() = when (this) {
                true -> R.drawable.ic_favorite_enable
                false -> R.drawable.ic_favorite_disable
            }

            setOnClickListener {
                CoroutineScope(Dispatchers.IO).launch {
                    val dao = database.tvShowDao()
                    val current = dao.getById(tvShow.id)?.isFavorite ?: false
                    val newValue = !current

                    dao.setFavorite(tvShow.id, newValue)

                    withContext(Dispatchers.Main) {
                        tvShow.isFavorite = newValue
                        setImageDrawable(
                            ContextCompat.getDrawable(context, newValue.drawable())
                        )
                    }
                }
            }

            setImageDrawable(
                ContextCompat.getDrawable(context, tvShow.isFavorite.drawable())
            )
        }
    }

    private fun displaySeasonsMobile(binding: ContentTvShowSeasonsMobileBinding) {
        binding.rvTvShowSeasons.apply {
            adapter = AppAdapter().apply {
                submitList(tvShow.seasons.onEach {
                    it.itemType = AppAdapter.Type.SEASON_MOBILE_ITEM
                })
            }
            if (itemDecorationCount == 0) {
                addItemDecoration(SpacingItemDecoration(20.dp(context)))
            }
        }
    }

    private fun displaySeasonsTv(binding: ContentTvShowSeasonsTvBinding) {
        binding.hgvTvShowSeasons.apply {
            setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
            adapter = AppAdapter().apply {
                submitList(tvShow.seasons.onEach {
                    it.itemType = AppAdapter.Type.SEASON_TV_ITEM
                })
            }
            setItemSpacing(80)
        }
    }

    private fun displayCastMobile(binding: ContentTvShowCastMobileBinding) {
        binding.rvTvShowCast.apply {
            adapter = AppAdapter().apply {
                submitList(tvShow.cast.onEach {
                    it.itemType = AppAdapter.Type.PEOPLE_MOBILE_ITEM
                })
            }
            if (itemDecorationCount == 0) {
                addItemDecoration(SpacingItemDecoration(20.dp(context)))
            }
        }
    }

    private fun displayCastTv(binding: ContentTvShowCastTvBinding) {
        binding.hgvTvShowCast.apply {
            setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
            adapter = AppAdapter().apply {
                submitList(tvShow.cast.onEach {
                    it.itemType = AppAdapter.Type.PEOPLE_TV_ITEM
                })
            }
            setItemSpacing(80)
        }
    }

    private fun displayDirectorsMobile(binding: ContentTvShowDirectorsMobileBinding) {
        binding.rvTvShowDirectors.text = tvShow.directors.joinToString (separator =", ") { it.name }
    }
    private fun displayDirectorsTv(binding: ContentTvShowDirectorsTvBinding) {
        binding.hgvTvShowDirectors.text = tvShow.directors.joinToString (separator =", ") { it.name }
    }

    private fun displayRecommendationsMobile(binding: ContentTvShowRecommendationsMobileBinding) {
        binding.rvTvShowRecommendations.apply {
            adapter = AppAdapter().apply {
                submitList(tvShow.recommendations.onEach {
                    when (it) {
                        is Movie -> it.itemType = AppAdapter.Type.MOVIE_MOBILE_ITEM
                        is TvShow -> it.itemType = AppAdapter.Type.TV_SHOW_MOBILE_ITEM
                    }
                })
            }
            if (itemDecorationCount == 0) {
                addItemDecoration(SpacingItemDecoration(10.dp(context)))
            }
        }
    }

    private fun displayRecommendationsTv(binding: ContentTvShowRecommendationsTvBinding) {
        binding.hgvTvShowRecommendations.apply {
            setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
            adapter = AppAdapter().apply {
                submitList(tvShow.recommendations.onEach {
                    when (it) {
                        is Movie -> it.itemType = AppAdapter.Type.MOVIE_TV_ITEM
                        is TvShow -> it.itemType = AppAdapter.Type.TV_SHOW_TV_ITEM
                    }
                })
            }
            setItemSpacing(20)
        }
    }
}
