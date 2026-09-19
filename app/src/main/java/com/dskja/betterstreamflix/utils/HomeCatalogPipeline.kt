package com.dskja.betterstreamflix.utils

import com.dskja.betterstreamflix.adapters.AppAdapter
import com.dskja.betterstreamflix.models.Category
import com.dskja.betterstreamflix.models.Episode
import com.dskja.betterstreamflix.models.Movie
import com.dskja.betterstreamflix.models.TvShow
import com.dskja.betterstreamflix.providers.Provider
import java.net.URI

/**
 * Shared post-process for every provider's [Provider.getHome] result.
 *
 * Ensures FEATURED exists, stamps [Movie.providerName] / [TvShow.providerName],
 * drops empty shelves, absolute-izes relative artwork URLs, and dedupes items.
 * Applied once in [com.dskja.betterstreamflix.fragments.home.HomeViewModel]
 * so all ~80 scrapers benefit without per-provider rewrites.
 */
object HomeCatalogPipeline {

    const val MAX_ITEMS_PER_CATEGORY = 40
    const val MAX_FEATURED_ITEMS = 12

    data class Result(
        val categories: List<Category>,
        val warnings: List<String> = emptyList(),
    ) {
        val warningText: String?
            get() = warnings.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    fun process(
        provider: Provider,
        categories: List<Category>,
        addonRows: List<Category> = emptyList(),
    ): Result {
        val warnings = mutableListOf<String>()
        val merged = categories + addonRows
        if (merged.isEmpty()) {
            return Result(emptyList(), listOf("Empty catalog from ${provider.name}"))
        }

        val stamped = merged.mapNotNull { category ->
            normalizeCategory(provider, category)
        }

        if (stamped.isEmpty()) {
            return Result(emptyList(), listOf("No usable rows from ${provider.name}"))
        }

        val withFeatured = ensureFeatured(stamped, warnings)
        val dedupedShelves = mergeDuplicateShelfNames(withFeatured)

        return Result(dedupedShelves, warnings)
    }

    private fun normalizeCategory(provider: Provider, category: Category): Category? {
        val name = category.name.trim()
        val cap = if (name == Category.FEATURED) MAX_FEATURED_ITEMS else MAX_ITEMS_PER_CATEGORY
        val seen = LinkedHashSet<String>()
        val items = category.list.mapNotNull { item ->
            stampAndNormalize(provider, item)
        }.filter { item ->
            val key = itemKey(item) ?: return@filter true
            seen.add(key)
        }.take(cap)

        if (items.isEmpty()) return null
        return Category(name = name, list = items)
    }

    private fun stampAndNormalize(provider: Provider, item: AppAdapter.Item): AppAdapter.Item? {
        return when (item) {
            is Movie -> {
                if (item.id.isBlank() && item.title.isBlank()) return null
                item.apply {
                    if (providerName.isNullOrBlank()) providerName = provider.name
                    poster = absoluteUrl(provider.baseUrl, poster)
                    banner = absoluteUrl(provider.baseUrl, banner)
                }
            }

            is TvShow -> {
                if (item.id.isBlank() && item.title.isBlank()) return null
                item.apply {
                    if (providerName.isNullOrBlank()) providerName = provider.name
                    poster = absoluteUrl(provider.baseUrl, poster)
                    banner = absoluteUrl(provider.baseUrl, banner)
                }
            }

            is Episode -> {
                item.apply {
                    tvShow?.let { show ->
                        if (show.providerName.isNullOrBlank()) show.providerName = provider.name
                        show.poster = absoluteUrl(provider.baseUrl, show.poster)
                        show.banner = absoluteUrl(provider.baseUrl, show.banner)
                    }
                    poster = absoluteUrl(provider.baseUrl, poster)
                }
            }

            else -> item
        }
    }

    private fun ensureFeatured(
        categories: List<Category>,
        warnings: MutableList<String>,
    ): List<Category> {
        val featured = categories.firstOrNull { it.name == Category.FEATURED }
        if (featured != null && featured.list.isNotEmpty()) {
            return categories
        }

        val donor = categories.firstOrNull {
            it.name != Category.FEATURED &&
                it.list.any { item -> item is Movie || item is TvShow }
        } ?: return categories

        warnings.add("Featured shelf synthesized from “${donor.name.ifBlank { "catalog" }}”")
        val featuredItems = donor.list
            .filter { it is Movie || it is TvShow }
            .take(MAX_FEATURED_ITEMS)
        val rest = categories.filterNot { it === donor }
            .filter { it.name != Category.FEATURED }
        // Keep donor as a named shelf too when it had a real title.
        val keepDonor = donor.name.isNotBlank()
        return buildList {
            add(Category(name = Category.FEATURED, list = featuredItems))
            if (keepDonor) add(donor)
            addAll(rest)
        }
    }

    private fun mergeDuplicateShelfNames(categories: List<Category>): List<Category> {
        val order = LinkedHashMap<String, MutableList<AppAdapter.Item>>()
        categories.forEach { category ->
            val bucket = order.getOrPut(category.name) { mutableListOf() }
            val seen = bucket.mapNotNullTo(mutableSetOf()) { itemKey(it) }
            category.list.forEach { item ->
                val key = itemKey(item)
                if (key == null || seen.add(key)) {
                    bucket.add(item)
                }
            }
        }
        return order.map { (name, items) ->
            val cap = if (name == Category.FEATURED) MAX_FEATURED_ITEMS else MAX_ITEMS_PER_CATEGORY
            Category(name = name, list = items.take(cap))
        }
    }

    private fun itemKey(item: AppAdapter.Item): String? = when (item) {
        is Movie -> "m:${item.id}"
        is TvShow -> "t:${item.id}"
        is Episode -> "e:${item.id}"
        else -> null
    }

    /**
     * Turn protocol-relative / site-relative artwork into an absolute URL.
     * Leaves data:, blob:, and already-absolute http(s) URLs alone.
     */
    fun absoluteUrl(baseUrl: String, raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        if (value.startsWith("data:", ignoreCase = true) ||
            value.startsWith("blob:", ignoreCase = true)
        ) {
            return value
        }
        if (value.startsWith("//")) {
            val scheme = runCatching { URI(baseUrl).scheme }.getOrNull()?.ifBlank { null } ?: "https"
            return "$scheme:$value"
        }
        if (value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true)
        ) {
            return value
        }
        val base = baseUrl.trim().trimEnd('/')
        if (base.isEmpty()) return value
        return if (value.startsWith("/")) "$base$value" else "$base/$value"
    }
}
