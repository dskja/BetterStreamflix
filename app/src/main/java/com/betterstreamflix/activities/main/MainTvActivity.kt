package com.betterstreamflix.activities.main

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.bumptech.glide.Glide
import com.tanasi.navigation.widget.setupWithNavController
import com.betterstreamflix.BuildConfig
import com.betterstreamflix.R
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.databinding.ActivityMainTvBinding
import com.betterstreamflix.databinding.ContentHeaderMenuMainTvBinding
import com.betterstreamflix.fragments.player.PlayerTvFragment
import com.betterstreamflix.ui.UpdateAppTvDialog
import com.betterstreamflix.providers.IptvProvider
import com.betterstreamflix.providers.Provider
import com.betterstreamflix.providers.Cine24hProvider
import com.betterstreamflix.providers.FilmyOnlineCcProvider
import com.betterstreamflix.providers.ZaluknijProvider
import com.betterstreamflix.providers.GuardaSerieProvider
import com.betterstreamflix.utils.AppLanguageManager
import com.betterstreamflix.utils.DeepLink
import com.betterstreamflix.utils.DeepLinkHandler
import com.betterstreamflix.utils.ThemeManager
import com.betterstreamflix.utils.UserPreferences
import com.betterstreamflix.utils.getCurrentFragment
import com.betterstreamflix.providers.AnimeOnlineNinjaProvider
import kotlinx.coroutines.launch

class MainTvActivity : FragmentActivity() {

    private var _binding: ActivityMainTvBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("Binding is null. View has been destroyed.")

    private val viewModel by viewModels<MainViewModel>()

    private lateinit var updateAppDialog: UpdateAppTvDialog

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Il setup delle preferenze è già avvenuto in StreamFlixApp
        setTheme(ThemeManager.tvThemeRes(UserPreferences.selectedTheme))
        
        super.onCreate(savedInstanceState)
        
        // Provider init can fail on JVM/Robolectric (e.g. missing Cronet native libs).
        // Keep TV startup resilient like MainMobileActivity.
        runCatching { AnimeOnlineNinjaProvider.init(this) }
            .onFailure { android.util.Log.e("MainTvActivity", "AnimeOnlineNinjaProvider init failed", it) }
        runCatching { Cine24hProvider.init(this) }
            .onFailure { android.util.Log.e("MainTvActivity", "Cine24hProvider init failed", it) }
        runCatching { FilmyOnlineCcProvider.init(this) }
            .onFailure { android.util.Log.e("MainTvActivity", "FilmyOnlineCcProvider init failed", it) }
        runCatching { ZaluknijProvider.init(this) }
            .onFailure { android.util.Log.e("MainTvActivity", "ZaluknijProvider init failed", it) }
        runCatching { GuardaSerieProvider.init(this) }
            .onFailure { android.util.Log.e("MainTvActivity", "GuardaSerieProvider init failed", it) }

        _binding = ActivityMainTvBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyThemeNavigationChrome()
        com.betterstreamflix.utils.FirstRunHelper.showStartupDialogsIfNeeded(this)
        com.betterstreamflix.utils.ProfilePickerHelper.showIfNeeded(this)

        binding.ivSplashOverlay.animate()
            .alpha(0f)
            .setDuration(800)
            .setStartDelay(400)
            .withEndAction {
                binding.ivSplashOverlay.visibility = View.GONE
            }

        val navHostFragment = supportFragmentManager
            .findFragmentById(binding.navMainFragment.id) as? androidx.navigation.fragment.NavHostFragment
            ?: run {
                android.util.Log.e("MainTvActivity", "NavHostFragment not found")
                finish()
                return
            }
        val navController = navHostFragment.navController

        adjustLayoutDelta(null, null)

