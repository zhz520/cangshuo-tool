package com.cangshuo.toolbox.feature.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import com.cangshuo.toolbox.core.ui.ToolboxButton
import com.cangshuo.toolbox.core.ui.ToolboxLoadingIndicator
import com.cangshuo.toolbox.core.ui.ToolboxLoadingState
import com.cangshuo.toolbox.core.ui.ToolboxOutlinedButton
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.core.ui.ToolboxTextField
import com.cangshuo.toolbox.core.ui.ToolboxAvatar
import com.cangshuo.toolbox.core.ui.ToolboxCaption
import com.cangshuo.toolbox.core.ui.ToolSectionHeader
import com.cangshuo.toolbox.core.ui.ToolboxDivider
import com.cangshuo.toolbox.ui.theme.ToolboxDimens
import com.cangshuo.toolbox.feature.auth.domain.AuthFailure
import com.cangshuo.toolbox.feature.auth.domain.AuthStatus
import com.cangshuo.toolbox.feature.sync.ui.CloudSyncRoute

@Composable
fun AuthRoute(factory: ViewModelProvider.Factory, modifier: Modifier = Modifier, syncFactory: ViewModelProvider.Factory? = null,
    feedbackFactory: ViewModelProvider.Factory? = null, diagnosticsFactory: ViewModelProvider.Factory? = null) {
    val model: AuthViewModel = viewModel(key = "account.auth", factory = factory)
    val state by model.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) model.clearCredentials() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); model.clearCredentials() }
    }
    AuthScreen(state, model, modifier, syncFactory, feedbackFactory, diagnosticsFactory)
}

