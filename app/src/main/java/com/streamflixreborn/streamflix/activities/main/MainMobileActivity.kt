package com.streamflixreborn.streamflix.activities.main

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import androidx.navigation.ui.setupWithNavController
import com.streamflixreborn.streamflix.BuildConfig
import com.streamflixreborn.streamflix.R
import com.streamflixreborn.streamflix.databinding.ActivityMainMobileBinding
import com.streamflixreborn.streamflix.fragments.player.PlayerMobileFragment
import com.streamflixreborn.streamflix.providers.Cine24hProvider
import com.streamflixreborn.streamflix.providers.Provider
import com.streamflixreborn.streamflix.ui.UpdateAppMobileDialog
import com.streamflixreborn.streamflix.utils.CustomTabHelper
import com.streamflixreborn.streamflix.utils.UserPreferences
import com.streamflixreborn.streamflix.utils.getCurrentFragment
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.*
import kotlin.coroutines.resume

class MainMobileActivity : FragmentActivity() {

    private var _binding: ActivityMainMobileBinding? = null
    private val binding get() = _binding!!

    private val viewModel by viewModels<MainViewModel>()
    private val resolverWebSocketClient by lazy { OkHttpClient() }
    private val customTabHelper = CustomTabHelper()

    // 🔥 Resolver state
    private var pendingWs: String? = null
    private var pendingToken: String? = null
    private var pendingUrl: String? = null
    private var isResolving = false
    private var customTabOpened = false

    private var updateAppDialog: UpdateAppMobileDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {

        when (UserPreferences.selectedTheme) {
            "nero_amoled_oled" -> setTheme(R.style.AppTheme_Mobile_NeroAmoledOled)
            else -> setTheme(R.style.AppTheme_Mobile)
        }

        super.onCreate(savedInstanceState)
        customTabHelper.warmup(this)
        if (handleIntent(intent)) return

        Cine24hProvider.init(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT

        _binding = ActivityMainMobileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContent) { view, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(sys.left, sys.top, sys.right, 0)
            binding.bnvMain.updatePadding(bottom = sys.bottom)
            insets
        }

        updateImmersiveMode()

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_main_fragment) as NavHostFragment
        val navController = navHost.navController

