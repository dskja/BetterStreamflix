package com.betterstreamflix.content

/**
 * Content lifecycle manager — manages content availability windows
 * and expiration.
 */
object ContentLifecycleManager {

    /**
     * Check if content is still available based on expiration.
     */
    fun isContentAvailable(expiresAt: Long?): Boolean {
        if (expiresAt == null) return true
        return System.currentTimeMillis() < expiresAt
    }

    /**
     * Get content availability status.
     */
    fun getAvailabilityStatus(
        addedAt: Long,
        expiresAt: Long?,
        isNewThresholdDays: Int = 30,
    ): AvailabilityStatus {
        if (expiresAt != null && System.currentTimeMillis() > expiresAt) {
            return AvailabilityStatus.EXPIRED
        }

        val isNewAge = (System.currentTimeMillis() - addedAt) < (isNewThresholdDays * 24L * 60L * 60L * 1000L)
        if (isNewAge) return AvailabilityStatus.NEW

        if (expiresAt != null) {
            val daysUntilExpiry = (expiresAt - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)
            if (daysUntilExpiry < 7) return AvailabilityStatus.EXPIRING_SOON
        }

        return AvailabilityStatus.AVAILABLE
    }

    /**
     * Get the remaining availability time.
     */
    fun getRemainingAvailability(expiresAt: Long?): Long? {
        if (expiresAt == null) return null
        return (expiresAt - System.currentTimeMillis()).coerceAtLeast(0)
    }

    /**
     * Format availability for display.
     */
    fun formatAvailability(status: AvailabilityStatus, expiresAt: Long?): String {
        return when (status) {
            AvailabilityStatus.AVAILABLE -> "Available"
            AvailabilityStatus.NEW -> "New"
            AvailabilityStatus.EXPIRING_SOON -> {
                val remaining = getRemainingAvailability(expiresAt) ?: 0
                val days = remaining / (24 * 60 * 60 * 1000)
                "Expires in ${days}d"
            }
            AvailabilityStatus.EXPIRED -> "Expired"
        }
    }

    enum class AvailabilityStatus {
        AVAILABLE,
        NEW,
        EXPIRING_SOON,
        EXPIRED,
    }
}
