package com.dskja.betterstreamflix.platform.simkl

import com.dskja.betterstreamflix.BuildConfig
import com.dskja.betterstreamflix.utils.UserPreferences

object SimklConfig {
    const val API = "https://api.simkl.com"
    const val APP_NAME = "betterstreamflix"
    const val APP_VERSION = "1.1.0"

    fun clientId(): String =
        UserPreferences.simklClientId.trim().ifBlank { BuildConfig.SIMKL_CLIENT_ID.trim() }

    fun configured(): Boolean =
        runCatching {
            UserPreferences.simklEnabled &&
                clientId().isNotBlank() &&
                UserPreferences.simklAccessToken.isNotBlank()
        }.getOrDefault(false)

    fun authHeaders(): Map<String, String> = mapOf(
        "Authorization" to "Bearer ${UserPreferences.simklAccessToken.trim()}",
        "Content-Type" to "application/json",
        "Accept" to "application/json",
        "User-Agent" to "BetterStreamflix/$APP_VERSION",
        "simkl-api-key" to clientId(),
    )

    fun queryParams(): String =
        "client_id=${clientId()}" +
            "&app-name=$APP_NAME&app-version=$APP_VERSION"
}
