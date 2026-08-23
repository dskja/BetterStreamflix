package com.betterstreamflix.ui

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager

/**
 * Keyboard helper — manages soft keyboard show/hide.
 */
object KeyboardHelper {

    /**
     * Show the soft keyboard for a view.
     */
    fun showKeyboard(view: View) {
        view.requestFocus()
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    /**
     * Hide the soft keyboard.
     */
    fun hideKeyboard(view: View) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    /**
     * Toggle the keyboard.
     */
    fun toggleKeyboard(context: Context) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    /**
     * Check if the keyboard is currently visible.
     */
    fun isKeyboardVisible(view: View): Boolean {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.isAcceptingText
    }
}
