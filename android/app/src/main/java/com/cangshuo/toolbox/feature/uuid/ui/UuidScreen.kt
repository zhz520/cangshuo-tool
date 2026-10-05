package com.cangshuo.toolbox.feature.uuid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.cangshuo.toolbox.ui.theme.ToolboxTheme

private val COUNT_OPTIONS = listOf(1, 5, 10, 20, 50, 100)

@Composable
fun UuidScreen(
    state: UuidUiState,
    onCountSelected: (Int) -> Unit,
    onUppercaseToggled: (Boolean) -> Unit,
    onHyphensToggled: (Boolean) -> Unit,
    onBracedToggled: (Boolean) -> Unit,
    onRegenerate: () -> Unit,
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

            // ── Configuration Card ──────────────────────────────────
            item(key = "config_card") {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            stringResource(R.string.uuid_config_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        // Count chips
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.uuid_count_label, state.count),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                COUNT_OPTIONS.forEach { opt ->
                                    FilterChip(
                                        selected = state.count == opt,
                                        onClick = { onCountSelected(opt) },
                                        label = { Text(opt.toString()) },
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Switches for formatting
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Uppercase
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.uuid_opt_uppercase),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        stringResource(R.string.uuid_opt_uppercase_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = state.isUppercase,
                                    onCheckedChange = onUppercaseToggled,
                                )
                            }

                            // Hyphens
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.uuid_opt_hyphens),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        stringResource(R.string.uuid_opt_hyphens_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = state.hasHyphens,
                                    onCheckedChange = onHyphensToggled,
                                )
                            }

                            // Braced
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.uuid_opt_braced),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        stringResource(R.string.uuid_opt_braced_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = state.isBraced,
                                    onCheckedChange = onBracedToggled,
                                )
                            }
                        }

                        // Action buttons
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Button(
                                onClick = onRegenerate,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.uuid_btn_regenerate))
                            }
                            OutlinedButton(
                                onClick = {
                                    val all = state.generatedUuids.joinToString("\n")
                                    clipboardManager.setText(AnnotatedString(all))
                                },
                                modifier = Modifier.weight(1f),
                                enabled = state.generatedUuids.isNotEmpty(),
                            ) {
                                Text(stringResource(R.string.uuid_btn_copy_all))
                            }
                        }
                    }
                }
            }

            // ── Generated Results Card ──────────────────────────────
            item(key = "results_header") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.uuid_results_title, state.generatedUuids.size),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.uuid_click_to_copy_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            itemsIndexed(
                items = state.generatedUuids,
                key = { index, item -> "$index-$item" },
            ) { index, uuidStr ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            clipboardManager.setText(AnnotatedString(uuidStr))
                        },
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${index + 1}.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.widthIn(min = 28.dp),
                        )
                        Text(
                            uuidStr,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(uuidStr))
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                stringResource(R.string.uuid_copy_single),
                                style = MaterialTheme.typography.labelSmall,
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

@Preview(showBackground = true, name = "UUID Screen Preview")
@Composable
private fun UuidScreenPreview() {
    ToolboxTheme {
        UuidScreen(
            state = UuidUiState(
                count = 3,
                generatedUuids = listOf(
                    "550e8400-e29b-41d4-a716-446655440000",
                    "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
                    "6ba7b811-9dad-11d1-80b4-00c04fd430c8",
                ),
            ),
            onCountSelected = {},
            onUppercaseToggled = {},
            onHyphensToggled = {},
            onBracedToggled = {},
            onRegenerate = {},
        )
    }
}
