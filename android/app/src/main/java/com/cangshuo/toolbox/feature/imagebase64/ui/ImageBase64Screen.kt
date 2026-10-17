package com.cangshuo.toolbox.feature.imagebase64.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolGroupCard
import com.cangshuo.toolbox.core.ui.ToolResultRow
import com.cangshuo.toolbox.core.ui.ToolboxButton
import com.cangshuo.toolbox.core.ui.ToolboxLoadingIndicator
import com.cangshuo.toolbox.core.ui.ToolboxOutlinedButton
import com.cangshuo.toolbox.core.ui.ToolboxSwitch
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.core.ui.ToolboxTextField
import com.cangshuo.toolbox.feature.imagebase64.domain.ImageBase64Error
import com.cangshuo.toolbox.feature.imagebase64.domain.ImageBase64Limits
import com.cangshuo.toolbox.feature.imagebase64.domain.ImageBase64Mode
import com.cangshuo.toolbox.feature.imagebase64.domain.imageExtension
import com.cangshuo.toolbox.ui.theme.ToolboxDimens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ImageBase64Screen(
    state: ImageBase64UiState,
    onModeChanged: (ImageBase64Mode) -> Unit,
    onInputChanged: (String) -> Unit,
    onClearInput: () -> Unit,
    onImagePicked: (String?) -> Unit,
    onDecode: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onPickerUnavailable: () -> Unit,
    onDataUrl: (Boolean) -> Unit,
    onUrlSafe: (Boolean) -> Unit,
    onLineWrap: (Boolean) -> Unit,
    onSave: (String?) -> Unit,
    onShare: () -> Unit,
    onShareHandled: (Boolean) -> Unit,
    onMessageConsumed: () -> Unit,
    onClearResult: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val copied = stringResource(R.string.image_base64_copied)
    val copyFailed = stringResource(R.string.image_base64_copy_failed)
    val shareTitle = stringResource(R.string.image_base64_share_title)
    val outputLabel = stringResource(R.string.image_base64_output_title)
    val saveSucceeded = stringResource(R.string.image_base64_save_succeeded)
    val saveFailed = stringResource(R.string.image_base64_save_failed)
    val shareFailed = stringResource(R.string.image_base64_share_failed)
    val exportBusy = state.saving || state.sharing || state.renderingOutput
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { onImagePicked(it?.toString()) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { onSave(it?.toString()) }
    val launchPicker = {
        try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        catch (_: Exception) { onPickerUnavailable() }
    }
    val copyOutput = {
        val value = state.output
        if (value.isNotEmpty()) scope.launch {
            try {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(outputLabel, value)))
                snackbar.showSnackbar(copied)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                snackbar.showSnackbar(copyFailed)
            }
        }
    }
    val pasteInput = {
        val manager = context.getSystemService(ClipboardManager::class.java)
        val text = manager?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        if (text.isNotEmpty()) onInputChanged(text)
    }
    LaunchedEffect(state.shareRequest) {
        val uri = state.shareRequest ?: return@LaunchedEffect
        val success = try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(shareTitle, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, shareTitle).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            true
        } catch (_: Exception) { false }
        onShareHandled(success)
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(when (message) {
            ImageBase64Message.SAVE_SUCCEEDED -> saveSucceeded
            ImageBase64Message.SAVE_FAILED -> saveFailed
            ImageBase64Message.SHARE_FAILED -> shareFailed
        })
        onMessageConsumed()
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = ToolboxDimens.formMaxWidth).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(horizontal = ToolboxDimens.pageGutter, vertical = ToolboxDimens.pageTop),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("mode") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.mode == ImageBase64Mode.ENCODE,
                        onClick = { onModeChanged(ImageBase64Mode.ENCODE) },
                        label = { Text(stringResource(R.string.image_base64_mode_encode)) },
                    )
                    FilterChip(
                        selected = state.mode == ImageBase64Mode.DECODE,
                        onClick = { onModeChanged(ImageBase64Mode.DECODE) },
                        label = { Text(stringResource(R.string.image_base64_mode_decode)) },
                    )
                }
            }
            item("input") {
                ToolGroupCard(title = stringResource(R.string.image_base64_input_title)) {
                    if (state.mode == ImageBase64Mode.ENCODE) {
                        Text(stringResource(R.string.image_base64_encode_empty), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.image_base64_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ToolboxButton(onClick = launchPicker, enabled = !state.loading) {
                            Text(stringResource(if (state.result == null) R.string.image_base64_pick else R.string.image_base64_change))
                        }
                    } else {
                        ToolboxTextField(
                            value = state.input,
                            onValueChange = onInputChanged,
                            modifier = Modifier.fillMaxWidth(),
                            label = stringResource(R.string.image_base64_decode_label),
                            placeholder = stringResource(R.string.image_base64_decode_placeholder),
                            enabled = !state.loading,
                            minLines = 5,
                            maxLines = 10,
                            supportingText = {
                                Text(stringResource(R.string.image_base64_input_count, state.input.length, ImageBase64Limits.MAX_INPUT_CHARS))
                            },
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ToolboxTextButton(onClick = pasteInput, enabled = !state.loading) { Text(stringResource(R.string.image_base64_paste)) }
                            ToolboxTextButton(onClick = onClearInput, enabled = state.input.isNotEmpty() && !state.loading) {
                                Text(stringResource(R.string.image_base64_clear))
                            }
                        }
                        ToolboxButton(onClick = onDecode, enabled = state.input.isNotBlank() && !state.loading) {
                            Text(stringResource(R.string.image_base64_decode_action))
                        }
                    }
                    if (state.loading) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ToolboxLoadingIndicator(compact = true)
                            Text(stringResource(R.string.image_base64_loading), style = MaterialTheme.typography.bodySmall)
                            ToolboxTextButton(onClick = onCancel) { Text(stringResource(R.string.image_base64_cancel)) }
                        }
                    }
                    state.error?.let { error ->
                        Text(
                            stringResource(error.messageRes()),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (error != ImageBase64Error.PICKER_UNAVAILABLE) ToolboxTextButton(onClick = onRetry, enabled = !state.loading) {
                            Text(stringResource(R.string.image_base64_retry))
                        }
                    }
                    if (state.cancelled) Text(stringResource(R.string.image_base64_cancelled), style = MaterialTheme.typography.bodySmall)
                }
            }
            item("options") {
                ToolGroupCard(title = stringResource(R.string.image_base64_options_title)) {
                    OptionRow(stringResource(R.string.image_base64_data_url), state.options.dataUrl, !state.loading, onDataUrl)
                    OptionRow(stringResource(R.string.image_base64_url_safe), state.options.urlSafe, !state.loading && !state.options.dataUrl, onUrlSafe)
                    OptionRow(stringResource(R.string.image_base64_wrap), state.options.lineWrap, !state.loading && !state.options.dataUrl, onLineWrap)
                    Text(
                        stringResource(R.string.image_base64_options_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.result?.let { result ->
                item("preview") {
                    ToolGroupCard(title = stringResource(R.string.image_base64_preview_title)) {
                        Box(
                            Modifier.fillMaxWidth().height(220.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                bitmap = result.preview.asImageBitmap(),
                                contentDescription = stringResource(R.string.image_base64_preview_description),
                                modifier = Modifier.fillMaxSize().padding(8.dp),
                                contentScale = ContentScale.Fit,
                            )
                        }
                        if (result.fileName.isNotBlank()) ToolResultRow(stringResource(R.string.image_base64_file), result.fileName)
                        ToolResultRow(stringResource(R.string.image_base64_mime), result.mimeType)
                        ToolResultRow(
                            stringResource(R.string.image_base64_dimensions),
                            stringResource(R.string.image_base64_dimensions_value, result.sourceWidth, result.sourceHeight, result.width, result.height),
                        )
                        ToolResultRow(stringResource(R.string.image_base64_bytes), stringResource(R.string.image_base64_bytes_value, result.byteCount))
                        ToolResultRow(stringResource(R.string.image_base64_output_chars), stringResource(R.string.image_base64_chars_value, state.output.length.toLong()))
                        if (result.width != result.sourceWidth || result.height != result.sourceHeight) {
                            Text(
                                stringResource(R.string.image_base64_scaled_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ToolboxOutlinedButton(onClick = if (state.mode == ImageBase64Mode.ENCODE) launchPicker else onDecode,
                                enabled = !state.loading) {
                                Text(stringResource(if (state.mode == ImageBase64Mode.ENCODE) R.string.image_base64_change else R.string.image_base64_decode_action))
                            }
                            ToolboxTextButton(onClick = onClearResult) { Text(stringResource(R.string.image_base64_remove)) }
                        }
                    }
                }
                item("output") {
                    ToolGroupCard(title = stringResource(R.string.image_base64_output_title)) {
                        Text(
                            text = outputPreview(state.output),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                                .padding(12.dp)
                                .heightIn(max = 240.dp),
                            maxLines = 10,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (state.output.length > ImageBase64Limits.PREVIEW_OUTPUT_PREFIX) {
                            Text(
                                stringResource(R.string.image_base64_preview_partial),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (state.renderingOutput) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ToolboxLoadingIndicator(compact = true)
                                Text(stringResource(R.string.image_base64_updating), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ToolboxButton(onClick = copyOutput, enabled = state.output.isNotEmpty() && !exportBusy) {
                                Text(stringResource(R.string.image_base64_copy))
                            }
                            ToolboxOutlinedButton(
                                onClick = { saver.launch(defaultFileName(result.fileName, result.mimeType)) },
                                enabled = state.output.isNotEmpty() && !exportBusy,
                            ) { Text(stringResource(if (state.saving) R.string.image_base64_saving else R.string.image_base64_save)) }
                            ToolboxTextButton(onClick = onShare, enabled = state.output.isNotEmpty() && !exportBusy) {
                                Text(stringResource(if (state.sharing) R.string.image_base64_sharing else R.string.image_base64_share))
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).imePadding())
    }
}

@Composable
private fun OptionRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected = checked, enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        ToolboxSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

private fun outputPreview(output: String): String =
    if (output.length <= ImageBase64Limits.PREVIEW_OUTPUT_PREFIX) output
    else output.take(ImageBase64Limits.PREVIEW_OUTPUT_PREFIX) + "\n…"

private fun defaultFileName(name: String, mimeType: String): String {
    val stem = name.substringBeforeLast('.', "image").take(60).ifBlank { "image" }
    return "$stem.${imageExtension(mimeType)}.base64.txt"
}

@StringRes
private fun ImageBase64Error.messageRes(): Int = when (this) {
    ImageBase64Error.INVALID_INPUT -> R.string.image_base64_error_invalid
    ImageBase64Error.UNSUPPORTED_FORMAT -> R.string.image_base64_error_unsupported
    ImageBase64Error.MIME_MISMATCH -> R.string.image_base64_error_mismatch
    ImageBase64Error.TOO_LARGE -> R.string.image_base64_error_large
    ImageBase64Error.MEMORY_LIMIT -> R.string.image_base64_error_memory
    ImageBase64Error.LOAD_FAILED -> R.string.image_base64_error_load
    ImageBase64Error.PICKER_UNAVAILABLE -> R.string.image_base64_error_picker
    ImageBase64Error.SAVE_FAILED -> R.string.image_base64_save_failed
    ImageBase64Error.SHARE_FAILED -> R.string.image_base64_share_failed
}
