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
import com.dskja.betterstreamflix.databinding.DialogSupportThanksBinding
import com.dskja.betterstreamflix.support.SupportLinkOpener
import com.dskja.betterstreamflix.support.SupportProvider

class SupportThanksDialog(
    context: Context,
) : Dialog(context, R.style.SupportDialogTheme) {

    private val binding = DialogSupportThanksBinding.inflate(LayoutInflater.from(context))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(binding.root)
        setCancelable(true)

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

        binding.btnSupportThanksBack.setOnClickListener { dismiss() }
        binding.btnSupportThanksCommunity.setOnClickListener {
            SupportLinkOpener.openProvider(context, SupportProvider.TELEGRAM)
            dismiss()
        }
        binding.btnSupportThanksBack.requestFocus()
    }
}
