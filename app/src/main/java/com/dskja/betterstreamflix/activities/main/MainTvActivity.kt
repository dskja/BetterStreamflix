package com.dskja.betterstreamflix.activities.main

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
import com.dskja.betterstreamflix.BuildConfig
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.database.AppDatabase
import com.dskja.betterstreamflix.databinding.ActivityMainTvBinding
import com.dskja.betterstreamflix.databinding.ContentHeaderMenuMainTvBinding
import com.dskja.betterstreamflix.fragments.player.PlayerTvFragment
import com.dskja.betterstreamflix.ui.UpdateAppTvDialog
import com.dskja.betterstreamflix.providers.IptvProvider
import com.dskja.betterstreamflix.providers.Provider
import com.dskja.betterstreamflix.providers.Cine24hProvider
import com.dskja.betterstreamflix.providers.FilmyOnlineCcProvider
import com.dskja.betterstreamflix.providers.ZaluknijProvider
import com.dskja.betterstreamflix.providers.GuardaSerieProvider
import com.dskja.betterstreamflix.utils.AppLanguageManager
import com.dskja.betterstreamflix.utils.ThemeManager
import com.dskja.betterstreamflix.utils.UserPreferences
import com.dskja.betterstreamflix.utils.getCurrentFragment
import com.dskja.betterstreamflix.providers.AnimeOnlineNinjaProvider
import kotlinx.coroutines.launch

class MainTvActivity : FragmentActivity() {

    private var _binding: ActivityMainTvBinding? = null
    private val binding get() = _binding!!

    private val viewModel by viewModels<MainViewModel>()

    private lateinit var updateAppDialog: UpdateAppTvDialog

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Il setup delle preferenze è già avvenuto in BetterStreamflixApp
        setTheme(ThemeManager.tvThemeRes(UserPreferences.selectedTheme))
        
        super.onCreate(savedInstanceState)
        
        // Defer heavy provider native/WebView setup — eager Cronet/WebView on Fire Stick
        // 4K (1st gen / Fire OS 6) was killing the process right after the splash overlay.
        bindingRootAndChrome()
        deferProviderInits()

        binding.ivSplashOverlay.animate()
            .alpha(0f)
            .setDuration(800)
            .setStartDelay(400)
            .withEndAction {
                _binding?.ivSplashOverlay?.visibility = View.GONE
            }

        val navHostFragment = this.supportFragmentManager
            .findFragmentById(binding.navMainFragment.id) as NavHostFragment
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
                R.id.search, R.id.home, R.id.movies, R.id.tv_shows, R.id.favorites, R.id.settings -> {
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
                        Toast.makeText(this@MainTvActivity, state.error.message ?: "Update failed", Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (navController.currentDestination?.id) {
                    R.id.home -> if (binding.navMain.hasFocus()) finish() else binding.navMain.requestFocus()
                    R.id.settings, R.id.search, R.id.movies, R.id.tv_shows, R.id.favorites -> {
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

    private fun bindingRootAndChrome() {
        _binding = ActivityMainTvBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyThemeNavigationChrome()
    }

    private fun deferProviderInits() {
        // Post so the splash/first frame can paint before native libs load.
        window.decorView.post {
            runCatching { AnimeOnlineNinjaProvider.init(this) }
            runCatching { Cine24hProvider.init(this) }
            runCatching { FilmyOnlineCcProvider.init(this) }
            runCatching { ZaluknijProvider.init(this) }
            runCatching { GuardaSerieProvider.init(this) }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkUpdate()
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
