package com.dskja.betterstreamflix.ui.support

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Window
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.databinding.DialogSupportStartupMobileBinding
import com.dskja.betterstreamflix.support.SupportLinkOpener
import com.dskja.betterstreamflix.support.SupportProvider
import com.dskja.betterstreamflix.utils.UserPreferences

class SupportStartupMobileDialog(
    context: Context,
    private val onOpenSupportHub: (() -> Unit)? = null,
) : Dialog(context, R.style.SupportDialogTheme) {

    private val binding = DialogSupportStartupMobileBinding.inflate(LayoutInflater.from(context))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(binding.root)
        setCancelable(true)
        setCanceledOnTouchOutside(true)

        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
            )
            attributes = attributes?.apply {
                windowAnimations = R.style.SupportDialogAnimation
            }
        }

        binding.cbSupportStartupNever.isChecked = UserPreferences.neverShowSupportOnStart

        binding.btnSupportStartupClose.setOnClickListener {
            persistNeverAgainIfChecked()
            dismiss()
        }
        binding.btnSupportStartupPrimary.setOnClickListener {
            persistNeverAgainIfChecked()
            dismiss()
            onOpenSupportHub?.invoke()
        }
        binding.btnSupportStartupSponsors.setOnClickListener {
            SupportLinkOpener.openProvider(context, SupportProvider.GITHUB_SPONSORS)
        }
        binding.btnSupportStartupBmc.setOnClickListener {
            SupportLinkOpener.openProvider(context, SupportProvider.BUY_ME_A_COFFEE)
        }
        binding.btnSupportStartupCommunity.setOnClickListener {
            SupportLinkOpener.openProvider(context, SupportProvider.DISCORD)
        }
        setOnDismissListener {
            persistNeverAgainIfChecked()
        }
    }

    private fun persistNeverAgainIfChecked() {
        if (binding.cbSupportStartupNever.isChecked) {
            UserPreferences.neverShowSupportOnStart = true
        }
    }
}
