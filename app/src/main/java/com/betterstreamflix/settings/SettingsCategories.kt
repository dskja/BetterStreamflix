package com.betterstreamflix.settings

/**
 * Settings categories for organized settings UI.
 */
object SettingsCategories {

    enum class Category(val title: String, val icon: String) {
        PLAYBACK("Playback", "play"),
        PROVIDER("Provider", "server"),
        VIDEO("Video Quality", "video"),
        SUBTITLES("Subtitles", "caption"),
        LANGUAGE("Language", "language"),
        DOWNLOADS("Downloads", "download"),
        NOTIFICATIONS("Notifications", "bell"),
        APPEARANCE("Appearance", "palette"),
        PLAYER("Player Controls", "controller"),
        ABOUT("About", "info"),
        ADVANCED("Advanced", "settings"),
    }

    /**
     * Get all categories in display order.
     */
    fun getAllCategories(): List<Category> = Category.values().toList()

    /**
     * Get categories available on TV.
     */
    fun getTvCategories(): List<Category> {
        return listOf(
            Category.PLAYBACK,
            Category.PROVIDER,
            Category.VIDEO,
            Category.SUBTITLES,
            Category.LANGUAGE,
            Category.ABOUT,
            Category.ADVANCED,
        )
    }

    /**
     * Get categories available on mobile.
     */
    fun getMobileCategories(): List<Category> = getAllCategories()
}
