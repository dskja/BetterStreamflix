package com.betterstreamflix.utils

/**
 * Non-UI parental PIN helpers shared by Compose Settings and preference controllers.
 * Returns an error message, or null on success.
 */
object ParentalPinLogic {

    const val MIN_PIN_LENGTH = 4

    fun lockRemainingMinutes(): Int {
        val millis = UserPreferences.parentalControlLockRemainingMillis
        return ((millis + 60_000L - 1L) / 60_000L).toInt().coerceAtLeast(1)
    }

    fun isLocked(): Boolean =
        UserPreferences.isParentalControlTemporarilyLocked || UserPreferences.parentalControlHardLocked

    fun sessionStatusLabel(lockedHard: String, lockedTemporary: (Int) -> String, unlocked: String, inactive: String): String =
        when {
            !UserPreferences.isParentalControlActive -> inactive
            UserPreferences.parentalControlHardLocked -> lockedHard
            UserPreferences.isParentalControlTemporarilyLocked -> lockedTemporary(lockRemainingMinutes())
            else -> unlocked
        }

    /**
     * Verify the current parental PIN before changing parental settings.
     * @return error message, or null if verified / no PIN set.
     */
    fun verifyCurrentPin(entered: String): String? {
        when {
            UserPreferences.parentalControlHardLocked -> return "HARD_LOCKED"
            UserPreferences.isParentalControlTemporarilyLocked -> return "TEMP_LOCKED"
        }
        val current = UserPreferences.parentalControlPin
        if (current.isBlank()) return null
        return if (entered == current) {
            UserPreferences.registerParentalPinSuccess()
            null
        } else {
            UserPreferences.registerParentalPinFailure()
            when {
                UserPreferences.parentalControlHardLocked -> "HARD_LOCKED"
                UserPreferences.isParentalControlTemporarilyLocked -> "TEMP_LOCKED"
                else -> "INVALID"
            }
        }
    }

    /**
     * Set, change, or clear the parental PIN.
     * Blank clears PIN + max age when a PIN was already set.
     */
    fun setParentalPin(newPin: String): String? {
        val trimmed = newPin.trim()
        return when {
            trimmed.isBlank() -> {
                if (UserPreferences.parentalControlPin.isBlank()) {
                    "TOO_SHORT"
                } else {
                    UserPreferences.parentalControlPin = ""
                    UserPreferences.parentalControlMaxAge = null
                    UserPreferences.unlockParentalControls()
                    null
                }
            }
            trimmed.length < MIN_PIN_LENGTH -> "TOO_SHORT"
            else -> {
                UserPreferences.parentalControlPin = trimmed
                null
            }
        }
    }

    fun setAdminPin(newPin: String): String? {
        val trimmed = newPin.trim()
        return when {
            trimmed.isBlank() -> {
                if (UserPreferences.parentalControlAdminPin.isBlank()) {
                    "TOO_SHORT"
                } else {
                    UserPreferences.parentalControlAdminPin = ""
                    null
                }
            }
            trimmed.length < MIN_PIN_LENGTH -> "TOO_SHORT"
            else -> {
                UserPreferences.parentalControlAdminPin = trimmed
                null
            }
        }
    }

    fun verifyAdminPin(entered: String): String? {
        val current = UserPreferences.parentalControlAdminPin
        if (current.isBlank()) return "NO_ADMIN"
        return if (entered == current) {
            UserPreferences.unlockParentalControls()
            null
        } else {
            "INVALID_ADMIN"
        }
    }

    fun setMaxAge(label: String): String? {
        if (!UserPreferences.enableTmdb) return "REQUIRES_TMDB"
        if (UserPreferences.parentalControlPin.isBlank()) return "SET_PIN_FIRST"
        if (isLocked()) {
            return if (UserPreferences.parentalControlHardLocked) "HARD_LOCKED" else "TEMP_LOCKED"
        }
        val age = label.trim().removeSuffix("+").toIntOrNull()
        UserPreferences.parentalControlMaxAge = age
        return null
    }
}
