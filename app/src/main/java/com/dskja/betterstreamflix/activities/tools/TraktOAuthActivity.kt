package com.dskja.betterstreamflix.activities.tools

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.platform.trakt.TraktClient
import com.dskja.betterstreamflix.platform.trakt.TraktOAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles [betterstreamflix://trakt/oauth] redirect after Trakt browser login.
 */
class TraktOAuthActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        when (val result = TraktOAuth.consumeCallback(this, intent?.data)) {
            is TraktOAuth.Result.Ignored -> {
                finish()
            }
            is TraktOAuth.Result.Failed -> {
                Toast.makeText(
                    this,
                    getString(R.string.platform_trakt_oauth_failed, result.reason),
                    Toast.LENGTH_LONG,
                ).show()
                finish()
            }
            is TraktOAuth.Result.Code -> {
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        TraktClient.exchangeAuthorizationCode(result.code)
                    }
                    Toast.makeText(
                        this@TraktOAuthActivity,
                        if (ok) R.string.platform_trakt_oauth_success
                        else R.string.platform_trakt_oauth_exchange_failed,
                        Toast.LENGTH_LONG,
                    ).show()
                    finish()
                }
            }
        }
    }
}
