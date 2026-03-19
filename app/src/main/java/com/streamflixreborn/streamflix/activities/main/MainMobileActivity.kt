package com.streamflixreborn.streamflix.activities.main

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.streamflixreborn.streamflix.BuildConfig
import com.streamflixreborn.streamflix.R
import com.streamflixreborn.streamflix.databinding.ActivityMainMobileBinding
import com.streamflixreborn.streamflix.fragments.player.PlayerMobileFragment
import com.streamflixreborn.streamflix.providers.Cine24hProvider
import com.streamflixreborn.streamflix.providers.Provider
import com.streamflixreborn.streamflix.ui.UpdateAppMobileDialog
import com.streamflixreborn.streamflix.utils.UserPreferences
import com.streamflixreborn.streamflix.utils.getCurrentFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*

class MainMobileActivity : FragmentActivity() {

    private var _binding: ActivityMainMobileBinding? = null
    private val binding get() = _binding!!

    private val viewModel by viewModels<MainViewModel>()

    // 🔥 Resolver state
    private var pendingWs: String? = null
    private var pendingUrl: String? = null
    private var isResolving = false
    private var customTabOpened = false

    private lateinit var updateAppDialog: UpdateAppMobileDialog

    override fun onCreate(savedInstanceState: Bundle?) {

        when (UserPreferences.selectedTheme) {
            "nero_amoled_oled" -> setTheme(R.style.AppTheme_Mobile_NeroAmoledOled)
            else -> setTheme(R.style.AppTheme_Mobile)
        }

        super.onCreate(savedInstanceState)

        // 🔴 handle deep link FIRST
        if (handleIntent(intent)) return

        Cine24hProvider.init(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT

        _binding = ActivityMainMobileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContent) { view, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(sys.left, sys.top, sys.right, sys.bottom)
            insets
        }

        updateImmersiveMode()

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_main_fragment) as NavHostFragment
        val navController = navHost.navController

        // redirect TV
        if (BuildConfig.APP_LAYOUT == "tv" ||
            (BuildConfig.APP_LAYOUT != "mobile" && packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK))
        ) {
            finish()
            startActivity(Intent(this, MainTvActivity::class.java))
            return
        }

        if (savedInstanceState == null) {
            UserPreferences.currentProvider?.let {
                navController.navigate(R.id.home)
            }
        }

        viewModel.checkUpdate()

        binding.bnvMain.setupWithNavController(navController)

        lifecycleScope.launch {
            viewModel.state.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { state ->
                when (state) {
                    is MainViewModel.State.SuccessCheckingUpdate -> {
                        updateAppDialog = UpdateAppMobileDialog(this@MainMobileActivity, state.newReleases).also {
                            it.setOnUpdateClickListener { _ ->
                                if (!it.isLoading) viewModel.downloadUpdate(this@MainMobileActivity, state.asset)
                            }
                            it.show()
                        }
                    }
                    MainViewModel.State.DownloadingUpdate -> if (::updateAppDialog.isInitialized) updateAppDialog.isLoading = true
                    is MainViewModel.State.SuccessDownloadingUpdate -> {
                        viewModel.installUpdate(this@MainMobileActivity, state.apk)
                        if (::updateAppDialog.isInitialized) updateAppDialog.hide()
                    }
                    MainViewModel.State.InstallingUpdate -> if (::updateAppDialog.isInitialized) updateAppDialog.isLoading = true
                    is MainViewModel.State.FailedUpdate -> {
                        Toast.makeText(this@MainMobileActivity, state.error.message ?: "Update failed", Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val handled =
                    (getCurrentFragment() as? PlayerMobileFragment)?.onBackPressed() ?: false
                if (handled) return

                if (!navController.navigateUp()) finish()
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()

        if (!isResolving || !customTabOpened) return

        val wsUrl = pendingWs ?: return

        isResolving = false
        customTabOpened = false

        lifecycleScope.launch {
            sendWebSocketDone(wsUrl)

            pendingWs = null
            pendingUrl = null

            finish()
        }
    }

    // -----------------------------
    // 🔥 WebSocket send
    // -----------------------------
    private suspend fun sendWebSocketDone(wsUrl: String) {
        withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()

                val request = Request.Builder()
                    .url(wsUrl)
                    .build()

                val ws = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.d("ResolverWS", "Connected → sending DONE")
                        webSocket.send("done")
                        webSocket.close(1000, null)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.e("ResolverWS", "WS failed", t)
                    }
                })

            } catch (e: Exception) {
                Log.e("ResolverWS", "Error", e)
            }
        }
    }

    // -----------------------------
    // 🔥 Deep link
    // -----------------------------
    private fun handleIntent(intent: Intent): Boolean {
        val data = intent.data ?: return false

        if (data.scheme == "streamflix" && data.host == "resolve") {

            val ws = data.getQueryParameter("ws") ?: return false
            val url = data.getQueryParameter("url") ?: return false

            Log.d("ResolverWS", "WS: $ws")

            resolve(ws, url)
            return true
        }

        return false
    }

    private fun resolve(ws: String, url: String) {
        pendingWs = ws
        pendingUrl = url
        isResolving = true
        customTabOpened = true

        val intent = CustomTabsIntent.Builder().build()
        intent.launchUrl(this, Uri.parse(url))
    }

    fun updateImmersiveMode() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (UserPreferences.immersiveMode) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}