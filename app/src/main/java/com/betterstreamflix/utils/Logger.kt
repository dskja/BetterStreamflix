package com.betterstreamflix.utils

import android.app.Application
import android.util.Log
import com.betterstreamflix.BuildConfig

/**
 * Centralized logger that respects build type.
 * In debug: logs everything with verbose detail.
 * In release: only logs warnings and errors.
 */
object Logger {

    private const val DEFAULT_TAG = "BetterStreamflix"

    fun v(tag: String = DEFAULT_TAG, message: String) {
        if (BuildConfig.DEBUG) Log.v(tag, message)
    }

    fun d(tag: String = DEFAULT_TAG, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun i(tag: String = DEFAULT_TAG, message: String) {
        Log.i(tag, message)
    }

    fun w(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
    }

    fun e(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
    }

    /**
     * Log a method entry (debug only).
     */
    fun enter(tag: String = DEFAULT_TAG, methodName: String) {
        if (BuildConfig.DEBUG) Log.d(tag, "→ $methodName")
    }

    /**
     * Log a method exit (debug only).
     */
    fun exit(tag: String = DEFAULT_TAG, methodName: String) {
        if (BuildConfig.DEBUG) Log.d(tag, "← $methodName")
    }
}
