package com.betterstreamflix.security

import android.content.Context
import androidx.core.content.edit

/**
 * PIN validator — validates parental control PINs with
 * rate limiting and lockout.
 */
object PinValidator {

    private const val PREFS_NAME = "pin_security"
    private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
    private const val KEY_LOCKED_UNTIL = "locked_until"
    private const val MAX_ATTEMPTS = 5
    private const val LOCKOUT_DURATION_MS = 5 * 60 * 1000L // 5 minutes

    /**
     * Validate a PIN against the stored PIN.
     */
    fun validate(context: Context, inputPin: String, storedPin: String): ValidationResult {
        if (isLocked(context)) {
            return ValidationResult.Locked(getRemainingLockTime(context))
        }

        if (inputPin == storedPin) {
            resetAttempts(context)
            return ValidationResult.Success
        }

        val attempts = incrementAttempts(context)
        if (attempts >= MAX_ATTEMPTS) {
            lock(context)
            return ValidationResult.Locked(LOCKOUT_DURATION_MS)
        }

        return ValidationResult.Invalid(attemptsRemaining = MAX_ATTEMPTS - attempts)
    }

    /**
     * Check if PIN input is locked.
     */
    fun isLocked(context: Context): Boolean {
        val lockedUntil = getLockedUntil(context)
        return System.currentTimeMillis() < lockedUntil
    }

    /**
     * Get remaining lock time in milliseconds.
     */
    fun getRemainingLockTime(context: Context): Long {
        val lockedUntil = getLockedUntil(context)
        return (lockedUntil - System.currentTimeMillis()).coerceAtLeast(0)
    }

    /**
     * Validate PIN format (4-8 digits).
     */
    fun isValidPinFormat(pin: String): Boolean {
        return pin.length in 4..8 && pin.all { it.isDigit() }
    }

    private fun getAttempts(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_FAILED_ATTEMPTS, 0)
    }

    private fun incrementAttempts(context: Context): Int {
        val attempts = getAttempts(context) + 1
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putInt(KEY_FAILED_ATTEMPTS, attempts)
        }
        return attempts
    }

    private fun resetAttempts(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putInt(KEY_FAILED_ATTEMPTS, 0)
            putLong(KEY_LOCKED_UNTIL, 0)
        }
    }

    private fun lock(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putLong(KEY_LOCKED_UNTIL, System.currentTimeMillis() + LOCKOUT_DURATION_MS)
        }
    }

    private fun getLockedUntil(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LOCKED_UNTIL, 0)
    }

    sealed class ValidationResult {
        data object Success : ValidationResult()
        data class Invalid(val attemptsRemaining: Int) : ValidationResult()
        data class Locked(val remainingMs: Long) : ValidationResult()
    }
}
