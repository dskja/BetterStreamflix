package com.dskja.betterstreamflix.utils

import android.content.Context
import android.widget.Toast
import com.dskja.betterstreamflix.R
import retrofit2.HttpException

/**
 * One-shot recovery for HTTP 409 responses from provider backends: clears the
 * app cache once, shows the standard notice and invokes [retry]. Used by every
 * content fragment instead of a hand-rolled `hasAutoCleared409` flag.
 *
 * Returns `true` when the error was a 409 and the retry was triggered — the
 * caller should skip its normal error handling in that case.
 */
class Http409CacheGuard {

    private var cleared = false

    fun handle(context: Context, error: Throwable?, retry: () -> Unit): Boolean {
        if (cleared || (error as? HttpException)?.code() != 409) return false
        // A 409 while offline is most likely a stale network callback — skip
        // the cache wipe and let the normal error path surface it.
        if (!com.dskja.betterstreamflix.download.DownloadConnectivityMonitor.isOnline(context)) {
            return false
        }
        cleared = true
        CacheUtils.clearAppCache(context)
        Toast.makeText(
            context,
            context.getString(R.string.clear_cache_done_409),
            Toast.LENGTH_SHORT,
        ).show()
        retry()
        return true
    }
}