        if (BuildConfig.APP_LAYOUT == "mobile" || (BuildConfig.APP_LAYOUT != "tv" && !packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK))) {
            finish()
            startActivity(Intent(this, MainMobileActivity::class.java))
            return
        }

        if (savedInstanceState == null) {
            UserPreferences.currentProvider?.let {
                navController.navigate(R.id.home)
            }
        }

        binding.navMain.setupWithNavController(navController)
        updateNavigationVisibility()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding.navMainFragment.isFocusedByDefault = true
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.navMain.headerView?.apply {
                val header = ContentHeaderMenuMainTvBinding.bind(this)

                Glide.with(context)
                    .load(UserPreferences.currentProvider?.logo?.takeIf { it.isNotEmpty() } ?: R.drawable.ic_provider_default_logo)
                    .error(R.drawable.ic_provider_default_logo)
                    .into(header.ivNavigationHeaderIcon)
                header.tvNavigationHeaderTitle.text = UserPreferences.currentProvider?.name
                header.tvNavigationHeaderSubtitle.text = getString(R.string.main_menu_change_provider)
                val palette = ThemeManager.palette(UserPreferences.selectedTheme)
                header.tvNavigationHeaderTitle.setTextColor(palette.tvHeaderPrimary)
                header.tvNavigationHeaderSubtitle.setTextColor(palette.tvHeaderSecondary)
                setBackgroundColor(palette.tvNavBackground)

                setOnOpenListener {
                    header.tvNavigationHeaderTitle.visibility = View.VISIBLE
                    header.tvNavigationHeaderSubtitle.visibility = View.VISIBLE
                }
                setOnCloseListener {
                    header.tvNavigationHeaderTitle.visibility = View.GONE
                    header.tvNavigationHeaderSubtitle.visibility = View.GONE
                }

                setOnClickListener {
                    // Navigazione manuale per evitare dipendenza da Safe Args Directions non generate
                    navController.navigate(R.id.providers)
                }
            }

            when (destination.id) {
                R.id.search, R.id.home, R.id.movies, R.id.tv_shows, R.id.favorites, R.id.downloads, R.id.settings -> {
                    binding.navMain.visibility = View.VISIBLE
                    updateNavigationVisibility()
                }
                else -> binding.navMain.visibility = View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.state.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { state ->
                when (state) {
                    is MainViewModel.State.SuccessCheckingUpdate -> {
                        updateAppDialog = UpdateAppTvDialog(this@MainTvActivity, state.newReleases).also {
                            it.setOnUpdateClickListener { _ ->
                                if (!it.isLoading) viewModel.downloadUpdate(this@MainTvActivity, state.asset)
                            }
                            it.show()
                        }
                    }
                    MainViewModel.State.DownloadingUpdate -> if (::updateAppDialog.isInitialized) updateAppDialog.isLoading = true
                    is MainViewModel.State.SuccessDownloadingUpdate -> {
                        viewModel.installUpdate(this@MainTvActivity, state.apk)
                        if (::updateAppDialog.isInitialized) updateAppDialog.hide()
                    }
                    MainViewModel.State.InstallingUpdate -> if (::updateAppDialog.isInitialized) updateAppDialog.isLoading = true
                    is MainViewModel.State.FailedUpdate -> {
                        Toast.makeText(this@MainTvActivity, state.error.message ?: getString(R.string.update_failed), Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            }
        }

        if (savedInstanceState == null) {
            handleIntent(intent)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (navController.currentDestination?.id) {
                    R.id.home -> if (binding.navMain.hasFocus()) finish() else binding.navMain.requestFocus()
                    R.id.settings, R.id.search, R.id.movies, R.id.tv_shows, R.id.favorites, R.id.downloads -> {
                        navigateToProviderHome(navController)
                        binding.navMain.requestFocus()
                    }
                    else -> {
                        val handled = (getCurrentFragment() as? PlayerTvFragment)?.onBackPressed() ?: false
                        if (!handled && !navController.navigateUp()) finish()
                    }
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkUpdate()
    }

    private fun handleIntent(intent: Intent): Boolean {
        val data = intent.data ?: return false
        val deepLink = DeepLinkHandler.parse(data) ?: return false
        DeepLinkHandler.applyProviderIfPresent(deepLink.providerName)
        val navHost = supportFragmentManager
            .findFragmentById(binding.navMainFragment.id) as? NavHostFragment
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
                } else {
                    false
                }
            }
            is DeepLink.Movie -> {
                runCatching {
                    navController.navigate(
                        R.id.action_global_movie,
                        Bundle().apply { putString("id", deepLink.id) },
                    )
                }.isSuccess
            }
            is DeepLink.TvShow -> {
                runCatching {
                    navController.navigate(
                        R.id.action_global_tv_show,
                        Bundle().apply { putString("id", deepLink.id) },
                    )
                }.isSuccess
            }
            is DeepLink.Episode -> {
                runCatching {
                    navController.navigate(
                        R.id.action_global_tv_show,
                        Bundle().apply { putString("id", deepLink.id) },
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

    private fun applyThemeNavigationChrome() {
        val palette = ThemeManager.palette(UserPreferences.selectedTheme)
        window.statusBarColor = palette.systemBar
        window.navigationBarColor = palette.systemBar
        binding.navMain.setBackgroundColor(palette.tvNavBackground)
        binding.navMain.headerView?.let { headerView ->
            headerView.setBackgroundColor(palette.tvNavBackground)
            val header = ContentHeaderMenuMainTvBinding.bind(headerView)
            header.tvNavigationHeaderTitle.setTextColor(palette.tvHeaderPrimary)
            header.tvNavigationHeaderSubtitle.setTextColor(palette.tvHeaderSecondary)
        }
    }
    
    private fun updateNavigationVisibility() {
        UserPreferences.currentProvider?.let { provider ->
            binding.navMain.menu.findItem(R.id.movies)?.isVisible = Provider.supportsMovies(provider)
            val tvShowsItem = binding.navMain.menu.findItem(R.id.tv_shows)
            tvShowsItem?.isVisible = Provider.supportsTvShows(provider)
            tvShowsItem?.title = if (provider is IptvProvider)
                getString(R.string.main_menu_all_channels) else getString(R.string.main_menu_tv_shows)
        }
    }

    fun adjustLayoutDelta(deltaX: Int?, deltaY: Int?) {
        val uDeltaX = deltaX ?: UserPreferences.paddingX
        val uDeltaY = deltaY ?: UserPreferences.paddingY
        binding.root.setPadding(uDeltaX, uDeltaY, uDeltaX, uDeltaY)
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
}

