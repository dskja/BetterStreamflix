package com.betterstreamflix.data

/**
 * Database health monitor — monitors database size, entry counts,
 * and performance metrics.
 */
object DatabaseHealthMonitor {

    data class DatabaseHealth(
        val totalSizeBytes: Long,
        val tableStats: List<TableStat>,
        val isHealthy: Boolean,
        val warnings: List<String>,
    )

    data class TableStat(
        val tableName: String,
        val rowCount: Int,
        val estimatedSizeBytes: Long,
    )

    /**
     * Check database health.
     */
    fun checkHealth(context: Context): DatabaseHealth {
        val warnings = mutableListOf<String>()
        val tableStats = mutableListOf<TableStat>()

        // Check database file size
        val dbFile = context.getDatabasePath("betterstreamflix.db")
        val dbSize = if (dbFile.exists()) dbFile.length() else 0L

        if (dbSize > 100 * 1024 * 1024) {
            warnings.add("Database is large (${dbSize / 1024 / 1024}MB), consider cleanup")
        }

        // Check WAL file size
        val walFile = context.getDatabasePath("betterstreamflix.db-wal")
        val walSize = if (walFile.exists()) walFile.length() else 0L
        if (walSize > 10 * 1024 * 1024) {
            warnings.add("WAL file is large (${walSize / 1024 / 1024}MB), consider checkpoint")
        }

        val isHealthy = warnings.isEmpty()

        return DatabaseHealth(
            totalSizeBytes = dbSize + walSize,
            tableStats = tableStats,
            isHealthy = isHealthy,
            warnings = warnings,
        )
    }

    /**
     * Get database file path.
     */
    fun getDatabasePath(context: Context): String {
        return context.getDatabasePath("betterstreamflix.db").absolutePath
    }

    /**
     * Format database size for display.
     */
    fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
            bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    /**
     * Check if database needs maintenance.
     */
    fun needsMaintenance(context: Context): Boolean {
        val health = checkHealth(context)
        return !health.isHealthy
    }
}
