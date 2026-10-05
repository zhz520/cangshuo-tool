package com.cangshuo.toolbox.feature.qr.ui

import android.content.ClipData
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.cangshuo.toolbox.R

@Composable
fun QrRoute(factory: ViewModelProvider.Factory) {
    val viewModel: QrViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pngLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) {
        viewModel.documentCreated(it)
    }
    val jpegLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) {
        viewModel.documentCreated(it)
    }
    val svgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/svg+xml")) {
        viewModel.documentCreated(it)
    }
    val imageShareTitle = stringResource(R.string.qr_share_image_title)
    val textShareTitle = stringResource(R.string.qr_share_text_title)
    val pendingShare = uiState.shareRequest
    LaunchedEffect(pendingShare) {
        val request = pendingShare?.let(viewModel::takeShareRequest) ?: return@LaunchedEffect
        val success = try {
            val intent = Intent(Intent.ACTION_SEND)
            val title = when (request) {
                is QrShareRequest.Image -> {
                    intent.type = request.mimeType
                    intent.putExtra(Intent.EXTRA_STREAM, request.uri)
                    intent.clipData = ClipData.newRawUri(imageShareTitle, request.uri)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    imageShareTitle
                }
                is QrShareRequest.Text -> {
                    intent.type = "text/plain"
                    intent.putExtra(Intent.EXTRA_TEXT, request.content)
                    textShareTitle
                }
            }
            context.startActivity(Intent.createChooser(intent, title))
            true
        } catch (_: Exception) { false }
        viewModel.shareHandled(success)
    }
    QrScreen(
        state = uiState,
        onModeChanged = viewModel::setMode,
        onContentTypeChanged = viewModel::setContentType,
        onFormatChanged = viewModel::setFormat,
        onTextChanged = viewModel::updateText,
        onWifiSsidChanged = viewModel::updateWifiSsid,
        onWifiPasswordChanged = viewModel::updateWifiPassword,
        onWifiSecurityChanged = viewModel::updateWifiSecurity,
        onWifiHiddenChanged = viewModel::setWifiHidden,
        onWifiEapChanged = viewModel::updateWifiEap,
        onWifiPhase2Changed = viewModel::updateWifiPhase2,
        onWifiIdentityChanged = viewModel::updateWifiIdentity,
        onWifiAnonymousChanged = viewModel::updateWifiAnonymous,
        onErrorCorrectionChanged = viewModel::setErrorCorrection,
        onColorStyleChanged = viewModel::setColorStyle,
        onDecodeImage = { uri -> viewModel.decodeImage(uri) },
        onPickerFailed = viewModel::pickerFailed,
        onRetryDecode = viewModel::retryDecode,
        onLoadSample = viewModel::loadSample,
        onClear = viewModel::clear,
        onExportSizeChanged = viewModel::setExportSize,
        onExportFormatChanged = viewModel::setExportFormat,
        onSaveImage = {
            val request = viewModel.saveRequested()
            if (request != null) {
                val launcher = when (request.mimeType) {
                    "image/jpeg" -> jpegLauncher
                    "image/svg+xml" -> svgLauncher
                    else -> pngLauncher
                }
                try { launcher.launch(request.fileName) }
                catch (_: Exception) { viewModel.exportUnavailable() }
            }
        },
        onShareImage = viewModel::shareImage,
        onShareDecodedText = viewModel::shareDecodedText,
        onRegenerateDecodedText = viewModel::regenerateDecodedText,
        onDismissExportMessage = viewModel::dismissExportMessage,
        onStartCameraScan = viewModel::startCameraScan,
        onStopCameraScan = viewModel::stopCameraScan,
        onCameraDecoded = viewModel::onCameraDecoded,
        onPhoneChanged = viewModel::updatePhone,
        onEmailChanged = viewModel::updateEmail,
        onEmailSubjectChanged = viewModel::updateEmailSubject,
        onEmailBodyChanged = viewModel::updateEmailBody,
        onEmailCcChanged = viewModel::updateEmailCc,
        onEmailBccChanged = viewModel::updateEmailBcc,
        onSmsNumberChanged = viewModel::updateSmsNumber,
        onSmsMessageChanged = viewModel::updateSmsMessage,
        onContactNameChanged = viewModel::updateContactName,
        onContactOrganizationChanged = viewModel::updateContactOrganization,
        onContactFormatChanged = viewModel::updateContactFormat,
        onContactPhoneChanged = viewModel::updateContactPhone,
        onContactPhoneAdded = viewModel::addContactPhone,
        onContactPhoneRemoved = viewModel::removeContactPhone,
        onContactEmailChanged = viewModel::updateContactEmail,
        onContactEmailAdded = viewModel::addContactEmail,
        onContactEmailRemoved = viewModel::removeContactEmail,
        onContactAddressChanged = viewModel::updateContactAddress,
        onContactAddressAdded = viewModel::addContactAddress,
        onContactAddressRemoved = viewModel::removeContactAddress,
        onEventTitleChanged = viewModel::updateEventTitle,
        onEventLocationChanged = viewModel::updateEventLocation,
        onEventDescriptionChanged = viewModel::updateEventDescription,
        onEventStartDateChanged = viewModel::updateEventStartDate,
        onEventStartTimeChanged = viewModel::updateEventStartTime,
        onEventEndDateChanged = viewModel::updateEventEndDate,
        onEventEndTimeChanged = viewModel::updateEventEndTime,
        onEventTimeModeChanged = viewModel::updateEventTimeMode,
        onEventReminderChanged = viewModel::updateEventReminder,
        onMultiDecodeChanged = viewModel::setMultiDecode,
        onShareDecodedTexts = viewModel::shareDecodedTexts,
        onHistoryEnabled = viewModel::setHistoryEnabled,
        onHistoryOpen = viewModel::openHistory,
        onHistoryRemove = viewModel::removeHistory,
        onHistoryClear = viewModel::clearHistory,
        onHistoryRetry = viewModel::observeHistory,
        onCropOpen = viewModel::openCrop,
        onCropCenterChanged = viewModel::updateCropCenter,
        onCropSideChanged = viewModel::updateCropSide,
        onCropConfirm = viewModel::confirmCrop,
        onCropCancel = viewModel::cancelCrop,
        onCropPreviewRetry = viewModel::cropPreviewRetry,
    )
}
