package com.cangshuo.toolbox.feature.purecolor.ui

import android.content.ClipData
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolGroupCard
import com.cangshuo.toolbox.core.ui.ToolResultRow
import com.cangshuo.toolbox.core.ui.ToolboxButton
import com.cangshuo.toolbox.core.ui.ToolboxLoadingIndicator
import com.cangshuo.toolbox.core.ui.ToolboxOutlinedButton
import com.cangshuo.toolbox.core.ui.ToolboxTextField
import com.cangshuo.toolbox.feature.purecolor.domain.PureColorLimits
import com.cangshuo.toolbox.feature.purecolor.domain.PureColorParams
import com.cangshuo.toolbox.feature.purecolor.domain.argbHex
import com.cangshuo.toolbox.feature.purecolor.domain.pureColorFileName
import com.cangshuo.toolbox.ui.theme.ToolboxDimens

private val colorPresets = listOf(
    0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF1677FF.toInt(), 0xFF16A34A.toInt(),
    0xFFF04438.toInt(), 0xFFF59E0B.toInt(), 0xFF7C3AED.toInt(), 0x001677FF,
)

private val sizePresets = listOf(
    1080 to 1080, 1080 to 1920, 1920 to 1080, 1440 to 2560, 2560 to 1440, 2160 to 2160,
)

@Composable
fun PureColorScreen(
    state: PureColorUiState,
    onColorChanged: (String) -> Unit,
    onWidthChanged: (String) -> Unit,
    onHeightChanged: (String) -> Unit,
    onPreset: (Int, Int) -> Unit,
    onSave: (String?) -> Unit,
    onShare: () -> Unit,
    onShareHandled: (Boolean) -> Unit,
    onMessageConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val shareTitle = stringResource(R.string.pure_color_share_title)
    val saved = stringResource(R.string.pure_color_saved)
    val saveFailed = stringResource(R.string.pure_color_save_failed)
    val shareFailed = stringResource(R.string.pure_color_share_failed)
    val invalidColor = stringResource(R.string.pure_color_invalid_color)
    val invalidSize = stringResource(R.string.pure_color_invalid_size)
    val tooLarge = stringResource(R.string.pure_color_too_large)
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) onSave(uri.toString())
    }
    val argb = state.argb
    val width = state.width
    val height = state.height
    val invalidDimensions = width == null || height == null || width !in 1..PureColorLimits.MAX_EDGE ||
        height !in 1..PureColorLimits.MAX_EDGE
    val excessivePixels = width != null && height != null && width.toLong() * height > PureColorLimits.MAX_PIXELS
    val params = if (state.valid && argb != null && width != null && height != null)
        PureColorParams(argb, width, height) else null
    val save = {
        if (params != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) onSave(null)
            else saver.launch(pureColorFileName(params))
        }
    }
    LaunchedEffect(state.shareRequest) {
        val uri = state.shareRequest ?: return@LaunchedEffect
        val success = try {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(shareTitle, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, shareTitle).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            true
        } catch (_: Exception) { false }
        onShareHandled(success)
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(when (message) {
            PureColorMessage.SAVED -> saved
            PureColorMessage.SAVE_FAILED -> saveFailed
            PureColorMessage.SHARE_FAILED -> shareFailed
        })
        onMessageConsumed()
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = ToolboxDimens.formMaxWidth).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(horizontal = ToolboxDimens.pageGutter, vertical = ToolboxDimens.pageTop),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("color") {
                ToolGroupCard(title = stringResource(R.string.pure_color_color_title)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(52.dp).background(colorOrFallback(state.argb), CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
                        ToolboxTextField(
                            value = state.colorInput,
                            onValueChange = onColorChanged,
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.pure_color_hex),
                            placeholder = "#RRGGBBAA",
                            enabled = !state.busy,
                            isError = state.argb == null,
                            supportingText = if (state.argb == null) { { Text(invalidColor) } } else null,
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        colorPresets.forEach { value ->
                            Box(
                                Modifier.size(44.dp).clickable(enabled = !state.busy, role = Role.Button) {
                                    onColorChanged(argbHex(value))
                                }, contentAlignment = Alignment.Center,
                            ) {
                                Box(Modifier.size(32.dp).background(Color(value), CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
                            }
                        }
                    }
                }
            }
            item("size") {
                ToolGroupCard(title = stringResource(R.string.pure_color_size_title)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ToolboxTextField(
                            value = state.widthInput,
                            onValueChange = onWidthChanged,
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.pure_color_width),
                            enabled = !state.busy,
                            isError = state.width == null || state.width !in 1..PureColorLimits.MAX_EDGE,
                        )
                        ToolboxTextField(
                            value = state.heightInput,
                            onValueChange = onHeightChanged,
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.pure_color_height),
                            enabled = !state.busy,
                            isError = state.height == null || state.height !in 1..PureColorLimits.MAX_EDGE,
                        )
                    }
                    Text(
                        stringResource(R.string.pure_color_size_hint, PureColorLimits.MAX_EDGE, PureColorLimits.MAX_PIXELS / 1_000_000),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (invalidDimensions || excessivePixels) Text(
                        if (excessivePixels) tooLarge else invalidSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sizePresets.forEach { preset ->
                            FilterChip(
                                selected = state.width == preset.first && state.height == preset.second,
                                onClick = { onPreset(preset.first, preset.second) },
                                enabled = !state.busy,
                                label = { Text("${preset.first}×${preset.second}") },
                            )
                        }
                    }
                }
            }
            item("preview") {
                ToolGroupCard(title = stringResource(R.string.pure_color_preview_title)) {
                    val previewColor = colorOrFallback(state.argb)
                    Box(
                        Modifier.fillMaxWidth().height(220.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            if (previewColor.alpha < 1f) drawCheckerboard()
                            drawRect(previewColor)
                        }
                    }
                    if (previewColor.alpha < 1f) Text(
                        stringResource(R.string.pure_color_alpha_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (argb != null) ToolResultRow(stringResource(R.string.pure_color_hex_value), argbHex(argb))
                    ToolResultRow(stringResource(R.string.pure_color_dimensions),
                        stringResource(R.string.pure_color_dimensions_value, state.widthInput.ifBlank { "—" }, state.heightInput.ifBlank { "—" }))
                    ToolResultRow(stringResource(R.string.pure_color_format), "PNG")
                    if (params != null) {
                        ToolResultRow(stringResource(R.string.pure_color_file), pureColorFileName(params))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolboxButton(onClick = save, enabled = params != null && !state.busy) {
                            Text(stringResource(if (state.saving) R.string.pure_color_saving else R.string.pure_color_save))
                        }
                        ToolboxOutlinedButton(onClick = onShare, enabled = params != null && !state.busy) {
                            Text(stringResource(if (state.sharing) R.string.pure_color_sharing else R.string.pure_color_share))
                        }
                    }
                    if (state.saving || state.sharing) Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ToolboxLoadingIndicator(compact = true)
                        Text(stringResource(R.string.pure_color_working), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).imePadding())
    }
}

private fun colorOrFallback(argb: Int?): Color = Color(argb ?: 0x001677FF)

private fun DrawScope.drawCheckerboard() {
    val cell = 12.dp.toPx()
    val light = Color(0xFFF5F5F5)
    val dark = Color(0xFFE5E7EB)
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = 0f
        var col = 0
        while (x < size.width) {
            drawRect(if ((row + col) % 2 == 0) light else dark, Offset(x, y), Size(cell, cell))
            x += cell
            col++
        }
        y += cell
        row++
    }
}
