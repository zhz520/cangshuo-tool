package com.cangshuo.toolbox.feature.qr.ui

import android.net.Uri
import android.os.Build
import com.cangshuo.toolbox.feature.qr.domain.QrExportFormat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.feature.qr.domain.DecodeQrUseCase
import com.cangshuo.toolbox.feature.qr.domain.GenerateQrUseCase
import com.cangshuo.toolbox.feature.qr.domain.LoadQrPreviewUseCase
import com.cangshuo.toolbox.feature.qr.domain.QrColorStyle
import com.cangshuo.toolbox.feature.qr.domain.QrCodeFormat
import com.cangshuo.toolbox.feature.qr.domain.QrContentType
import com.cangshuo.toolbox.feature.qr.domain.QrCropPolicy
import com.cangshuo.toolbox.feature.qr.domain.QrCropRegion
import com.cangshuo.toolbox.feature.qr.domain.QrDecodeResult
import com.cangshuo.toolbox.feature.qr.domain.QrDecodeEntry
import com.cangshuo.toolbox.feature.qr.domain.QrErrorCorrection
import com.cangshuo.toolbox.feature.qr.domain.QrContactInput
import com.cangshuo.toolbox.feature.qr.domain.QrContactFormat
import com.cangshuo.toolbox.feature.qr.domain.QrEventInput
import com.cangshuo.toolbox.feature.qr.domain.QrEventReminder
import com.cangshuo.toolbox.feature.qr.domain.QrEventTimeMode
import com.cangshuo.toolbox.feature.qr.domain.QrFormInput
import com.cangshuo.toolbox.feature.qr.domain.PrepareQrShareUseCase
import com.cangshuo.toolbox.feature.qr.domain.QrExportFailure
import com.cangshuo.toolbox.feature.qr.domain.QrExportResult
import com.cangshuo.toolbox.feature.qr.domain.QrExportSize
import com.cangshuo.toolbox.feature.qr.domain.QrExportSnapshot
import com.cangshuo.toolbox.feature.qr.domain.SaveQrImageUseCase
import com.cangshuo.toolbox.feature.qr.domain.QrFailure
import com.cangshuo.toolbox.feature.qr.domain.QrGenerateResult
import com.cangshuo.toolbox.feature.qr.domain.QrInput
import com.cangshuo.toolbox.feature.qr.domain.QrInputPolicy
import com.cangshuo.toolbox.feature.qr.domain.QrMode
import com.cangshuo.toolbox.feature.qr.domain.QrPreviewResult
import com.cangshuo.toolbox.feature.qr.domain.QrHistoryEntry
import com.cangshuo.toolbox.feature.qr.domain.QrHistoryUseCases
import com.cangshuo.toolbox.feature.qr.domain.QrHistoryRecordResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QrViewModel(
    private val generateQrUseCase: GenerateQrUseCase,
    private val decodeQrUseCase: DecodeQrUseCase,
    private val saveQrImageUseCase: SaveQrImageUseCase,
    private val prepareQrShareUseCase: PrepareQrShareUseCase,
    private val loadQrPreviewUseCase: LoadQrPreviewUseCase,
    private val savedStateHandle: SavedStateHandle,
    private val historyUseCases: QrHistoryUseCases? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(QrUiState(
        mode = restoredEnum("mode", QrMode.GENERATE),
        contentType = restoredEnum("content_type", QrContentType.TEXT),
        format = restoredEnum("format", QrCodeFormat.QR_CODE),
        textContent = restoredText("text", QrInputPolicy.MAX_TEXT_LENGTH),
        wifiSsid = restoredText("wifi_ssid", QrInputPolicy.MAX_FIELD_LENGTH),
        wifiSecurity = savedStateHandle.get<String>("wifi_sec")?.takeIf { it in SECURITIES } ?: "WPA",
        wifiHidden = savedStateHandle["wifi_hidden"] ?: false,
        wifiEap = savedStateHandle.get<String>("wifi_eap")?.takeIf { it in QrInputPolicy.EAP_METHODS } ?: "TTLS",
        wifiPhase2 = savedStateHandle.get<String>("wifi_phase2")?.takeIf { it in QrInputPolicy.PHASE2_METHODS }
            ?: "MSCHAPV2",
        errorCorrection = restoredEnum("ec_level", QrErrorCorrection.MEDIUM),
        colorStyle = restoredEnum("color_style", QrColorStyle.BLACK_WHITE),
        exportSize = restoredEnum("export_size", QrExportSize.MEDIUM),
        exportFormat = restoredEnum("export_format", QrExportFormat.PNG),
        multiDecode = savedStateHandle["multi_decode"] ?: false,
        form = QrFormInput(
            phone = restoredText("form_phone", QrInputPolicy.MAX_FIELD_LENGTH),
            email = restoredText("form_email", QrInputPolicy.MAX_FIELD_LENGTH),
            emailSubject = restoredText("form_email_subject", QrInputPolicy.MAX_FIELD_LENGTH),
            emailBody = restoredText("form_email_body", QrInputPolicy.MAX_MESSAGE_LENGTH),
            emailCc = restoredText("form_email_cc", QrInputPolicy.MAX_MESSAGE_LENGTH),
            emailBcc = restoredText("form_email_bcc", QrInputPolicy.MAX_MESSAGE_LENGTH),
            smsNumber = restoredText("form_sms_number", QrInputPolicy.MAX_FIELD_LENGTH),
            smsMessage = restoredText("form_sms_message", QrInputPolicy.MAX_MESSAGE_LENGTH),
            contact = QrContactInput(
                name = restoredText("form_contact_name", QrInputPolicy.MAX_FIELD_LENGTH),
                organization = restoredText("form_contact_org", QrInputPolicy.MAX_FIELD_LENGTH),
                format = restoredEnum("form_contact_format", QrContactFormat.MECARD),
                phones = restoredList("form_contact_phones"),
                emails = restoredList("form_contact_emails"),
                addresses = restoredList("form_contact_addresses"),
            ),
            event = QrEventInput(
                title = restoredText("form_event_title", QrInputPolicy.MAX_FIELD_LENGTH),
                location = restoredText("form_event_location", QrInputPolicy.MAX_FIELD_LENGTH),
                description = restoredText("form_event_description", QrInputPolicy.MAX_MESSAGE_LENGTH),
                startDate = restoredText("form_event_start_date", QrInputPolicy.MAX_EVENT_DATE_LENGTH),
                startTime = restoredText("form_event_start_time", QrInputPolicy.MAX_EVENT_TIME_LENGTH),
                endDate = restoredText("form_event_end_date", QrInputPolicy.MAX_EVENT_DATE_LENGTH),
                endTime = restoredText("form_event_end_time", QrInputPolicy.MAX_EVENT_TIME_LENGTH),
                timeMode = restoredEnum("form_event_time_mode", QrEventTimeMode.FLOATING),
                reminder = restoredEnum("form_event_reminder", QrEventReminder.NONE),
            ),
        ),
    ))
    val uiState: StateFlow<QrUiState> = mutableState.asStateFlow()
    private var generateJob: Job? = null
    private var decodeJob: Job? = null
    private var generationVersion = 0L
    private var decodeVersion = 0L
    private var selectedUri: Uri? = null
    private var pendingDocumentExport: QrExportSnapshot? = null
    private var exportJob: Job? = null
    private var exportVersion = 0L
    private var previewJob: Job? = null
    private var previewVersion = 0L
    private var historyObserveJob: Job? = null

    init {
        // Passwords stay in this ViewModel's memory, including for restored older versions.
        savedStateHandle.remove<String>("wifi_pass")
        requestGeneration()
        observeHistory()
    }

    fun observeHistory() {
        historyObserveJob?.cancel()
        val useCases = historyUseCases ?: run {
            mutableState.update { it.copy(historyLoading = false) }
            return
        }
        mutableState.update { it.copy(historyLoading = true, historyMessageRes = null) }
        historyObserveJob = viewModelScope.launch {
            try {
                useCases.observe().collect { history ->
                    mutableState.update { it.copy(history = history, historyLoading = false) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutableState.update { it.copy(historyLoading = false, historyMessageRes = R.string.qr_history_error) }
            }
        }
    }

    fun setHistoryEnabled(enabled: Boolean) = historyAction { setEnabled(enabled) }
    fun removeHistory(key: String) = historyAction { remove(key) }
    fun clearHistory() = historyAction { clear() }

    private fun historyAction(action: suspend QrHistoryUseCases.() -> Unit) {
        val useCases = historyUseCases ?: return
        if (uiState.value.historyBusy || uiState.value.historyLoading) return
        mutableState.update { it.copy(historyBusy = true, historyMessageRes = null) }
        viewModelScope.launch {
            try { useCases.action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutableState.update { it.copy(historyMessageRes = R.string.qr_history_error) }
            } finally { mutableState.update { it.copy(historyBusy = false) } }
        }
    }

    private fun recordHistory(entries: List<QrDecodeEntry>) {
        val useCases = historyUseCases ?: return
        viewModelScope.launch {
            try {
                if (useCases.record(entries) == QrHistoryRecordResult.SKIPPED)
                    mutableState.update { it.copy(historyMessageRes = R.string.qr_history_skipped) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutableState.update { it.copy(historyMessageRes = R.string.qr_history_error) }
            }
        }
    }

    fun openHistory(entry: QrHistoryEntry) {
        if (uiState.value.isExporting || uiState.value.historyBusy) return
        if (uiState.value.history.entries.none { it.key == entry.key }) return
        cancelDecode()
        savedStateHandle["mode"] = QrMode.DECODE.name
        mutableState.update { it.copy(mode = QrMode.DECODE, cameraScanning = false,
            cameraResults = emptyList(), isDecoding = false, canRetryDecode = false,
            decodeEntry = entry.result, decodeEntries = emptyList(), decodeErrorRes = null) }
        requestGeneration()
    }

    fun setMode(mode: QrMode) {
        if (uiState.value.isExporting) return
        if (uiState.value.mode == mode) return
        cancelDecode()
        val leavingDecode = mode != QrMode.DECODE
        if (leavingDecode) cancelPreview()
        savedStateHandle["mode"] = mode.name
        mutableState.update { it.copy(mode = mode, inputMessageRes = null, cameraScanning = false,
            cropVisible = if (leavingDecode) false else it.cropVisible,
            preview = if (leavingDecode) null else it.preview,
            previewLoading = if (leavingDecode) false else it.previewLoading,
            previewErrorRes = if (leavingDecode) null else it.previewErrorRes) }
        requestGeneration()
    }

    fun setContentType(type: QrContentType) {
        if (uiState.value.isExporting) return
        savedStateHandle["content_type"] = type.name
        mutableState.update { it.copy(contentType = type, inputMessageRes = null) }
        requestGeneration()
    }

    /** Linear writers encode plain text only, so a barcode format forces the text content type. */
    fun setFormat(format: QrCodeFormat) {
        val state = uiState.value
        if (state.isExporting || state.format == format) return
        savedStateHandle["format"] = format.name
        if (format.linear) savedStateHandle["content_type"] = QrContentType.TEXT.name
        mutableState.update { it.copy(format = format,
            contentType = if (format.linear) QrContentType.TEXT else it.contentType,
            inputMessageRes = null) }
        requestGeneration()
    }

    fun updateText(text: String) {
        if (uiState.value.isExporting) return
        if (!acceptLength(text, QrInputPolicy.MAX_TEXT_LENGTH)) return
        savedStateHandle["text"] = text
        mutableState.update { it.copy(textContent = text, inputMessageRes = null) }
        requestGeneration(debounce = true)
    }

    fun updateWifiSsid(ssid: String) {
        if (uiState.value.isExporting) return
        if (!acceptLength(ssid, QrInputPolicy.MAX_FIELD_LENGTH)) return
        savedStateHandle["wifi_ssid"] = ssid
        mutableState.update { it.copy(wifiSsid = ssid, inputMessageRes = null) }
        requestGeneration(debounce = true)
    }

    fun updateWifiPassword(password: String) {
        if (uiState.value.isExporting) return
        if (!acceptLength(password, QrInputPolicy.MAX_FIELD_LENGTH)) return
        mutableState.update { it.copy(wifiPassword = password, inputMessageRes = null) }
        requestGeneration(debounce = true)
    }

    fun updatePhone(value: String) =
        updateForm("form_phone", QrInputPolicy.MAX_FIELD_LENGTH, value) { it.copy(phone = value) }

    fun updateEmail(value: String) =
        updateForm("form_email", QrInputPolicy.MAX_FIELD_LENGTH, value) { it.copy(email = value) }

    fun updateEmailSubject(value: String) =
        updateForm("form_email_subject", QrInputPolicy.MAX_FIELD_LENGTH, value) { it.copy(emailSubject = value) }

    fun updateEmailBody(value: String) =
        updateForm("form_email_body", QrInputPolicy.MAX_MESSAGE_LENGTH, value) { it.copy(emailBody = value) }

    fun updateEmailCc(value: String) =
        updateForm("form_email_cc", QrInputPolicy.MAX_MESSAGE_LENGTH, value) { it.copy(emailCc = value) }

    fun updateEmailBcc(value: String) =
        updateForm("form_email_bcc", QrInputPolicy.MAX_MESSAGE_LENGTH, value) { it.copy(emailBcc = value) }

    fun updateSmsNumber(value: String) =
        updateForm("form_sms_number", QrInputPolicy.MAX_FIELD_LENGTH, value) { it.copy(smsNumber = value) }

    fun updateSmsMessage(value: String) =
        updateForm("form_sms_message", QrInputPolicy.MAX_MESSAGE_LENGTH, value) { it.copy(smsMessage = value) }

    fun updateContactName(value: String) = updateForm("form_contact_name", QrInputPolicy.MAX_FIELD_LENGTH, value) {
        it.copy(contact = it.contact.copy(name = value))
    }

    fun updateContactOrganization(value: String) =
        updateForm("form_contact_org", QrInputPolicy.MAX_FIELD_LENGTH, value) {
            it.copy(contact = it.contact.copy(organization = value))
        }

    fun updateContactFormat(format: QrContactFormat) {
        if (uiState.value.isExporting || uiState.value.form.contact.format == format) return
        savedStateHandle["form_contact_format"] = format.name
        mutableState.update { it.copy(form = it.form.copy(contact = it.form.contact.copy(format = format)),
            inputMessageRes = null) }
        requestGeneration()
    }

    fun updateContactPhone(index: Int, value: String) = updateContactEntry("form_contact_phones", index, value,
        { it.phones }, { form, list -> form.copy(contact = form.contact.copy(phones = list)) })

    fun updateContactEmail(index: Int, value: String) = updateContactEntry("form_contact_emails", index, value,
        { it.emails }, { form, list -> form.copy(contact = form.contact.copy(emails = list)) })

    fun updateContactAddress(index: Int, value: String) = updateContactEntry("form_contact_addresses", index, value,
        { it.addresses }, { form, list -> form.copy(contact = form.contact.copy(addresses = list)) })

    fun addContactPhone() = addContactEntry("form_contact_phones",
        { it.phones }, { form, list -> form.copy(contact = form.contact.copy(phones = list)) })

    fun addContactEmail() = addContactEntry("form_contact_emails",
        { it.emails }, { form, list -> form.copy(contact = form.contact.copy(emails = list)) })

    fun addContactAddress() = addContactEntry("form_contact_addresses",
        { it.addresses }, { form, list -> form.copy(contact = form.contact.copy(addresses = list)) })

    fun removeContactPhone(index: Int) = removeContactEntry("form_contact_phones", index,
        { it.phones }, { form, list -> form.copy(contact = form.contact.copy(phones = list)) })

    fun removeContactEmail(index: Int) = removeContactEntry("form_contact_emails", index,
        { it.emails }, { form, list -> form.copy(contact = form.contact.copy(emails = list)) })

    fun removeContactAddress(index: Int) = removeContactEntry("form_contact_addresses", index,
        { it.addresses }, { form, list -> form.copy(contact = form.contact.copy(addresses = list)) })

    private fun updateContactEntry(key: String, index: Int, value: String,
        select: (QrContactInput) -> List<String>,
        apply: (QrFormInput, List<String>) -> QrFormInput,
    ) {
        if (uiState.value.isExporting) return
        if (!acceptLength(value, QrInputPolicy.MAX_FIELD_LENGTH)) return
        val current = select(uiState.value.form.contact)
        if (index !in current.indices) return
        val updated = current.toMutableList().also { it[index] = value }
        savedStateHandle[key] = ArrayList(updated)
        mutableState.update { it.copy(form = apply(it.form, updated), inputMessageRes = null) }
        requestGeneration(debounce = true)
    }

    private fun addContactEntry(key: String,
        select: (QrContactInput) -> List<String>,
        apply: (QrFormInput, List<String>) -> QrFormInput,
    ) {
        if (uiState.value.isExporting) return
        val current = select(uiState.value.form.contact)
        if (current.size >= QrInputPolicy.MAX_CONTACT_ENTRIES) return
        val updated = current + ""
        savedStateHandle[key] = ArrayList(updated)
        mutableState.update { it.copy(form = apply(it.form, updated), inputMessageRes = null) }
        requestGeneration()
    }

    private fun removeContactEntry(key: String, index: Int,
        select: (QrContactInput) -> List<String>,
        apply: (QrFormInput, List<String>) -> QrFormInput,
    ) {
        if (uiState.value.isExporting) return
        val current = select(uiState.value.form.contact)
        if (index !in current.indices || current.size <= 1) return
        val updated = current.toMutableList().also { it.removeAt(index) }
        if (updated.isEmpty()) updated.add("")
        savedStateHandle[key] = ArrayList(updated)
        mutableState.update { it.copy(form = apply(it.form, updated), inputMessageRes = null) }
        requestGeneration()
    }

    fun updateEventTitle(value: String) = updateForm("form_event_title", QrInputPolicy.MAX_FIELD_LENGTH, value) {
        it.copy(event = it.event.copy(title = value))
    }

    fun updateEventLocation(value: String) = updateForm("form_event_location", QrInputPolicy.MAX_FIELD_LENGTH, value) {
        it.copy(event = it.event.copy(location = value))
    }

    fun updateEventDescription(value: String) =
        updateForm("form_event_description", QrInputPolicy.MAX_MESSAGE_LENGTH, value) {
            it.copy(event = it.event.copy(description = value))
        }

    fun updateEventStartDate(value: String) =
        updateForm("form_event_start_date", QrInputPolicy.MAX_EVENT_DATE_LENGTH, value) {
            it.copy(event = it.event.copy(startDate = value))
        }

    fun updateEventStartTime(value: String) =
        updateForm("form_event_start_time", QrInputPolicy.MAX_EVENT_TIME_LENGTH, value) {
            it.copy(event = it.event.copy(startTime = value))
        }

    fun updateEventEndDate(value: String) =
        updateForm("form_event_end_date", QrInputPolicy.MAX_EVENT_DATE_LENGTH, value) {
            it.copy(event = it.event.copy(endDate = value))
        }

    fun updateEventEndTime(value: String) =
        updateForm("form_event_end_time", QrInputPolicy.MAX_EVENT_TIME_LENGTH, value) {
            it.copy(event = it.event.copy(endTime = value))
        }

    fun updateEventTimeMode(mode: QrEventTimeMode) {
        if (uiState.value.isExporting || uiState.value.form.event.timeMode == mode) return
        savedStateHandle["form_event_time_mode"] = mode.name
        mutableState.update { it.copy(form = it.form.copy(event = it.form.event.copy(timeMode = mode)),
            inputMessageRes = null) }
        requestGeneration()
    }

    fun updateEventReminder(reminder: QrEventReminder) {
        if (uiState.value.isExporting || uiState.value.form.event.reminder == reminder) return
        savedStateHandle["form_event_reminder"] = reminder.name
        mutableState.update { it.copy(form = it.form.copy(event = it.form.event.copy(reminder = reminder)),
            inputMessageRes = null) }
        requestGeneration()
    }

    private fun updateForm(key: String, limit: Int, value: String, apply: (QrFormInput) -> QrFormInput) {
        if (uiState.value.isExporting) return
        if (!acceptLength(value, limit)) return
        savedStateHandle[key] = value
        mutableState.update { it.copy(form = apply(it.form), inputMessageRes = null) }
        requestGeneration(debounce = true)
    }

    fun updateWifiSecurity(security: String) {
        if (uiState.value.isExporting) return
        if (security !in SECURITIES) return
        savedStateHandle["wifi_sec"] = security
        mutableState.update { it.copy(wifiSecurity = security, inputMessageRes = null) }
        requestGeneration()
    }

    fun updateWifiEap(eap: String) {
        if (uiState.value.isExporting || eap !in QrInputPolicy.EAP_METHODS) return
        savedStateHandle["wifi_eap"] = eap
        mutableState.update { it.copy(wifiEap = eap, inputMessageRes = null) }
        requestGeneration()
    }

    fun updateWifiPhase2(phase2: String) {
        if (uiState.value.isExporting || phase2 !in QrInputPolicy.PHASE2_METHODS) return
        savedStateHandle["wifi_phase2"] = phase2
        mutableState.update { it.copy(wifiPhase2 = phase2, inputMessageRes = null) }
        requestGeneration()
    }

    /** Identity and anonymous identity stay in memory only, like the Wi-Fi password. */
    fun updateWifiIdentity(value: String) {
        if (uiState.value.isExporting) return
        if (!acceptLength(value, QrInputPolicy.MAX_FIELD_LENGTH)) return
        mutableState.update { it.copy(wifiIdentity = value, inputMessageRes = null) }
        requestGeneration(debounce = true)
    }

    fun updateWifiAnonymous(value: String) {
        if (uiState.value.isExporting) return
        if (!acceptLength(value, QrInputPolicy.MAX_FIELD_LENGTH)) return
        mutableState.update { it.copy(wifiAnonymous = value, inputMessageRes = null) }
        requestGeneration(debounce = true)
    }

    fun setWifiHidden(hidden: Boolean) {
        if (uiState.value.isExporting) return
        savedStateHandle["wifi_hidden"] = hidden
        mutableState.update { it.copy(wifiHidden = hidden, inputMessageRes = null) }
        requestGeneration()
    }

    fun setErrorCorrection(ec: QrErrorCorrection) {
        if (uiState.value.isExporting) return
        savedStateHandle["ec_level"] = ec.name
        mutableState.update { it.copy(errorCorrection = ec) }
        requestGeneration()
    }

    fun setColorStyle(color: QrColorStyle) {
        if (uiState.value.isExporting) return
        savedStateHandle["color_style"] = color.name
        mutableState.update { it.copy(colorStyle = color) }
    }

    private fun requestGeneration(debounce: Boolean = false) {
        generateJob?.cancel()
        val version = ++generationVersion
        val snapshot = uiState.value
        val input = QrInput(type = snapshot.contentType, text = snapshot.textContent, ssid = snapshot.wifiSsid,
            password = snapshot.wifiPassword, security = snapshot.wifiSecurity, hidden = snapshot.wifiHidden,
            form = snapshot.form, format = snapshot.format, eap = snapshot.wifiEap,
            phase2 = snapshot.wifiPhase2, identity = snapshot.wifiIdentity, anonymous = snapshot.wifiAnonymous)
        val hasContent = QrInputPolicy.hasContent(input)
        mutableState.update { it.copy(matrix = null, resolvedContent = "", generateErrorRes = null, exportMessageRes = null,
            isGenerating = snapshot.mode == QrMode.GENERATE && hasContent) }
        if (snapshot.mode != QrMode.GENERATE || !hasContent) return
        generateJob = viewModelScope.launch {
            try {
                if (debounce) delay(150)
                val result = withContext(Dispatchers.Default) { generateQrUseCase(input, snapshot.errorCorrection) }
                if (version != generationVersion) return@launch
                mutableState.update { it.copy(
                    isGenerating = false,
                    matrix = (result as? QrGenerateResult.Success)?.matrix,
                    resolvedContent = (result as? QrGenerateResult.Success)?.content.orEmpty(),
                    generateErrorRes = (result as? QrGenerateResult.Error)?.reason?.messageRes(),
                ) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (version == generationVersion) mutableState.update {
                    it.copy(isGenerating = false, generateErrorRes = R.string.qr_error_generate)
                }
            }
        }
    }

    fun decodeImage(uri: Uri, region: QrCropRegion? = null) {
        if (uiState.value.isExporting) return
        if (uiState.value.mode != QrMode.DECODE) return
        cancelDecode()
        cancelPreview()
        selectedUri = uri
        val version = decodeVersion
        mutableState.update { it.copy(isDecoding = true, decodeEntry = null, decodeEntries = emptyList(),
            decodeErrorRes = null, exportMessageRes = null, canRetryDecode = false, inputMessageRes = null,
            cameraScanning = false, hasSelectedImage = true, cropVisible = false, preview = null,
            previewLoading = false, previewErrorRes = null) }
        decodeJob = viewModelScope.launch {
            try {
                val result = decodeQrUseCase(uri, uiState.value.multiDecode, region)
                if (version != decodeVersion || selectedUri != uri) return@launch
                mutableState.update { it.copy(isDecoding = false,
                    decodeEntry = (result as? QrDecodeResult.Success)?.entry,
                    decodeEntries = (result as? QrDecodeResult.Multiple)?.entries.orEmpty(),
                    decodeErrorRes = (result as? QrDecodeResult.Error)?.reason?.messageRes(),
                    canRetryDecode = result is QrDecodeResult.Error) }
                when (result) {
                    is QrDecodeResult.Success -> recordHistory(listOf(result.entry))
                    is QrDecodeResult.Multiple -> recordHistory(result.entries)
                    is QrDecodeResult.Error -> Unit
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (version == decodeVersion) mutableState.update {
                    it.copy(isDecoding = false, decodeErrorRes = R.string.qr_error_decode, canRetryDecode = true)
                }
            }
        }
    }

    fun retryDecode() { selectedUri?.let(::decodeImage) }

    /** Opens the manual crop overlay; the image is loaded once and reused until the panel closes. */
    fun openCrop() {
        val state = uiState.value
        if (state.mode != QrMode.DECODE || state.isExporting || state.isDecoding) return
        val uri = selectedUri ?: return
        mutableState.update { it.copy(cropVisible = true, previewErrorRes = null) }
        if (uiState.value.preview == null) loadPreview(uri)
    }

    fun cropPreviewRetry() {
        if (!uiState.value.cropVisible || uiState.value.isExporting) return
        selectedUri?.let(::loadPreview)
    }

    fun updateCropCenter(centerX: Float, centerY: Float) {
        val state = uiState.value
        if (!state.cropVisible || state.isExporting) return
        if (!centerX.isFinite() || !centerY.isFinite()) return
        mutableState.update { it.copy(cropRegion = it.cropRegion.copy(
            centerX = centerX.coerceIn(0f, 1f), centerY = centerY.coerceIn(0f, 1f))) }
    }

    fun updateCropSide(sideFraction: Float) {
        val state = uiState.value
        if (!state.cropVisible || state.isExporting) return
        if (!sideFraction.isFinite()) return
        mutableState.update { it.copy(cropRegion = it.cropRegion.copy(
            sideFraction = sideFraction.coerceIn(QrCropPolicy.MIN_SIDE_FRACTION, 1f))) }
    }

    fun confirmCrop() {
        val state = uiState.value
        val uri = selectedUri ?: return
        if (!state.cropVisible || state.preview == null || state.isExporting || state.isDecoding) return
        decodeImage(uri, state.cropRegion)
    }

    fun cancelCrop() {
        cancelPreview()
        mutableState.update { it.copy(cropVisible = false, preview = null, previewLoading = false,
            previewErrorRes = null) }
    }

    private fun loadPreview(uri: Uri) {
        previewJob?.cancel()
        val version = ++previewVersion
        mutableState.update { it.copy(previewLoading = true, previewErrorRes = null) }
        previewJob = viewModelScope.launch {
            try {
                val result = loadQrPreviewUseCase(uri)
                if (version != previewVersion || selectedUri != uri) return@launch
                when (result) {
                    is QrPreviewResult.Success -> mutableState.update {
                        it.copy(previewLoading = false, preview = result.bitmap, previewErrorRes = null) }
                    is QrPreviewResult.Error -> mutableState.update {
                        it.copy(previewLoading = false, preview = null, previewErrorRes = R.string.qr_crop_preview_error) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (version == previewVersion) mutableState.update {
                    it.copy(previewLoading = false, previewErrorRes = R.string.qr_crop_preview_error) }
            }
        }
    }

    private fun cancelPreview() {
        previewJob?.cancel()
        ++previewVersion
    }

    /** Opens the camera surface; the visitor is asked for CAMERA only at this point. */
    fun startCameraScan() {
        val state = uiState.value
        if (state.mode != QrMode.DECODE || state.isExporting) return
        cancelDecode()
        mutableState.update {
            it.copy(cameraScanning = true, cameraResults = emptyList(), decodeErrorRes = null,
                canRetryDecode = false, inputMessageRes = null)
        }
    }

    fun stopCameraScan() {
        val results = uiState.value.cameraResults
        cancelDecode()
        if (results.isEmpty()) {
            mutableState.update { it.copy(cameraScanning = false, cameraResults = emptyList()) }
            return
        }
        mutableState.update {
            it.copy(cameraScanning = false, cameraResults = emptyList(),
                decodeEntry = results.singleOrNull(),
                decodeEntries = if (results.size > 1) results else emptyList(),
                decodeErrorRes = null, canRetryDecode = false)
        }
        recordHistory(results)
    }

    fun onCameraDecoded(entry: QrDecodeEntry) {
        if (uiState.value.mode != QrMode.DECODE || !uiState.value.cameraScanning || entry.text.isEmpty()) return
        val state = uiState.value
        if (state.multiDecode) {
            // Collect mode keeps the preview running; duplicates are filtered by the analyzer.
            if (state.cameraResults.any { it.text == entry.text } || state.cameraResults.size >= 50) return
            mutableState.update { it.copy(cameraResults = it.cameraResults + entry) }
            return
        }
        cancelDecode()
        mutableState.update {
            it.copy(cameraScanning = false, isDecoding = false, decodeEntry = entry, decodeEntries = emptyList(),
                decodeErrorRes = null, canRetryDecode = false, inputMessageRes = null)
        }
        recordHistory(listOf(entry))
    }

    /** Multi-code mode lists every QR code found in a gallery image; single results stay unchanged. */
    fun setMultiDecode(enabled: Boolean) {
        if (uiState.value.isExporting || uiState.value.multiDecode == enabled) return
        savedStateHandle["multi_decode"] = enabled
        cancelDecode()
        mutableState.update { it.copy(multiDecode = enabled, decodeEntry = null, decodeEntries = emptyList(),
            cameraScanning = false, cameraResults = emptyList(), decodeErrorRes = null, canRetryDecode = false) }
    }

    fun pickerFailed() {
        if (uiState.value.isExporting) return
        cancelDecode()
        mutableState.update { it.copy(decodeEntry = null, decodeErrorRes = R.string.qr_error_read,
            canRetryDecode = selectedUri != null) }
    }

    private fun cancelDecode() {
        ++decodeVersion
        decodeJob?.cancel()
        mutableState.update { it.copy(isDecoding = false) }
    }

    fun loadSample() {
        if (uiState.value.isExporting) return
        if (uiState.value.format.linear) {
            val sample = barcodeSample(uiState.value.format)
            savedStateHandle["text"] = sample
            mutableState.update { it.copy(textContent = sample, inputMessageRes = null) }
            requestGeneration()
            return
        }
        when (uiState.value.contentType) {
            QrContentType.TEXT -> {
                savedStateHandle["text"] = SAMPLE_TEXT
                mutableState.update { it.copy(textContent = SAMPLE_TEXT, inputMessageRes = null) }
            }
            QrContentType.WIFI -> {
                savedStateHandle["wifi_ssid"] = SAMPLE_SSID
                savedStateHandle["wifi_sec"] = "WPA"
                savedStateHandle["wifi_hidden"] = false
                savedStateHandle["wifi_eap"] = "TTLS"
                savedStateHandle["wifi_phase2"] = "MSCHAPV2"
                mutableState.update { it.copy(wifiSsid = SAMPLE_SSID, wifiPassword = "cangshuo@2026",
                    wifiSecurity = "WPA", wifiHidden = false, wifiEap = "TTLS", wifiPhase2 = "MSCHAPV2",
                    wifiIdentity = "", wifiAnonymous = "", inputMessageRes = null) }
            }
            QrContentType.PHONE -> sampleForm("form_phone" to SAMPLE_PHONE) { it.copy(phone = SAMPLE_PHONE) }
            QrContentType.EMAIL -> sampleForm(
                "form_email" to SAMPLE_EMAIL,
                "form_email_subject" to SAMPLE_MAIL_SUBJECT,
                "form_email_body" to SAMPLE_MAIL_BODY,
            ) { it.copy(email = SAMPLE_EMAIL, emailSubject = SAMPLE_MAIL_SUBJECT, emailBody = SAMPLE_MAIL_BODY) }
            QrContentType.SMS -> sampleForm(
                "form_sms_number" to SAMPLE_PHONE,
                "form_sms_message" to SAMPLE_SMS,
            ) { it.copy(smsNumber = SAMPLE_PHONE, smsMessage = SAMPLE_SMS) }
            QrContentType.CONTACT -> {
                savedStateHandle["form_contact_name"] = SAMPLE_CONTACT_NAME
                savedStateHandle["form_contact_org"] = SAMPLE_CONTACT_ORG
                savedStateHandle["form_contact_format"] = QrContactFormat.MECARD.name
                savedStateHandle["form_contact_phones"] = arrayListOf(SAMPLE_PHONE)
                savedStateHandle["form_contact_emails"] = arrayListOf(SAMPLE_EMAIL)
                savedStateHandle["form_contact_addresses"] = arrayListOf("")
                mutableState.update { it.copy(inputMessageRes = null,
                    form = it.form.copy(contact = QrContactInput(
                        name = SAMPLE_CONTACT_NAME, organization = SAMPLE_CONTACT_ORG,
                        format = QrContactFormat.MECARD, phones = listOf(SAMPLE_PHONE),
                        emails = listOf(SAMPLE_EMAIL), addresses = listOf("")))) }
            }
            QrContentType.EVENT -> sampleForm(
                "form_event_title" to SAMPLE_EVENT_TITLE,
                "form_event_location" to SAMPLE_EVENT_LOCATION,
                "form_event_description" to SAMPLE_EVENT_DESCRIPTION,
                "form_event_start_date" to SAMPLE_EVENT_DATE,
                "form_event_start_time" to SAMPLE_EVENT_START,
                "form_event_end_date" to SAMPLE_EVENT_DATE,
                "form_event_end_time" to SAMPLE_EVENT_END,
                "form_event_time_mode" to QrEventTimeMode.FLOATING.name,
                "form_event_reminder" to QrEventReminder.NONE.name,
            ) {
                it.copy(event = QrEventInput(
                    title = SAMPLE_EVENT_TITLE, location = SAMPLE_EVENT_LOCATION,
                    description = SAMPLE_EVENT_DESCRIPTION,
                    startDate = SAMPLE_EVENT_DATE, startTime = SAMPLE_EVENT_START,
                    endDate = SAMPLE_EVENT_DATE, endTime = SAMPLE_EVENT_END,
                    timeMode = QrEventTimeMode.FLOATING, reminder = QrEventReminder.NONE))
            }
        }
        requestGeneration()
    }

    private fun sampleForm(vararg values: Pair<String, String>, apply: (QrFormInput) -> QrFormInput) {
        values.forEach { (key, value) -> savedStateHandle[key] = value }
        mutableState.update { it.copy(form = apply(it.form), inputMessageRes = null) }
    }

    fun clear() {
        if (uiState.value.isExporting) return
        dismissExportMessage()
        if (uiState.value.mode == QrMode.DECODE) {
            cancelDecode()
            cancelPreview()
            selectedUri = null
            mutableState.update { it.copy(decodeEntry = null, decodeEntries = emptyList(), decodeErrorRes = null,
                canRetryDecode = false, cameraScanning = false, cameraResults = emptyList(),
                hasSelectedImage = false, cropVisible = false, preview = null, previewLoading = false,
                previewErrorRes = null) }
        } else {
            savedStateHandle["text"] = ""
            savedStateHandle["wifi_ssid"] = ""
            FORM_KEYS.forEach { savedStateHandle[it] = "" }
            savedStateHandle["form_contact_format"] = QrContactFormat.MECARD.name
            savedStateHandle["form_contact_phones"] = arrayListOf<String>()
            savedStateHandle["form_contact_emails"] = arrayListOf<String>()
            savedStateHandle["form_contact_addresses"] = arrayListOf<String>()
            mutableState.update { it.copy(textContent = "", wifiSsid = "", wifiPassword = "",
                wifiIdentity = "", wifiAnonymous = "", form = QrFormInput(), inputMessageRes = null) }
            requestGeneration()
        }
    }

    private fun acceptLength(value: String, maxLength: Int): Boolean {
        if (value.length <= maxLength) return true
        mutableState.update { it.copy(inputMessageRes = R.string.qr_error_input_limit) }
        return false
    }

    fun setExportSize(size: QrExportSize) {
        if (uiState.value.isExporting) return
        savedStateHandle["export_size"] = size.name
        mutableState.update { it.copy(exportSize = size, exportMessageRes = null) }
    }

    fun setExportFormat(format: QrExportFormat) {
        if (uiState.value.isExporting) return
        savedStateHandle["export_format"] = format.name
        mutableState.update { it.copy(exportFormat = format, exportMessageRes = null) }
    }

    private fun exportSnapshot(): QrExportSnapshot? {
        val state = uiState.value
        if (!state.canExportImage) return null
        val matrix = state.matrix ?: return null
        return QrExportSnapshot(matrix.copy(data = matrix.data.map { it.copyOf() }),
            state.colorStyle, state.exportSize, state.format, state.exportFormat)
    }

    /** Returns a request only when the UI needs to launch the system document picker. */
    fun saveRequested(): QrSaveRequest? {
        val state = uiState.value
        val snapshot = exportSnapshot() ?: return null
        val extension = when (state.exportFormat) {
            QrExportFormat.PNG -> "png"
            QrExportFormat.JPEG -> "jpg"
            QrExportFormat.SVG -> "svg"
        }
        // SVG and API 26-28 always need a chosen destination; PNG/JPEG use the gallery on API 29+.
        val useDocument = state.exportFormat == QrExportFormat.SVG ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        mutableState.update { it.copy(exportMessageRes = null,
            exportAction = if (useDocument) QrExportAction.CHOOSE_DESTINATION else QrExportAction.SAVE) }
        if (useDocument) {
            pendingDocumentExport = snapshot
            return QrSaveRequest("qr_${System.currentTimeMillis()}.$extension", state.exportFormat.mimeType())
        }
        save(snapshot, null)
        return null
    }

    fun documentCreated(uri: Uri?) {
        if (uiState.value.exportAction != QrExportAction.CHOOSE_DESTINATION) return
        val snapshot = pendingDocumentExport
        pendingDocumentExport = null
        if (uri == null) {
            mutableState.update { it.copy(exportAction = null) }
        } else if (snapshot == null) {
            exportUnavailable()
        } else {
            mutableState.update { it.copy(exportAction = QrExportAction.SAVE) }
            save(snapshot, uri)
        }
    }

    private fun save(snapshot: QrExportSnapshot, destination: Uri?) {
        val version = ++exportVersion
        exportJob = viewModelScope.launch {
            try {
                val result = saveQrImageUseCase(snapshot, destination)
                if (version != exportVersion) return@launch
                mutableState.update { it.copy(exportAction = null,
                    exportMessageRes = when (result) {
                        is QrExportResult.Success -> if (destination == null) R.string.qr_saved_gallery else R.string.qr_saved_document
                        is QrExportResult.Error -> result.reason.exportMessageRes(R.string.qr_error_save)
                    }, exportMessageIsError = result is QrExportResult.Error) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (version == exportVersion) exportUnavailable()
            }
        }
    }

    fun exportUnavailable() {
        pendingDocumentExport = null
        mutableState.update { it.copy(exportAction = null, exportMessageRes = R.string.qr_error_save, exportMessageIsError = true) }
    }

    fun shareImage() {
        val snapshot = exportSnapshot() ?: return
        mutableState.update { it.copy(exportAction = QrExportAction.SHARE_IMAGE, exportMessageRes = null) }
        val version = ++exportVersion
        exportJob = viewModelScope.launch {
            try {
                val result = prepareQrShareUseCase(snapshot)
                if (version != exportVersion) return@launch
                mutableState.update { when (result) {
                    is QrExportResult.Success -> it.copy(shareRequest =
                        QrShareRequest.Image(result.uri, uiState.value.exportFormat.mimeType()))
                    is QrExportResult.Error -> it.copy(exportAction = null,
                        exportMessageRes = result.reason.exportMessageRes(R.string.qr_error_share), exportMessageIsError = true)
                } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (version == exportVersion) shareHandled(false)
            }
        }
    }

    fun shareDecodedText() {
        val state = uiState.value
        if (state.isExporting || state.isDecoding) return
        val content = state.decodeEntry?.text ?: return
        mutableState.update { it.copy(exportAction = QrExportAction.SHARE_TEXT,
            exportMessageRes = null, shareRequest = QrShareRequest.Text(content), cameraScanning = false) }
    }

    fun shareDecodedTexts() {
        val state = uiState.value
        if (state.isExporting || state.isDecoding || state.decodeEntries.size < 2) return
        val content = state.decodeEntries.joinToString("\n") { it.text }
        mutableState.update { it.copy(exportAction = QrExportAction.SHARE_TEXT,
            exportMessageRes = null, shareRequest = QrShareRequest.Text(content), cameraScanning = false) }
    }

    /** Consume before opening another activity, so recreation cannot reopen the chooser. */
    fun takeShareRequest(request: QrShareRequest): QrShareRequest? {
        if (uiState.value.shareRequest != request) return null
        mutableState.update { it.copy(shareRequest = null) }
        return request
    }

    fun shareHandled(success: Boolean) {
        mutableState.update { it.copy(exportAction = null, shareRequest = null,
            exportMessageRes = if (success) null else R.string.qr_error_share, exportMessageIsError = !success) }
    }

    fun regenerateDecodedText() {
        val state = uiState.value
        if (state.isExporting || state.isDecoding) return
        val content = state.decodeEntry?.text ?: return
        if (!acceptLength(content, QrInputPolicy.MAX_TEXT_LENGTH)) return
        if (content.isEmpty() || !QrInputPolicy.isUtf8(content)) {
            mutableState.update { it.copy(inputMessageRes = R.string.qr_error_regenerate) }
            return
        }
        cancelDecode()
        // Recognized data may contain credentials; keep transferred content in memory only.
        savedStateHandle["text"] = ""
        savedStateHandle["mode"] = QrMode.GENERATE.name
        savedStateHandle["content_type"] = QrContentType.TEXT.name
        mutableState.update { it.copy(mode = QrMode.GENERATE, contentType = QrContentType.TEXT,
            textContent = content, inputMessageRes = null, cameraScanning = false) }
        requestGeneration()
    }

    fun dismissExportMessage() { mutableState.update { it.copy(exportMessageRes = null) } }

    private fun restoredText(key: String, limit: Int): String =
        savedStateHandle.get<String>(key)?.takeIf { it.length <= limit }.orEmpty()

    /** Saved lists tolerate older plain-string values and keep at most the allowed rows. */
    private fun restoredList(key: String): List<String> {
        val stored = savedStateHandle.get<Any?>(key) as? List<*> ?: return listOf("")
        val values = stored.filterIsInstance<String>().take(QrInputPolicy.MAX_CONTACT_ENTRIES)
            .map { it.take(QrInputPolicy.MAX_FIELD_LENGTH) }
        return values.ifEmpty { listOf("") }
    }

    private inline fun <reified T : Enum<T>> restoredEnum(key: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == savedStateHandle.get<String>(key) } ?: fallback

    override fun onCleared() {
        ++generationVersion
        ++decodeVersion
        ++exportVersion
        generateJob?.cancel()
        decodeJob?.cancel()
        exportJob?.cancel()
        previewJob?.cancel()
        pendingDocumentExport = null
        super.onCleared()
    }

    companion object {
        private fun barcodeSample(format: QrCodeFormat): String = when (format) {
            QrCodeFormat.QR_CODE -> SAMPLE_TEXT
            QrCodeFormat.CODE_128 -> "Cangshuo-2026"
            QrCodeFormat.CODE_39 -> "CANG-SHUO 39"
            QrCodeFormat.CODE_93 -> "CANG-SHUO 93"
            QrCodeFormat.EAN_13 -> "690123456789"
            QrCodeFormat.EAN_8 -> "1234567"
            QrCodeFormat.UPC_A -> "03600029145"
            QrCodeFormat.ITF -> "1234567890"
            QrCodeFormat.CODABAR -> "A123456B"
        }
        private val SECURITIES = QrInputPolicy.WIFI_SECURITIES
        private val FORM_KEYS = listOf(
            "form_phone", "form_email", "form_email_subject", "form_email_body",
            "form_email_cc", "form_email_bcc",
            "form_sms_number", "form_sms_message",
            "form_contact_name", "form_contact_org",
            "form_event_title", "form_event_location", "form_event_description",
            "form_event_start_date", "form_event_start_time",
            "form_event_end_date", "form_event_end_time",
            "form_event_time_mode", "form_event_reminder",
        )
        // Sample payloads are data, not interface copy: they stay language neutral on purpose.
        private const val SAMPLE_TEXT = "https://tool.zhzgo.cn"
        private const val SAMPLE_SSID = "Cangshuo-Guest-5G"
        private const val SAMPLE_PHONE = "13800138000"
        private const val SAMPLE_EMAIL = "hello@example.com"
        private const val SAMPLE_MAIL_SUBJECT = "Hello from Cangshuo Toolbox"
        private const val SAMPLE_MAIL_BODY = "This QR code opens a prefilled email."
        private const val SAMPLE_SMS = "Hello from Cangshuo Toolbox"
        private const val SAMPLE_CONTACT_NAME = "Cangshuo"
        private const val SAMPLE_CONTACT_ORG = "Cangshuo Studio"
        private const val SAMPLE_EVENT_TITLE = "Team review"
        private const val SAMPLE_EVENT_LOCATION = "Online meeting"
        private const val SAMPLE_EVENT_DESCRIPTION = "Agenda: release checklist"
        private const val SAMPLE_EVENT_DATE = "2026-10-08"
        private const val SAMPLE_EVENT_START = "10:00"
        private const val SAMPLE_EVENT_END = "11:00"
        fun factory(generateQrUseCase: GenerateQrUseCase, decodeQrUseCase: DecodeQrUseCase,
            saveQrImageUseCase: SaveQrImageUseCase, prepareQrShareUseCase: PrepareQrShareUseCase,
            loadQrPreviewUseCase: LoadQrPreviewUseCase,
            historyUseCases: QrHistoryUseCases? = null): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    require(modelClass.isAssignableFrom(QrViewModel::class.java))
                    return QrViewModel(generateQrUseCase, decodeQrUseCase, saveQrImageUseCase,
                        prepareQrShareUseCase, loadQrPreviewUseCase, extras.createSavedStateHandle(),
                        historyUseCases) as T
                }
            }
    }
}

private fun QrExportFormat.mimeType(): String = when (this) {
    QrExportFormat.PNG -> "image/png"
    QrExportFormat.JPEG -> "image/jpeg"
    QrExportFormat.SVG -> "image/svg+xml"
}

private fun QrExportFailure.exportMessageRes(fallback: Int): Int = when (this) {
    QrExportFailure.INVALID_RESULT -> R.string.qr_error_export_result
    QrExportFailure.MEMORY_LIMIT -> R.string.qr_error_export_memory
    QrExportFailure.OUTPUT_LIMIT -> R.string.qr_error_export_limit
    QrExportFailure.CACHE_FULL -> R.string.qr_error_export_cache
    else -> fallback
}

private fun QrFailure.messageRes(): Int = when (this) {
    QrFailure.INPUT_TOO_LONG -> R.string.qr_error_input_limit
    QrFailure.INVALID_TEXT -> R.string.qr_error_text
    QrFailure.INVALID_WIFI -> R.string.qr_error_wifi
    QrFailure.SSID_TOO_LONG -> R.string.qr_error_ssid
    QrFailure.PASSWORD_REQUIRED -> R.string.qr_error_password
    QrFailure.INVALID_PHONE -> R.string.qr_error_phone
    QrFailure.INVALID_EMAIL -> R.string.qr_error_email
    QrFailure.INVALID_SMS -> R.string.qr_error_sms
    QrFailure.INVALID_CONTACT -> R.string.qr_error_contact
    QrFailure.INVALID_EVENT -> R.string.qr_error_event
    QrFailure.EVENT_RANGE -> R.string.qr_error_event_range
    QrFailure.EVENT_TIME_GAP -> R.string.qr_error_event_time_gap
    QrFailure.INVALID_BARCODE -> R.string.qr_error_barcode
    QrFailure.CAPACITY_EXCEEDED -> R.string.qr_error_capacity
    QrFailure.GENERATE_FAILED -> R.string.qr_error_generate
    QrFailure.INVALID_IMAGE -> R.string.qr_error_image
    QrFailure.IMAGE_TOO_LARGE -> R.string.qr_error_image_large
    QrFailure.MEMORY_LIMIT -> R.string.qr_error_memory
    QrFailure.IMAGE_READ_FAILED -> R.string.qr_error_read
    QrFailure.NOT_FOUND -> R.string.qr_error_not_found
    QrFailure.DAMAGED_CODE -> R.string.qr_error_damaged
    QrFailure.DECODE_FAILED -> R.string.qr_error_decode
}
