package com.dskja.betterstreamflix.fragments.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.preference.Preference
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.activities.tools.WatchlistImportActivity
import com.dskja.betterstreamflix.providers.SerienStreamAuthManager
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

/**
 * GuardaFlix-style Settings binding for the full SerienStream account / session surface:
 * status, WebView sign-in, validate, paste, copy, sign-out.
 */
object SerienStreamAuthSettingsController {

    fun bind(
        fragment: Fragment,
        scope: LifecycleCoroutineScope,
        ensurePreferences: (() -> Unit)? = null,
        findPreference: (String) -> Preference?,
    ) {
        ensurePreferences?.invoke()

        val status = findPreference("SERIENSTREAM_AUTH_STATUS")
        val login = findPreference("SERIENSTREAM_SESSION_LOGIN")
        val validate = findPreference("SERIENSTREAM_SESSION_VALIDATE")
        val paste = findPreference("SERIENSTREAM_SESSION_PASTE")
        val copy = findPreference("SERIENSTREAM_SESSION_COPY")
        val logout = findPreference("SERIENSTREAM_SESSION_LOGOUT")
        val legacyCookies = findPreference("SERIENSTREAM_SESSION_COOKIES")

        fun refresh() {
            val snap = SerienStreamAuthManager.snapshot()
            status?.summary = buildStatusSummary(fragment, snap)
            login?.isVisible = true
            validate?.isVisible = snap.isLoggedIn
            paste?.isVisible = true
            copy?.isVisible = snap.isLoggedIn
            logout?.isVisible = snap.isLoggedIn
            legacyCookies?.isVisible = false
        }

        login?.setOnPreferenceClickListener {
            fragment.startActivity(
                Intent(fragment.requireContext(), WatchlistImportActivity::class.java)
                    .putExtra(
                        WatchlistImportActivity.EXTRA_SOURCE,
                        WatchlistImportActivity.SOURCE_SERIENSTREAM,
                    )
                    .putExtra(WatchlistImportActivity.EXTRA_SAVE_SESSION_ONLY, true),
            )
            true
        }

        validate?.setOnPreferenceClickListener {
            val progress = AlertDialog.Builder(fragment.requireContext())
                .setTitle(R.string.serienstream_auth_validate_progress_title)
                .setMessage(R.string.serienstream_auth_validate_progress_message)
                .setCancelable(false)
                .create()
            progress.show()
            scope.launch {
                val result = SerienStreamAuthManager.validateSession()
                if (fragment.isAdded && progress.isShowing) {
                    progress.dismiss()
                }
                refresh()
                if (!fragment.isAdded) return@launch
                val message = when {
                    result.ok && !result.displayName.isNullOrBlank() ->
                        fragment.getString(
                            R.string.serienstream_auth_validate_ok_named,
                            result.displayName,
                        )
                    result.ok -> fragment.getString(R.string.serienstream_auth_validate_ok)
                    result.challengeActive ->
                        fragment.getString(R.string.serienstream_auth_validate_challenge)
                    result.redirectedToLogin ->
                        fragment.getString(R.string.serienstream_auth_validate_login)
                    else -> fragment.getString(R.string.serienstream_auth_validate_failed)
                }
                Toast.makeText(fragment.requireContext(), message, Toast.LENGTH_LONG).show()
            }
            true
        }

        paste?.setOnPreferenceClickListener {
            showPasteDialog(fragment) { raw ->
                val ok = SerienStreamAuthManager.pasteCookies(raw)
                refresh()
                Toast.makeText(
                    fragment.requireContext(),
                    if (ok) {
                        R.string.settings_serienstream_session_login_saved
                    } else {
                        R.string.serienstream_auth_paste_invalid
                    },
                    Toast.LENGTH_LONG,
                ).show()
            }
            true
        }

        copy?.setOnPreferenceClickListener {
            val cookies = SerienStreamAuthManager.exportCookieHeader()
            if (cookies.isBlank()) {
                Toast.makeText(
                    fragment.requireContext(),
                    R.string.settings_serienstream_session_cookies_empty,
                    Toast.LENGTH_SHORT,
                ).show()
                return@setOnPreferenceClickListener true
            }
            val clipboard = fragment.requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(
                ClipData.newPlainText("SerienStream cookies", cookies),
            )
            Toast.makeText(
                fragment.requireContext(),
                R.string.serienstream_auth_copied,
                Toast.LENGTH_SHORT,
            ).show()
            true
        }

        logout?.setOnPreferenceClickListener {
            AlertDialog.Builder(fragment.requireContext())
                .setTitle(R.string.settings_serienstream_session_cookies_clear_title)
                .setMessage(R.string.serienstream_auth_logout_message)
                .setPositiveButton(R.string.serienstream_auth_sign_out) { _, _ ->
                    SerienStreamAuthManager.logout()
                    refresh()
                    Toast.makeText(
                        fragment.requireContext(),
                        R.string.settings_serienstream_session_cookies_cleared,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }

        // Legacy single preference — redirect into the new controller actions.
        legacyCookies?.setOnPreferenceClickListener {
            if (SerienStreamAuthManager.isLoggedIn()) {
                logout?.performClick()
            } else {
                login?.performClick()
            }
            true
        }

        refresh()
    }

    private fun buildStatusSummary(
        fragment: Fragment,
        snap: SerienStreamAuthManager.SessionSnapshot,
    ): String {
        if (!snap.isLoggedIn) {
            return fragment.getString(R.string.serienstream_auth_status_signed_out)
        }
        val namePart = snap.displayName?.takeIf { it.isNotBlank() }
            ?: fragment.getString(R.string.serienstream_auth_status_signed_in)
        val cookiePart = fragment.getString(
            R.string.serienstream_auth_status_cookies,
            snap.cookieCount,
            snap.domain,
        )
        val validatedPart = when {
            snap.lastValidatedAtMs == null ->
                fragment.getString(R.string.serienstream_auth_status_not_validated)
            snap.lastValidatedOk == true -> {
                val whenText = DateFormat.getDateTimeInstance(
                    DateFormat.SHORT,
                    DateFormat.SHORT,
                ).format(Date(snap.lastValidatedAtMs))
                fragment.getString(R.string.serienstream_auth_status_validated_ok, whenText)
            }
            else -> {
                val whenText = DateFormat.getDateTimeInstance(
                    DateFormat.SHORT,
                    DateFormat.SHORT,
                ).format(Date(snap.lastValidatedAtMs))
                fragment.getString(R.string.serienstream_auth_status_validated_fail, whenText)
            }
        }
        return "$namePart\n$cookiePart\n$validatedPart"
    }

    private fun showPasteDialog(fragment: Fragment, onSubmit: (String) -> Unit) {
        val context = fragment.requireContext()
        val padding = (20 * context.resources.displayMetrics.density).toInt()
        val input = EditText(context).apply {
            hint = context.getString(R.string.serienstream_auth_paste_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
            maxLines = 8
            setText(SerienStreamAuthManager.exportCookieHeader())
            setSelection(text?.length ?: 0)
        }
        val container = FrameLayout(context).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(
                input,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.serienstream_auth_paste_title)
            .setMessage(R.string.serienstream_auth_paste_message)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.serienstream_auth_paste_save) { _, _ ->
                onSubmit(input.text?.toString().orEmpty())
            }
            .show()
    }
}
