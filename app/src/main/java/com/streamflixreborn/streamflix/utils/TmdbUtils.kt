package com.streamflixreborn.streamflix.utils

import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Genre
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.People
import com.streamflixreborn.streamflix.models.Season
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.utils.TMDb3.original
import com.streamflixreborn.streamflix.utils.TMDb3.w500

object TmdbUtils {

    suspend fun getMovie(title: String, year: Int? = null): Movie? {
        val results = TMDb3.Search.multi(title).results.filterIsInstance<TMDb3.Movie>()
        val movie = results.find {
            it.title.equals(title, ignoreCase = true) && (year == null || it.releaseDate?.contains(year.toString()) == true)
        } ?: results.firstOrNull() ?: return null

        val details = TMDb3.Movies.details(
            movieId = movie.id,
            appendToResponse = listOf(
                TMDb3.Params.AppendToResponse.Movie.CREDITS,
                TMDb3.Params.AppendToResponse.Movie.RECOMMENDATIONS,
                TMDb3.Params.AppendToResponse.Movie.VIDEOS,
                TMDb3.Params.AppendToResponse.Movie.EXTERNAL_IDS,
            )
        )

        return Movie(
            id = details.id.toString(),
            title = details.title,
            overview = details.overview,
            released = details.releaseDate,
            runtime = details.runtime,
            trailer = details.videos?.results
                ?.sortedBy { it.publishedAt ?: "" }
                ?.firstOrNull { it.site == TMDb3.Video.VideoSite.YOUTUBE }
                ?.let { "https://www.youtube.com/watch?v=${it.key}" },
            rating = details.voteAverage.toDouble(),
            poster = details.posterPath?.original,
            banner = details.backdropPath?.original,
            imdbId = details.externalIds?.imdbId,
            genres = details.genres.map { Genre(it.id.toString(), it.name) },
            cast = details.credits?.cast?.map { People(it.id.toString(), it.name, it.profilePath?.w500) } ?: listOf(),
        )
    }

    suspend fun getTvShow(title: String, year: Int? = null): TvShow? {
        val results = TMDb3.Search.multi(title).results.filterIsInstance<TMDb3.Tv>()
        val tv = results.find {
            it.name.equals(title, ignoreCase = true) && (year == null || it.firstAirDate?.contains(year.toString()) == true)
        } ?: results.firstOrNull() ?: return null

        val details = TMDb3.TvSeries.details(
            seriesId = tv.id,
            appendToResponse = listOf(
                TMDb3.Params.AppendToResponse.Tv.CREDITS,
                TMDb3.Params.AppendToResponse.Tv.RECOMMENDATIONS,
                TMDb3.Params.AppendToResponse.Tv.VIDEOS,
                TMDb3.Params.AppendToResponse.Tv.EXTERNAL_IDS,
            )
        )

        return TvShow(
            id = details.id.toString(),
            title = details.name,
            overview = details.overview,
            released = details.firstAirDate,
            trailer = details.videos?.results
                ?.sortedBy { it.publishedAt ?: "" }
                ?.firstOrNull { it.site == TMDb3.Video.VideoSite.YOUTUBE }
                ?.let { "https://www.youtube.com/watch?v=${it.key}" },
            rating = details.voteAverage.toDouble(),
            poster = details.posterPath?.original,
            banner = details.backdropPath?.original,
            imdbId = details.externalIds?.imdbId,
            seasons = details.seasons.map {
                Season(
                    id = "${details.id}-${it.seasonNumber}",
                    number = it.seasonNumber,
                    title = it.name,
                    poster = it.posterPath?.w500,
                )
            },
            genres = details.genres.map { Genre(it.id.toString(), it.name) },
            cast = details.credits?.cast?.map { People(it.id.toString(), it.name, it.profilePath?.w500) } ?: listOf(),
        )
    }

    suspend fun getEpisodesBySeason(tvShowId: String, seasonNumber: Int): List<Episode> {
        return TMDb3.TvSeasons.details(
            seriesId = tvShowId.toInt(),
            seasonNumber = seasonNumber,
        ).episodes?.map {
            Episode(
                id = it.id.toString(),
                number = it.episodeNumber,
                title = it.name ?: "",
                released = it.airDate,
                poster = it.stillPath?.w500,
                overview = it.overview,
            )
        } ?: listOf()
    }
}
