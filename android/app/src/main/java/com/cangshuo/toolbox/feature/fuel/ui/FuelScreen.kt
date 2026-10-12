package com.cangshuo.toolbox.feature.fuel.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.formatAmount
import com.cangshuo.toolbox.core.ui.ToolResultRow
import com.cangshuo.toolbox.core.ui.ToolGroupCard
import com.cangshuo.toolbox.core.ui.ToolboxTextField
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.feature.fuel.domain.DistanceUnit
import com.cangshuo.toolbox.feature.fuel.domain.FuelError
import com.cangshuo.toolbox.feature.fuel.domain.FuelOutcome
import com.cangshuo.toolbox.feature.fuel.domain.FuelUnit
import com.cangshuo.toolbox.ui.theme.ToolboxTheme

@Composable
fun FuelScreen(
    state: FuelUiState,
    onDistanceChanged: (String) -> Unit,
    onFuelChanged: (String) -> Unit,
    onPriceChanged: (String) -> Unit,
    onDistanceUnitSelected: (DistanceUnit) -> Unit,
    onFuelUnitSelected: (FuelUnit) -> Unit,
    onClear: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "params") {
            ToolGroupCard(title = stringResource(R.string.fuel_params_title)) {
                ToolboxTextField(
                    value = state.distanceText,
                    onValueChange = onDistanceChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.fuel_distance_label),
                    placeholder = stringResource(R.string.fuel_distance_hint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.distanceUnit == DistanceUnit.KM,
                        onClick = { onDistanceUnitSelected(DistanceUnit.KM) },
                        label = { Text(stringResource(R.string.fuel_unit_km)) },
                    )
                    FilterChip(
                        selected = state.distanceUnit == DistanceUnit.MILE,
                        onClick = { onDistanceUnitSelected(DistanceUnit.MILE) },
                        label = { Text(stringResource(R.string.fuel_unit_mile)) },
                    )
                }
                ToolboxTextField(
                    value = state.fuelText,
                    onValueChange = onFuelChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.fuel_fuel_label),
                    placeholder = stringResource(R.string.fuel_fuel_hint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.fuelUnit == FuelUnit.LITER,
                        onClick = { onFuelUnitSelected(FuelUnit.LITER) },
                        label = { Text(stringResource(R.string.fuel_unit_liter)) },
                    )
                    FilterChip(
                        selected = state.fuelUnit == FuelUnit.US_GALLON,
                        onClick = { onFuelUnitSelected(FuelUnit.US_GALLON) },
                        label = { Text(stringResource(R.string.fuel_unit_us_gal)) },
                    )
                    FilterChip(
                        selected = state.fuelUnit == FuelUnit.UK_GALLON,
                        onClick = { onFuelUnitSelected(FuelUnit.UK_GALLON) },
                        label = { Text(stringResource(R.string.fuel_unit_uk_gal)) },
                    )
                }
                ToolboxTextField(
                    value = state.priceText,
                    onValueChange = onPriceChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.fuel_price_label),
                    placeholder = stringResource(R.string.fuel_price_hint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (state.distanceText.isNotEmpty() || state.fuelText.isNotEmpty() || state.priceText.isNotEmpty()) {
                    ToolboxTextButton(onClick = onClear) { Text(stringResource(R.string.fuel_btn_clear)) }
                }
            }
        }

        when (val outcome = state.outcome) {
            null -> item(key = "placeholder") {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.fuel_empty_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            is FuelOutcome.Failure -> item(key = "error") {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(errorMessage(outcome.error)),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            is FuelOutcome.Success -> {
                val result = outcome.result
                item(key = "result") {
                    val summary = stringResource(
                        R.string.fuel_copy_summary,
                        formatAmount(result.litersPer100Km, 2, grouping = false),
                        formatAmount(result.kmPerLiter, 2, grouping = false),
                        formatAmount(result.mpgUs, 1, grouping = false),
                        formatAmount(result.mpgUk, 1, grouping = false),
                    )
                    ToolGroupCard(title = stringResource(R.string.fuel_result_title)) {
                        Text(
                            text = formatAmount(result.litersPer100Km, 2, grouping = false),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.fuel_result_consumption),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ToolResultRow(stringResource(R.string.fuel_result_km_per_l), formatAmount(result.kmPerLiter, 2, grouping = false))
                        ToolResultRow(stringResource(R.string.fuel_result_mpg_us), formatAmount(result.mpgUs, 1, grouping = false))
                        ToolResultRow(stringResource(R.string.fuel_result_mpg_uk), formatAmount(result.mpgUk, 1, grouping = false))
                        if (result.totalCost != null) {
                            ToolResultRow(stringResource(R.string.fuel_result_total_cost), formatAmount(result.totalCost, 2))
                            ToolResultRow(stringResource(R.string.fuel_result_cost_per_km), formatAmount(result.costPerKm ?: 0.0, 3))
                            ToolResultRow(stringResource(R.string.fuel_result_cost_per_100km), formatAmount(result.costPer100Km ?: 0.0, 2))
                        }
                        ToolboxTextButton(
                            onClick = { clipboard.setText(AnnotatedString(summary)) },
                        ) { Text(stringResource(R.string.fuel_btn_copy)) }
                        Text(
                            text = stringResource(R.string.fuel_disclaimer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item(key = "bottom_spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

@StringRes
private fun errorMessage(error: FuelError): Int = when (error) {
    FuelError.INVALID -> R.string.fuel_error_invalid
    FuelError.RANGE -> R.string.fuel_error_range
    FuelError.INPUT_TOO_LONG -> R.string.numeric_error_input_too_long
}

@Preview(showBackground = true, name = "Fuel Screen Preview")
@Composable
private fun FuelScreenPreview() {
    ToolboxTheme {
        FuelScreen(
            state = FuelUiState(distanceText = "520", fuelText = "38"),
            onDistanceChanged = {},
            onFuelChanged = {},
            onPriceChanged = {},
            onDistanceUnitSelected = {},
            onFuelUnitSelected = {},
            onClear = {},
        )
    }
}
