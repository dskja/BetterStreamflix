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
}