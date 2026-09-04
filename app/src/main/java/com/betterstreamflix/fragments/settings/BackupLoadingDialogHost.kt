package com.betterstreamflix.fragments.settings

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.betterstreamflix.R
import com.betterstreamflix.compose.screens.GlassProgressDialog
import com.betterstreamflix.compose.theme.BetterStreamflixTheme

/**
 * Hosts a small non-cancelable glass progress dialog for backup / restore work.
 */
internal class BackupLoadingDialogHost(private val fragment: Fragment) {
    private var dialog: Dialog? = null

    fun show(titleRes: Int) {
        if (!fragment.isAdded) return
        val context = fragment.requireContext()
        val title = context.getString(titleRes)
        val message = context.getString(R.string.settings_refresh_cache_message)
        val existing = dialog
        if (existing?.isShowing == true) {
            existing.setContentView(composeView(title, message))
            return
        }
        val next = Dialog(context)
        next.setContentView(composeView(title, message))
        next.setCancelable(false)
        next.setCanceledOnTouchOutside(false)
        next.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.5f)
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        dialog = next
        next.show()
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }

    private fun composeView(title: String, message: String): ComposeView {
        return ComposeView(fragment.requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                BetterStreamflixTheme {
                    GlassProgressDialog(
                        title = title,
                        message = message,
                    )
                }
            }
        }
    }
}
