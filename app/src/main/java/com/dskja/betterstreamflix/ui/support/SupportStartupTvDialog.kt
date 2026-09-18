package com.dskja.betterstreamflix.ui.support

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Window
import android.view.WindowManager
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.databinding.DialogSupportStartupTvBinding
import com.dskja.betterstreamflix.support.SupportLinkOpener
import com.dskja.betterstreamflix.support.SupportProvider
import com.dskja.betterstreamflix.utils.UserPreferences

class SupportStartupTvDialog(
    context: Context,
    private val onOpenSupportHub: (() -> Unit)? = null,
) : Dialog(context, R.style.SupportDialogTheme) {

    private val binding = DialogSupportStartupTvBinding.inflate(LayoutInflater.from(context))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(binding.root)
        setCancelable(true)
        setCanceledOnTouchOutside(true)

        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.92f).toInt(),
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
            SupportLinkOpener.openProvider(context, SupportProvider.TELEGRAM)
        }
        binding.btnSupportStartupRepo.setOnClickListener {
            SupportLinkOpener.openProvider(context, SupportProvider.GITHUB_REPOSITORY)
        }

        binding.btnSupportStartupPrimary.requestFocus()

        setOnDismissListener { persistNeverAgainIfChecked() }
    }

    private fun persistNeverAgainIfChecked() {
        if (binding.cbSupportStartupNever.isChecked) {
            UserPreferences.neverShowSupportOnStart = true
        }
    }
}
