package com.cangshuo.toolbox.feature.bmi.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.formatAmount
import com.cangshuo.toolbox.core.ui.ToolGroupCard
import com.cangshuo.toolbox.core.ui.ToolboxTextField
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.feature.bmi.domain.BmiBand
import com.cangshuo.toolbox.feature.bmi.domain.BmiError
import com.cangshuo.toolbox.feature.bmi.domain.BmiLimits
import com.cangshuo.toolbox.feature.bmi.domain.BmiOutcome
import com.cangshuo.toolbox.feature.bmi.domain.BmiResult
import com.cangshuo.toolbox.feature.bmi.domain.BmiStandard
import com.cangshuo.toolbox.ui.theme.ToolboxTheme

@Composable
fun BmiScreen(
    state: BmiUiState,
    onHeightChanged: (String) -> Unit,
    onWeightChanged: (String) -> Unit,
    onUnitSelected: (Boolean) -> Unit,
    onStandardSelected: (BmiStandard) -> Unit,
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
        item(key = "measurements") {
            ToolGroupCard(title = stringResource(R.string.bmi_units_title)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !state.imperial,
                        onClick = { onUnitSelected(false) },
                        label = { Text(stringResource(R.string.bmi_units_metric)) },
                    )
                    FilterChip(
                        selected = state.imperial,
                        onClick = { onUnitSelected(true) },
                        label = { Text(stringResource(R.string.bmi_units_imperial)) },
                    )
                }
                ToolboxTextField(
                    value = state.heightText,
                    onValueChange = onHeightChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(if (state.imperial) R.string.bmi_height_in else R.string.bmi_height_cm),
                    placeholder = stringResource(if (state.imperial) R.string.bmi_height_in_hint else R.string.bmi_height_cm_hint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                ToolboxTextField(
                    value = state.weightText,
                    onValueChange = onWeightChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(if (state.imperial) R.string.bmi_weight_lb else R.string.bmi_weight_kg),
                    placeholder = stringResource(if (state.imperial) R.string.bmi_weight_lb_hint else R.string.bmi_weight_kg_hint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (state.heightText.isNotEmpty() || state.weightText.isNotEmpty()) {
                    ToolboxTextButton(onClick = onClear) { Text(stringResource(R.string.bmi_btn_clear)) }
                }
            }
        }

        item(key = "standard") {
            ToolGroupCard(title = stringResource(R.string.bmi_standard_title)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.standard == BmiStandard.CHINA,
                        onClick = { onStandardSelected(BmiStandard.CHINA) },
                        label = { Text(stringResource(R.string.bmi_standard_cn)) },
                    )
                    FilterChip(
                        selected = state.standard == BmiStandard.WHO,
                        onClick = { onStandardSelected(BmiStandard.WHO) },
                        label = { Text(stringResource(R.string.bmi_standard_who)) },
                    )
                }
                Text(
                    text = stringResource(R.string.bmi_standard_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                            stringResource(R.string.bmi_empty_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            is BmiOutcome.Failure -> item(key = "error") {
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

            is BmiOutcome.Success -> item(key = "result") {
                val result = outcome.result
                val healthyText = if (state.imperial) {
                    stringResource(
                        R.string.bmi_result_healthy_lb,
                        formatAmount(result.healthyMinKg / BmiLimits.KG_PER_POUND, 1),
                        formatAmount(result.healthyMaxKg / BmiLimits.KG_PER_POUND, 1),
                    )
                } else {
                    stringResource(
                        R.string.bmi_result_healthy_kg,
                        formatAmount(result.healthyMinKg, 1),
                        formatAmount(result.healthyMaxKg, 1),
                    )
                }
                val summary = stringResource(
                    R.string.bmi_copy_summary,
                    formatAmount(result.bmi, 1, grouping = false),
                    stringResource(result.band.labelResource()),
                    healthyText,
                )
                ToolGroupCard(title = stringResource(R.string.bmi_result_title)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatAmount(result.bmi, 1, grouping = false),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = bandContainerColor(result.band),
                        ) {
                            Text(
                                text = stringResource(result.band.labelResource()),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = bandContentColor(result.band),
                            )
                        }
                    }
                    Text(
                        text = healthyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.bmi_result_formula),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ToolboxTextButton(
                        onClick = { clipboard.setText(AnnotatedString(summary)) },
                    ) { Text(stringResource(R.string.bmi_btn_copy)) }
                }
            }
        }

        item(key = "bottom_spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

@StringRes
private fun BmiError.messageResource(): Int = when (this) {
    BmiError.INVALID -> R.string.bmi_error_invalid
    BmiError.OUT_OF_RANGE -> R.string.bmi_error_range
    BmiError.INPUT_TOO_LONG -> R.string.numeric_error_input_too_long
}

private fun errorMessage(error: BmiError): Int = error.messageResource()

@StringRes
private fun BmiBand.labelResource(): Int = when (this) {
    BmiBand.SEVERE_THINNESS -> R.string.bmi_band_severe_thinness
    BmiBand.MODERATE_THINNESS -> R.string.bmi_band_moderate_thinness
    BmiBand.MILD_THINNESS -> R.string.bmi_band_mild_thinness
    BmiBand.UNDERWEIGHT -> R.string.bmi_band_underweight
    BmiBand.NORMAL -> R.string.bmi_band_normal
    BmiBand.OVERWEIGHT -> R.string.bmi_band_overweight
    BmiBand.OBESE -> R.string.bmi_band_obese
    BmiBand.OBESE_I -> R.string.bmi_band_obese_i
    BmiBand.OBESE_II -> R.string.bmi_band_obese_ii
    BmiBand.OBESE_III -> R.string.bmi_band_obese_iii
}

@Composable
private fun bandContainerColor(band: BmiBand): Color = when (band) {
    BmiBand.NORMAL -> MaterialTheme.colorScheme.primaryContainer
    BmiBand.OBESE, BmiBand.OBESE_I, BmiBand.OBESE_II, BmiBand.OBESE_III -> MaterialTheme.colorScheme.errorContainer
    else -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun bandContentColor(band: BmiBand): Color = when (band) {
    BmiBand.NORMAL -> MaterialTheme.colorScheme.onPrimaryContainer
    BmiBand.OBESE, BmiBand.OBESE_I, BmiBand.OBESE_II, BmiBand.OBESE_III -> MaterialTheme.colorScheme.onErrorContainer
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Preview(showBackground = true, name = "BMI Screen Preview")
@Composable
private fun BmiScreenPreview() {
    ToolboxTheme {
        BmiScreen(
            state = BmiUiState(heightText = "170", weightText = "65"),
            onHeightChanged = {},
            onWeightChanged = {},
            onUnitSelected = {},
            onStandardSelected = {},
            onClear = {},
        )
    }
}
