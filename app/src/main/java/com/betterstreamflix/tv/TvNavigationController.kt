package com.betterstreamflix.tv

import android.content.Context
import android.view.KeyEvent
import androidx.leanback.widget.BrowseFrameLayout
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter

/**
 * TV navigation controller — manages D-pad navigation, focus handling,
 * and back button behavior for TV layouts.
 */
class TvNavigationController {

    private var selectedPosition: Int = 0
    private var selectedRow: Int = 0

    /**
     * Handle D-pad key events.
     */
    fun handleKeyEvent(keyCode: Int, action: Int): Boolean {
        if (action != KeyEvent.ACTION_DOWN) return false

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                navigateUp()
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                navigateDown()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                navigateLeft()
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                navigateRight()
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                selectCurrent()
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                handleBack()
            }
            else -> false
        }
    }

    /**
     * Save current navigation state.
     */
    fun saveState(row: Int, position: Int) {
        selectedRow = row
        selectedPosition = position
    }

    /**
     * Restore navigation state.
     */
    fun restoreState(): Pair<Int, Int> = selectedRow to selectedPosition

    private fun navigateUp() { selectedRow = (selectedRow - 1).coerceAtLeast(0) }
    private fun navigateDown() { selectedRow = selectedRow + 1 }
    private fun navigateLeft() { selectedPosition = (selectedPosition - 1).coerceAtLeast(0) }
    private fun navigateRight() { selectedPosition = selectedPosition + 1 }
    private fun selectCurrent() { /* Trigger item click callback */ }
    private fun handleBack(): Boolean { return false /* Let fragment handle back */ }
}
