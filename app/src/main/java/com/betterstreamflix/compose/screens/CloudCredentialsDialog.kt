package com.betterstreamflix.compose.screens

import android.util.Patterns
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.betterstreamflix.R
import com.betterstreamflix.compose.components.BsGhostButton
import com.betterstreamflix.compose.components.BsPrimaryButton
import com.betterstreamflix.compose.theme.BsTheme

@Composable
fun CloudCredentialsDialog(
    titleRes: Int,
    onConfirm: (email: String, password: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }

    fun submit() {
        if (submitting) return
        val emailValue = email.trim()
        val passwordValue = password
        if (!Patterns.EMAIL_ADDRESS.matcher(emailValue).matches() || passwordValue.length < 6) {
            Toast.makeText(context, R.string.cloud_sync_invalid_credentials, Toast.LENGTH_LONG).show()
            return
        }
        submitting = true
        onConfirm(emailValue, passwordValue)
    }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleLarge,
                color = BsTheme.colors.Mist,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    enabled = !submitting,
                    singleLine = true,
                    label = { Text(stringResource(R.string.cloud_sync_email_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = glassFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    enabled = !submitting,
                    singleLine = true,
                    label = { Text(stringResource(R.string.cloud_sync_password_hint)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = glassFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            BsPrimaryButton(
                text = stringResource(titleRes),
                onClick = { submit() },
            )
        },
        dismissButton = {
            BsGhostButton(
                text = stringResource(android.R.string.cancel),
                onClick = { if (!submitting) onDismiss() },
            )
        },
        containerColor = BsTheme.colors.GlassStrong,
    )
}

@Composable
private fun glassFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = BsTheme.colors.Mist,
    unfocusedTextColor = BsTheme.colors.Mist,
    disabledTextColor = BsTheme.colors.MistDim,
    focusedBorderColor = BsTheme.colors.Amber,
    unfocusedBorderColor = BsTheme.colors.Hairline,
    disabledBorderColor = BsTheme.colors.Hairline,
    cursorColor = BsTheme.colors.Amber,
    focusedLabelColor = BsTheme.colors.AmberBright,
    unfocusedLabelColor = BsTheme.colors.MistDim,
    focusedContainerColor = BsTheme.colors.Glass,
    unfocusedContainerColor = BsTheme.colors.GlassSoft,
    disabledContainerColor = BsTheme.colors.GlassSoft,
)
