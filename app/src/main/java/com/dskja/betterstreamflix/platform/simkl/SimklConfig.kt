package com.dskja.betterstreamflix.platform.simkl

import com.dskja.betterstreamflix.utils.UserPreferences

object SimklConfig {
    const val API = "https://api.simkl.com"
    const val APP_NAME = "betterstreamflix"
    const val APP_VERSION = "1.1.0"

    fun configured(): Boolean =
        runCatching {
            UserPreferences.simklEnabled &&
                UserPreferences.simklClientId.isNotBlank() &&
                UserPreferences.simklAccessToken.isNotBlank()
        }.getOrDefault(false)

    fun authHeaders(): Map<String, String> = mapOf(
        "Authorization" to "Bearer ${UserPreferences.simklAccessToken.trim()}",
        "Content-Type" to "application/json",
        "Accept" to "application/json",
        "User-Agent" to "BetterStreamflix/$APP_VERSION",
        "simkl-api-key" to UserPreferences.simklClientId.trim(),
    )

    fun queryParams(): String =
        "client_id=${UserPreferences.simklClientId.trim()}" +
            "&app-name=$APP_NAME&app-version=$APP_VERSION"
}
