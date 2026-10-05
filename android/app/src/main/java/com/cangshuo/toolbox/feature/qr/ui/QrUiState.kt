package com.cangshuo.toolbox.feature.qr.ui

import android.net.Uri
import android.graphics.Bitmap
import com.cangshuo.toolbox.feature.qr.domain.QrColorStyle
import com.cangshuo.toolbox.feature.qr.domain.QrCodeFormat
import com.cangshuo.toolbox.feature.qr.domain.QrContentType
import com.cangshuo.toolbox.feature.qr.domain.QrErrorCorrection
import com.cangshuo.toolbox.feature.qr.domain.QrExportSize
import com.cangshuo.toolbox.feature.qr.domain.QrExportFormat
import com.cangshuo.toolbox.feature.qr.domain.QrCropRegion
import com.cangshuo.toolbox.feature.qr.domain.QrFormInput
import com.cangshuo.toolbox.feature.qr.domain.QrDecodeEntry
import com.cangshuo.toolbox.feature.qr.domain.QrMatrix
import com.cangshuo.toolbox.feature.qr.domain.QrMode
import com.cangshuo.toolbox.feature.qr.domain.QrHistoryState

enum class QrExportAction { CHOOSE_DESTINATION, SAVE, SHARE_IMAGE, SHARE_TEXT }

sealed interface QrShareRequest {
    data class Image(val uri: Uri, val mimeType: String = "image/png") : QrShareRequest
    data class Text(val content: String) : QrShareRequest
}

/** Non-null when the caller must open the system document picker for the chosen export format. */
data class QrSaveRequest(val fileName: String, val mimeType: String)

data class QrUiState(
    val mode: QrMode = QrMode.GENERATE,
    val contentType: QrContentType = QrContentType.TEXT,
    val format: QrCodeFormat = QrCodeFormat.QR_CODE,
    val textContent: String = "",
    val wifiSsid: String = "",
    val wifiPassword: String = "",
    val wifiSecurity: String = "WPA",
    val wifiHidden: Boolean = false,
    val wifiEap: String = "TTLS",
    val wifiPhase2: String = "MSCHAPV2",
    val wifiIdentity: String = "",
    val wifiAnonymous: String = "",
    val errorCorrection: QrErrorCorrection = QrErrorCorrection.MEDIUM,
    val colorStyle: QrColorStyle = QrColorStyle.BLACK_WHITE,
    val exportSize: QrExportSize = QrExportSize.MEDIUM,
    val exportFormat: QrExportFormat = QrExportFormat.PNG,
    val form: QrFormInput = QrFormInput(),
    val matrix: QrMatrix? = null,
    val resolvedContent: String = "",
    val generateErrorRes: Int? = null,
    val inputMessageRes: Int? = null,
    val decodeEntry: QrDecodeEntry? = null,
    val decodeEntries: List<QrDecodeEntry> = emptyList(),
    val decodeErrorRes: Int? = null,
    val isGenerating: Boolean = false,
    val isDecoding: Boolean = false,
    val canRetryDecode: Boolean = false,
    val cameraScanning: Boolean = false,
    val cameraResults: List<QrDecodeEntry> = emptyList(),
    val multiDecode: Boolean = false,
    val exportAction: QrExportAction? = null,
    val exportMessageRes: Int? = null,
    val exportMessageIsError: Boolean = false,
    val shareRequest: QrShareRequest? = null,
    val history: QrHistoryState = QrHistoryState(),
    val historyLoading: Boolean = true,
    val historyBusy: Boolean = false,
    val historyMessageRes: Int? = null,
    val hasSelectedImage: Boolean = false,
    val cropVisible: Boolean = false,
    val cropRegion: QrCropRegion = QrCropRegion(),
    val preview: Bitmap? = null,
    val previewLoading: Boolean = false,
    val previewErrorRes: Int? = null,
) {
    val isExporting: Boolean get() = exportAction != null
    val canExportImage: Boolean get() = matrix != null && !isGenerating && !isExporting
}
