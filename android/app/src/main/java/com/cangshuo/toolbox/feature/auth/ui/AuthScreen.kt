package com.cangshuo.toolbox.feature.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolboxLoadingIndicator
import com.cangshuo.toolbox.feature.auth.domain.AuthFailure
import com.cangshuo.toolbox.feature.auth.domain.AuthStatus

@Composable
fun AuthRoute(factory: ViewModelProvider.Factory, modifier: Modifier = Modifier, syncFactory: ViewModelProvider.Factory? = null,
    feedbackFactory: ViewModelProvider.Factory? = null) {
    val model: AuthViewModel = viewModel(key = "account.auth", factory = factory)
    val state by model.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) model.clearCredentials() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); model.clearCredentials() }
    }
    AuthScreen(state, model, modifier, syncFactory, feedbackFactory)
}

@Composable
private fun AuthScreen(state: AuthUiState, model: AuthViewModel, modifier: Modifier = Modifier, syncFactory: ViewModelProvider.Factory? = null,
    feedbackFactory: ViewModelProvider.Factory? = null) {
    Column(modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.nav_profile), style = MaterialTheme.typography.headlineMedium)
        if (state.status == AuthStatus.RESTORING) {
            com.cangshuo.toolbox.core.ui.ToolboxLoadingState(stringResource(R.string.auth_restoring))
        }
        if (state.status == AuthStatus.RETRY_REQUIRED) {
            Text(stringResource(R.string.auth_restore_retry), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = model::restore, enabled = !state.busy) { Text(stringResource(R.string.action_retry)) }
            TextButton(onClick = model::logout, enabled = !state.busy) { Text(stringResource(R.string.auth_logout)) }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val account = state.account
                if (account != null) {
                    Text(account.nickname, style = MaterialTheme.typography.titleLarge)
                    Text(account.email, style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.auth_signed_in), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = state.profileNickname, onValueChange = model::profileNickname,
                        enabled = !state.busy, label = { Text(stringResource(R.string.auth_nickname)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    state.failure?.let { Text(stringResource(it.label()), color = MaterialTheme.colorScheme.error) }
                    if (state.profileSaved) Text(stringResource(R.string.auth_profile_saved), color = MaterialTheme.colorScheme.primary)
                    Button(onClick = model::saveProfile, enabled = !state.busy && state.profileNickname.trim() != account.nickname) {
                        if (state.busy) ToolboxLoadingIndicator(compact = true)
                        Text(stringResource(R.string.auth_save_profile))
                    }
                    OutlinedButton(onClick = model::logout, enabled = !state.busy) { Text(stringResource(R.string.auth_logout)) }
                } else {
                    Text(stringResource(if (state.registering) R.string.auth_register else R.string.auth_login),
                        style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(value = state.email, onValueChange = model::email, enabled = !state.busy,
                        label = { Text(stringResource(R.string.auth_email)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                    if (state.registering) OutlinedTextField(value = state.nickname, onValueChange = model::nickname,
                        enabled = !state.busy, label = { Text(stringResource(R.string.auth_nickname)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = state.password, onValueChange = model::password, enabled = !state.busy,
                        label = { Text(stringResource(R.string.auth_password)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    Text(stringResource(R.string.auth_password_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.registering) OutlinedTextField(value = state.confirmation, onValueChange = model::confirmation,
                        enabled = !state.busy, label = { Text(stringResource(R.string.auth_confirm_password)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    state.failure?.let { Text(stringResource(it.label()), color = MaterialTheme.colorScheme.error) }
                    Button(onClick = model::submit, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        if (state.busy) { ToolboxLoadingIndicator(compact = true); Spacer(Modifier.padding(4.dp)) }
                        Text(stringResource(if (state.busy) R.string.auth_working else if (state.registering)
                            R.string.auth_register else R.string.auth_login))
                    }
                    TextButton(onClick = model::toggleMode, enabled = !state.busy) {
                        Text(stringResource(if (state.registering) R.string.auth_switch_login else R.string.auth_switch_register))
                    }
                }
            }
        }
        if (state.account != null && syncFactory != null) com.cangshuo.toolbox.feature.sync.ui.CloudSyncRoute(syncFactory)
        if (syncFactory != null) com.cangshuo.toolbox.feature.sync.ui.SettingsRoute(syncFactory)
        if (feedbackFactory != null) com.cangshuo.toolbox.feature.feedback.ui.FeedbackRoute(feedbackFactory)
        Text(stringResource(R.string.auth_local_data_kept), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun AuthFailure.label(): Int = when (this) {
    AuthFailure.INVALID_EMAIL -> R.string.auth_invalid_email
    AuthFailure.INVALID_PASSWORD -> R.string.auth_invalid_password
    AuthFailure.INVALID_NICKNAME -> R.string.auth_invalid_nickname
    AuthFailure.PASSWORD_MISMATCH -> R.string.auth_password_mismatch
    AuthFailure.CREDENTIALS -> R.string.auth_credentials_error
    AuthFailure.ACCOUNT_EXISTS -> R.string.auth_account_exists
    AuthFailure.NETWORK -> R.string.auth_network_error
    AuthFailure.EXPIRED -> R.string.auth_expired
    AuthFailure.SERVICE, AuthFailure.STORAGE -> R.string.auth_service_error
}
