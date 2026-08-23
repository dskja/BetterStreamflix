package com.betterstreamflix.testing

import android.content.Context
import androidx.core.content.edit

/**
 * QA configuration — manages QA and debug configuration for
 * testing environments.
 */
object QaConfiguration {

    private const val PREFS_NAME = "qa_config"

    data class QaConfig(
        val isQaMode: Boolean,
        val mockDataEnabled: Boolean,
        val slowNetworkSimulation: Boolean,
        val errorInjectionRate: Float,
        val logLevel: String,
        val autoPlayEnabled: Boolean,
        val skipIntroEnabled: Boolean,
    )

    /**
     * Get QA configuration.
     */
    fun getConfig(context: Context): QaConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return QaConfig(
            isQaMode = prefs.getBoolean("qa_mode", false),
            mockDataEnabled = prefs.getBoolean("mock_data", false),
            slowNetworkSimulation = prefs.getBoolean("slow_network", false),
            errorInjectionRate = prefs.getFloat("error_rate", 0f),
            logLevel = prefs.getString("log_level", "INFO") ?: "INFO",
            autoPlayEnabled = prefs.getBoolean("auto_play", true),
            skipIntroEnabled = prefs.getBoolean("skip_intro", true),
        )
    }

    /**
     * Set QA configuration.
     */
    fun setConfig(context: Context, config: QaConfig) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean("qa_mode", config.isQaMode)
            putBoolean("mock_data", config.mockDataEnabled)
            putBoolean("slow_network", config.slowNetworkSimulation)
            putFloat("error_rate", config.errorInjectionRate)
            putString("log_level", config.logLevel)
            putBoolean("auto_play", config.autoPlayEnabled)
            putBoolean("skip_intro", config.skipIntroEnabled)
        }
    }

    /**
     * Enable QA mode with defaults.
     */
    fun enableQaMode(context: Context) {
        setConfig(context, QaConfig(
            isQaMode = true,
            mockDataEnabled = true,
            slowNetworkSimulation = false,
            errorInjectionRate = 0f,
            logLevel = "DEBUG",
            autoPlayEnabled = true,
            skipIntroEnabled = true,
        ))
    }

    /**
     * Disable QA mode.
     */
    fun disableQaMode(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { clear() }
    }

    /**
     * Check if QA mode is enabled.
     */
    fun isQaMode(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("qa_mode", false)
    }

    /**
     * Set error injection rate for testing.
     */
    fun setErrorInjectionRate(context: Context, rate: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putFloat("error_rate", rate.coerceIn(0f, 1f)).apply()
    }

    /**
     * Enable slow network simulation.
     */
    fun enableSlowNetwork(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("slow_network", enabled).apply()
    }
}