@Composable
private fun AuthScreen(state: AuthUiState, model: AuthViewModel, modifier: Modifier = Modifier, syncFactory: ViewModelProvider.Factory? = null,
    feedbackFactory: ViewModelProvider.Factory? = null, diagnosticsFactory: ViewModelProvider.Factory? = null) {
    val focus = LocalFocusManager.current
    var showPassword by rememberSaveable { mutableStateOf(false) }
    Box(modifier, contentAlignment = Alignment.TopCenter) {
    Column(Modifier.widthIn(max = ToolboxDimens.formMaxWidth).fillMaxSize().verticalScroll(rememberScrollState()).padding(ToolboxDimens.pageGutter),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (state.deleted) Text(stringResource(R.string.auth_deleted), color = MaterialTheme.colorScheme.primary)
        if (state.status == AuthStatus.RESTORING) {
            ToolboxLoadingState(stringResource(R.string.auth_restoring))
        }
        if (state.status == AuthStatus.RETRY_REQUIRED) {
            Text(stringResource(R.string.auth_restore_retry), color = MaterialTheme.colorScheme.onSurfaceVariant)
            ToolboxOutlinedButton(onClick = model::restore, enabled = !state.busy) { Text(stringResource(R.string.action_retry)) }
            ToolboxTextButton(onClick = model::logout, enabled = !state.busy) { Text(stringResource(R.string.auth_logout)) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val account = state.account
            if (account != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ToolboxAvatar(state.avatar, state.avatarLoading, ToolboxDimens.accountAvatarSize)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(account.nickname, style = MaterialTheme.typography.titleLarge)
                        Text(account.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                ToolboxDivider(startIndent = 0.dp)
                ToolSectionHeader(stringResource(R.string.auth_profile_heading))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolboxTextField(value = state.profileNickname, onValueChange = model::profileNickname,
                        enabled = !state.busy, label = stringResource(R.string.auth_nickname),
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            state.failure?.let { AuthFailureText(it, state.failureDetail) }
                            if (state.profileSaved) Text(stringResource(R.string.auth_profile_saved), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                        }
                        ToolboxButton(onClick = model::saveProfile, enabled = !state.busy && state.profileNickname.trim() != account.nickname) {
                            if (state.busy) ToolboxLoadingIndicator(compact = true)
                            Text(stringResource(R.string.auth_save_profile))
                        }
                    }
                }
                ToolboxDivider(startIndent = 0.dp)
                ToolSectionHeader(stringResource(R.string.auth_avatar_heading))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolboxTextField(state.qqNumber, model::qqNumber, modifier=Modifier.fillMaxWidth(),
                        label=stringResource(R.string.auth_qq_number), enabled=!state.avatarSaving,
                        isError=state.qqInvalid, keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number,imeAction=ImeAction.Done),
                        keyboardActions=KeyboardActions(onDone={focus.clearFocus();model.saveAvatar()}),
                        supportingText={Text(stringResource(if(state.qqInvalid) R.string.auth_qq_invalid else R.string.auth_qq_hint))})
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if(state.avatarSaved) Text(stringResource(R.string.auth_avatar_saved),style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.primary)
                            if(state.avatarSaveFailed) Text(stringResource(R.string.auth_avatar_save_failed),style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.error)
                            if(state.avatarFailed) {
                                Text(stringResource(R.string.auth_avatar_failed),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                ToolboxTextButton(onClick=model::retryAvatar,enabled=!state.avatarLoading && state.savedQqNumber.isNotBlank()) {
                                    Text(stringResource(R.string.action_retry))
                                }
                            }
                        }
                        ToolboxOutlinedButton(onClick=model::saveAvatar,
                            enabled=!state.avatarSaving && state.qqNumber.trim()!=state.savedQqNumber) {
                            if(state.avatarSaving) ToolboxLoadingIndicator(compact=true)
                            Text(stringResource(R.string.auth_avatar_save))
                        }
                    }
                }
                ToolboxDivider(startIndent = 0.dp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolboxOutlinedButton(onClick = model::logout, modifier=Modifier.weight(1f), enabled = !state.busy) { Text(stringResource(R.string.auth_logout)) }
                    ToolboxTextButton(onClick = { model.showDeletion(true) }, modifier=Modifier.weight(1f), enabled = !state.busy) {
                        Text(stringResource(R.string.auth_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                Column(Modifier.padding(top=20.dp,bottom=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    ToolboxAvatar(null,size=ToolboxDimens.accountAvatarSize)
                    Text(stringResource(if (state.registering) R.string.auth_register else R.string.auth_login),
                        style = MaterialTheme.typography.headlineLarge)
                    Text(stringResource(R.string.auth_welcome_hint),style=MaterialTheme.typography.bodyMedium,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ToolboxTextField(value = state.email, onValueChange = model::email, enabled = !state.busy,
                    label = stringResource(R.string.auth_email), singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email,imeAction=ImeAction.Next))
                if (state.registering) ToolboxTextField(value = state.nickname, onValueChange = model::nickname,
                    enabled = !state.busy, label = stringResource(R.string.auth_nickname), singleLine = true,
                    modifier = Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Next))
                ToolboxTextField(value = state.password, onValueChange = model::password, enabled = !state.busy,
                    label = stringResource(R.string.auth_password), singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if(showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon={ToolboxTextButton(onClick={showPassword=!showPassword}) {
                        Text(stringResource(if(showPassword) R.string.auth_password_hide else R.string.auth_password_show))}},
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password,imeAction=if(state.registering) ImeAction.Next else ImeAction.Done),
                    keyboardActions=KeyboardActions(onDone={focus.clearFocus();model.submit()}))
                if (state.registering) Text(stringResource(R.string.auth_password_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.registering) ToolboxTextField(value = state.confirmation, onValueChange = model::confirmation,
                    enabled = !state.busy, label = stringResource(R.string.auth_confirm_password), singleLine = true,
                    modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password,imeAction=ImeAction.Done),
                    keyboardActions=KeyboardActions(onDone={focus.clearFocus();model.submit()}))
                state.failure?.let { AuthFailureText(it, state.failureDetail) }
                ToolboxButton(onClick = {focus.clearFocus();model.submit()}, enabled = !state.busy && state.email.isNotBlank() && state.password.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    if (state.busy) { ToolboxLoadingIndicator(compact = true); Spacer(Modifier.padding(4.dp)) }
                    Text(stringResource(if (state.busy) R.string.auth_working else if (state.registering)
                        R.string.auth_register else R.string.auth_login))
                }
                ToolboxTextButton(onClick = {showPassword=false;model.toggleMode()}, modifier=Modifier.fillMaxWidth(), enabled = !state.busy) {
                    Text(stringResource(if (state.registering) R.string.auth_switch_login else R.string.auth_switch_register))
                }
            }
        }
        if (state.account != null && syncFactory != null) {
            ToolboxDivider(startIndent=0.dp)
            CloudSyncRoute(syncFactory)
        }
        ToolboxCaption(stringResource(R.string.auth_local_data_kept), Modifier.padding(bottom=8.dp))
    }
    }
    if (state.deleting && state.account != null) AlertDialog(
        onDismissRequest = { if (!state.busy) model.showDeletion(false) },
        title = { Text(stringResource(R.string.auth_delete)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.auth_delete_warning))
            ToolboxTextField(state.deleteEmail, model::deleteEmail, enabled = !state.busy, singleLine = true,
                label = stringResource(R.string.auth_delete_email), modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            ToolboxTextField(state.deletePassword, model::deletePassword, enabled = !state.busy, singleLine = true,
                label = stringResource(R.string.auth_password), modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            state.failure?.let { AuthFailureText(it, state.failureDetail) }
            if (state.busy) ToolboxLoadingIndicator(compact = true)
        } },
        confirmButton = { TextButton(onClick = model::deleteAccount, enabled = !state.busy && state.deleteEmail.trim().equals(state.account.email, ignoreCase = true) && state.deletePassword.isNotEmpty()) {
            Text(stringResource(R.string.auth_delete_confirm), color = MaterialTheme.colorScheme.error)
        } },
        dismissButton = { TextButton(onClick = { model.showDeletion(false) }, enabled = !state.busy) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun AuthFailureText(failure: AuthFailure, detail: String?) {
    val label = stringResource(failure.label())
    Text(
        text = if (detail.isNullOrEmpty()) label else "$label（$detail）",
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
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
    AuthFailure.LIMITED -> R.string.auth_too_many
}
