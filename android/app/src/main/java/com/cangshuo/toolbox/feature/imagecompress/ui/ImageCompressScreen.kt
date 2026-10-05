package com.cangshuo.toolbox.feature.imagecompress.ui

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolboxLoadingIndicator
import com.cangshuo.toolbox.core.ui.ToolboxLoadingState
import com.cangshuo.toolbox.feature.imagecompress.domain.CompressParams
import com.cangshuo.toolbox.feature.imagecompress.domain.CompressResult
import com.cangshuo.toolbox.feature.imagecompress.domain.ImageMetadata
import com.cangshuo.toolbox.feature.imagecompress.domain.ImageExportRequest
import com.cangshuo.toolbox.feature.imagecompress.domain.OutputImageFormat
import com.cangshuo.toolbox.feature.imagecompress.domain.ResizeMode
import java.util.Locale

@Composable
fun ImageCompressScreen(
    state: ImageCompressUiState,
    onImageSelected: (Uri) -> Unit,
    onQualityChanged: (Int) -> Unit,
    onResizeModeChanged: (ResizeMode) -> Unit,
    onScalePercentChanged: (Int) -> Unit,
    onMaxDimensionChanged: (Int) -> Unit,
    onFormatChanged: (OutputImageFormat) -> Unit,
    onSaveRequested: (Boolean) -> ImageExportRequest?,
    onDocumentCreated: (Uri?) -> Unit,
    onExportUnavailable: () -> Unit,
    onShareRequested: () -> Unit,
    onShareHandled: (Boolean) -> Unit,
    onPreviewTabChanged: (PreviewTab) -> Unit,
    onClear: () -> Unit,
    onDismissMessage: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            onImageSelected(uri)
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        onDocumentCreated(if (result.resultCode == Activity.RESULT_OK) result.data?.data else null)
    }
    val shareTitle = stringResource(R.string.image_compress_share_title)
    val shareRequest = state.shareRequest
    LaunchedEffect(shareRequest) {
        if (shareRequest != null) {
            val success = try {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = shareRequest.mimeType
                    putExtra(Intent.EXTRA_STREAM, shareRequest.uri)
                    clipData = ClipData.newRawUri(shareTitle, shareRequest.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, shareTitle).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                true
            } catch (_: Exception) { false }
            onShareHandled(success)
        }
    }
    val isExporting = state.isSaving || state.isSharing
    val isProcessing = state.isLoadingImage || state.isCompressing
    val canExport = state.result != null && !isProcessing && !isExporting
    val isPngOutput = state.compressParams.outputFormat == OutputImageFormat.PNG ||
        (state.compressParams.outputFormat == OutputImageFormat.MATCH_SOURCE && state.originalMetadata?.mimeType == "image/png")
    val isJpegOutput = state.compressParams.outputFormat == OutputImageFormat.JPEG ||
        (state.compressParams.outputFormat == OutputImageFormat.MATCH_SOURCE && state.originalMetadata?.mimeType == "image/jpeg")

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Notification banner if user message is present
        if (state.userMessageRes != null) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (state.isSuccessMessage) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(state.userMessageRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state.isSuccessMessage) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (!state.isSuccessMessage && state.result == null && !isProcessing && !isExporting) {
                            TextButton(onClick = onRetry) { Text(stringResource(R.string.image_compress_retry)) }
                        }
                        TextButton(onClick = onDismissMessage) {
                            Text(
                                text = stringResource(R.string.image_compress_dismiss),
                                color = if (state.isSuccessMessage) {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                },
                            )
                        }
                    }
                }
            }
        }

        if (state.selectedUri == null) {
            // Empty state: prompt to select image
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_image_compress),
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                        )
                        Text(
                            text = stringResource(R.string.image_compress_pick_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.image_compress_pick_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        Button(
                            onClick = {
                                pickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(0.75f),
                        ) {
                            Text(stringResource(R.string.image_compress_btn_pick))
                        }
                    }
                }
            }
        } else {
            // Repick & Clear Actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        enabled = !isExporting,
                        onClick = {
                            pickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.image_compress_btn_repick))
                    }
                    OutlinedButton(
                        enabled = !isExporting,
                        onClick = onClear,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.image_compress_btn_clear))
                    }
                }
            }

            // Comparison Summary Card
            item {
                StatsComparisonCard(
                    originalMeta = state.originalMetadata,
                    result = state.result,
                    isCompressing = isProcessing,
                )
            }

            // Preview Card with Tabs
            item {
                PreviewCard(
                    previewTab = state.previewTab,
                    onTabSelected = onPreviewTabChanged,
                    originalBitmap = state.originalPreviewBitmap,
                    compressedBitmap = state.result?.previewBitmap,
                    isCompressing = isProcessing,
                )
            }

            // Compression Settings Card
            item {
                SettingsCard(
                    params = state.compressParams,
                    enabled = !isExporting,
                    qualityApplies = !isPngOutput,
                    jpegOutput = isJpegOutput,
                    onQualityChanged = onQualityChanged,
                    onResizeModeChanged = onResizeModeChanged,
                    onScalePercentChanged = onScalePercentChanged,
                    onMaxDimensionChanged = onMaxDimensionChanged,
                    onFormatChanged = onFormatChanged,
                )
            }

            // Export Actions
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = {
                            val request = onSaveRequested(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
                            if (request != null) {
                                try {
                                    documentLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                        addCategory(Intent.CATEGORY_OPENABLE)
                                        type = request.mimeType
                                        putExtra(Intent.EXTRA_TITLE, request.fileName)
                                    })
                                } catch (_: Exception) { onExportUnavailable() }
                            }
                        },
                        enabled = canExport,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.isSaving) {
                            ToolboxLoadingIndicator(compact = true)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.image_compress_saving))
                        } else {
                            Text(stringResource(if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) R.string.image_compress_save_document else R.string.image_compress_btn_save))
                        }
                    }

                    OutlinedButton(
                        onClick = onShareRequested,
                        enabled = canExport,
                    ) {
                        if (state.isSharing) {
                            ToolboxLoadingIndicator(compact = true)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.image_compress_btn_share))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsComparisonCard(
    originalMeta: ImageMetadata?,
    result: CompressResult?,
    isCompressing: Boolean,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.image_compress_stats_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (isCompressing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ToolboxLoadingIndicator(compact = true)
                        Text(
                            text = stringResource(R.string.image_compress_processing),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatColumn(
                    title = stringResource(R.string.image_compress_stat_original),
                    value = formatFileSize(originalMeta?.sizeBytes ?: 0L),
                    sub = originalMeta?.let { "${it.width} × ${it.height}" } ?: "-",
                )
                StatColumn(
                    title = stringResource(R.string.image_compress_stat_compressed),
                    value = result?.let { formatFileSize(it.compressedMetadata.sizeBytes) } ?: "-",
                    sub = result?.let { "${it.compressedMetadata.width} × ${it.compressedMetadata.height}" } ?: "-",
                )
                StatColumn(
                    title = stringResource(R.string.image_compress_stat_ratio),
                    value = result?.compressionRatio?.let { formatRatio(it) } ?: "-",
                    isHighlight = result?.compressionRatio != null,
                    isPositiveSaving = (result?.compressionRatio ?: 0f) < 0f,
                    sub = result?.let {
                        if (it.savedBytes > 0) stringResource(R.string.image_compress_saved_bytes, formatFileSize(it.savedBytes)) else ""
                    } ?: "",
                )
            }
        }
    }
}

