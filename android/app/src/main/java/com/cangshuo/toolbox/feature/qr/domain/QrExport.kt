package com.cangshuo.toolbox.feature.qr.domain

import android.net.Uri

enum class QrExportSize(val pixels: Int) { SMALL(512), MEDIUM(1024), LARGE(2048) }

/** Output encoding for a generated result; SVG is text and always uses the system file picker. */
enum class QrExportFormat { PNG, JPEG, SVG }

data class QrExportSnapshot(
    val matrix: QrMatrix,
    val colorStyle: QrColorStyle,
    val size: QrExportSize,
    val format: QrCodeFormat = QrCodeFormat.QR_CODE,
    val fileFormat: QrExportFormat = QrExportFormat.PNG,
)

enum class QrExportFailure { INVALID_RESULT, MEMORY_LIMIT, OUTPUT_LIMIT, CACHE_FULL, SAVE_FAILED, SHARE_FAILED }

sealed interface QrExportResult {
    data class Success(val uri: Uri) : QrExportResult
    data class Error(val reason: QrExportFailure) : QrExportResult
}

interface QrExportRepository {
    suspend fun save(snapshot: QrExportSnapshot, destination: Uri?): QrExportResult
    suspend fun prepareShare(snapshot: QrExportSnapshot): QrExportResult
}

class SaveQrImageUseCase(private val repository: QrExportRepository) {
    suspend operator fun invoke(snapshot: QrExportSnapshot, destination: Uri?) = repository.save(snapshot, destination)
}

class PrepareQrShareUseCase(private val repository: QrExportRepository) {
    suspend operator fun invoke(snapshot: QrExportSnapshot) = repository.prepareShare(snapshot)
}

/** Pixel layout is independent of screen density; both matrices already include their quiet zone. */
sealed interface QrExportLayout {
    data class Qr(val side: Int, val cell: Int, val offset: Int) : QrExportLayout

    data class Linear(
        val width: Int,
        val height: Int,
        val barWidth: Int,
        val leftOffset: Int,
        val topOffset: Int,
        val barHeight: Int,
    ) : QrExportLayout

    companion object {
        fun forSnapshot(snapshot: QrExportSnapshot): QrExportLayout? =
            if (snapshot.format.linear) linear(snapshot) else qr(snapshot)

        private fun qr(snapshot: QrExportSnapshot): QrExportLayout? {
            val matrix = snapshot.matrix
            if (matrix.width !in 29..185 || matrix.width != matrix.height ||
                (matrix.width - 29) % 4 != 0 || matrix.data.size != matrix.height ||
                matrix.data.any { it.size != matrix.width }) return null
            val side = snapshot.size.pixels
            val cell = side / matrix.width
            return Qr(side, cell, (side - cell * matrix.width) / 2)
        }

        /** Bars stretch to one third of the selected width, with an eighth-height quiet zone. */
        private fun linear(snapshot: QrExportSnapshot): QrExportLayout? {
            val matrix = snapshot.matrix
            if (matrix.height != 1 || matrix.data.size != 1) return null
            if (matrix.width !in 8..4096 || matrix.data[0].size != matrix.width) return null
            val width = snapshot.size.pixels
            val height = width / 3
            val barWidth = width / matrix.width
            if (height <= 0 || barWidth < 1) return null
            val topOffset = height / 8
            val barHeight = height - topOffset * 2
            if (barHeight <= 0) return null
            return Linear(width, height, barWidth,
                (width - barWidth * matrix.width) / 2, topOffset, barHeight)
        }
    }
}
