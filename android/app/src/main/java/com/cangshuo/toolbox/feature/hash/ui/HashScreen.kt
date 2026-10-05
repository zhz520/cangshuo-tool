package com.cangshuo.toolbox.feature.hash.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.feature.hash.domain.HashAlgorithm
import com.cangshuo.toolbox.feature.hash.domain.HashResult
import com.cangshuo.toolbox.ui.theme.ToolboxTheme

@Composable
fun HashScreen(
    state: HashUiState,
    onInputChanged: (String) -> Unit,
    onUppercaseToggled: (Boolean) -> Unit,
    onClear: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val byteCount = state.inputText.toByteArray(Charsets.UTF_8).size

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
                                stringResource(R.string.hash_input_label),
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
                                    Text(stringResource(R.string.hash_btn_paste))
                                }
                                if (state.inputText.isNotEmpty()) {
                                    TextButton(onClick = onClear) {
                                        Text(stringResource(R.string.hash_btn_clear))
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = state.inputText,
                            onValueChange = onInputChanged,
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 6,
                            placeholder = {
                                Text(stringResource(R.string.hash_input_placeholder))
                            },
                            supportingText = {
                                if (state.inputText.isNotEmpty()) {
                                    Text(
                                        stringResource(
                                            R.string.hash_input_stats,
                                            state.inputText.length,
                                            byteCount,
                                        ),
                                    )
                                }
                            },
                        )
                    }
                }
            }

            // ── Options Card ────────────────────────────────────────
            item(key = "options_card") {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.hash_options_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = state.uppercase,
                                onClick = { onUppercaseToggled(!state.uppercase) },
                                label = { Text(stringResource(R.string.hash_opt_uppercase)) },
                            )
                        }
                    }
                }
            }

            // ── Results Header ──────────────────────────────────────
            item(key = "results_header") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.hash_results_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (state.hashes.isNotEmpty()) {
                        TextButton(
                            onClick = {
                            val allFormatted = state.hashes.joinToString("\n\n") { item ->
                                "${item.algorithm.displayName}:\n${item.hash}"
                            }
                            clipboardManager.setText(AnnotatedString(allFormatted))
                        }) {
                            Text(stringResource(R.string.hash_btn_copy_all))
                        }
                    }
                }
            }

            // ── Results Empty State ─────────────────────────────────
            if (state.hashes.isEmpty()) {
                item(key = "empty_results") {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.hash_empty_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ── Results List ────────────────────────────────────────
            items(
                items = state.hashes,
                key = { it.algorithm.name },
            ) { result ->
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    result.algorithm.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                ) {
                                    Text(
                                        "${result.algorithm.bitLength}-bit",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                            TextButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(result.hash))
                                },
                            ) {
                                Text(stringResource(R.string.hash_btn_copy))
                            }
                        }

                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                            ) {
                                SelectionContainer {
                                    Text(
                                        result.hash,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
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

@Preview(showBackground = true, name = "Hash Screen Preview")
@Composable
private fun HashScreenPreview() {
    ToolboxTheme {
        HashScreen(
            state = HashUiState(
                inputText = "Hello World",
                uppercase = false,
                hashes = listOf(
                    HashResult(HashAlgorithm.MD5, "b10a8db164e0754105b7a99be72e3fe5"),
                    HashResult(HashAlgorithm.SHA1, "0a4d55a8d778e5022fab701977c5d840bbc486d0"),
                    HashResult(HashAlgorithm.SHA256, "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e"),
                ),
            ),
            onInputChanged = {},
            onUppercaseToggled = {},
            onClear = {},
        )
    }
}
