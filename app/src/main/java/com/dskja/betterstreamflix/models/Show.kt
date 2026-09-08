package com.dskja.betterstreamflix.models

import com.dskja.betterstreamflix.adapters.AppAdapter

sealed interface Show : AppAdapter.Item {
    var isFavorite: Boolean
}
