package com.streamflixreborn.streamflix.activities.main

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
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
import com.streamflixreborn.streamflix.database.AppDatabase
import com.streamflixreborn.streamflix.databinding.ActivityMainMobileBinding
import com.streamflixreborn.streamflix.fragments.player.PlayerMobileFragment
import com.streamflixreborn.streamflix.ui.UpdateAppMobileDialog
import com.streamflixreborn.streamflix.providers.Provider
import com.streamflixreborn.streamflix.utils.UserPreferences
import com.streamflixreborn.streamflix.utils.getCurrentFragment
import kotlinx.coroutines.launch

class MainMobileActivity : FragmentActivity() {

    private var _binding: ActivityMainMobileBinding? = null
    private val binding get() = _binding!!

    private val viewModel by viewModels<MainViewModel>()

    private lateinit var updateAppDialog: UpdateAppMobileDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        window.statusBarColor = Color.TRANSPARENT

        _binding = ActivityMainMobileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Gestione Safezone Intelligente
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContent) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_main_fragment) as? NavHostFragment
            val currentFragment = navHostFragment?.childFragmentManager?.primaryNavigationFragment

            val isPlayer = currentFragment is PlayerMobileFragment
            val isBottomNavVisible = binding.bnvMain.visibility == View.VISIBLE

            // Se siamo nel player, nessun padding.
            // Se la barra sotto è visibile, non aggiungiamo padding al fondo (lo gestisce lei).
            // Se la barra sotto è nascosta (Dettagli), aggiungiamo padding per non finire sotto i tasti Android.
            val bottomPadding = if (isPlayer || isBottomNavVisible) 0 else insets.bottom
            val topPadding = if (isPlayer) 0 else insets.top

            view.setPadding(insets.left, topPadding, insets.right, bottomPadding)
            windowInsets
        }

        updateImmersiveMode()

        val navHostFragment = this.supportFragmentManager
            .findFragmentById(binding.navMainFragment.id) as NavHostFragment
        val navController = navHostFragment.navController

        AppDatabase.setup(this)

        when (BuildConfig.APP_LAYOUT) {
            "mobile" -> {}
            "tv" -> {
                finish()
                startActivity(Intent(this, MainTvActivity::class.java))
            }
            else -> {
                if (packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
                    finish()
                    startActivity(Intent(this, MainTvActivity::class.java))
                }
            }
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
                R.id.search,
                R.id.home,
                R.id.movies,
                R.id.tv_shows,
                R.id.settings -> {
                    binding.bnvMain.visibility = View.VISIBLE
                    updateNavigationVisibility()
                    updateImmersiveMode()
                }
                else -> binding.bnvMain.visibility = View.GONE
            }
            // Forza il ricalcolo della safezone dopo aver cambiato visibilità alla barra
            binding.mainContent.post {
                binding.mainContent.requestApplyInsets()
            }
        }

        lifecycleScope.launch {
            viewModel.state.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { state ->
                when (state) {
                    MainViewModel.State.CheckingUpdate -> {}
                    is MainViewModel.State.SuccessCheckingUpdate -> {
                        updateAppDialog = UpdateAppMobileDialog(this@MainMobileActivity, state.newReleases).also {
                            it.setOnUpdateClickListener { _ ->
                                if (!it.isLoading) viewModel.downloadUpdate(this@MainMobileActivity, state.asset)
                            }
                            it.show()
                        }
                    }

                    MainViewModel.State.DownloadingUpdate -> updateAppDialog.isLoading = true
                    is MainViewModel.State.SuccessDownloadingUpdate -> {
                        viewModel.installUpdate(this@MainMobileActivity, state.apk)
                        updateAppDialog.hide()
                    }

                    MainViewModel.State.InstallingUpdate -> updateAppDialog.isLoading = true

                    is MainViewModel.State.FailedUpdate -> {
                        Toast.makeText(
                            this@MainMobileActivity,
                            state.error.message ?: "",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (navController.currentDestination?.id) {
                    R.id.home -> finish()
                    R.id.search,
                    R.id.movies,
                    R.id.tv_shows,
                    R.id.settings -> binding.bnvMain.findViewById<View>(R.id.home).performClick()
                    else -> navController.navigateUp()
                        .takeIf { !it }?.let { finish() }
                }
            }
        })
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        when (val currentFragment = getCurrentFragment()) {
            is PlayerMobileFragment -> currentFragment.onUserLeaveHint()
        }
    }
    
    private fun updateNavigationVisibility() {
        UserPreferences.currentProvider?.let { provider ->
            binding.bnvMain.findViewById<View>(R.id.movies)?.visibility = 
                if (Provider.supportsMovies(provider)) View.VISIBLE else View.GONE
            
            binding.bnvMain.findViewById<View>(R.id.tv_shows)?.visibility = 
                if (Provider.supportsTvShows(provider)) View.VISIBLE else View.GONE
        }
    }

    fun updateImmersiveMode() {
        val window = window
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)

        if (UserPreferences.immersiveMode) {
            insetsController.systemBarsBehavior = 
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = 
                WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }
    }
}