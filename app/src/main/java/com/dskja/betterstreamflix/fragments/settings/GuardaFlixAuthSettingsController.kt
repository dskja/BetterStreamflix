package com.dskja.betterstreamflix.fragments.settings

import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.preference.Preference
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.providers.GuardaFlixProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object GuardaFlixAuthSettingsController {
    fun bind(
        fragment: Fragment,
        scope: LifecycleCoroutineScope,
        findPreference: (String) -> Preference?,
    ) {
        val status = findPreference("provider_guardaflix_auth_status") ?: return
        val signIn = findPreference("provider_guardaflix_sign_in")
        val signUp = findPreference("provider_guardaflix_sign_up")
        val signOut = findPreference("provider_guardaflix_sign_out")

        fun refresh() {
            val username = GuardaFlixProvider.authUsername()
            val loggedIn = GuardaFlixProvider.isLoggedIn()
            status.summary = if (loggedIn && username.isNotBlank()) {
                fragment.getString(R.string.guardaflix_signed_in_as, username)
            } else if (loggedIn) {
                fragment.getString(R.string.guardaflix_signed_in)
            } else {
                fragment.getString(R.string.guardaflix_signed_out)
            }
            signIn?.isVisible = !loggedIn
            signUp?.isVisible = !loggedIn
            signOut?.isVisible = loggedIn
        }

        signIn?.setOnPreferenceClickListener {
            showCredentialsDialog(
                fragment,
                titleRes = R.string.guardaflix_sign_in,
                confirmPassword = false,
            ) { username, password ->
                runAuth(fragment, scope, ::refresh) {
                    GuardaFlixProvider.login(username, password)
                }
            }
            true
        }

        signUp?.setOnPreferenceClickListener {
            showCredentialsDialog(
                fragment,
                titleRes = R.string.guardaflix_sign_up,
                confirmPassword = true,
            ) { username, password ->
                runAuth(fragment, scope, ::refresh) {
                    GuardaFlixProvider.register(username, password)
                }
            }
            true
        }

        signOut?.setOnPreferenceClickListener {
            GuardaFlixProvider.logout()
            refresh()
            Toast.makeText(
                fragment.requireContext(),
                R.string.guardaflix_sign_out_success,
                Toast.LENGTH_SHORT,
            ).show()
            true
        }

        refresh()
    }

    private fun showCredentialsDialog(
        fragment: Fragment,
        titleRes: Int,
        confirmPassword: Boolean,
        onSubmit: (String, String) -> Unit,
    ) {
        val context = fragment.requireContext()
        val padding = (24 * context.resources.displayMetrics.density).toInt()
        val username = EditText(context).apply {
            hint = context.getString(R.string.guardaflix_username_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME
            isSingleLine = true
        }
        val password = EditText(context).apply {
            hint = context.getString(R.string.guardaflix_password_hint)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
        }
        val confirm = EditText(context).apply {
            hint = context.getString(R.string.guardaflix_confirm_password_hint)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
            visibility = if (confirmPassword) android.view.View.VISIBLE else android.view.View.GONE
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
            addView(username, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(password, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(confirm, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle(titleRes)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(titleRes, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val usernameValue = username.text.toString().trim()
                val passwordValue = password.text.toString()
                val confirmValue = confirm.text.toString()
                when {
                    usernameValue.length < 3 -> {
                        Toast.makeText(
                            context,
                            R.string.guardaflix_invalid_username,
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    passwordValue.length < 6 -> {
                        Toast.makeText(
                            context,
                            R.string.guardaflix_invalid_password,
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    confirmPassword && passwordValue != confirmValue -> {
                        Toast.makeText(
                            context,
                            R.string.guardaflix_password_mismatch,
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    else -> {
                        dialog.dismiss()
                        onSubmit(usernameValue, passwordValue)
                    }
                }
            }
        }
        dialog.show()
    }

    private fun runAuth(
        fragment: Fragment,
        scope: LifecycleCoroutineScope,
        refresh: () -> Unit,
        action: () -> GuardaFlixProvider.AuthResult,
    ) {
        val progress = AlertDialog.Builder(fragment.requireContext())
            .setTitle(R.string.guardaflix_auth_progress_title)
            .setMessage(R.string.guardaflix_auth_progress_message)
            .setCancelable(false)
            .create()
        progress.show()
        scope.launch {
            val result = withContext(Dispatchers.IO) { action() }
            progress.dismiss()
            refresh()
            val message = if (result.ok) {
                fragment.getString(R.string.guardaflix_auth_success)
            } else {
                result.error?.takeIf { it.isNotBlank() }
                    ?: fragment.getString(R.string.guardaflix_auth_failed)
            }
            Toast.makeText(fragment.requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }
}