        if (BuildConfig.APP_LAYOUT == "tv" ||
            (BuildConfig.APP_LAYOUT != "mobile" && packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK))
        ) {
            finish()
            startActivity(Intent(this, MainTvActivity::class.java))
            return
        }

        if (savedInstanceState == null) {
            UserPreferences.currentProvider?.let {
                navController.navigate(
                    R.id.home,
                    null,
                    navOptions {
                        launchSingleTop = true
                        popUpTo(R.id.providers) {
                            inclusive = true
                        }
                    }
                )
            }
        }

        viewModel.checkUpdate()

        binding.bnvMain.setupWithNavController(navController)
        updateBottomNavigationVisibility(navController.currentDestination?.id)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateBottomNavigationVisibility(destination.id)
        }

        lifecycleScope.launch {
            viewModel.state.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { state ->
                when (state) {
                    is MainViewModel.State.SuccessCheckingUpdate -> {
                        showUpdateDialog(state)
                    }
                    MainViewModel.State.DownloadingUpdate -> updateAppDialog?.isLoading = true
                    is MainViewModel.State.SuccessDownloadingUpdate -> {
                        viewModel.installUpdate(this@MainMobileActivity, state.apk)
                        dismissUpdateDialog()
                    }
                    MainViewModel.State.InstallingUpdate -> updateAppDialog?.isLoading = true
                    is MainViewModel.State.FailedUpdate -> {
                        updateAppDialog?.isLoading = false
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

                if (UserPreferences.currentProvider != null && isTopLevelProviderDestination(navController.currentDestination?.id)) {
                    closeTask()
                    return
                }

                if (!navController.navigateUp()) finish()
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        customTabHelper.release()
        dismissUpdateDialog()
        _binding = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()

        if (!isResolving || !customTabOpened) return

        val wsUrl = pendingWs ?: return
        val token = pendingToken ?: return

        isResolving = false
        customTabOpened = false

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setMessage("Did you complete the bypass?")
            .setPositiveButton(android.R.string.ok) { _, _ ->
                lifecycleScope.launch {
                    sendWebSocketDone(wsUrl, token)
                    clearResolverState()
                    finish()
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                clearResolverState()
                finish()
            }
            .setNeutralButton("Retry") { _, _ ->
                pendingUrl?.let { customTabHelper.open(this, it) }
                customTabOpened = true
                isResolving = true
            }
            .setOnCancelListener {
                clearResolverState()
                finish()
            }
            .show()
    }

    private fun clearResolverState() {
        pendingWs = null
        pendingToken = null
        pendingUrl = null
    }

    private fun showUpdateDialog(state: MainViewModel.State.SuccessCheckingUpdate) {
        if (isFinishing || isDestroyed) return

        dismissUpdateDialog()
        updateAppDialog = UpdateAppMobileDialog(this, state.newReleases).also { dialog ->
            dialog.setOnUpdateClickListener {
                if (!dialog.isLoading) {
                    viewModel.downloadUpdate(this@MainMobileActivity, state.asset)
                }
            }
            dialog.show()
        }
    }

    private fun dismissUpdateDialog() {
        updateAppDialog?.takeIf { it.isShowing }?.dismiss()
        updateAppDialog = null
    }

    private fun updateBottomNavigationVisibility(destinationId: Int?) {
        val showBottomNav = UserPreferences.currentProvider != null && isTopLevelProviderDestination(destinationId)
        binding.bnvMain.visibility = if (showBottomNav) View.VISIBLE else View.GONE
    }

    private fun isTopLevelProviderDestination(destinationId: Int?): Boolean {
        return destinationId in setOf(
            R.id.search,
            R.id.home,
            R.id.movies,
            R.id.tv_shows,
            R.id.settings,
        )
    }

    private fun closeTask() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask()
        } else {
            finishAffinity()
        }
    }

    private suspend fun requestResolvedUrl(wsUrl: String, token: String): String? =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(5_000) {
                suspendCancellableCoroutine { continuation ->
                    val request = Request.Builder()
                        .url(wsUrl)
                        .build()

                    val socket = resolverWebSocketClient.newWebSocket(request, object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            Log.d("ResolverWS", "Connected → requesting URL")
                            webSocket.send("resolve:$token")
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            when {
                                text.startsWith("url:") -> {
                                    val url = text.substringAfter("url:")
                                    if (continuation.isActive) {
                                        continuation.resume(url)
                                    }
                                    webSocket.close(1000, null)
                                }

                                text.startsWith("error:") -> {
                                    if (continuation.isActive) {
                                        continuation.resume(null)
                                    }
                                    webSocket.close(1000, null)
                                }
                            }
                        }

                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            Log.e("ResolverWS", "WS resolve failed", t)
                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }
                    })

                    continuation.invokeOnCancellation {
                        socket.cancel()
                    }
                }
            }
        }

    private suspend fun sendWebSocketDone(wsUrl: String, token: String) {
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(5_000) {
                suspendCancellableCoroutine { continuation ->
                    val request = Request.Builder()
                        .url(wsUrl)
                        .build()

                    val socket = resolverWebSocketClient.newWebSocket(request, object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            Log.d("ResolverWS", "Connected → sending DONE")
                            webSocket.send("done:$token")
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            if (text == "ack:$token" && continuation.isActive) {
                                continuation.resume(Unit)
                                webSocket.close(1000, null)
                            }
                        }

                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            Log.e("ResolverWS", "WS failed", t)
                            if (continuation.isActive) {
                                continuation.resume(Unit)
                            }
                        }
                    })

                    continuation.invokeOnCancellation {
                        socket.cancel()
                    }
                }
            }
        }
    }

    private fun handleIntent(intent: Intent): Boolean {
        val data = intent.data ?: return false

        if (data.scheme == "streamflix" && data.host == "resolve") {
            val ws = data.getQueryParameter("ws") ?: return false
            val token = data.getQueryParameter("token") ?: return false

            Log.d("ResolverWS", "WS: $ws")

            resolve(ws, token)
            return true
        }

        return false
    }

    private fun resolve(ws: String, token: String) {
        pendingWs = ws
        pendingToken = token
        isResolving = true

        lifecycleScope.launch {
            val url = requestResolvedUrl(ws, token)
            if (url == null) {
                Toast.makeText(this@MainMobileActivity, "Unable to start bypass", Toast.LENGTH_SHORT).show()
                clearResolverState()
                finish()
                return@launch
            }

            pendingUrl = url
            customTabOpened = true
            customTabHelper.open(this@MainMobileActivity, url)
        }
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
