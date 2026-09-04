package com.betterstreamflix.activities.main

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
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
import com.betterstreamflix.BuildConfig
import com.betterstreamflix.R
import com.betterstreamflix.activities.tools.BypassWebViewActivity
import com.betterstreamflix.databinding.ActivityMainMobileBinding
import com.betterstreamflix.fragments.player.PlayerMobileFragment
import com.betterstreamflix.providers.Cine24hProvider
import com.betterstreamflix.providers.FilmyOnlineCcProvider
import com.betterstreamflix.providers.GuardaSerieProvider
import com.betterstreamflix.providers.IptvProvider
import com.betterstreamflix.providers.Provider
import com.betterstreamflix.providers.ZaluknijProvider
import com.betterstreamflix.ui.UpdateAppMobileDialog
import com.betterstreamflix.utils.AppLanguageManager
import com.betterstreamflix.utils.AppShortcuts
import com.betterstreamflix.utils.DeepLink
import com.betterstreamflix.utils.DeepLinkHandler
import com.betterstreamflix.utils.FileLogger
import com.betterstreamflix.utils.FirstRunHelper
import com.betterstreamflix.utils.ProfilePickerHelper
import com.betterstreamflix.utils.ProviderChangeNotifier
import com.betterstreamflix.utils.ThemeManager
import com.betterstreamflix.utils.UserPreferences
import com.betterstreamflix.utils.getCurrentFragment
import com.betterstreamflix.providers.AnimeOnlineNinjaProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.Base64
import kotlin.coroutines.resume

class MainMobileActivity : FragmentActivity() {

    private companion object {
        const val RESOLVER_TIMEOUT_MS = 12_000L
    }

    private data class ResolverPayload(
        val url: String,
    )

    private var _binding: ActivityMainMobileBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("Binding is null. Activity has been destroyed.")

    private val viewModel by viewModels<MainViewModel>()
    private val resolverWebSocketClient by lazy { OkHttpClient() }
    private val bypassWebViewLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val wsUrl = pendingWs
            val token = pendingToken
            val cookies =
                result.data?.getStringExtra(BypassWebViewActivity.EXTRA_COOKIE_HEADER)?.trim()

            clearResolverState()

            if (result.resultCode != Activity.RESULT_OK || wsUrl.isNullOrBlank() || token.isNullOrBlank()) {
                return@registerForActivityResult
            }

