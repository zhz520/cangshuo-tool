package com.cangshuo.toolbox.feature.qr.ui

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolboxLoadingState
import com.cangshuo.toolbox.core.ui.ToolboxLoadingIndicator
import com.cangshuo.toolbox.feature.qr.domain.QrHistoryEntry
import com.cangshuo.toolbox.feature.qr.domain.QrColorStyle
import com.cangshuo.toolbox.feature.qr.domain.QrCodeFormat
import com.cangshuo.toolbox.feature.qr.domain.QrContactFormat
import com.cangshuo.toolbox.feature.qr.domain.QrContentType
import com.cangshuo.toolbox.feature.qr.domain.QrCropPolicy
import com.cangshuo.toolbox.feature.qr.domain.QrDecodeEntry
import com.cangshuo.toolbox.feature.qr.domain.QrErrorCorrection
import com.cangshuo.toolbox.feature.qr.domain.QrEventReminder
import com.cangshuo.toolbox.feature.qr.domain.QrEventTimeMode
import com.cangshuo.toolbox.feature.qr.domain.QrExportSize
import com.cangshuo.toolbox.feature.qr.domain.QrExportFormat
import com.cangshuo.toolbox.feature.qr.domain.QrInputPolicy
import com.cangshuo.toolbox.feature.qr.domain.QrMatrix
import com.cangshuo.toolbox.feature.qr.domain.QrMode
import com.cangshuo.toolbox.feature.qr.domain.QrSymbology
import com.cangshuo.toolbox.ui.theme.ToolboxTheme
import kotlin.math.floor
import kotlin.math.roundToInt
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun QrScreen(
    state: QrUiState,
    onModeChanged: (QrMode) -> Unit,
    onContentTypeChanged: (QrContentType) -> Unit,
    onFormatChanged: (QrCodeFormat) -> Unit = {},
    onTextChanged: (String) -> Unit,
    onWifiSsidChanged: (String) -> Unit,
    onWifiPasswordChanged: (String) -> Unit,
    onWifiSecurityChanged: (String) -> Unit,
    onWifiHiddenChanged: (Boolean) -> Unit,
    onWifiEapChanged: (String) -> Unit = {},
    onWifiPhase2Changed: (String) -> Unit = {},
    onWifiIdentityChanged: (String) -> Unit = {},
    onWifiAnonymousChanged: (String) -> Unit = {},
    onErrorCorrectionChanged: (QrErrorCorrection) -> Unit,
    onColorStyleChanged: (QrColorStyle) -> Unit,
    onDecodeImage: (Uri) -> Unit,
    onPickerFailed: () -> Unit,
    onRetryDecode: () -> Unit,
    onLoadSample: () -> Unit,
    onClear: () -> Unit,
    onExportSizeChanged: (QrExportSize) -> Unit,
    onExportFormatChanged: (QrExportFormat) -> Unit = {},
    onSaveImage: () -> Unit,
    onShareImage: () -> Unit,
    onShareDecodedText: () -> Unit,
    onRegenerateDecodedText: () -> Unit,
    onDismissExportMessage: () -> Unit,
    onStartCameraScan: () -> Unit,
    onStopCameraScan: () -> Unit,
    onCameraDecoded: (QrDecodeEntry) -> Unit,
    onPhoneChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onEmailSubjectChanged: (String) -> Unit,
    onEmailBodyChanged: (String) -> Unit,
    onEmailCcChanged: (String) -> Unit = {},
    onEmailBccChanged: (String) -> Unit = {},
    onSmsNumberChanged: (String) -> Unit,
    onSmsMessageChanged: (String) -> Unit,
    onContactNameChanged: (String) -> Unit,
    onContactOrganizationChanged: (String) -> Unit,
    onContactFormatChanged: (QrContactFormat) -> Unit = {},
    onContactPhoneChanged: (Int, String) -> Unit = { _, _ -> },
    onContactPhoneAdded: () -> Unit = {},
    onContactPhoneRemoved: (Int) -> Unit = {},
    onContactEmailChanged: (Int, String) -> Unit = { _, _ -> },
    onContactEmailAdded: () -> Unit = {},
    onContactEmailRemoved: (Int) -> Unit = {},
    onContactAddressChanged: (Int, String) -> Unit = { _, _ -> },
    onContactAddressAdded: () -> Unit = {},
    onContactAddressRemoved: (Int) -> Unit = {},
    onEventTitleChanged: (String) -> Unit,
    onEventLocationChanged: (String) -> Unit,
    onEventDescriptionChanged: (String) -> Unit,
    onEventStartDateChanged: (String) -> Unit,
    onEventStartTimeChanged: (String) -> Unit,
    onEventEndDateChanged: (String) -> Unit,
    onEventEndTimeChanged: (String) -> Unit,
    onEventTimeModeChanged: (QrEventTimeMode) -> Unit,
    onEventReminderChanged: (QrEventReminder) -> Unit,
    onMultiDecodeChanged: (Boolean) -> Unit,
    onShareDecodedTexts: () -> Unit,
    onHistoryEnabled: (Boolean) -> Unit = {},
    onHistoryOpen: (QrHistoryEntry) -> Unit = {},
    onHistoryRemove: (String) -> Unit = {},
    onHistoryClear: () -> Unit = {},
    onHistoryRetry: () -> Unit = {},
    onCropOpen: () -> Unit = {},
    onCropCenterChanged: (Float, Float) -> Unit = { _, _ -> },
    onCropSideChanged: (Float) -> Unit = {},
    onCropConfirm: () -> Unit = {},
    onCropCancel: () -> Unit = {},
    onCropPreviewRetry: () -> Unit = {},
) {
    val clipboard = LocalClipboardManager.current
    val hiddenNetworkLabel = stringResource(R.string.qr_wifi_hidden)
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onDecodeImage(uri)
    }

    Box(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 600.dp).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                QrCard {
                    QrChipRow {
                        FilterChip(selected = state.mode == QrMode.GENERATE,
                            enabled = !state.isExporting,
                            onClick = { onModeChanged(QrMode.GENERATE) },
                            label = { Text(stringResource(R.string.qr_mode_generate)) })
                        FilterChip(selected = state.mode == QrMode.DECODE,
                            enabled = !state.isExporting,
                            onClick = { onModeChanged(QrMode.DECODE) },
                            label = { Text(stringResource(R.string.qr_mode_decode)) })
                        FilterChip(selected = state.mode == QrMode.HISTORY,
                            enabled = !state.isExporting,
                            onClick = { onModeChanged(QrMode.HISTORY) },
                            label = { Text(stringResource(R.string.qr_history_title)) })
                    }
                }
            }
            state.exportMessageRes?.let { message ->
                item {
                    QrCard {
                        Text(stringResource(message), color = if (state.exportMessageIsError)
                            MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        TextButton(onClick = onDismissExportMessage) { Text(stringResource(R.string.qr_btn_dismiss)) }
                    }
                }
            }
            state.exportAction?.let { action ->
                item { ToolboxLoadingState(stringResource(when (action) {
                    QrExportAction.CHOOSE_DESTINATION -> R.string.qr_choosing_destination
                    QrExportAction.SAVE -> R.string.qr_saving
                    QrExportAction.SHARE_IMAGE, QrExportAction.SHARE_TEXT -> R.string.qr_preparing_share
                })) }
            }
            if (state.mode == QrMode.GENERATE) {
                item {
                    QrCard {
                        Text(stringResource(R.string.qr_format_label), style = MaterialTheme.typography.labelLarge)
                        QrChipRow {
                            QrCodeFormat.entries.forEach { format ->
                                FilterChip(selected = state.format == format, enabled = !state.isExporting,
                                    onClick = { onFormatChanged(format) },
                                    label = { Text(stringResource(format.labelRes())) })
                            }
                        }
                        if (state.format.linear) {
                            Text(stringResource(R.string.qr_format_linear_hint), style = MaterialTheme.typography.bodySmall)
                        }
                        QrChipRow {
                            FilterChip(selected = state.contentType == QrContentType.TEXT,
                                enabled = !state.isExporting && !state.format.linear,
                                onClick = { onContentTypeChanged(QrContentType.TEXT) },
                                label = { Text(stringResource(R.string.qr_type_text)) })
                            FilterChip(selected = state.contentType == QrContentType.WIFI,
                                enabled = !state.isExporting && !state.format.linear,
                                onClick = { onContentTypeChanged(QrContentType.WIFI) },
                                label = { Text(stringResource(R.string.qr_type_wifi)) })
                            FilterChip(selected = state.contentType == QrContentType.PHONE,
                                enabled = !state.isExporting && !state.format.linear,
                                onClick = { onContentTypeChanged(QrContentType.PHONE) },
                                label = { Text(stringResource(R.string.qr_type_phone)) })
                            FilterChip(selected = state.contentType == QrContentType.EMAIL,
                                enabled = !state.isExporting && !state.format.linear,
                                onClick = { onContentTypeChanged(QrContentType.EMAIL) },
                                label = { Text(stringResource(R.string.qr_type_email)) })
                            FilterChip(selected = state.contentType == QrContentType.SMS,
                                enabled = !state.isExporting && !state.format.linear,
                                onClick = { onContentTypeChanged(QrContentType.SMS) },
                                label = { Text(stringResource(R.string.qr_type_sms)) })
                            FilterChip(selected = state.contentType == QrContentType.CONTACT,
                                enabled = !state.isExporting && !state.format.linear,
                                onClick = { onContentTypeChanged(QrContentType.CONTACT) },
                                label = { Text(stringResource(R.string.qr_type_contact)) })
                            FilterChip(selected = state.contentType == QrContentType.EVENT,
                                enabled = !state.isExporting && !state.format.linear,
                                onClick = { onContentTypeChanged(QrContentType.EVENT) },
                                label = { Text(stringResource(R.string.qr_type_event)) })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onLoadSample, enabled = !state.isExporting) { Text(stringResource(R.string.qr_btn_sample)) }
                            TextButton(onClick = onClear, enabled = !state.isExporting) { Text(stringResource(R.string.qr_btn_clear)) }
                        }
                        state.inputMessageRes?.let { QrError(it) }
                        when (state.contentType) {
                            QrContentType.TEXT -> {
                            OutlinedTextField(
                                value = state.textContent, onValueChange = onTextChanged,
                                enabled = !state.isExporting,
                                modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6,
                                label = { Text(stringResource(R.string.qr_label_text)) },
                                placeholder = { Text(stringResource(R.string.qr_placeholder_text)) },
                                supportingText = { Text(stringResource(R.string.qr_input_count,
                                    state.textContent.length, QrInputPolicy.MAX_TEXT_LENGTH)) },
                            )
                            TextButton(onClick = { clipboard.getText()?.text?.let(onTextChanged) }, enabled = !state.isExporting) {
                                Text(stringResource(R.string.qr_btn_paste))
                            }
                            }
                            QrContentType.WIFI -> {
                            OutlinedTextField(
                                value = state.wifiSsid, onValueChange = onWifiSsidChanged,
                                enabled = !state.isExporting,
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                label = { Text(stringResource(R.string.qr_wifi_ssid_label)) },
                                placeholder = { Text(stringResource(R.string.qr_wifi_ssid_placeholder)) },
                                supportingText = { Text(stringResource(R.string.qr_wifi_ssid_hint)) },
                            )
                            OutlinedTextField(
                                value = state.wifiPassword, onValueChange = onWifiPasswordChanged,
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                enabled = state.wifiSecurity != "nopass" && !state.isExporting,
                                label = { Text(stringResource(R.string.qr_wifi_password_label)) },
                                placeholder = { Text(stringResource(R.string.qr_wifi_password_placeholder)) },
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    TextButton(onClick = { showPassword = !showPassword }, enabled = state.wifiSecurity != "nopass") {
                                        Text(stringResource(if (showPassword) R.string.qr_hide_password else R.string.qr_show_password))
                                    }
                                },
                            )
                            QrChipRow {
                                listOf("WPA", "WPA3", "WPA-EAP", "WPA3-EAP", "WEP", "nopass").forEach { security ->
                                    FilterChip(selected = state.wifiSecurity == security,
                                        enabled = !state.isExporting,
                                        onClick = { onWifiSecurityChanged(security) },
                                        label = { Text(stringResource(security.securityLabelRes())) })
                                }
                            }
                            if (state.wifiSecurity == "WPA-EAP" || state.wifiSecurity == "WPA3-EAP") {
                                Text(stringResource(R.string.qr_wifi_eap_label), style = MaterialTheme.typography.labelLarge)
                                QrChipRow {
                                    listOf("TTLS", "PEAP", "TLS").forEach { eap ->
                                        FilterChip(selected = state.wifiEap == eap, enabled = !state.isExporting,
                                            onClick = { onWifiEapChanged(eap) },
                                            label = { Text(stringResource(eap.eapLabelRes())) })
                                    }
                                }
                                Text(stringResource(R.string.qr_wifi_phase2_label), style = MaterialTheme.typography.labelLarge)
                                QrChipRow {
                                    listOf("MSCHAPV2", "GTC").forEach { phase2 ->
                                        FilterChip(selected = state.wifiPhase2 == phase2, enabled = !state.isExporting,
                                            onClick = { onWifiPhase2Changed(phase2) },
                                            label = { Text(stringResource(phase2.phase2LabelRes())) })
                                    }
                                }
                                QrField(
                                    value = state.wifiIdentity, onValueChange = onWifiIdentityChanged,
                                    labelRes = R.string.qr_wifi_identity_label, enabled = !state.isExporting,
                                )
                                QrField(
                                    value = state.wifiAnonymous, onValueChange = onWifiAnonymousChanged,
                                    labelRes = R.string.qr_wifi_anonymous_label, enabled = !state.isExporting,
                                )
                                Text(stringResource(R.string.qr_wifi_enterprise_hint), style = MaterialTheme.typography.bodySmall)
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(hiddenNetworkLabel, Modifier.weight(1f))
                                Switch(checked = state.wifiHidden, onCheckedChange = onWifiHiddenChanged, enabled = !state.isExporting,
                                    modifier = Modifier.semantics { contentDescription = hiddenNetworkLabel })
                            }
                            }
                            QrContentType.PHONE -> QrField(
                                value = state.form.phone, onValueChange = onPhoneChanged,
                                labelRes = R.string.qr_phone_label, enabled = !state.isExporting,
                                placeholderRes = R.string.qr_phone_placeholder, hintRes = R.string.qr_phone_hint,
                            )
                            QrContentType.EMAIL -> {
                                QrField(
                                    value = state.form.email, onValueChange = onEmailChanged,
                                    labelRes = R.string.qr_email_label, enabled = !state.isExporting,
                                    placeholderRes = R.string.qr_email_placeholder, hintRes = R.string.qr_email_hint,
                                )
                                QrField(
                                    value = state.form.emailSubject, onValueChange = onEmailSubjectChanged,
                                    labelRes = R.string.qr_email_subject_label, enabled = !state.isExporting,
                                    placeholderRes = R.string.qr_email_subject_placeholder,
                                )
                                QrField(
                                    value = state.form.emailBody, onValueChange = onEmailBodyChanged,
                                    labelRes = R.string.qr_email_body_label, enabled = !state.isExporting,
                                    placeholderRes = R.string.qr_email_body_placeholder,
                                    singleLine = false, minLines = 2, maxLines = 4,
                                )
                                QrField(
                                    value = state.form.emailCc, onValueChange = onEmailCcChanged,
                                    labelRes = R.string.qr_email_cc_label, enabled = !state.isExporting,
                                    placeholderRes = R.string.qr_email_recipients_placeholder,
                                )
                                QrField(
                                    value = state.form.emailBcc, onValueChange = onEmailBccChanged,
                                    labelRes = R.string.qr_email_bcc_label, enabled = !state.isExporting,
                                    placeholderRes = R.string.qr_email_recipients_placeholder,
                                )
                            }
                            QrContentType.SMS -> {
                                QrField(
                                    value = state.form.smsNumber, onValueChange = onSmsNumberChanged,
                                    labelRes = R.string.qr_sms_number_label, enabled = !state.isExporting,
                                    placeholderRes = R.string.qr_phone_placeholder, hintRes = R.string.qr_phone_hint,
                                )
                                QrField(
                                    value = state.form.smsMessage, onValueChange = onSmsMessageChanged,
                                    labelRes = R.string.qr_sms_message_label, enabled = !state.isExporting,
                                    placeholderRes = R.string.qr_sms_message_placeholder,
                                    singleLine = false, minLines = 2, maxLines = 4,
                                )
                            }
                            QrContentType.CONTACT -> {
                                QrField(
                                    value = state.form.contact.name, onValueChange = onContactNameChanged,
                                    labelRes = R.string.qr_contact_name_label, enabled = !state.isExporting,
                                    placeholderRes = R.string.qr_contact_name_placeholder,
                                )
                                QrField(
                                    value = state.form.contact.organization, onValueChange = onContactOrganizationChanged,
                                    labelRes = R.string.qr_contact_org_label, enabled = !state.isExporting,
                                )
                                Text(stringResource(R.string.qr_contact_format_label), style = MaterialTheme.typography.labelLarge)
                                QrChipRow {
                                    QrContactFormat.entries.forEach { format ->
                                        FilterChip(selected = state.form.contact.format == format,
                                            enabled = !state.isExporting,
                                            onClick = { onContactFormatChanged(format) },
                                            label = { Text(stringResource(format.labelRes())) })
                                    }
                                }
                                QrContactListEditor(
                                    values = state.form.contact.phones, labelRes = R.string.qr_contact_phone_label,
                                    addLabelRes = R.string.qr_contact_add_phone, enabled = !state.isExporting,
                                    maxEntries = QrInputPolicy.MAX_CONTACT_ENTRIES,
                                    onValueChange = onContactPhoneChanged, onAdd = onContactPhoneAdded,
                                    onRemove = onContactPhoneRemoved,
                                )
                                QrContactListEditor(
                                    values = state.form.contact.emails, labelRes = R.string.qr_contact_email_label,
                                    addLabelRes = R.string.qr_contact_add_email, enabled = !state.isExporting,
                                    maxEntries = QrInputPolicy.MAX_CONTACT_ENTRIES,
                                    onValueChange = onContactEmailChanged, onAdd = onContactEmailAdded,
                                    onRemove = onContactEmailRemoved,
                                )
                                if (state.form.contact.format == QrContactFormat.VCARD) {
                                    QrContactListEditor(
                                        values = state.form.contact.addresses, labelRes = R.string.qr_contact_address_label,
                                        addLabelRes = R.string.qr_contact_add_address, enabled = !state.isExporting,
                                        maxEntries = QrInputPolicy.MAX_CONTACT_ENTRIES,
                                        onValueChange = onContactAddressChanged, onAdd = onContactAddressAdded,
                                        onRemove = onContactAddressRemoved,
                                    )
                                }
                                Text(stringResource(R.string.qr_contact_hint), style = MaterialTheme.typography.bodySmall)
                            }
                            QrContentType.EVENT -> {
                                QrField(
                                    value = state.form.event.title, onValueChange = onEventTitleChanged,
                                    labelRes = R.string.qr_event_title_label, enabled = !state.isExporting,
                                    placeholderRes = R.string.qr_event_title_placeholder,
                                )
                                QrField(
                                    value = state.form.event.location, onValueChange = onEventLocationChanged,
                                    labelRes = R.string.qr_event_location_label, enabled = !state.isExporting,
                                )
                                QrField(
                                    value = state.form.event.description, onValueChange = onEventDescriptionChanged,
                                    labelRes = R.string.qr_event_description_label, enabled = !state.isExporting,
                                    placeholderRes = R.string.qr_event_description_placeholder,
                                    singleLine = false, minLines = 2, maxLines = 4,
                                )
                                Text(stringResource(R.string.qr_event_time_mode_label), style = MaterialTheme.typography.labelLarge)
                                QrChipRow {
                                    QrEventTimeMode.entries.forEach { mode ->
                                        FilterChip(selected = state.form.event.timeMode == mode,
                                            enabled = !state.isExporting,
                                            onClick = { onEventTimeModeChanged(mode) },
                                            label = { Text(stringResource(mode.labelRes())) })
                                    }
                                }
                                Text(stringResource(R.string.qr_event_start_label), style = MaterialTheme.typography.labelLarge)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    QrField(
                                        value = state.form.event.startDate, onValueChange = onEventStartDateChanged,
                                        labelRes = R.string.qr_event_date_label, enabled = !state.isExporting,
                                        placeholderRes = R.string.qr_event_date_placeholder,
                                        modifier = Modifier.weight(1f),
                                    )
                                    QrField(
                                        value = state.form.event.startTime, onValueChange = onEventStartTimeChanged,
                                        labelRes = R.string.qr_event_time_label, enabled = !state.isExporting,
                                        placeholderRes = R.string.qr_event_time_placeholder,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Text(stringResource(R.string.qr_event_end_label), style = MaterialTheme.typography.labelLarge)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    QrField(
                                        value = state.form.event.endDate, onValueChange = onEventEndDateChanged,
                                        labelRes = R.string.qr_event_date_label, enabled = !state.isExporting,
                                        placeholderRes = R.string.qr_event_date_placeholder,
                                        modifier = Modifier.weight(1f),
                                    )
                                    QrField(
                                        value = state.form.event.endTime, onValueChange = onEventEndTimeChanged,
                                        labelRes = R.string.qr_event_time_label, enabled = !state.isExporting,
                                        placeholderRes = R.string.qr_event_time_placeholder,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Text(stringResource(R.string.qr_event_reminder_label), style = MaterialTheme.typography.labelLarge)
                                QrChipRow {
                                    QrEventReminder.entries.forEach { reminder ->
                                        FilterChip(selected = state.form.event.reminder == reminder,
                                            enabled = !state.isExporting,
                                            onClick = { onEventReminderChanged(reminder) },
                                            label = { Text(stringResource(reminder.labelRes())) })
                                    }
                                }
                                Text(stringResource(R.string.qr_event_hint), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                item {
                    QrCard {
                        Text(stringResource(R.string.qr_ec_title), style = MaterialTheme.typography.labelLarge)
                        QrChipRow {
                            QrErrorCorrection.entries.forEach { ec ->
                                FilterChip(selected = state.errorCorrection == ec,
                                    enabled = !state.isExporting && !state.format.linear,
                                    onClick = { onErrorCorrectionChanged(ec) },
                                    label = { Text(stringResource(ec.labelRes())) })
                            }
                        }
                        Text(stringResource(R.string.qr_color_title), style = MaterialTheme.typography.labelLarge)
                        QrChipRow {
                            QrColorStyle.entries.forEach { style ->
                                FilterChip(selected = state.colorStyle == style,
                                    enabled = !state.isExporting,
                                    onClick = { onColorStyleChanged(style) },
                                    label = { Text(stringResource(style.nameRes())) })
                            }
                        }
                    }
                }
                item {
                    when {
                        state.isGenerating -> ToolboxLoadingState(stringResource(R.string.qr_generating))
                        state.generateErrorRes != null -> QrCard { QrError(state.generateErrorRes) }
                        state.matrix != null -> QrCard {
                            val description = stringResource(R.string.qr_preview_description)
                            QrCodeCanvas(state.matrix, Color(state.colorStyle.darkColorArgb), Color(state.colorStyle.lightColorArgb),
                                Modifier.align(Alignment.CenterHorizontally).widthIn(max = 280.dp).fillMaxWidth()
                                    .aspectRatio(if (state.format.linear) 2.4f else 1f)
                                    .semantics { contentDescription = description })
                            QrContent(state.resolvedContent)
                            Button(onClick = { clipboard.setText(AnnotatedString(state.resolvedContent)) }) {
                                Text(stringResource(R.string.qr_btn_copy_content))
                            }
                            Text(stringResource(R.string.qr_export_format_title), style = MaterialTheme.typography.titleSmall)
                            QrChipRow {
                                QrExportFormat.entries.forEach { format ->
                                    FilterChip(selected = state.exportFormat == format, enabled = !state.isExporting,
                                        onClick = { onExportFormatChanged(format) },
                                        label = { Text(stringResource(format.labelRes())) })
                                }
                            }
                            Text(stringResource(R.string.qr_export_size_title), style = MaterialTheme.typography.titleSmall)
                            QrChipRow {
                                QrExportSize.entries.forEach { size ->
                                    FilterChip(selected = state.exportSize == size, enabled = !state.isExporting,
                                        onClick = { onExportSizeChanged(size) },
                                        label = { Text(stringResource(R.string.qr_export_size, size.pixels)) })
                                }
                            }
                            Button(onClick = onSaveImage, enabled = state.canExportImage, modifier = Modifier.fillMaxWidth()) {
                                val gallery = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                    state.exportFormat != QrExportFormat.SVG
                                Text(stringResource(if (gallery) R.string.qr_btn_save_gallery
                                    else R.string.qr_btn_save_document))
                            }
                            TextButton(onClick = onShareImage, enabled = state.canExportImage, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.qr_btn_share_image))
                            }
                        }
                        else -> QrCard { Text(stringResource(R.string.qr_placeholder_empty)) }
                    }
                }
            } else if (state.mode == QrMode.HISTORY) {
                item {
                    var confirmClear by rememberSaveable { mutableStateOf(false) }
                    QrCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.qr_history_enabled), Modifier.weight(1f))
                            Switch(checked = state.history.enabled, onCheckedChange = onHistoryEnabled,
                                enabled = !state.historyLoading && !state.historyBusy)
                        }
                        Text(stringResource(R.string.qr_history_notice), style = MaterialTheme.typography.bodySmall)
                        if (state.historyBusy) ToolboxLoadingIndicator(compact = true)
                        state.historyMessageRes?.let { message ->
                            Text(stringResource(message), color = MaterialTheme.colorScheme.error)
                            if (message == R.string.qr_history_error) TextButton(onClick = onHistoryRetry) {
                                Text(stringResource(R.string.qr_btn_retry))
                            }
                        }
                        TextButton(onClick = { confirmClear = true },
                            enabled = !state.historyBusy && state.history.entries.isNotEmpty()) {
                            Text(stringResource(R.string.qr_history_clear))
                        }
                    }
                    if (confirmClear) AlertDialog(
                        onDismissRequest = { confirmClear = false },
                        title = { Text(stringResource(R.string.qr_history_clear)) },
                        text = { Text(stringResource(R.string.qr_history_clear_confirm)) },
                        confirmButton = { TextButton(onClick = { confirmClear = false; onHistoryClear() }) {
                            Text(stringResource(R.string.qr_history_clear))
                        } },
                        dismissButton = { TextButton(onClick = { confirmClear = false }) {
                            Text(stringResource(R.string.qr_btn_cancel))
                        } },
                    )
                }
                if (state.historyLoading) item { ToolboxLoadingState(stringResource(R.string.qr_history_loading)) }
                else if (state.history.entries.isEmpty()) item { QrCard { Text(stringResource(R.string.qr_history_empty)) } }
                items(state.history.entries, key = { it.key }) { entry ->
                    QrCard {
                        QrSymbologyLabel(entry.result.symbology)
                        Text(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                            .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(entry.scannedAt)),
                            style = MaterialTheme.typography.bodySmall)
                        Text(entry.result.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row {
                            TextButton(onClick = { onHistoryOpen(entry) }, enabled = !state.historyBusy) {
                                Text(stringResource(R.string.qr_history_open))
                            }
                            TextButton(onClick = { onHistoryRemove(entry.key) }, enabled = !state.historyBusy) {
                                Text(stringResource(R.string.qr_history_remove))
                            }
                        }
                    }
                }
            } else {
                item {
                    QrCard {
                        Text(stringResource(R.string.qr_camera_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.qr_camera_desc), style = MaterialTheme.typography.bodySmall)
                        if (state.cameraScanning) {
                            QrCameraScanner(
                                collect = state.multiDecode,
                                collected = state.cameraResults.size,
                                onDecoded = onCameraDecoded,
                                onClose = onStopCameraScan,
                            )
                        } else {
                            Button(
                                onClick = onStartCameraScan,
                                enabled = !state.isExporting,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.qr_btn_open_camera)) }
                        }
                    }
                }
                item {
                    QrCard {
                        Text(stringResource(R.string.qr_decode_desc))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.qr_multi_toggle), Modifier.weight(1f))
                            Switch(checked = state.multiDecode, onCheckedChange = onMultiDecodeChanged,
                                enabled = !state.isExporting && !state.cameraScanning)
                        }
                        Text(stringResource(R.string.qr_multi_hint), style = MaterialTheme.typography.bodySmall)
                        state.inputMessageRes?.let { QrError(it) }
                        Button(enabled = !state.isExporting, onClick = {
                            try {
                                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            } catch (_: Exception) { onPickerFailed() }
                        }) { Text(stringResource(R.string.qr_btn_pick_image)) }
                        TextButton(onClick = onClear, enabled = !state.isExporting) {
                            Text(stringResource(if (state.isDecoding) R.string.qr_btn_cancel else R.string.qr_btn_clear))
                        }
                        if (state.hasSelectedImage) TextButton(onClick = onCropOpen,
                            enabled = !state.isExporting && !state.isDecoding) {
                            Text(stringResource(R.string.qr_crop_open))
                        }
                    }
                }
                if (state.cropVisible) {
                    item {
                        QrCard {
                            Text(stringResource(R.string.qr_crop_title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.qr_crop_hint), style = MaterialTheme.typography.bodySmall)
                            if (state.previewLoading) ToolboxLoadingState(stringResource(R.string.qr_crop_loading))
                            state.previewErrorRes?.let { error ->
                                QrError(error)
                                TextButton(onClick = onCropPreviewRetry) { Text(stringResource(R.string.qr_btn_retry)) }
                            }
                            state.preview?.let { bitmap ->
                                val image = remember(bitmap) { bitmap.asImageBitmap() }
                                val overlayColor = MaterialTheme.colorScheme.primary
                                BoxWithConstraints(Modifier.fillMaxWidth()) {
                                    val ratio = bitmap.width.toFloat() / bitmap.height
                                    val maxHeight = 420.dp
                                    val wide = maxWidth / maxHeight >= ratio
                                    Box(Modifier.size(if (wide) maxHeight * ratio else maxWidth,
                                        if (wide) maxHeight else maxWidth / ratio).align(Alignment.Center)) {
                                        Image(bitmap = image, contentDescription = stringResource(R.string.qr_crop_title),
                                            contentScale = ContentScale.FillBounds,
                                            modifier = Modifier.matchParentSize().clip(MaterialTheme.shapes.medium))
                                        Canvas(Modifier.matchParentSize().pointerInput(bitmap, state.cropRegion) {
                                            detectTapGestures { offset ->
                                                if (size.width > 0 && size.height > 0) {
                                                    onCropCenterChanged(offset.x / size.width, offset.y / size.height)
                                                }
                                            }
                                        }) {
                                            val rect = QrCropPolicy.rect(state.cropRegion,
                                                size.width.roundToInt(), size.height.roundToInt()) ?: return@Canvas
                                            val left = rect.left.toFloat()
                                            val top = rect.top.toFloat()
                                            val side = rect.side.toFloat()
                                            val dim = Color.Black.copy(alpha = 0.5f)
                                            drawRect(dim, Offset(0f, 0f), Size(size.width, top))
                                            drawRect(dim, Offset(0f, top + side),
                                                Size(size.width, (size.height - top - side).coerceAtLeast(0f)))
                                            drawRect(dim, Offset(0f, top), Size(left, side))
                                            drawRect(dim, Offset(left + side, top),
                                                Size((size.width - left - side).coerceAtLeast(0f), side))
                                            drawRect(color = overlayColor, topLeft = Offset(left, top),
                                                size = Size(side, side), style = Stroke(width = 2.dp.toPx()))
                                        }
                                    }
                                }
                                Text(stringResource(R.string.qr_crop_size), style = MaterialTheme.typography.labelLarge)
                                Slider(value = state.cropRegion.sideFraction, onValueChange = onCropSideChanged,
                                    valueRange = QrCropPolicy.MIN_SIDE_FRACTION..1f, enabled = !state.isExporting)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = onCropConfirm,
                                    enabled = state.preview != null && !state.isExporting && !state.isDecoding) {
                                    Text(stringResource(R.string.qr_crop_confirm))
                                }
                                TextButton(onClick = onCropCancel) { Text(stringResource(R.string.qr_crop_cancel)) }
                            }
                        }
                    }
                }
                if (state.isDecoding) item { ToolboxLoadingState(stringResource(R.string.qr_decoding)) }
                state.decodeErrorRes?.let { error ->
                    item {
                        QrCard {
                            QrError(error)
                            if (state.canRetryDecode) TextButton(onClick = onRetryDecode, enabled = !state.isExporting) {
                                Text(stringResource(R.string.qr_btn_retry))
                            }
                        }
                    }
                }
                state.decodeEntry?.let { entry ->
                    item {
                        QrCard {
                            Text(stringResource(R.string.qr_decode_result_title), style = MaterialTheme.typography.titleMedium)
                            QrSymbologyLabel(entry.symbology)
                            QrContent(entry.text)
                            Button(onClick = { clipboard.setText(AnnotatedString(entry.text)) }) {
                                Text(stringResource(R.string.qr_btn_copy_content))
                            }
                            TextButton(onClick = onShareDecodedText, enabled = !state.isExporting) {
                                Text(stringResource(R.string.qr_btn_share_content))
                            }
                            TextButton(onClick = onRegenerateDecodedText, enabled = !state.isExporting) {
                                Text(stringResource(R.string.qr_btn_regenerate))
                            }
                        }
                    }
                }
                if (state.decodeEntries.size > 1) {
                    item {
                        QrCard {
                            Text(stringResource(R.string.qr_multi_result_title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.qr_multi_result_count, state.decodeEntries.size),
                                style = MaterialTheme.typography.bodySmall)
                            state.decodeEntries.forEachIndexed { index, entry ->
                                Text(stringResource(R.string.qr_multi_item_index, index + 1),
                                    style = MaterialTheme.typography.labelLarge)
                                QrSymbologyLabel(entry.symbology)
                                QrContent(entry.text)
                                TextButton(onClick = { clipboard.setText(AnnotatedString(entry.text)) }) {
                                    Text(stringResource(R.string.qr_btn_copy_content))
                                }
                            }
                            Button(onClick = {
                                clipboard.setText(AnnotatedString(state.decodeEntries.joinToString("\n") { it.text }))
                            }) {
                                Text(stringResource(R.string.qr_btn_copy_all))
                            }
                            TextButton(onClick = onShareDecodedTexts, enabled = !state.isExporting) {
                                Text(stringResource(R.string.qr_btn_share_content))
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun QrCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun QrChipRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun QrError(messageRes: Int) {
    Text(stringResource(messageRes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

/** Reports which symbology the decoder matched. */
@Composable
private fun QrSymbologyLabel(symbology: QrSymbology) {
    Text(stringResource(symbology.nameRes()), color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge)
}

private fun QrSymbology.nameRes(): Int = when (this) {
    QrSymbology.QR_CODE -> R.string.qr_symbology_qr_code
    QrSymbology.DATA_MATRIX -> R.string.qr_symbology_data_matrix
    QrSymbology.AZTEC -> R.string.qr_symbology_aztec
    QrSymbology.PDF_417 -> R.string.qr_symbology_pdf417
    QrSymbology.CODE_128 -> R.string.qr_symbology_code128
    QrSymbology.CODE_93 -> R.string.qr_symbology_code93
    QrSymbology.CODE_39 -> R.string.qr_symbology_code39
    QrSymbology.CODABAR -> R.string.qr_symbology_codabar
    QrSymbology.ITF -> R.string.qr_symbology_itf
    QrSymbology.EAN_13 -> R.string.qr_symbology_ean13
    QrSymbology.EAN_8 -> R.string.qr_symbology_ean8
    QrSymbology.UPC_A -> R.string.qr_symbology_upca
    QrSymbology.UPC_E -> R.string.qr_symbology_upce
    QrSymbology.UNKNOWN -> R.string.qr_symbology_unknown
}

/** Form field used by the structured content types; always spans the available width. */
@Composable
private fun QrField(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    placeholderRes: Int? = null,
    hintRes: Int? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
) {
    val label = stringResource(labelRes)
    val placeholder: (@Composable () -> Unit)? = placeholderRes?.let { res -> { Text(stringResource(res)) } }
    val hint: (@Composable () -> Unit)? = hintRes?.let { res -> { Text(stringResource(res)) } }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        label = { Text(label) },
        placeholder = placeholder,
        supportingText = hint,
    )
}

@Composable
private fun QrContent(content: String) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        SelectionContainer(Modifier.padding(12.dp)) {
            Text(content, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
        }
    }
}

@Composable
private fun QrCodeCanvas(matrix: QrMatrix, darkColor: Color, lightColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(lightColor)
        if (matrix.height == 1) {
            // Linear barcode: stretch the single module row into full-height bars inside its quiet zone.
            val bars = matrix.data.firstOrNull() ?: return@Canvas
            val barWidth = floor(size.width / bars.size)
            if (barWidth < 1f) return@Canvas
            val left = floor((size.width - barWidth * bars.size) / 2)
            val top = size.height / 6f
            val barHeight = size.height - top * 2
            if (barHeight <= 0f) return@Canvas
            for (x in bars.indices) if (bars[x]) {
                drawRect(darkColor, Offset(left + x * barWidth, top), Size(barWidth, barHeight))
            }
            return@Canvas
        }
        val cell = floor(minOf(size.width / matrix.width, size.height / matrix.height))
        if (cell < 1f) return@Canvas
        val left = floor((size.width - matrix.width * cell) / 2)
        val top = floor((size.height - matrix.height * cell) / 2)
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix.data[y][x]) drawRect(darkColor, Offset(left + x * cell, top + y * cell), Size(cell, cell))
        }
    }
}

private fun QrColorStyle.nameRes(): Int = when (this) {
    QrColorStyle.BLACK_WHITE -> R.string.qr_color_black_white
    QrColorStyle.NAVY_BLUE -> R.string.qr_color_navy
    QrColorStyle.EMERALD_GREEN -> R.string.qr_color_green
    QrColorStyle.PURPLE -> R.string.qr_color_purple
}

/** Protocol markers keep their standard spelling; resources keep the UI free of literals. */
private fun String.securityLabelRes(): Int = when (this) {
    "WPA" -> R.string.qr_wifi_security_wpa
    "WPA3" -> R.string.qr_wifi_security_wpa3
    "WPA-EAP" -> R.string.qr_wifi_security_wpa_eap
    "WPA3-EAP" -> R.string.qr_wifi_security_wpa3_eap
    "WEP" -> R.string.qr_wifi_security_wep
    else -> R.string.qr_wifi_no_pass
}

private fun String.eapLabelRes(): Int = when (this) {
    "TTLS" -> R.string.qr_wifi_eap_ttls
    "PEAP" -> R.string.qr_wifi_eap_peap
    else -> R.string.qr_wifi_eap_tls
}

private fun String.phase2LabelRes(): Int = when (this) {
    "MSCHAPV2" -> R.string.qr_wifi_phase2_mschapv2
    else -> R.string.qr_wifi_phase2_gtc
}

private fun QrErrorCorrection.labelRes(): Int = when (this) {
    QrErrorCorrection.LOW -> R.string.qr_ec_low
    QrErrorCorrection.MEDIUM -> R.string.qr_ec_medium
    QrErrorCorrection.QUARTILE -> R.string.qr_ec_quartile
    QrErrorCorrection.HIGH -> R.string.qr_ec_high
}

private fun QrEventTimeMode.labelRes(): Int = when (this) {
    QrEventTimeMode.FLOATING -> R.string.qr_event_time_floating
    QrEventTimeMode.DEVICE_TO_UTC -> R.string.qr_event_time_utc
}

private fun QrEventReminder.labelRes(): Int = when (this) {
    QrEventReminder.NONE -> R.string.qr_event_rem_none
    QrEventReminder.AT_START -> R.string.qr_event_rem_at
    QrEventReminder.MINUTES_5 -> R.string.qr_event_rem_5m
    QrEventReminder.MINUTES_15 -> R.string.qr_event_rem_15m
    QrEventReminder.MINUTES_30 -> R.string.qr_event_rem_30m
    QrEventReminder.HOUR_1 -> R.string.qr_event_rem_1h
    QrEventReminder.DAY_1 -> R.string.qr_event_rem_1d
}

/** Protocol names keep their standard spelling; resources keep the UI free of literals. */
private fun QrCodeFormat.labelRes(): Int = when (this) {
    QrCodeFormat.QR_CODE -> R.string.qr_format_qr_code
    QrCodeFormat.CODE_128 -> R.string.qr_format_code128
    QrCodeFormat.CODE_39 -> R.string.qr_format_code39
    QrCodeFormat.CODE_93 -> R.string.qr_format_code93
    QrCodeFormat.EAN_13 -> R.string.qr_format_ean13
    QrCodeFormat.EAN_8 -> R.string.qr_format_ean8
    QrCodeFormat.UPC_A -> R.string.qr_format_upca
    QrCodeFormat.ITF -> R.string.qr_format_itf
    QrCodeFormat.CODABAR -> R.string.qr_format_codabar
}

private fun QrExportFormat.labelRes(): Int = when (this) {
    QrExportFormat.PNG -> R.string.qr_export_png
    QrExportFormat.JPEG -> R.string.qr_export_jpeg
    QrExportFormat.SVG -> R.string.qr_export_svg
}

private fun QrContactFormat.labelRes(): Int = when (this) {
    QrContactFormat.MECARD -> R.string.qr_contact_format_mecard
    QrContactFormat.VCARD -> R.string.qr_contact_format_vcard
}

/** Bounded repeatable contact rows; each row can be removed and one more can be added. */
@Composable
private fun QrContactListEditor(
    values: List<String>,
    labelRes: Int,
    addLabelRes: Int,
    enabled: Boolean,
    maxEntries: Int,
    onValueChange: (Int, String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
) {
    values.forEachIndexed { index, value ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QrField(
                value = value, onValueChange = { onValueChange(index, it) },
                labelRes = labelRes, enabled = enabled, modifier = Modifier.weight(1f),
            )
            if (values.size > 1) TextButton(onClick = { onRemove(index) }, enabled = enabled) {
                Text(stringResource(R.string.qr_contact_remove_row))
            }
        }
    }
    if (values.size < maxEntries) TextButton(onClick = onAdd, enabled = enabled) {
        Text(stringResource(addLabelRes))
    }
}

@Preview(name = "QR Chinese", locale = "zh", widthDp = 360, showBackground = true)
@Preview(name = "QR English", locale = "en", widthDp = 360, showBackground = true)
@Composable
private fun QrScreenPreview() {
    ToolboxTheme {
        QrScreen(
            state = QrUiState(textContent = "https://tool.zhzgo.cn"),
            onModeChanged = {}, onContentTypeChanged = {}, onTextChanged = {},
            onWifiSsidChanged = {}, onWifiPasswordChanged = {}, onWifiSecurityChanged = {},
            onWifiHiddenChanged = {}, onErrorCorrectionChanged = {}, onColorStyleChanged = {},
            onDecodeImage = {}, onPickerFailed = {}, onRetryDecode = {}, onLoadSample = {}, onClear = {},
            onExportSizeChanged = {}, onSaveImage = {}, onShareImage = {}, onShareDecodedText = {},
            onRegenerateDecodedText = {}, onDismissExportMessage = {},
            onStartCameraScan = {}, onStopCameraScan = {}, onCameraDecoded = {},
            onPhoneChanged = {}, onEmailChanged = {}, onSmsNumberChanged = {}, onSmsMessageChanged = {},
            onEmailSubjectChanged = {}, onEmailBodyChanged = {}, onEventDescriptionChanged = {},
            onContactNameChanged = {}, onContactOrganizationChanged = {},
            onEventTitleChanged = {}, onEventLocationChanged = {},
            onEventStartDateChanged = {}, onEventStartTimeChanged = {}, onEventEndDateChanged = {},
            onEventEndTimeChanged = {},
            onEventTimeModeChanged = {}, onEventReminderChanged = {},
            onMultiDecodeChanged = {}, onShareDecodedTexts = {},
        )
    }
}
