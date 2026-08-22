package com.betterstreamflix.models

import com.betterstreamflix.adapters.AppAdapter

open class Provider(
    val name: String,
    val logo: String,
    val language: String,

    val provider: com.betterstreamflix.providers.Provider,
    var isFavorite: Boolean = false,
) : AppAdapter.Item {


    override lateinit var itemType: AppAdapter.Type

    fun copy(
        name: String = this.name,
        logo: String = this.logo,
        language: String = this.language,
        provider: com.betterstreamflix.providers.Provider = this.provider,
        isFavorite: Boolean = this.isFavorite,
        itemType: AppAdapter.Type = this.itemType,
    ): Provider {
        return Provider(name, logo, language, provider, isFavorite).also {
            it.itemType = itemType
        }
    }
}