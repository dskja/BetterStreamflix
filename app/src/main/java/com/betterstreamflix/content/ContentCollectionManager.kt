package com.betterstreamflix.content

/**
 * Content collection manager — manages collections and categories
 * for organizing content (e.g., "Action Movies", "Trending TV").
 */
object ContentCollectionManager {

    data class Collection(
        val id: String,
        val name: String,
        val type: CollectionType,
        val contentIds: List<String>,
        val iconUrl: String?,
        val updatedAt: Long,
    )

    enum class CollectionType {
        CUSTOM,
        GENRE_BASED,
        TRENDING,
        NEW_RELEASES,
        WATCHLIST,
        CONTINUE_WATCHING,
    }

    private val collections = mutableMapOf<String, Collection>()

    /**
     * Create a new collection.
     */
    fun createCollection(name: String, type: CollectionType = CollectionType.CUSTOM): Collection {
        val id = "collection_${System.currentTimeMillis()}"
        val collection = Collection(
            id = id,
            name = name,
            type = type,
            contentIds = emptyList(),
            iconUrl = null,
            updatedAt = System.currentTimeMillis(),
        )
        collections[id] = collection
        return collection
    }

    /**
     * Add content to a collection.
     */
    fun addToCollection(collectionId: String, contentId: String) {
        val collection = collections[collectionId] ?: return
        if (contentId !in collection.contentIds) {
            collections[collectionId] = collection.copy(
                contentIds = collection.contentIds + contentId,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    /**
     * Remove content from a collection.
     */
    fun removeFromCollection(collectionId: String, contentId: String) {
        val collection = collections[collectionId] ?: return
        collections[collectionId] = collection.copy(
            contentIds = collection.contentIds - contentId,
            updatedAt = System.currentTimeMillis(),
        )
    }

    /**
     * Get a collection by ID.
     */
    fun getCollection(id: String): Collection? = collections[id]

    /**
     * Get all collections.
     */
    fun getAllCollections(): List<Collection> = collections.values.toList()

    /**
     * Delete a collection.
     */
    fun deleteCollection(id: String) {
        collections.remove(id)
    }

    /**
     * Get collections containing a specific content item.
     */
    fun getCollectionsForContent(contentId: String): List<Collection> {
        return collections.values.filter { contentId in it.contentIds }
    }
}