            lifecycleScope.launch {
                sendWebSocketDone(wsUrl, token, cookies)
                showPostBypassCloseDialog()
            }
        }

    private var pendingWs: String? = null
    private var pendingToken: String? = null

    private var updateAppDialog: UpdateAppMobileDialog? = null

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        FileLogger.logLifecycle("MainMobileActivity.onCreate")
        setTheme(ThemeManager.mobileThemeRes(UserPreferences.selectedTheme))

        super.onCreate(savedInstanceState)

        FileLogger.i("MainMobileActivity", "Initializing providers...")
        runCatching { AnimeOnlineNinjaProvider.init(this) }
            .onSuccess { FileLogger.i("MainMobileActivity", "✓ AnimeOnlineNinjaProvider init") }
            .onFailure { FileLogger.e("MainMobileActivity", "✗ AnimeOnlineNinjaProvider init FAILED", it) }
        runCatching { Cine24hProvider.init(this) }
            .onSuccess { FileLogger.i("MainMobileActivity", "✓ Cine24hProvider init") }
            .onFailure { FileLogger.e("MainMobileActivity", "✗ Cine24hProvider init FAILED", it) }
        runCatching { FilmyOnlineCcProvider.init(this) }
            .onSuccess { FileLogger.i("MainMobileActivity", "✓ FilmyOnlineCcProvider init") }
            .onFailure { FileLogger.e("MainMobileActivity", "✗ FilmyOnlineCcProvider init FAILED", it) }
        runCatching { GuardaSerieProvider.init(this) }
            .onSuccess { FileLogger.i("MainMobileActivity", "✓ GuardaSerieProvider init") }
            .onFailure { FileLogger.e("MainMobileActivity", "✗ GuardaSerieProvider init FAILED", it) }
        runCatching { ZaluknijProvider.init(this) }
            .onSuccess { FileLogger.i("MainMobileActivity", "✓ ZaluknijProvider init") }
            .onFailure { FileLogger.e("MainMobileActivity", "✗ ZaluknijProvider init FAILED", it) }
        FileLogger.i("MainMobileActivity", "All provider init attempts done")

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val palette = ThemeManager.palette(UserPreferences.selectedTheme)
        window.statusBarColor = palette.systemBar
        window.navigationBarColor = palette.systemBar

        _binding = ActivityMainMobileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyThemeNavigationChrome()
        FirstRunHelper.showStartupDialogsIfNeeded(this)
        ProfilePickerHelper.showIfNeeded(this)
        AppShortcuts.publish(this)

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContent) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_main_fragment) as? NavHostFragment
            val currentFragment = navHostFragment?.childFragmentManager?.primaryNavigationFragment

            val isPlayer = currentFragment is PlayerMobileFragment
            val isBottomNavVisible = binding.bnvMain.visibility == View.VISIBLE

            val bottomPadding = if (isPlayer || isBottomNavVisible) 0 else insets.bottom
            val topPadding = if (isPlayer) 0 else insets.top

            view.setPadding(insets.left, topPadding, insets.right, bottomPadding)
            windowInsets
        }


        updateImmersiveMode()

        val navHost =
            supportFragmentManager.findFragmentById(R.id.nav_main_fragment) as? NavHostFragment
            ?: run {
                Log.e("MainMobileActivity", "NavHostFragment not found")
                finish()
                return
            }
        val navController = navHost.navController

        if (BuildConfig.APP_LAYOUT == "tv" ||
            (BuildConfig.APP_LAYOUT != "mobile" &&
                packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK))
        ) {
            finish()
            startActivity(
                Intent(this, MainTvActivity::class.java).apply {
                    data = intent.data
                    action = intent.action
                    intent.extras?.let(::putExtras)
                },
            )
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

        FileLogger.i("MainMobileActivity", "Calling viewModel.checkUpdate()")
        viewModel.checkUpdate()

        binding.bnvMain.setupWithNavController(navController)
        binding.btnMainSearch.setOnClickListener {
            if (navController.currentDestination?.id != R.id.search) {
                navController.navigate(R.id.search)
            }
        }
        updateNavigationVisibility()
        updateBottomNavigationVisibility(navController.currentDestination?.id)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateNavigationVisibility(destination.id)
            updateBottomNavigationVisibility(destination.id)
            binding.mainContent.post { binding.mainContent.requestApplyInsets() }
        }

        lifecycleScope.launch {
            ProviderChangeNotifier.providerChangeFlow
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect {
                    updateNavigationVisibility(navController.currentDestination?.id)
                }
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
                        FileLogger.e("MainMobileActivity", "State.FailedUpdate: ${state.error.message}", state.error)
                        updateAppDialog?.isLoading = false
                        Toast.makeText(
                            this@MainMobileActivity,
                            state.error.message ?: getString(R.string.update_failed),
                            Toast.LENGTH_SHORT
                        ).show()
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

                val currentDestinationId = navController.currentDestination?.id

                if (currentDestinationId == R.id.settings) {
                    navigateToProviderHome(navController)
                    return
                }

                if (UserPreferences.currentProvider != null && currentDestinationId == R.id.home) {
                    closeTask()
                    return
                }

                if (UserPreferences.currentProvider != null &&
                    isTopLevelProviderDestination(currentDestinationId)
                ) {
                    navigateToProviderHome(navController)
                    return
                }

                if (!navController.navigateUp()) finish()
            }
        })

        if (savedInstanceState == null) {
            handleIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        dismissUpdateDialog()
        _binding = null
        super.onDestroy()
    }

    private fun clearResolverState() {
        pendingWs = null
        pendingToken = null
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
        val showBottomNav =
            UserPreferences.currentProvider != null && isTopLevelProviderDestination(destinationId)
        binding.bnvMain.visibility = if (showBottomNav) View.VISIBLE else View.GONE
        binding.btnMainSearch.visibility = if (
            UserPreferences.currentProvider != null &&
            isTopLevelProviderDestination(destinationId) &&
            destinationId != R.id.search
        ) View.VISIBLE else View.GONE
    }

    private fun updateNavigationVisibility(currentDestinationId: Int? = null) {
        val provider = UserPreferences.currentProvider ?: return
        val supportsMovies = Provider.supportsMovies(provider)
        val supportsTvShows = Provider.supportsTvShows(provider)

        binding.bnvMain.menu.findItem(R.id.movies)?.isVisible = supportsMovies
        binding.bnvMain.menu.findItem(R.id.tv_shows)?.apply {
            isVisible = supportsTvShows
            title = if (provider is IptvProvider) {
                getString(R.string.main_menu_all_channels)
            } else {
                getString(R.string.main_menu_tv_shows)
            }
        }

        val navHost =
            supportFragmentManager.findFragmentById(R.id.nav_main_fragment) as? NavHostFragment
        val navController = navHost?.navController ?: return
        when {
            currentDestinationId == R.id.movies && !supportsMovies -> {
                navController.navigate(R.id.tv_shows)
            }

            currentDestinationId == R.id.tv_shows && !supportsTvShows -> {
                navController.navigate(R.id.home)
            }
        }
    }

    private fun isTopLevelProviderDestination(destinationId: Int?): Boolean {
        return destinationId in setOf(
            R.id.search,
            R.id.home,
            R.id.movies,
            R.id.tv_shows,
            R.id.favorites,
            R.id.downloads,
            R.id.settings,
        )
    }

    private fun navigateToProviderHome(navController: androidx.navigation.NavController) {
        if (!navController.popBackStack(R.id.home, false)) {
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

    private fun closeTask() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask()
        } else {
            finishAffinity()
        }
    }

    private suspend fun requestResolverPayload(wsUrl: String, token: String): ResolverPayload? =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(RESOLVER_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val request = Request.Builder()
                        .url(wsUrl)
                        .build()

                    val socket =
                        resolverWebSocketClient.newWebSocket(request, object : WebSocketListener() {
                            override fun onOpen(webSocket: WebSocket, response: Response) {
                                Log.d("ResolverWS", "Connected -> requesting URL")
                                webSocket.send("resolve:$token")
                            }

                            override fun onMessage(webSocket: WebSocket, text: String) {
                                when {
                                    text.startsWith("payload:") -> {
                                        val payload = text.substringAfter("payload:").trim()
                                        val parsed = runCatching {
                                            val json = JSONObject(payload)
                                            ResolverPayload(
                                                url = json.optString("url"),
                                            )
                                        }.getOrNull()

                                        if (continuation.isActive) {
                                            continuation.resume(
                                                parsed?.takeUnless {
                                                    it.url.isBlank() || it.url.equals("null", ignoreCase = true)
                                                }
                                            )
                                        }
                                        webSocket.close(1000, null)
                                    }

                                    text.startsWith("url:") -> {
                                        val url = text.substringAfter("url:").trim()
                                        if (continuation.isActive) {
                                            continuation.resume(
                                                url.takeUnless {
                                                    it.isEmpty() || it.equals("null", ignoreCase = true)
                                                }?.let { ResolverPayload(url = it) }
                                            )
                                        }
                                        webSocket.close(1000, null)
                                    }

                                    text.startsWith("error:") -> {
                                        Log.e("ResolverWS", "Resolver returned error: $text")
                                        if (continuation.isActive) {
                                            continuation.resume(null)
                                        }
                                        webSocket.close(1000, null)
                                    }
                                }
                            }

                            override fun onFailure(
                                webSocket: WebSocket,
                                t: Throwable,
                                response: Response?,
                            ) {
                                if (!continuation.isActive) {
                                    Log.d("ResolverWS", "WS resolve cancelled or timed out")
                                    return
                                }
                                Log.e("ResolverWS", "WS resolve failed", t)
                                continuation.resume(null)
                            }
                        })

                    continuation.invokeOnCancellation {
                        socket.cancel()
                    }
                }
            }
        }

    private suspend fun sendWebSocketDone(wsUrl: String, token: String, cookies: String?) {
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(RESOLVER_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val request = Request.Builder()
                        .url(wsUrl)
                        .build()

                    val socket =
                        resolverWebSocketClient.newWebSocket(request, object : WebSocketListener() {
                            override fun onOpen(webSocket: WebSocket, response: Response) {
                                Log.d("ResolverWS", "Connected -> sending DONE")
                                val encodedCookies = cookies
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let {
                                        Base64.getEncoder().encodeToString(
                                            it.toByteArray(Charsets.UTF_8)
                                        )
                                    }
                                val message = if (encodedCookies.isNullOrBlank()) {
                                    "done:$token"
                                } else {
                                    "done:$token:$encodedCookies"
                                }
                                webSocket.send(message)
                            }

                            override fun onMessage(webSocket: WebSocket, text: String) {
                                if (text == "ack:$token" && continuation.isActive) {
                                    continuation.resume(Unit)
                                    webSocket.close(1000, null)
                                }
                            }

                            override fun onFailure(
                                webSocket: WebSocket,
                                t: Throwable,
                                response: Response?,
                            ) {
                                if (!continuation.isActive) {
                                    Log.d("ResolverWS", "WS done cancelled or timed out")
                                    return
                                }
                                Log.e("ResolverWS", "WS failed", t)
                                continuation.resume(Unit)
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

            if (BuildConfig.DEBUG) {
                Log.d("ResolverWS", "WS: $ws")
            }

            resolve(ws, token)
            return true
        }

        val deepLink = DeepLinkHandler.parse(data) ?: return false
        DeepLinkHandler.applyProviderIfPresent(deepLink.providerName)
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_main_fragment) as? NavHostFragment
            ?: return false
        val navController = navHost.navController

        return when (deepLink) {
            is DeepLink.Search -> {
                DeepLinkHandler.pendingSearchQuery = deepLink.query
                runCatching { navController.navigate(R.id.search) }.isSuccess
            }
            is DeepLink.Provider -> {
                val provider = Provider.providers.keys.find {
                    it.name.equals(deepLink.name, ignoreCase = true)
                }
                if (provider != null) {
                    UserPreferences.currentProvider = provider
                    runCatching { navController.navigate(R.id.home) }.isSuccess
                } else false
            }
            is DeepLink.Movie -> {
                runCatching {
                    navController.navigate(
                        R.id.action_global_movie,
                        android.os.Bundle().apply { putString("id", deepLink.id) },
                    )
                }.isSuccess
            }
            is DeepLink.TvShow -> {
                runCatching {
                    navController.navigate(
                        R.id.action_global_tv_show,
                        android.os.Bundle().apply { putString("id", deepLink.id) },
                    )
                }.isSuccess
            }
            is DeepLink.Episode -> {
                // Episode deep links lack season context; open as TV show id when possible.
                runCatching {
                    navController.navigate(
                        R.id.action_global_tv_show,
                        android.os.Bundle().apply { putString("id", deepLink.id) },
                    )
                }.isSuccess
            }
            is DeepLink.Favorites -> {
                runCatching { navController.navigate(R.id.favorites) }.isSuccess
            }
            is DeepLink.ContinueWatching -> {
                DeepLinkHandler.pendingOpenContinueWatching = true
                runCatching { navController.navigate(R.id.home) }.isSuccess
            }
        }
    }

    private fun resolve(ws: String, token: String) {
        pendingWs = ws
        pendingToken = token

        lifecycleScope.launch {
            val payload = requestResolverPayload(ws, token)
            if (payload == null) {
                showResolverConnectionErrorDialog(ws, token)
                return@launch
            }

            bypassWebViewLauncher.launch(
                Intent(this@MainMobileActivity, BypassWebViewActivity::class.java)
                    .putExtra(BypassWebViewActivity.EXTRA_URL, payload.url)
            )
        }
    }

    private fun showResolverConnectionErrorDialog(ws: String, token: String) {
        if (isFinishing || isDestroyed) return

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setMessage("Unable to reach the TV bypass websocket. Retry?")
            .setPositiveButton("Retry") { _, _ ->
                resolve(ws, token)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                clearResolverState()
            }
            .setOnCancelListener {
                clearResolverState()
            }
            .show()
    }

    private fun showPostBypassCloseDialog() {
        if (isFinishing || isDestroyed) return

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setMessage("Bypass completed. Do you want to close the app?")
            .setPositiveButton("Close app") { _, _ ->
                closeTask()
            }
            .setNegativeButton("Keep open", null)
            .setOnCancelListener(null)
            .show()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        (getCurrentFragment() as? PlayerMobileFragment)?.onUserLeaveHint()
    }

    fun updateImmersiveMode() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (UserPreferences.immersiveMode) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun applyThemeNavigationChrome() {
        val palette = ThemeManager.palette(UserPreferences.selectedTheme)
        val navColors = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(),
            ),
            intArrayOf(
                palette.mobileNavActive,
                palette.mobileNavInactive,
            )
        )

        binding.root.setBackgroundColor(palette.mobileNavBackground)
        binding.bnvMain.setBackgroundColor(palette.mobileNavBackground)
        binding.bnvMain.elevation = 0f
        binding.bnvMain.itemIconTintList = navColors
        binding.bnvMain.itemTextColor = navColors
        binding.bnvMain.itemActiveIndicatorColor = ColorStateList.valueOf(
            android.graphics.Color.TRANSPARENT,
        )

        window.statusBarColor = palette.systemBar
        window.navigationBarColor = palette.systemBar

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }
}
