package com.betterstreamflix.fragments.player.settings

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.betterstreamflix.compose.theme.BetterStreamflixTheme

class PlayerSettingsMobileView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : PlayerSettingsView(context, attrs, defStyleAttr) {

    override var onSubtitlesClicked: (() -> Unit)? = null
    var onManualZoomClicked: (() -> Unit)? = null
    var onDownloadClicked: (() -> Unit)? = null

    private var screen by mutableStateOf(Setting.MAIN)
    private var refreshKey by mutableIntStateOf(0)
    private val composeView = ComposeView(context)

    init {
        addView(
            composeView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        composeView.setContent {
            BetterStreamflixTheme {
                PlayerSettingsPanel(
                    setting = screen,
                    items = itemsFor(screen, isTv = false),
                    title = titleFor(screen),
                    isTvLayout = false,
                    refreshKey = refreshKey,
                    onBack = { onBackPressed() },
                    onClose = { hide() },
                    onItemClick = { item -> applyNav(dispatchItemClick(item, isTvLayout = false)) },
                )
            }
        }
        visibility = View.GONE
    }

    fun onBackPressed(): Boolean {
        val parent = parentOf(screen)
        if (parent == null) {
            hide()
        } else {
            openScreen(parent)
        }
        return true
    }

    fun show() {
        visibility = View.VISIBLE
        openScreen(Setting.MAIN)
    }

    fun hide() {
        visibility = View.GONE
    }

    private fun openScreen(setting: Setting) {
        if (setting == Setting.SUBTITLES) {
            onSubtitlesClicked?.invoke()
        }
        screen = setting
        currentSettings = setting
        refreshKey++
    }

    private fun applyNav(result: NavResult) {
        when (result) {
            is NavResult.Open -> openScreen(result.setting)
            NavResult.Close -> hide()
            NavResult.Refresh -> refreshKey++
        }
    }
}
