package com.cangshuo.toolbox.feature.converter.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.feature.converter.domain.ConversionType
import com.cangshuo.toolbox.feature.converter.domain.UnitDef
import com.cangshuo.toolbox.ui.theme.ToolboxTheme

@Composable
fun ConverterScreen(
    state: ConverterUiState,
    onTypeSelected: (ConversionType) -> Unit,
    onFromUnitSelected: (UnitDef) -> Unit,
    onToUnitSelected: (UnitDef) -> Unit,
    onInputChanged: (String) -> Unit,
    onSwap: () -> Unit,
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

            // ── Conversion type chips ───────────────────────────────
            item(key = "types") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ConversionType.entries.forEach { type ->
                        FilterChip(
                            selected = type == state.selectedType,
                            onClick = { onTypeSelected(type) },
                            label = { Text(typeLabel(type)) },
                        )
                    }
                }
            }

            // ── From section ────────────────────────────────────────
            item(key = "from") {
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
                            stringResource(R.string.converter_from_label),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        UnitDropdown(
                            units = state.units,
                            selected = state.fromUnit,
                            onSelected = onFromUnitSelected,
                        )
                        OutlinedTextField(
                            value = state.inputValue,
                            onValueChange = onInputChanged,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.converter_input_hint)) },
                            supportingText = {
                                when {
                                    state.isInputError -> Text(stringResource(R.string.converter_error_invalid))
                                    state.isConversionError -> Text(stringResource(R.string.converter_error_range))
                                }
                            },
                            isError = state.isInputError || state.isConversionError,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                }
            }

            // ── Swap button ─────────────────────────────────────────
            item(key = "swap") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    IconButton(
                        onClick = onSwap,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_swap),
                            contentDescription = stringResource(R.string.converter_swap),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }

            // ── To section ──────────────────────────────────────────
            item(key = "to") {
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
                            stringResource(R.string.converter_to_label),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        UnitDropdown(
                            units = state.units,
                            selected = state.toUnit,
                            onSelected = onToUnitSelected,
                        )
                        // Result display
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    stringResource(R.string.converter_result),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (state.result.isNotEmpty()) {
                                    SelectionContainer {
                                        Text(
                                            state.result,
                                            style = MaterialTheme.typography.headlineSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                } else {
                                    Text(
                                        stringResource(R.string.converter_result_placeholder),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Copy button ─────────────────────────────────────────
            if (state.result.isNotEmpty()) {
                item(key = "copy") {
                    OutlinedButton(
                        onClick = { clipboardManager.setText(AnnotatedString(state.result)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.converter_copy))
                    }
                }
            }

            // ── Bottom spacer for keyboard clearance ────────────────
            item(key = "spacer") { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ── Unit dropdown ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitDropdown(
    units: List<UnitDef>,
    selected: UnitDef?,
    onSelected: (UnitDef) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected?.let { "${it.symbol}  —  ${stringResource(it.nameResId)}" } ?: "",
            onValueChange = {},
            readOnly = true,
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
            units.forEach { unit ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                unit.symbol,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.widthIn(min = 48.dp),
                            )
                            Text(
                                stringResource(unit.nameResId),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    },
                    onClick = {
                        onSelected(unit)
                        expanded = false
                    },
                )
            }
        }
    }
}

// ── Conversion type label helper ────────────────────────────────────────

@Composable
private fun typeLabel(type: ConversionType): String = stringResource(
    when (type) {
        ConversionType.LENGTH -> R.string.converter_type_length
        ConversionType.AREA -> R.string.converter_type_area
        ConversionType.VOLUME -> R.string.converter_type_volume
        ConversionType.MASS -> R.string.converter_type_mass
        ConversionType.TEMPERATURE -> R.string.converter_type_temperature
        ConversionType.SPEED -> R.string.converter_type_speed
        ConversionType.DATA -> R.string.converter_type_data
        ConversionType.TIME -> R.string.converter_type_time
        ConversionType.PRESSURE -> R.string.converter_type_pressure
        ConversionType.POWER -> R.string.converter_type_power
        ConversionType.ENERGY -> R.string.converter_type_energy
        ConversionType.ANGLE -> R.string.converter_type_angle
    },
)

// ── Previews ────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Converter Chinese", locale = "zh")
@Preview(showBackground = true, name = "Converter English", locale = "en")
@Composable
private fun ConverterPreview() {
    ToolboxTheme {
        ConverterScreen(
            state = ConverterUiState(),
            onTypeSelected = {},
            onFromUnitSelected = {},
            onToUnitSelected = {},
            onInputChanged = {},
            onSwap = {},
        )
    }
}
