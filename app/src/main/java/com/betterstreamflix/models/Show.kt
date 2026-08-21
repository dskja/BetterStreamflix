package com.betterstreamflix.models

import com.betterstreamflix.adapters.AppAdapter

sealed interface Show : AppAdapter.Item {
    var isFavorite: Boolean
}
