package com.cangshuo.toolbox.feature.timestamp.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.feature.timestamp.domain.SupportedTimeZone
import com.cangshuo.toolbox.feature.timestamp.domain.TimestampUnit
import com.cangshuo.toolbox.ui.theme.ToolboxTheme

@Composable
fun TimestampScreen(
    state: TimestampUiState,
    onTimestampInputChanged: (String) -> Unit,
    onTimestampUnitChanged: (TimestampUnit) -> Unit,
    onTimestampTimeZoneChanged: (SupportedTimeZone) -> Unit,
    onFillCurrentTimestamp: () -> Unit,
    onDateInputChanged: (String) -> Unit,
    onDateTimeZoneChanged: (SupportedTimeZone) -> Unit,
    onFillCurrentDateTime: () -> Unit,
    onToggleLive: () -> Unit,
    onRefreshNow: () -> Unit,
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

            // ── Current Time Card ───────────────────────────────────
            item(key = "current_card") {
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
                                stringResource(R.string.timestamp_current_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = onToggleLive) {
                                    Text(
                                        if (state.isLive) {
                                            stringResource(R.string.timestamp_live)
                                        } else {
                                            stringResource(R.string.timestamp_paused)
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                                TextButton(onClick = onRefreshNow) {
                                    Text(stringResource(R.string.action_retry))
                                }
                            }
                        }

                        // Formatted DateTime
                        SelectionContainer {
                            Text(
                                state.currentFormatted,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        // Timestamps row
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // Seconds
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.weight(1f),
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        stringResource(R.string.timestamp_unit_seconds),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        state.currentSeconds.toString(),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                        ),
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    TextButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(state.currentSeconds.toString()))
                                        },
                                        contentPadding = PaddingValues(0.dp),
                                    ) {
                                        Text(stringResource(R.string.timestamp_copy), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            // Millis
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.weight(1f),
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        stringResource(R.string.timestamp_unit_millis),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        state.currentMillis.toString(),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                        ),
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    TextButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(state.currentMillis.toString()))
                                        },
                                        contentPadding = PaddingValues(0.dp),
                                    ) {
                                        Text(stringResource(R.string.timestamp_copy), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Timestamp to Date Card ──────────────────────────────
            item(key = "ts_to_date_card") {
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
                        Text(
                            stringResource(R.string.timestamp_to_date_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        // Unit selection chips
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.tsUnit == TimestampUnit.SECONDS,
                                onClick = { onTimestampUnitChanged(TimestampUnit.SECONDS) },
                                label = { Text(stringResource(R.string.timestamp_unit_seconds)) },
                            )
                            FilterChip(
                                selected = state.tsUnit == TimestampUnit.MILLISECONDS,
                                onClick = { onTimestampUnitChanged(TimestampUnit.MILLISECONDS) },
                                label = { Text(stringResource(R.string.timestamp_unit_millis)) },
                            )
                        }

                        // Timezone dropdown
                        TimeZoneDropdown(
                            selected = state.tsTimeZone,
                            onSelected = onTimestampTimeZoneChanged,
                        )

                        // Input field
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = state.tsInput,
                                onValueChange = onTimestampInputChanged,
                                modifier = Modifier.weight(1f),
                                placeholder = { Text(stringResource(R.string.timestamp_input_hint)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                isError = state.tsIsError,
                                supportingText = if (state.tsIsError) {
                                    { Text(stringResource(R.string.timestamp_error_invalid)) }
                                } else null,
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = onFillCurrentTimestamp) {
                                Text(stringResource(R.string.timestamp_fill_now))
                            }
                        }

                        // Result
                        if (state.tsResultFormatted.isNotEmpty()) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                stringResource(R.string.timestamp_result_formatted),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            SelectionContainer {
                                                Text(
                                                    state.tsResultFormatted,
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                    ),
                                                )
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(state.tsResultFormatted))
                                            },
                                        ) {
                                            Text(stringResource(R.string.timestamp_copy))
                                        }
                                    }

                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                stringResource(R.string.timestamp_result_iso),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            SelectionContainer {
                                                Text(
                                                    state.tsResultIso,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                    ),
                                                )
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(state.tsResultIso))
                                            },
                                        ) {
                                            Text(stringResource(R.string.timestamp_copy))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Date to Timestamp Card ──────────────────────────────
            item(key = "date_to_ts_card") {
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
                        Text(
                            stringResource(R.string.timestamp_to_timestamp_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        // Timezone dropdown
                        TimeZoneDropdown(
                            selected = state.dateTimeZone,
                            onSelected = onDateTimeZoneChanged,
                        )

                        // Input field
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = state.dateInput,
                                onValueChange = onDateInputChanged,
                                modifier = Modifier.weight(1f),
                                placeholder = { Text(stringResource(R.string.timestamp_date_input_hint)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                isError = state.dateError != null,
                                supportingText = state.dateError?.let { error ->
                                    { Text(stringResource(error.messageResId)) }
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = onFillCurrentDateTime) {
                                Text(stringResource(R.string.timestamp_fill_now))
                            }
                        }

                        // Results
                        if (state.dateResultSeconds.isNotEmpty()) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                stringResource(R.string.timestamp_result_seconds),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            SelectionContainer {
                                                Text(
                                                    state.dateResultSeconds,
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                    ),
                                                )
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(state.dateResultSeconds))
                                            },
                                        ) {
                                            Text(stringResource(R.string.timestamp_copy))
                                        }
                                    }

                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                stringResource(R.string.timestamp_result_millis),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            SelectionContainer {
                                                Text(
                                                    state.dateResultMillis,
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                    ),
                                                )
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(state.dateResultMillis))
                                            },
                                        ) {
                                            Text(stringResource(R.string.timestamp_copy))
                                        }
                                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeZoneDropdown(
    selected: SupportedTimeZone,
    onSelected: (SupportedTimeZone) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = stringResource(selected.labelResId),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.timestamp_timezone_label)) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            SupportedTimeZone.entries.forEach { tz ->
                DropdownMenuItem(
                    text = { Text(stringResource(tz.labelResId)) },
                    onClick = {
                        onSelected(tz)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Timestamp Preview")
@Composable
private fun TimestampPreview() {
    ToolboxTheme {
        TimestampScreen(
            state = TimestampUiState(
                currentSeconds = 1728000000L,
                currentMillis = 1728000000000L,
                currentFormatted = "2026-10-04 13:30:00",
            ),
            onTimestampInputChanged = {},
            onTimestampUnitChanged = {},
            onTimestampTimeZoneChanged = {},
            onFillCurrentTimestamp = {},
            onDateInputChanged = {},
            onDateTimeZoneChanged = {},
            onFillCurrentDateTime = {},
            onToggleLive = {},
            onRefreshNow = {},
        )
    }
}
