package com.betterstreamflix.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.betterstreamflix.adapters.AppAdapter
import com.betterstreamflix.compose.screens.ShowOptionsSheet
import com.betterstreamflix.compose.theme.BetterStreamflixTheme
import com.betterstreamflix.fragments.home.HomeMobileFragment
import com.betterstreamflix.fragments.home.HomeMobileFragmentDirections
import com.betterstreamflix.fragments.home.HomeTvFragment
import com.betterstreamflix.fragments.home.HomeTvFragmentDirections
import com.betterstreamflix.models.TvShow
import com.betterstreamflix.utils.getCurrentFragment
import com.betterstreamflix.utils.toActivity

/**
 * Unified Compose host for show options (mobile bottom sheet + TV side panel).
 */
class ShowOptionsDialog(
    context: Context,
    private val show: AppAdapter.Item,
    private val isTv: Boolean,
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val fragment = context.toActivity()?.getCurrentFragment()
        val canOpenTvShow = when {
            isTv -> fragment is HomeTvFragment
            else -> fragment is HomeMobileFragment
        }

        setContentView(
            ComposeView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    if (isTv) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    BetterStreamflixTheme {
                        ShowOptionsSheet(
                            item = show,
                            isTvLayout = isTv,
                            onDismiss = { dismiss() },
                            canOpenTvShow = canOpenTvShow,
                            onOpenTvShow = { tvShow -> navigateToTvShow(tvShow) },
                            scope = context.toActivity()?.lifecycleScope,
                        )
                    }
                }
            },
        )

        setCancelable(true)
        setCanceledOnTouchOutside(true)

        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(if (isTv) 0.55f else 0.45f)

            val metrics = context.resources.displayMetrics
            if (isTv) {
                setGravity(Gravity.END)
                setLayout(
                    (metrics.widthPixels * 0.35f).toInt(),
                    metrics.heightPixels,
                )
            } else {
                setGravity(Gravity.BOTTOM)
                setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
        }
    }

    private fun navigateToTvShow(tvShow: TvShow) {
        when (val fragment = context.toActivity()?.getCurrentFragment()) {
            is HomeMobileFragment -> {
                NavHostFragment.findNavController(fragment).navigate(
                    HomeMobileFragmentDirections.actionHomeToTvShow(
                        id = tvShow.id,
                        poster = tvShow.poster,
                        banner = tvShow.banner,
                    ),
                )
            }
            is HomeTvFragment -> {
                NavHostFragment.findNavController(fragment).navigate(
                    HomeTvFragmentDirections.actionHomeToTvShow(
                        id = tvShow.id,
                        poster = tvShow.poster,
                        banner = tvShow.banner,
                    ),
                )
            }
        }
    }
}