@Composable
private fun StatColumn(
    title: String,
    value: String,
    sub: String,
    isHighlight: Boolean = false,
    isPositiveSaving: Boolean = true,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = if (isHighlight) {
                if (isPositiveSaving) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        if (sub.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun PreviewCard(
    previewTab: PreviewTab,
    onTabSelected: (PreviewTab) -> Unit,
    originalBitmap: Bitmap?,
    compressedBitmap: Bitmap?,
    isCompressing: Boolean,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PrimaryTabRow(
                selectedTabIndex = previewTab.ordinal,
                containerColor = Color.Transparent,
                divider = {},
            ) {
                Tab(
                    selected = previewTab == PreviewTab.COMPRESSED,
                    onClick = { onTabSelected(PreviewTab.COMPRESSED) },
                    text = { Text(stringResource(R.string.image_compress_preview_compressed)) },
                )
                Tab(
                    selected = previewTab == PreviewTab.ORIGINAL,
                    onClick = { onTabSelected(PreviewTab.ORIGINAL) },
                    text = { Text(stringResource(R.string.image_compress_preview_original)) },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .padding(12.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                val bitmapToDisplay = if (previewTab == PreviewTab.COMPRESSED) {
                    compressedBitmap
                } else {
                    originalBitmap
                }

                if (bitmapToDisplay != null) {
                    Image(
                        bitmap = bitmapToDisplay.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (isCompressing) {
                    ToolboxLoadingState(stringResource(R.string.image_compress_processing))
                } else {
                    Text(stringResource(R.string.image_compress_preview_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (isCompressing && previewTab == PreviewTab.COMPRESSED && bitmapToDisplay != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        ToolboxLoadingIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    params: CompressParams,
    enabled: Boolean,
    qualityApplies: Boolean,
    jpegOutput: Boolean,
    onQualityChanged: (Int) -> Unit,
    onResizeModeChanged: (ResizeMode) -> Unit,
    onScalePercentChanged: (Int) -> Unit,
    onMaxDimensionChanged: (Int) -> Unit,
    onFormatChanged: (OutputImageFormat) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (jpegOutput) {
                Text(stringResource(R.string.image_compress_jpeg_note), style = MaterialTheme.typography.bodySmall)
            }
            // Quality Control
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.image_compress_quality_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${params.quality}%",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                if (!qualityApplies) {
                    Text(stringResource(R.string.image_compress_png_quality_note), style = MaterialTheme.typography.bodySmall)
                }
                Slider(
                    enabled = enabled && qualityApplies,
                    value = params.quality.toFloat(),
                    onValueChange = { onQualityChanged(it.toInt()) },
                    valueRange = 1f..100f,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(50, 70, 80, 90, 100).forEach { preset ->
                        FilterChip(
                            enabled = enabled && qualityApplies,
                            selected = params.quality == preset,
                            onClick = { onQualityChanged(preset) },
                            label = { Text("$preset%") },
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Resize Strategy
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.image_compress_resize_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        enabled = enabled,
                        selected = params.resizeMode == ResizeMode.ORIGINAL,
                        onClick = { onResizeModeChanged(ResizeMode.ORIGINAL) },
                        label = { Text(stringResource(R.string.image_compress_resize_original)) },
                    )
                    FilterChip(
                        enabled = enabled,
                        selected = params.resizeMode == ResizeMode.SCALE_PERCENT,
                        onClick = { onResizeModeChanged(ResizeMode.SCALE_PERCENT) },
                        label = { Text(stringResource(R.string.image_compress_resize_scale)) },
                    )
                    FilterChip(
                        enabled = enabled,
                        selected = params.resizeMode == ResizeMode.MAX_DIMENSION,
                        onClick = { onResizeModeChanged(ResizeMode.MAX_DIMENSION) },
                        label = { Text(stringResource(R.string.image_compress_resize_max_dim)) },
                    )
                }

                if (params.resizeMode == ResizeMode.SCALE_PERCENT) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.image_compress_scale_percent),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = "${params.scalePercent}%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Slider(
                        enabled = enabled,
                        value = params.scalePercent.toFloat(),
                        onValueChange = { onScalePercentChanged(it.toInt()) },
                        valueRange = 10f..100f,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(25, 50, 75, 100).forEach { p ->
                            FilterChip(
                                enabled = enabled,
                                selected = params.scalePercent == p,
                                onClick = { onScalePercentChanged(p) },
                                label = { Text("$p%") },
                            )
                        }
                    }
                } else if (params.resizeMode == ResizeMode.MAX_DIMENSION) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.image_compress_max_dimension),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(1080, 1440, 1920, 2048, 4096).forEach { dim ->
                            FilterChip(
                                enabled = enabled,
                                selected = params.maxDimension == dim,
                                onClick = { onMaxDimensionChanged(dim) },
                                label = { Text("${dim}px") },
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Output Format
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.image_compress_format_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        enabled = enabled,
                        selected = params.outputFormat == OutputImageFormat.MATCH_SOURCE,
                        onClick = { onFormatChanged(OutputImageFormat.MATCH_SOURCE) },
                        label = { Text(stringResource(R.string.image_compress_format_source)) },
                    )
                    FilterChip(
                        enabled = enabled,
                        selected = params.outputFormat == OutputImageFormat.JPEG,
                        onClick = { onFormatChanged(OutputImageFormat.JPEG) },
                        label = { Text("JPEG") },
                    )
                    FilterChip(
                        enabled = enabled,
                        selected = params.outputFormat == OutputImageFormat.PNG,
                        onClick = { onFormatChanged(OutputImageFormat.PNG) },
                        label = { Text("PNG") },
                    )
                    FilterChip(
                        enabled = enabled,
                        selected = params.outputFormat == OutputImageFormat.WEBP,
                        onClick = { onFormatChanged(OutputImageFormat.WEBP) },
                        label = { Text("WEBP") },
                    )
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "-"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format(Locale.ROOT, "%.2f MB", mb)
        kb >= 1.0 -> String.format(Locale.ROOT, "%.1f KB", kb)
        else -> "$bytes B"
    }
}

private fun formatRatio(ratio: Float): String {
    return if (ratio <= 0f) {
        String.format(Locale.ROOT, "%.1f%%", ratio)
    } else {
        String.format(Locale.ROOT, "+%.1f%%", ratio)
    }
}
