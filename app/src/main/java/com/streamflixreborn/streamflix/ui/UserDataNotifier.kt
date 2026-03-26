package com.streamflixreborn.streamflix.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

object UserDataNotifier {
    private val _updates = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1
    )

    val updates = _updates
        .debounce(300) // 🔥 key fix
        .distinctUntilChanged()

    fun notifyChanged() {
        _updates.tryEmit(Unit)
    }
}