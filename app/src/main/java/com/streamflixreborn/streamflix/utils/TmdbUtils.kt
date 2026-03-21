package com.streamflixreborn.streamflix.utils

import com.streamflixreborn.streamflix.models.Episode
import com.streamflixreborn.streamflix.models.Genre
import com.streamflixreborn.streamflix.models.Movie
import com.streamflixreborn.streamflix.models.People
import com.streamflixreborn.streamflix.models.Season
import com.streamflixreborn.streamflix.models.TvShow
import com.streamflixreborn.streamflix.utils.TMDb3.original
import com.streamflixreborn.streamflix.utils.TMDb3.w500
import java.text.Normalizer
import kotlin.math.max

object TmdbUtils {
    private const val MIN_ACCEPTABLE_SCORE = 60
    private const val MAX_LOCALIZED_DETAIL_CANDIDATES = 5

    suspend fun getMovie(title: String, year: Int? = null, language: String? = null): Movie? {
        if (!UserPreferences.enableTmdb) return null
        return try {
            val effectiveYear = year ?: extractYear(title)
            val movie = findBestMovieMatch(title, effectiveYear, language) ?: return null

            val details = TMDb3.Movies.details(
                movieId = movie.id,
                appendToResponse = listOf(
                    TMDb3.Params.AppendToResponse.Movie.CREDITS,
                    TMDb3.Params.AppendToResponse.Movie.RECOMMENDATIONS,
                    TMDb3.Params.AppendToResponse.Movie.VIDEOS,
                    TMDb3.Params.AppendToResponse.Movie.EXTERNAL_IDS,
                ),
                language = language
            )

            Movie(
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
        } catch (_: Exception) { null }
    }

    suspend fun getTvShow(title: String, year: Int? = null, language: String? = null): TvShow? {
        if (!UserPreferences.enableTmdb) return null
        return try {
            val effectiveYear = year ?: extractYear(title)
            val tv = findBestTvMatch(title, effectiveYear, language) ?: return null

            val details = TMDb3.TvSeries.details(
                seriesId = tv.id,
                appendToResponse = listOf(
                    TMDb3.Params.AppendToResponse.Tv.CREDITS,
                    TMDb3.Params.AppendToResponse.Tv.RECOMMENDATIONS,
                    TMDb3.Params.AppendToResponse.Tv.VIDEOS,
                    TMDb3.Params.AppendToResponse.Tv.EXTERNAL_IDS,
                ),
                language = language
            )

            TvShow(
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
        } catch (_: Exception) { null }
    }

    suspend fun getEpisodesBySeason(tvShowId: String, seasonNumber: Int, language: String? = null): List<Episode> {
        if (!UserPreferences.enableTmdb) return listOf()
        return try {
            TMDb3.TvSeasons.details(
                seriesId = tvShowId.toInt(),
                seasonNumber = seasonNumber,
                language = language
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
        } catch (_: Exception) { listOf() }
    }

    suspend fun getMovieById(id: Int, language: String? = null): Movie? {
        if (!UserPreferences.enableTmdb) return null
        return try {
            val details = TMDb3.Movies.details(
                movieId = id,
                appendToResponse = listOf(
                    TMDb3.Params.AppendToResponse.Movie.CREDITS,
                    TMDb3.Params.AppendToResponse.Movie.RECOMMENDATIONS,
                    TMDb3.Params.AppendToResponse.Movie.VIDEOS,
                    TMDb3.Params.AppendToResponse.Movie.EXTERNAL_IDS,
                ),
                language = language
            )

            Movie(
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
        } catch (_: Exception) { null }
    }

    suspend fun getTvShowById(id: Int, language: String? = null): TvShow? {
        if (!UserPreferences.enableTmdb) return null
        return try {
            val details = TMDb3.TvSeries.details(
                seriesId = id,
                appendToResponse = listOf(
                    TMDb3.Params.AppendToResponse.Tv.CREDITS,
                    TMDb3.Params.AppendToResponse.Tv.RECOMMENDATIONS,
                    TMDb3.Params.AppendToResponse.Tv.VIDEOS,
                    TMDb3.Params.AppendToResponse.Tv.EXTERNAL_IDS,
                ),
                language = language
            )

            TvShow(
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
        } catch (_: Exception) { null }
    }

    private suspend fun findBestMovieMatch(
        rawTitle: String,
        year: Int?,
        language: String?,
    ): TMDb3.Movie? {
        val results = searchMovieCandidates(rawTitle, language)
        val scoredResults = results.map { movie ->
            movie to scoreCandidate(
                candidateTitles = listOf(movie.title, movie.originalTitle),
                queryTitle = rawTitle,
                year = year,
                candidateYear = extractYear(movie.releaseDate),
            )
        }

        val bestSearchMatch = scoredResults.maxByOrNull { it.second }
        if (bestSearchMatch != null && bestSearchMatch.second >= MIN_ACCEPTABLE_SCORE) {
            return bestSearchMatch.first
        }

        val localizedFallback = findBestLocalizedMovieCandidate(
            rawTitle = rawTitle,
            year = year,
            language = language,
            candidates = scoredResults
                .sortedByDescending { it.second }
                .map { it.first }
                .take(MAX_LOCALIZED_DETAIL_CANDIDATES),
        )
        if (localizedFallback != null) return localizedFallback

        return bestSearchMatch
            ?.takeIf { it.second > 0 }
            ?.first
    }

    private suspend fun findBestTvMatch(
        rawTitle: String,
        year: Int?,
        language: String?,
    ): TMDb3.Tv? {
        val results = searchTvCandidates(rawTitle, language)
        val scoredResults = results.map { tv ->
            tv to scoreCandidate(
                candidateTitles = listOf(tv.name, tv.originalName),
                queryTitle = rawTitle,
                year = year,
                candidateYear = extractYear(tv.firstAirDate),
            )
        }

        val bestSearchMatch = scoredResults.maxByOrNull { it.second }
        if (bestSearchMatch != null && bestSearchMatch.second >= MIN_ACCEPTABLE_SCORE) {
            return bestSearchMatch.first
        }

        val localizedFallback = findBestLocalizedTvCandidate(
            rawTitle = rawTitle,
            year = year,
            language = language,
            candidates = scoredResults
                .sortedByDescending { it.second }
                .map { it.first }
                .take(MAX_LOCALIZED_DETAIL_CANDIDATES),
        )
        if (localizedFallback != null) return localizedFallback

        return bestSearchMatch
            ?.takeIf { it.second > 0 }
            ?.first
    }

    private suspend fun searchMovieCandidates(rawTitle: String, language: String?): List<TMDb3.Movie> {
        val variants = buildTitleVariants(rawTitle)
        val languages = listOfNotNull(language).plus(null).distinct()

        return languages
            .flatMap { searchLanguage ->
                variants.flatMap { query ->
                    TMDb3.Search.multi(query, language = searchLanguage).results.filterIsInstance<TMDb3.Movie>()
                }
            }
            .distinctBy { it.id }
    }

    private suspend fun searchTvCandidates(rawTitle: String, language: String?): List<TMDb3.Tv> {
        val variants = buildTitleVariants(rawTitle)
        val languages = listOfNotNull(language).plus(null).distinct()

        return languages
            .flatMap { searchLanguage ->
                variants.flatMap { query ->
                    TMDb3.Search.multi(query, language = searchLanguage).results.filterIsInstance<TMDb3.Tv>()
                }
            }
            .distinctBy { it.id }
    }

    private suspend fun findBestLocalizedMovieCandidate(
        rawTitle: String,
        year: Int?,
        language: String?,
        candidates: List<TMDb3.Movie>,
    ): TMDb3.Movie? {
        if (language.isNullOrBlank() || candidates.isEmpty()) return null

        return candidates
            .mapNotNull { movie ->
                val details = runCatching {
                    TMDb3.Movies.details(
                        movieId = movie.id,
                        appendToResponse = listOf(TMDb3.Params.AppendToResponse.Movie.ALTERNATIVE_TITLES),
                        language = language,
                    )
                }.getOrNull() ?: return@mapNotNull null

                val score = scoreCandidate(
                    candidateTitles = listOf(details.title, details.originalTitle),
                    queryTitle = rawTitle,
                    year = year,
                    candidateYear = extractYear(details.releaseDate),
                )
                movie to score
            }
            .maxByOrNull { it.second }
            ?.takeIf { it.second >= MIN_ACCEPTABLE_SCORE }
            ?.first
    }

    private suspend fun findBestLocalizedTvCandidate(
        rawTitle: String,
        year: Int?,
        language: String?,
        candidates: List<TMDb3.Tv>,
    ): TMDb3.Tv? {
        if (language.isNullOrBlank() || candidates.isEmpty()) return null

        return candidates
            .mapNotNull { tv ->
                val details = runCatching {
                    TMDb3.TvSeries.details(
                        seriesId = tv.id,
                        appendToResponse = listOf(TMDb3.Params.AppendToResponse.Tv.ALTERNATIVE_TITLES),
                        language = language,
                    )
                }.getOrNull() ?: return@mapNotNull null

                val score = scoreCandidate(
                    candidateTitles = listOf(details.name, details.originalName),
                    queryTitle = rawTitle,
                    year = year,
                    candidateYear = extractYear(details.firstAirDate),
                )
                tv to score
            }
            .maxByOrNull { it.second }
            ?.takeIf { it.second >= MIN_ACCEPTABLE_SCORE }
            ?.first
    }

    private fun scoreCandidate(
        candidateTitles: List<String?>,
        queryTitle: String,
        year: Int?,
        candidateYear: Int?,
    ): Int {
        val queryVariants = buildTitleVariants(queryTitle).map(::normalizeTitle)
        val candidateVariants = candidateTitles
            .filterNotNull()
            .map(::normalizeTitle)
            .distinct()

        val titleScore = candidateVariants.maxOfOrNull { candidate ->
            queryVariants.maxOfOrNull { query ->
                when {
                    candidate == query -> 120
                    candidate.replace(" ", "") == query.replace(" ", "") -> 110
                    candidate.startsWith(query) || query.startsWith(candidate) -> 85
                    candidate.contains(query) || query.contains(candidate) -> 70
                    overlapScore(candidate, query) >= 0.8 -> 55
                    else -> 0
                }
            } ?: 0
        } ?: 0

        val yearScore = when {
            year == null || candidateYear == null -> 0
            year == candidateYear -> 20
            max(year, candidateYear) - minOf(year, candidateYear) == 1 -> 5
            else -> -25
        }

        return titleScore + yearScore
    }

    private fun buildTitleVariants(title: String): List<String> {
        val trimmed = title.trim()
        val withoutTrailingYear = trimmed
            .replace(Regex("\\s*\\((19|20)\\d{2}\\)\\s*$"), "")
            .replace(Regex("\\s*\\[(19|20)\\d{2}]\\s*$"), "")
            .trim()
        val withoutDecorators = withoutTrailingYear
            .replace(Regex("\\s*[\\-–:]\\s*(sub|dub|ita|ger|de|eng|en)\\s*$", RegexOption.IGNORE_CASE), "")
            .trim()

        return listOf(trimmed, withoutTrailingYear, withoutDecorators)
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun extractYear(value: String?): Int? {
        return value
            ?.let { Regex("(19|20)\\d{2}").find(it)?.value }
            ?.toIntOrNull()
    }

    private fun normalizeTitle(value: String): String {
        val ascii = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

        return ascii
            .lowercase()
            .replace("&", " and ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    private fun overlapScore(left: String, right: String): Double {
        val leftWords = left.split(" ").filter { it.isNotBlank() }.toSet()
        val rightWords = right.split(" ").filter { it.isNotBlank() }.toSet()
        if (leftWords.isEmpty() || rightWords.isEmpty()) return 0.0

        val overlap = leftWords.intersect(rightWords).size.toDouble()
        return overlap / max(leftWords.size, rightWords.size).toDouble()
    }
}
