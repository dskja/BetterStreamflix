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
import com.streamflixreborn.streamflix.ui.UpdateAppMobileDialog
import com.streamflixreborn.streamflix.providers.Provider
import com.streamflixreborn.streamflix.providers.Cine24hProvider
import com.streamflixreborn.streamflix.utils.UserPreferences
import com.streamflixreborn.streamflix.utils.getCurrentFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.RequestBody.Companion.toRequestBody

class MainMobileActivity : FragmentActivity() {

    private var _binding: ActivityMainMobileBinding? = null
    private val binding get() = _binding!!
    private var pendingTv: String? = null
    private var pendingUrl: String? = null
    private val viewModel by viewModels<MainViewModel>()

    private lateinit var updateAppDialog: UpdateAppMobileDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        // Tema impostato in base alle preferenze già caricate in StreamFlixApp
        when (UserPreferences.selectedTheme) {
            "nero_amoled_oled" -> setTheme(R.style.AppTheme_Mobile_NeroAmoledOled)
            else -> setTheme(R.style.AppTheme_Mobile)
        }

        super.onCreate(savedInstanceState)
        handleIntent(intent)
        
        // Inizializza il provider con il context dell'attività per gestire eventuali bypass visibili
        Cine24hProvider.init(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT

        _binding = ActivityMainMobileBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        val navHostFragment = this.supportFragmentManager
            .findFragmentById(binding.navMainFragment.id) as NavHostFragment
        val navController = navHostFragment.navController

        // Reindirizzamento TV se necessario
        if (BuildConfig.APP_LAYOUT == "tv" || (BuildConfig.APP_LAYOUT != "mobile" && packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK))) {
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
        binding.bnvMain.setOnItemReselectedListener { item ->
            navController.popBackStack(item.itemId, inclusive = true)
            navController.navigate(item.itemId)
        }
        
        updateNavigationVisibility()

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.search, R.id.home, R.id.movies, R.id.tv_shows, R.id.settings -> {
                    binding.bnvMain.visibility = View.VISIBLE
                    updateNavigationVisibility()
                    updateImmersiveMode()
                }
                else -> binding.bnvMain.visibility = View.GONE
            }
            binding.mainContent.post { binding.mainContent.requestApplyInsets() }
        }

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
                val handled = (getCurrentFragment() as? PlayerMobileFragment)?.onBackPressed() ?: false
                if (handled) return

                when (navController.currentDestination?.id) {
                    R.id.home -> finish()
                    R.id.search, R.id.movies, R.id.tv_shows, R.id.settings -> binding.bnvMain.findViewById<View>(R.id.home).performClick()
                    else -> if (!navController.navigateUp()) finish()
                }
            }
        })
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        (getCurrentFragment() as? PlayerMobileFragment)?.onUserLeaveHint()
    }
    
    private fun updateNavigationVisibility() {
        UserPreferences.currentProvider?.let { provider ->
            binding.bnvMain.menu.findItem(R.id.movies)?.isVisible = Provider.supportsMovies(provider)
            val tvShowsItem = binding.bnvMain.menu.findItem(R.id.tv_shows)
            tvShowsItem?.isVisible = Provider.supportsTvShows(provider)
            
            tvShowsItem?.title = if (provider.name == "CableVisionHD" || provider.name == "TvporinternetHD") 
                getString(R.string.main_menu_all_channels) else getString(R.string.main_menu_tv_shows)
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    fun updateImmersiveMode() {
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        if (UserPreferences.immersiveMode) {
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }
    }

    override fun onResume() {
        super.onResume()

        val tv = pendingTv ?: return

        lifecycleScope.launch {
            delay(4000) // give time after clicking "Weiter"

            try {
                okhttp3.OkHttpClient().newCall(
                    okhttp3.Request.Builder()
                        .url("http://$tv/callback")
                        .post("done".toRequestBody())
                        .build()
                ).execute()

                Log.d("Resolver", "TV notified")
            } catch (e: Exception) {
                Log.e("Resolver", "Failed to notify TV", e)
            }

            pendingTv = null
            pendingUrl = null
        }
    }

    private fun handleIntent(intent: Intent) {
        val data = intent.data ?: return

        if (data.scheme == "streamflix" && data.host == "resolve") {
            val tv = data.getQueryParameter("tv") ?: return
            val url = data.getQueryParameter("url") ?: return

            resolveForTv(tv, url)
        }
    }
    private fun resolveForTv(tv: String, url: String) {
        pendingTv = tv
        pendingUrl = url

        val customTabsIntent = androidx.browser.customtabs.CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()

        customTabsIntent.launchUrl(this, Uri.parse(url))
    }
}
