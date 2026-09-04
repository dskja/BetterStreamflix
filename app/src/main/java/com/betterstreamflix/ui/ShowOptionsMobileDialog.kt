package com.betterstreamflix.ui

import android.app.Dialog
import android.content.Context
import com.betterstreamflix.adapters.AppAdapter

/**
 * Compatibility wrapper — prefer [ShowOptionsDialog].
 */
@Deprecated("Use ShowOptionsDialog(context, show, isTv = false)")
class ShowOptionsMobileDialog(
    context: Context,
    show: AppAdapter.Item,
) : Dialog(context) {
    private val real = ShowOptionsDialog(context, show, isTv = false)

    override fun show() {
        real.show()
    }

    override fun dismiss() {
        real.dismiss()
        super.dismiss()
    }

    override fun hide() {
        real.hide()
        super.hide()
    }
}
