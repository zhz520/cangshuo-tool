package com.cangshuo.toolbox.feature.calculator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolboxButton
import com.cangshuo.toolbox.core.ui.ToolboxOutlinedButton
import com.cangshuo.toolbox.core.ui.ToolboxTextField
import com.cangshuo.toolbox.ui.theme.ToolboxTheme

@Composable
fun CalculatorScreen(
    state: CalculatorUiState,
    onExpressionChanged: (String, Int, Int) -> Unit,
    onInsert: (String) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onCalculate: () -> Unit,
    onUseResult: () -> Unit,
    onCopy: () -> Unit,
) {
    Box(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 600.dp).fillMaxWidth().fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "input") {
                ToolboxTextField(
                    value = TextFieldValue(state.expression, TextRange(state.selectionStart, state.selectionEnd)),
                    onValueChange = { onExpressionChanged(it.text, it.selection.start, it.selection.end) },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.calculator_expression),
                    placeholder = stringResource(R.string.calculator_input_hint),
                    supportingText = {
                        Text(stringResource(state.error?.labelResource() ?: R.string.calculator_input_help))
                    },
                    isError = state.error != null,
                    minLines = 2,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onCalculate() }),
                )
            }
            item(key = "keypad") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CalculatorKey.entries.chunked(4).forEach { keys ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            keys.forEach { key ->
                                val isOperation = key in setOf(
                                    CalculatorKey.ADD, CalculatorKey.SUBTRACT, CalculatorKey.MULTIPLY, CalculatorKey.DIVIDE,
                                )
                                val isClear = key == CalculatorKey.CLEAR
                                Button(
                                    onClick = {
                                        when (key) {
                                            CalculatorKey.CLEAR -> onClear()
                                            CalculatorKey.DELETE -> onDelete()
                                            else -> key.token?.let(onInsert)
                                        }
                                    },
                                    modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                    shape = MaterialTheme.shapes.medium,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = when {
                                            isClear -> MaterialTheme.colorScheme.surfaceVariant
                                            isOperation -> MaterialTheme.colorScheme.surfaceContainerHigh
                                            else -> MaterialTheme.colorScheme.surfaceContainer
                                        },
                                        contentColor = if (isClear) MaterialTheme.colorScheme.onSurfaceVariant
                                            else MaterialTheme.colorScheme.onSurface,
                                    ),
                                ) { Text(stringResource(key.label), style = MaterialTheme.typography.titleMedium) }
                            }
                        }
                    }
                    ToolboxButton(
                        onClick = onCalculate,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                    ) {
                        Text(stringResource(R.string.calculator_calculate))
                    }
                }
            }
            item(key = "result") {
                Column(Modifier.fillMaxWidth()) {
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Column(
                        Modifier.fillMaxWidth().padding(top = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.calculator_result),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.result == null) {
                            Text(
                                stringResource(R.string.calculator_result_placeholder),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            SelectionContainer {
                                Text(
                                    state.result,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ToolboxOutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.calculator_copy))
                                }
                                ToolboxOutlinedButton(onClick = onUseResult, modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.calculator_use_result))
                                }
                            }
                        }
                    }
                }
            }
            item(key = "rules") {
                Text(
                    stringResource(R.string.calculator_percent_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Calculator Chinese", locale = "zh")
@Preview(showBackground = true, name = "Calculator English", locale = "en")
@Composable
private fun CalculatorPreview() {
    ToolboxTheme {
        CalculatorScreen(
            state = CalculatorUiState(),
            onExpressionChanged = { _, _, _ -> },
            onInsert = {},
            onDelete = {},
            onClear = {},
            onCalculate = {},
            onUseResult = {},
            onCopy = {},
        )
    }
}
