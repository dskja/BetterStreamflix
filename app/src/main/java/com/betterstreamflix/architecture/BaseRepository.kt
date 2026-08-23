package com.betterstreamflix.architecture

/**
 * Repository pattern base — provides a standard interface for
 * data repositories with local and remote data sources.
 */
abstract class BaseRepository<Local, Remote> {

    /**
     * Get data from local source first, then remote.
     */
    abstract suspend fun get(key: String): OperationResult<Local>

    /**
     * Get data from remote source only.
     */
    abstract suspend fun fetchRemote(key: String): OperationResult<Remote>

    /**
     * Save data to local source.
     */
    abstract suspend fun save(key: String, data: Local): OperationResult<Unit>

    /**
     * Delete data from local source.
     */
    abstract suspend fun delete(key: String): OperationResult<Unit>

    /**
     * Check if data exists in local source.
     */
    abstract suspend fun exists(key: String): Boolean

    /**
     * Get all items from local source.
     */
    abstract suspend fun getAll(): OperationResult<List<Local>>

    /**
     * Sync local data with remote source.
     */
    abstract suspend fun sync(): OperationResult<Int>

    /**
     * Clear all local data.
     */
    abstract suspend fun clear(): OperationResult<Unit>
}

/**
 * Cached repository — wraps a base repository with in-memory caching.
 */
class CachedRepository<Local, Remote>(
    private val delegate: BaseRepository<Local, Remote>,
    private val cache: MutableMap<String, Local> = mutableMapOf(),
) : BaseRepository<Local, Remote>() {

    override suspend fun get(key: String): OperationResult<Local> {
        cache[key]?.let { return OperationResult.success(it) }
        val result = delegate.get(key)
        if (result is OperationResult.Success) cache[key] = result.data
        return result
    }

    override suspend fun fetchRemote(key: String): OperationResult<Remote> {
        return delegate.fetchRemote(key)
    }

    override suspend fun save(key: String, data: Local): OperationResult<Unit> {
        cache[key] = data
        return delegate.save(key, data)
    }

    override suspend fun delete(key: String): OperationResult<Unit> {
        cache.remove(key)
        return delegate.delete(key)
    }

    override suspend fun exists(key: String): Boolean {
        return cache.containsKey(key) || delegate.exists(key)
    }

    override suspend fun getAll(): OperationResult<List<Local>> {
        return delegate.getAll()
    }

    override suspend fun sync(): OperationResult<Int> {
        cache.clear()
        return delegate.sync()
    }

    override suspend fun clear(): OperationResult<Unit> {
        cache.clear()
        return delegate.clear()
    }

    fun clearCache() {
        cache.clear()
    }
}
