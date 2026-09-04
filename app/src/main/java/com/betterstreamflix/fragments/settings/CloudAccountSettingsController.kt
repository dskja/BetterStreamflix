package com.betterstreamflix.fragments.settings

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.preference.Preference
import com.betterstreamflix.R
import com.betterstreamflix.compose.screens.CloudCredentialsDialog
import com.betterstreamflix.compose.screens.CloudSyncConflictDialog
import com.betterstreamflix.compose.screens.CloudSyncProgressDialog
import com.betterstreamflix.compose.theme.BetterStreamflixTheme
import com.betterstreamflix.sync.CloudAccountAlreadyLinkedException
import com.betterstreamflix.sync.CloudAccountDataConflictException
import com.betterstreamflix.sync.CloudSyncManager
import com.betterstreamflix.sync.CloudSyncProgress
import com.betterstreamflix.sync.SupabaseProvider
import com.betterstreamflix.utils.UserProfiles
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object CloudAccountSettingsController {
    fun bind(
        fragment: Fragment,
        scope: LifecycleCoroutineScope,
        findPreference: (String) -> Preference?,
    ) {
        val status = findPreference("cloud_account_status") ?: return
        val signIn = findPreference("cloud_sign_in")
        val signUp = findPreference("cloud_sign_up")
        val signOut = findPreference("cloud_sign_out")
        val syncNow = findPreference("cloud_sync_now")

        fun refresh() {
            val profile = UserProfiles.active()
            val email = CloudSyncManager.currentUserEmail(profile.id)
                ?: com.betterstreamflix.sync.CloudAccountStore.activeUserEmail(
                    fragment.requireContext(),
                    profile.id,
                )
            val lastSynced = CloudSyncManager.lastSyncedAtMillis(fragment.requireContext(), profile.id)
            val accountLine = when {
                email == null -> fragment.getString(R.string.cloud_sync_signed_out)
                lastSynced > 0L -> {
                    val formatted = java.text.DateFormat.getDateTimeInstance(
                        java.text.DateFormat.SHORT,
                        java.text.DateFormat.SHORT,
                    ).format(java.util.Date(lastSynced))
                    fragment.getString(R.string.cloud_sync_signed_in_as, email) +
                        "\n" + fragment.getString(R.string.sync_last_synced, formatted)
                }
                else -> fragment.getString(R.string.cloud_sync_signed_in_as, email) +
                    "\n" + fragment.getString(R.string.sync_status_ok)
            }
            status.summary = fragment.getString(R.string.cloud_sync_profile_line, profile.name) +
                "\n" + accountLine
            signIn?.isVisible = email == null
            signUp?.isVisible = email == null
            signOut?.isVisible = email != null
            syncNow?.isVisible = email != null
            status.isEnabled = SupabaseProvider.isConfigured
            signIn?.isEnabled = SupabaseProvider.isConfigured && email == null
            signUp?.isEnabled = SupabaseProvider.isConfigured && email == null
            signOut?.isEnabled = SupabaseProvider.isConfigured && email != null
            syncNow?.isEnabled = SupabaseProvider.isConfigured && email != null
        }

        signIn?.setOnPreferenceClickListener {
            startSignIn(fragment, scope, ::refresh)
            true
        }

        signUp?.setOnPreferenceClickListener {
            startSignUp(fragment, scope, ::refresh)
            true
        }

        signOut?.setOnPreferenceClickListener {
            startSignOut(fragment, scope, ::refresh)
            true
        }

        syncNow?.setOnPreferenceClickListener {
            startSyncNow(fragment, scope, ::refresh)
            true
        }

        refresh()
        if (SupabaseProvider.isConfigured) {
            scope.launch {
                runCatching {
                    val profileId = UserProfiles.active().id
                    SupabaseProvider.initialize(fragment.requireContext(), profileId)
                    SupabaseProvider.clientOrNull(profileId)?.auth?.awaitInitialization()
                }
                if (fragment.isAdded) refresh()
            }
        }
    }

    fun handleComposeAction(
        fragment: Fragment,
        scope: LifecycleCoroutineScope,
        action: String,
        onRefresh: () -> Unit,
    ): Boolean {
        return when (action) {
            "cloudSignIn" -> {
                startSignIn(fragment, scope, onRefresh)
                true
            }
            "cloudSignUp" -> {
                startSignUp(fragment, scope, onRefresh)
                true
            }
            "cloudSignOut" -> {
                startSignOut(fragment, scope, onRefresh)
                true
            }
            "cloudSyncNow" -> {
                startSyncNow(fragment, scope, onRefresh)
                true
            }
            else -> false
        }
    }

    private fun startSignIn(
        fragment: Fragment,
        scope: LifecycleCoroutineScope,
        refresh: () -> Unit,
    ) {
        showCredentialsDialog(fragment, scope, R.string.cloud_sync_sign_in) { email, password ->
            runProgressAction(fragment, scope, refresh, email, password) { onProgress ->
                CloudSyncManager.signIn(
                    fragment.requireContext(),
                    email,
                    password,
                    onProgress,
                )
                R.string.cloud_sync_sign_in_success
            }
        }
    }

    private fun startSignUp(
        fragment: Fragment,
        scope: LifecycleCoroutineScope,
        refresh: () -> Unit,
    ) {
        showCredentialsDialog(fragment, scope, R.string.cloud_sync_sign_up) { email, password ->
            runProgressAction(fragment, scope, refresh, email, password) { onProgress ->
                val signedIn = CloudSyncManager.signUp(
                    fragment.requireContext(),
                    email,
                    password,
                    onProgress,
                )
                if (signedIn) R.string.cloud_sync_sign_up_success else R.string.cloud_sync_confirm_email
            }
        }
    }

    private fun startSignOut(
        fragment: Fragment,
        scope: LifecycleCoroutineScope,
        refresh: () -> Unit,
    ) {
        runAction(fragment, scope, refresh) {
            CloudSyncManager.signOut(fragment.requireContext())
            R.string.cloud_sync_sign_out_success
        }
    }

    private fun startSyncNow(
        fragment: Fragment,
        scope: LifecycleCoroutineScope,
        refresh: () -> Unit,
    ) {
        runProgressAction(fragment, scope, refresh) { onProgress ->
            CloudSyncManager.syncNow(
                fragment.requireContext(),
                onProgress = onProgress,
            )
            R.string.cloud_sync_success
        }
    }

    private fun showCredentialsDialog(
        fragment: Fragment,
        scope: LifecycleCoroutineScope,
        titleRes: Int,
        onSubmit: (String, String) -> Unit,
    ) {
        if (!fragment.isAdded) return
        val startedProfileId = UserProfiles.active().id
        val dialog = Dialog(fragment.requireContext())
        var dismissedByProfileSwitch = false
        val profileWatch = watchProfileSwitch(scope, startedProfileId) {
            dismissedByProfileSwitch = true
            if (dialog.isShowing) dialog.dismiss()
        }
        dialog.setOnDismissListener { profileWatch.cancel() }
        dialog.setContentView(
            composeHost(fragment) {
                CloudCredentialsDialog(
                    titleRes = titleRes,
                    onConfirm = { email, password ->
                        if (dismissedByProfileSwitch || UserProfiles.active().id != startedProfileId) {
                            dialog.dismiss()
                            return@CloudCredentialsDialog
                        }
                        dialog.dismiss()
                        onSubmit(email, password)
                    },
                    onDismiss = { dialog.dismiss() },
                )
            },
        )
        styleGlassDialog(dialog, cancelable = true)
        dialog.show()
    }

    private fun runProgressAction(
        fragment: Fragment,
        scope: LifecycleCoroutineScope,
        refresh: () -> Unit,
        conflictEmail: String? = null,
        conflictPassword: String? = null,
        action: suspend ((CloudSyncProgress) -> Unit) -> Int,
    ) {
        if (!fragment.isAdded) return
        val startedProfileId = UserProfiles.active().id
        val progressState = mutableStateOf<CloudSyncProgress?>(null)
        val dialog = Dialog(fragment.requireContext())
        dialog.setContentView(
            composeHost(fragment) {
                CloudSyncProgressDialog(progress = progressState.value)
            },
        )
        styleGlassDialog(dialog, cancelable = false)
        dialog.show()

        val actionJobRef = java.util.concurrent.atomic.AtomicReference<Job?>(null)
        val profileWatch = watchProfileSwitch(scope, startedProfileId) {
            if (dialog.isShowing) dialog.dismiss()
            actionJobRef.get()?.cancel()
        }

        actionJobRef.set(
            scope.launch {
            try {
                if (UserProfiles.active().id != startedProfileId) {
                    throw CancellationException("Profile switched")
                }
                val resultMessage = action { progress ->
                    if (!fragment.isAdded || UserProfiles.active().id != startedProfileId) return@action
                    progressState.value = progress
                }
                if (dialog.isShowing) dialog.dismiss()
                refresh()
                if (fragment.isAdded) {
                    Toast.makeText(
                        fragment.requireContext(),
                        resultMessage,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            } catch (cancellation: CancellationException) {
                if (dialog.isShowing) dialog.dismiss()
                throw cancellation
            } catch (error: Throwable) {
                if (dialog.isShowing) dialog.dismiss()
                if (UserProfiles.active().id != startedProfileId) {
                    // Profile switched mid-flight — discard result.
                } else if (error is CloudAccountDataConflictException &&
                    !conflictEmail.isNullOrBlank() &&
                    !conflictPassword.isNullOrBlank()
                ) {
                    showConflictDialog(fragment, scope, refresh, conflictEmail, conflictPassword)
                } else if (fragment.isAdded) {
                    showError(fragment, error)
                }
            } finally {
                profileWatch.cancel()
                if (dialog.isShowing) dialog.dismiss()
            }
        },
        )
    }

    private fun showConflictDialog(
        fragment: Fragment,
        scope: LifecycleCoroutineScope,
        refresh: () -> Unit,
        email: String,
        password: String,
    ) {
        if (!fragment.isAdded) return
        val startedProfileId = UserProfiles.active().id
        val conflictDialog = Dialog(fragment.requireContext())
        val profileWatch = watchProfileSwitch(scope, startedProfileId) {
            if (conflictDialog.isShowing) conflictDialog.dismiss()
        }
        conflictDialog.setOnDismissListener { profileWatch.cancel() }
        conflictDialog.setContentView(
            composeHost(fragment) {
                CloudSyncConflictDialog(
                    onKeepLocal = {
                        if (UserProfiles.active().id != startedProfileId) {
                            conflictDialog.dismiss()
                            return@CloudSyncConflictDialog
                        }
                        conflictDialog.dismiss()
                        runProgressAction(fragment, scope, refresh) { onProgress ->
                            CloudSyncManager.completeSignInAfterConflict(
                                fragment.requireContext(),
                                email,
                                password,
                                keepLocal = true,
                                onProgress = onProgress,
                            )
                            R.string.cloud_sync_sign_in_success
                        }
                    },
                    onUseCloud = {
                        if (UserProfiles.active().id != startedProfileId) {
                            conflictDialog.dismiss()
                            return@CloudSyncConflictDialog
                        }
                        conflictDialog.dismiss()
                        runProgressAction(fragment, scope, refresh) { onProgress ->
                            CloudSyncManager.completeSignInAfterConflict(
                                fragment.requireContext(),
                                email,
                                password,
                                keepLocal = false,
                                onProgress = onProgress,
                            )
                            R.string.cloud_sync_sign_in_success
                        }
                    },
                    onDismiss = { conflictDialog.dismiss() },
                )
            },
        )
        styleGlassDialog(conflictDialog, cancelable = true)
        conflictDialog.show()
    }

    private fun watchProfileSwitch(
        scope: LifecycleCoroutineScope,
        startedProfileId: String,
        onSwitched: () -> Unit,
    ): Job {
        return scope.launch {
            UserProfiles.activeProfileChanges.first { it != startedProfileId }
            onSwitched()
        }
    }

    private fun composeHost(
        fragment: Fragment,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ): ComposeView {
        return ComposeView(fragment.requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                BetterStreamflixTheme {
                    content()
                }
            }
        }
    }

    private fun styleGlassDialog(dialog: Dialog, cancelable: Boolean) {
        dialog.setCancelable(cancelable)
        dialog.setCanceledOnTouchOutside(cancelable)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.5f)
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
    }

    private fun runAction(
        fragment: Fragment,
        scope: LifecycleCoroutineScope,
        refresh: () -> Unit,
        action: suspend () -> Int,
    ) {
        scope.launch {
            runCatching { action() }
                .onSuccess { message ->
                    refresh()
                    if (fragment.isAdded) {
                        Toast.makeText(fragment.requireContext(), message, Toast.LENGTH_LONG).show()
                    }
                }
                .onFailure { error ->
                    showError(fragment, error)
                }
        }
    }

    private fun showError(fragment: Fragment, error: Throwable) {
        if (!fragment.isAdded) return
        val message = when (error) {
            is CloudAccountAlreadyLinkedException ->
                fragment.getString(
                    R.string.cloud_sync_account_already_linked,
                    error.existingProfileName,
                )
            is RestException -> when {
                error.message?.contains("Invalid login credentials", true) == true ->
                    fragment.getString(R.string.cloud_sync_error, "Invalid email or password")
                error.message?.contains("Email not confirmed", true) == true ->
                    fragment.getString(R.string.cloud_sync_error, "Please confirm your email first")
                error.message?.contains("User already registered", true) == true ->
                    fragment.getString(R.string.cloud_sync_error, "This email is already registered")
                error.message?.contains("Password should be at least", true) == true ->
                    fragment.getString(R.string.cloud_sync_error, "Password must be at least 6 characters")
                else -> fragment.getString(R.string.cloud_sync_error, error.message ?: error.javaClass.simpleName)
            }
            else -> fragment.getString(R.string.cloud_sync_error, error.message ?: error.javaClass.simpleName)
        }
        Toast.makeText(
            fragment.requireContext(),
            message,
            Toast.LENGTH_LONG,
        ).show()
    }
}
