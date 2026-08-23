package com.betterstreamflix.i18n

import android.content.Context

/**
 * String resource helper — provides type-safe access to string resources
 * with fallback for missing translations.
 */
object StringResourceHelper {

    /**
     * Get a string resource by name (for dynamic string lookups).
     */
    fun getStringByName(context: Context, name: String): String? {
        val resId = context.resources.getIdentifier(name, "string", context.packageName)
        return if (resId != 0) context.getString(resId) else null
    }

    /**
     * Get a string resource by name with a fallback.
     */
    fun getStringByName(context: Context, name: String, fallback: String): String {
        return getStringByName(context, name) ?: fallback
    }

    /**
     * Check if a string resource exists.
     */
    fun stringExists(context: Context, name: String): Boolean {
        return context.resources.getIdentifier(name, "string", context.packageName) != 0
    }

    /**
     * Get a plural string.
     */
    fun getQuantityString(context: Context, name: String, quantity: Int, vararg formatArgs: Any): String {
        val resId = context.resources.getIdentifier(name, "plurals", context.packageName)
        return if (resId != 0) {
            context.resources.getQuantityString(resId, quantity, *formatArgs)
        } else {
            quantity.toString()
        }
    }
}
