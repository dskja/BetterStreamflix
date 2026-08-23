package com.betterstreamflix.deployment

/**
 * Signing configuration — manages APK/AAB signing configuration
 * for release builds.
 */
object SigningConfiguration {

    data class SigningInfo(
        val keyStorePath: String,
        val keyAlias: String,
        val keyPassword: String,
        val storePassword: String,
        val isV1SigningEnabled: Boolean,
        val isV2SigningEnabled: Boolean,
        val isV3SigningEnabled: Boolean,
    )

    /**
     * Get signing configuration from environment variables.
     */
    fun getSigningConfig(): SigningInfo? {
        val keyStorePath = System.getenv("KEYSTORE_PATH") ?: return null
        val keyAlias = System.getenv("KEY_ALIAS") ?: return null
        val keyPassword = System.getenv("KEY_PASSWORD") ?: return null
        val storePassword = System.getenv("STORE_PASSWORD") ?: return null

        return SigningInfo(
            keyStorePath = keyStorePath,
            keyAlias = keyAlias,
            keyPassword = keyPassword,
            storePassword = storePassword,
            isV1SigningEnabled = true,
            isV2SigningEnabled = true,
            isV3SigningEnabled = true,
        )
    }

    /**
     * Check if signing is configured.
     */
    fun isSigningConfigured(): Boolean {
        return getSigningConfig() != null
    }

    /**
     * Validate signing configuration.
     */
    fun validateSigningConfig(): List<String> {
        val errors = mutableListOf<String>()

        val config = getSigningConfig()
        if (config == null) {
            errors.add("Signing configuration not found in environment variables")
            return errors
        }

        if (!java.io.File(config.keyStorePath).exists()) {
            errors.add("Keystore file not found: ${config.keyStorePath}")
        }

        if (config.keyAlias.isBlank()) {
            errors.add("Key alias is empty")
        }

        if (config.keyPassword.isBlank()) {
            errors.add("Key password is empty")
        }

        if (config.storePassword.isBlank()) {
            errors.add("Store password is empty")
        }

        return errors
    }

    /**
     * Check if signing configuration is valid.
     */
    fun isSigningValid(): Boolean {
        return validateSigningConfig().isEmpty()
    }

    /**
     * Get signing info for display (without passwords).
     */
    fun getSigningInfoForDisplay(): String {
        val config = getSigningConfig() ?: return "Signing not configured"

        return buildString {
            appendLine("Keystore: ${config.keyStorePath}")
            appendLine("Key alias: ${config.keyAlias}")
            appendLine("V1 signing: ${config.isV1SigningEnabled}")
            appendLine("V2 signing: ${config.isV2SigningEnabled}")
            appendLine("V3 signing: ${config.isV3SigningEnabled}")
        }
    }
}
