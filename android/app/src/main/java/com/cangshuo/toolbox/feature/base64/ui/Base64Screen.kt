package com.cangshuo.toolbox.feature.base64.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.feature.base64.domain.Base64Mode
import com.cangshuo.toolbox.ui.theme.ToolboxTheme

@Composable
fun Base64Screen(
    state: Base64UiState,
    onModeChanged: (Base64Mode) -> Unit,
    onInputChanged: (String) -> Unit,
    onUrlSafeToggled: (Boolean) -> Unit,
    onSwap: () -> Unit,
    onClear: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current

    Box(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .fillMaxHeight(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // ── Mode & Options Card ─────────────────────────────────
            item(key = "mode_card") {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Mode selection chips
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = state.mode == Base64Mode.ENCODE,
                                onClick = { onModeChanged(Base64Mode.ENCODE) },
                                label = { Text(stringResource(R.string.base64_mode_encode)) },
                            )
                            FilterChip(
                                selected = state.mode == Base64Mode.DECODE,
                                onClick = { onModeChanged(Base64Mode.DECODE) },
                                label = { Text(stringResource(R.string.base64_mode_decode)) },
                            )
                        }

                        // URL-safe switch
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.base64_opt_url_safe),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    stringResource(R.string.base64_opt_url_safe_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = state.isUrlSafe,
                                onCheckedChange = onUrlSafeToggled,
                            )
                        }
                    }
                }
            }

            // ── Input Card ──────────────────────────────────────────
            item(key = "input_card") {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (state.mode == Base64Mode.ENCODE) {
                                    stringResource(R.string.base64_input_label_text)
                                } else {
                                    stringResource(R.string.base64_input_label_b64)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Row {
                                TextButton(
                                    onClick = {
                                        val clipText = clipboardManager.getText()?.text
                                        if (!clipText.isNullOrEmpty()) {
                                            onInputChanged(clipText)
                                        }
                                    },
                                ) {
                                    Text(stringResource(R.string.base64_btn_paste))
                                }
                                if (state.inputText.isNotEmpty()) {
                                    TextButton(onClick = onClear) {
                                        Text(stringResource(R.string.base64_btn_clear))
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = state.inputText,
                            onValueChange = onInputChanged,
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            maxLines = 8,
                            placeholder = {
                                Text(
                                    if (state.mode == Base64Mode.ENCODE) {
                                        stringResource(R.string.base64_placeholder_text)
                                    } else {
                                        stringResource(R.string.base64_placeholder_b64)
                                    },
                                )
                            },
                            isError = state.isError,
                            supportingText = {
                                if (state.isError) {
                                    Text(stringResource(R.string.base64_error_invalid))
                                } else if (state.inputText.isNotEmpty()) {
                                    Text(stringResource(R.string.base64_char_count, state.inputText.length))
                                }
                            },
                        )
                    }
                }
            }

            // ── Swap / Invert Button ────────────────────────────────
            if (state.outputText.isNotEmpty()) {
                item(key = "swap_action") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        OutlinedButton(onClick = onSwap) {
                            Text(stringResource(R.string.base64_btn_swap))
                        }
                    }
                }
            }

            // ── Output Card ─────────────────────────────────────────
            item(key = "output_card") {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val decodeInfo = state.decodeInfo
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (state.mode == Base64Mode.ENCODE) {
                                    stringResource(R.string.base64_output_label_b64)
                                } else {
                                    stringResource(R.string.base64_output_label_text)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (state.outputText.isNotEmpty()) {
                                TextButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(state.outputText))
                                    },
                                ) {
                                    Text(stringResource(R.string.base64_btn_copy))
                                }
                            }
                        }

                        if (state.mode == Base64Mode.DECODE && decodeInfo != null && decodeInfo.hasUtf8Error) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.base64_error_utf8,
                                        decodeInfo.byteCount,
                                        decodeInfo.invalidUtf8Count,
                                        (decodeInfo.firstInvalidByteOffset ?: 0) + 1,
                                    ),
                                    modifier = Modifier.padding(14.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        } else {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                ) {
                                    if (state.outputText.isNotEmpty()) {
                                        SelectionContainer {
                                            Text(
                                                state.outputText,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    } else {
                                        Text(
                                            stringResource(R.string.base64_output_placeholder),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        if (state.mode == Base64Mode.DECODE && decodeInfo != null) {
                            HexResultSection(
                                info = decodeInfo,
                                onCopyHex = { clipboardManager.setText(AnnotatedString(decodeInfo.hex)) },
                            )
                        }
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun HexResultSection(
    info: Base64DecodeInfo,
    onCopyHex: () -> Unit,
) {
    val preview = remember(info.hex) { info.hex.take(HEX_PREVIEW_CHARS) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.base64_hex_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.base64_hex_byte_count, info.byteCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onCopyHex) {
                Text(stringResource(R.string.base64_btn_copy_hex))
            }
        }
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SelectionContainer {
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (preview.length < info.hex.length) {
                    Text(
                        stringResource(
                            R.string.base64_hex_preview_partial,
                            preview.length.toString(),
                            info.hex.length.toString(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private const val HEX_PREVIEW_CHARS = 8_000

@Preview(showBackground = true, name = "Base64 Screen Preview")
@Composable
private fun Base64ScreenPreview() {
    ToolboxTheme {
        Base64Screen(
            state = Base64UiState(
                mode = Base64Mode.ENCODE,
                inputText = "Hello World!",
                outputText = "SGVsbG8gV29ybGQh",
            ),
            onModeChanged = {},
            onInputChanged = {},
            onUrlSafeToggled = {},
            onSwap = {},
            onClear = {},
        )
    }
}
