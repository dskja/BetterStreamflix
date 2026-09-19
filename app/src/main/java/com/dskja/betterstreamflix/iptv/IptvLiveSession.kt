package com.dskja.betterstreamflix.iptv

import com.dskja.betterstreamflix.models.TvShow
import com.dskja.betterstreamflix.models.Video
import com.dskja.betterstreamflix.providers.IptvProvider
import com.dskja.betterstreamflix.providers.Provider
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory channel guide for IPTV live playback — powers prev/next zapping
 * without leaving the player.
 */
object IptvLiveSession {

    data class Channel(
        val id: String,
        val name: String,
        val logo: String? = null,
        val group: String? = null,
    )

    private val channels = CopyOnWriteArrayList<Channel>()
    @Volatile
    private var currentId: String? = null
    @Volatile
    private var providerName: String? = null

    fun clear() {
        channels.clear()
        currentId = null
        providerName = null
    }

    fun remember(list: List<Channel>, provider: Provider? = null) {
        if (list.isEmpty()) return
        val name = provider?.name
        if (name != null && providerName != null && providerName != name) {
            channels.clear()
        }
        providerName = name ?: providerName
        val byId = channels.associateBy { it.id }.toMutableMap()
        list.forEach { byId[it.id] = it }
        channels.clear()
        channels.addAll(byId.values)
    }

    fun rememberShows(shows: List<TvShow>, provider: Provider? = null) {
        remember(
            shows.map { Channel(id = it.id, name = it.title, logo = it.poster) },
            provider,
        )
    }

    fun setCurrent(id: String) {
        currentId = id
    }

    fun current(): Channel? = currentId?.let { id -> channels.firstOrNull { it.id == id } }

    fun currentIndex(): Int {
        val id = currentId ?: return -1
        return channels.indexOfFirst { it.id == id }
    }

    fun size(): Int = channels.size

    fun hasPrevious(): Boolean = currentIndex() > 0

    fun hasNext(): Boolean {
        val idx = currentIndex()
        return idx >= 0 && idx < channels.lastIndex
    }

    fun previous(): Channel? {
        val idx = currentIndex()
        if (idx <= 0) return null
        val channel = channels[idx - 1]
        currentId = channel.id
        return channel
    }

    fun next(): Channel? {
        val idx = currentIndex()
        if (idx < 0 || idx >= channels.lastIndex) return null
        val channel = channels[idx + 1]
        currentId = channel.id
        return channel
    }

    fun snapshot(): List<Channel> = channels.toList()

    fun toEpisodeType(channel: Channel): Video.Type.Episode =
        Video.Type.Episode(
            id = channel.id,
            number = 1,
            title = channel.name,
            poster = channel.logo,
            overview = null,
            tvShow = Video.Type.Episode.TvShow(
                id = channel.id,
                title = channel.name,
                poster = channel.logo,
                banner = channel.logo,
                releaseDate = null,
                imdbId = null,
            ),
            season = Video.Type.Episode.Season(
                number = 1,
                title = "Live",
            ),
        )

    suspend fun ensureLoaded(provider: Provider, aroundId: String?) {
        if (provider !is IptvProvider) return
        if (channels.isNotEmpty() &&
            (aroundId == null || channels.any { it.id == aroundId }) &&
            providerName == provider.name
        ) {
            aroundId?.let { setCurrent(it) }
            return
        }
        val loaded = provider.listLiveChannels(aroundId = aroundId, limit = 250)
        remember(loaded, provider)
        aroundId?.let { setCurrent(it) }
    }
}
