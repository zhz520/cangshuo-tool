package com.cangshuo.toolbox.feature.colorpicker.ui

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolGroupCard
import com.cangshuo.toolbox.core.ui.ToolResultRow
import com.cangshuo.toolbox.core.ui.ToolboxButton
import com.cangshuo.toolbox.core.ui.ToolboxLoadingIndicator
import com.cangshuo.toolbox.core.ui.ToolboxOutlinedButton
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.feature.colorpicker.domain.ColorPickerError
import com.cangshuo.toolbox.feature.colorpicker.domain.LoadedImage
import com.cangshuo.toolbox.feature.colorpicker.domain.PickedColor
import com.cangshuo.toolbox.feature.colorpicker.domain.SamplePoint
import com.cangshuo.toolbox.feature.colorpicker.domain.argbHex
import com.cangshuo.toolbox.feature.colorpicker.domain.argbHsl
import com.cangshuo.toolbox.feature.colorpicker.domain.argbRgb
import com.cangshuo.toolbox.feature.colorpicker.domain.fitImage
import com.cangshuo.toolbox.feature.colorpicker.domain.mapTouch
import com.cangshuo.toolbox.feature.colorpicker.domain.markerCenter
import com.cangshuo.toolbox.ui.theme.ToolboxDimens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ColorPickerScreen(
    state: ColorPickerUiState,
    onImagePicked: (String?) -> Unit,
    onSample: (SamplePoint?) -> Unit,
    onPaletteSelected: (PickedColor) -> Unit,
    onClearImage: () -> Unit,
    onClearPalette: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onPickerUnavailable: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val copiedTemplate = stringResource(R.string.color_picker_copied)
    val copyFailed = stringResource(R.string.color_picker_copy_failed)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        onImagePicked(uri?.toString())
    }
    val launchPicker = {
        try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        catch (_: Exception) { onPickerUnavailable() }
    }
    val copy: (String, String) -> Unit = { label, value ->
        scope.launch {
            try {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, value)))
                snackbar.showSnackbar(copiedTemplate.format(value))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                snackbar.showSnackbar(copyFailed)
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = ToolboxDimens.formMaxWidth).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(horizontal = ToolboxDimens.pageGutter, vertical = ToolboxDimens.pageTop),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("image") {
                ToolGroupCard(title = stringResource(R.string.color_picker_image_title)) {
                    val image = state.image
                    if (image == null) {
                        Text(stringResource(R.string.color_picker_empty), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.color_picker_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        ImageSampler(image = image, point = state.point, onSample = onSample, enabled = !state.loading)
                        Text(
                            stringResource(R.string.color_picker_dimensions, image.sourceWidth, image.sourceHeight, image.width, image.height),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (image.scaled) Text(
                            stringResource(R.string.color_picker_scaled),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (image.fileName.isNotBlank()) Text(image.fileName, style = MaterialTheme.typography.bodySmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ToolboxOutlinedButton(onClick = launchPicker, enabled = !state.loading) {
                                Text(stringResource(R.string.color_picker_change))
                            }
                            ToolboxTextButton(onClick = onClearImage) {
                                Text(stringResource(R.string.color_picker_remove))
                            }
                        }
                    }
                    if (state.loading) {
                        ToolboxLoadingIndicator(compact = true)
                        Text(stringResource(R.string.color_picker_loading), style = MaterialTheme.typography.bodySmall)
                        ToolboxTextButton(onClick = onCancel) { Text(stringResource(R.string.color_picker_cancel)) }
                    } else if (image == null) {
                        ToolboxButton(onClick = launchPicker) {
                            Text(stringResource(R.string.color_picker_pick))
                        }
                    }
                    state.error?.let { error ->
                        Text(stringResource(error.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        if (error != ColorPickerError.PICKER_UNAVAILABLE) ToolboxTextButton(onClick = onRetry, enabled = !state.loading) {
                            Text(stringResource(R.string.color_picker_retry))
                        }
                    }
                    if (state.cancelled) {
                        Text(stringResource(R.string.color_picker_cancelled), style = MaterialTheme.typography.bodySmall)
                        ToolboxTextButton(onClick = onRetry) { Text(stringResource(R.string.color_picker_retry)) }
                    }
                }
            }
            item("sample") {
                ToolGroupCard(title = stringResource(R.string.color_picker_sample_title)) {
                    val color = state.selected
                    if (color == null) {
                        Text(
                            stringResource(R.string.color_picker_sample_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            val swatchDescription = stringResource(R.string.color_picker_sample_swatch)
                            Box(
                                Modifier.size(56.dp)
                                    .background(Color(color.argb), MaterialTheme.shapes.medium)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                                    .semantics { contentDescription = swatchDescription },
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(argbHex(color.argb), style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
                                Text(
                                    argbRgb(color.argb),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text(
                                    argbHsl(color.argb),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                )
                                if (color.argb ushr 24 != 0xFF) {
                                    Text(
                                        stringResource(R.string.color_picker_alpha, (color.argb ushr 24) * 100 / 255),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        state.point?.let { point ->
                            ToolResultRow(
                                stringResource(R.string.color_picker_sample_position),
                                stringResource(R.string.color_picker_sample_position_value, point.x, point.y),
                            )
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ToolboxTextButton(onClick = { copy(argbHex(color.argb), argbHex(color.argb)) }, enabled = !state.loading) {
                                Text(stringResource(R.string.color_picker_copy_hex))
                            }
                            ToolboxTextButton(onClick = { copy(argbRgb(color.argb), argbRgb(color.argb)) }, enabled = !state.loading) {
                                Text(stringResource(R.string.color_picker_copy_rgb))
                            }
                            ToolboxTextButton(onClick = { copy(argbHsl(color.argb), argbHsl(color.argb)) }, enabled = !state.loading) {
                                Text(stringResource(R.string.color_picker_copy_hsl))
                            }
                            ToolboxTextButton(onClick = {
                                try {
                                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, listOf(argbHex(color.argb), argbRgb(color.argb), argbHsl(color.argb)).joinToString("\n"))
                                    }, null))
                                } catch (_: Exception) { scope.launch { snackbar.showSnackbar(copyFailed) } }
                            }, enabled = !state.loading) { Text(stringResource(R.string.color_picker_share)) }
                        }
                        state.point?.let { point ->
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(
                                    Triple(-1, 0, R.string.color_picker_left),
                                    Triple(1, 0, R.string.color_picker_right),
                                    Triple(0, -1, R.string.color_picker_up),
                                    Triple(0, 1, R.string.color_picker_down),
                                ).forEach { (dx, dy, label) ->
                                    val image = state.image
                                    val x = point.x + dx
                                    val y = point.y + dy
                                    ToolboxTextButton(onClick = {
                                        if (image != null) onSample(SamplePoint(x, y, (x + 0.5f) / image.width, (y + 0.5f) / image.height))
                                    }, enabled = !state.loading && image != null && x in 0 until image.width && y in 0 until image.height) {
                                        Text(stringResource(label))
                                    }
                                }
                            }
                        }
                        Text(
                            stringResource(R.string.color_picker_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item("palette") {
                ToolGroupCard(title = stringResource(R.string.color_picker_palette_title)) {
                    if (state.palette.isEmpty()) {
                        Text(
                            stringResource(R.string.color_picker_palette_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            state.palette.forEach { entry ->
                                val description = argbHex(entry.argb)
                                Box(
                                    Modifier.size(48.dp)
                                        .clickable(enabled = !state.loading, role = Role.Button) { onPaletteSelected(entry) }
                                        .semantics { contentDescription = description },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        Modifier.size(34.dp)
                                            .background(Color(entry.argb), CircleShape)
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                                    )
                                }
                            }
                        }
                        Row {
                            ToolboxTextButton(onClick = {
                                val paletteText = state.palette.joinToString("\n") { argbHex(it.argb) }
                                copy(paletteText, paletteText)
                            }, enabled = !state.loading) { Text(stringResource(R.string.color_picker_copy_palette)) }
                            ToolboxTextButton(onClick = onClearPalette, enabled = !state.loading) {
                                Text(stringResource(R.string.color_picker_palette_clear))
                            }
                        }
                        Text(
                            stringResource(R.string.color_picker_palette_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).imePadding())
    }
}

@Composable
private fun ImageSampler(image: LoadedImage, point: SamplePoint?, onSample: (SamplePoint?) -> Unit, enabled: Boolean) {
    var boxSize by remember(image) { mutableStateOf(IntSize.Zero) }
    val sampleAt: (Offset) -> SamplePoint? = { offset ->
        val fit = fitImage(boxSize.width.toFloat(), boxSize.height.toFloat(), image.width, image.height)
        fit?.let { mapTouch(it, image.width, image.height, offset.x, offset.y) }
    }
    val description = stringResource(R.string.color_picker_image_accessible)
    val centerLabel = stringResource(R.string.color_picker_center)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
            .onSizeChanged { boxSize = it }
            .semantics {
                contentDescription = description
                if (enabled) onClick(centerLabel) {
                    val x = image.width / 2
                    val y = image.height / 2
                    onSample(SamplePoint(x, y, (x + 0.5f) / image.width, (y + 0.5f) / image.height))
                    true
                }
            }
            .pointerInput(image, boxSize, enabled) {
                detectTapGestures { offset -> if (enabled) onSample(sampleAt(offset)) }
            }
            .pointerInput(image, boxSize, enabled) {
                detectDragGestures(
                    onDragStart = { offset -> if (enabled) onSample(sampleAt(offset)) },
                    onDrag = { change, _ -> if (enabled) { change.consume(); onSample(sampleAt(change.position)) } },
                )
            },
    ) {
        Image(
            bitmap = image.bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.matchParentSize(),
        )
        if (point != null) {
            val fit = fitImage(boxSize.width.toFloat(), boxSize.height.toFloat(), image.width, image.height)
            if (fit != null) {
                val (centerX, centerY) = markerCenter(fit, point)
                val outer = MaterialTheme.colorScheme.surface
                val inner = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                Canvas(Modifier.matchParentSize()) {
                    drawCircle(outer, radius = 9.dp.toPx(), center = Offset(centerX, centerY), style = Stroke(width = 3.dp.toPx()))
                    drawCircle(inner, radius = 9.dp.toPx(), center = Offset(centerX, centerY), style = Stroke(width = 1.25.dp.toPx()))
                }
            }
        }
    }
}

@StringRes
private fun ColorPickerError.messageRes(): Int = when (this) {
    ColorPickerError.INVALID_IMAGE -> R.string.color_picker_error_invalid
    ColorPickerError.UNSUPPORTED_FORMAT -> R.string.color_picker_error_unsupported
    ColorPickerError.TOO_LARGE -> R.string.color_picker_error_large
    ColorPickerError.MEMORY_LIMIT -> R.string.color_picker_error_memory
    ColorPickerError.LOAD_FAILED -> R.string.color_picker_error_load
    ColorPickerError.PICKER_UNAVAILABLE -> R.string.color_picker_error_picker
}
