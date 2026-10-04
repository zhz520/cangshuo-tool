package com.cangshuo.toolbox.feature.calculator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
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
            modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 600.dp).fillMaxWidth().fillMaxHeight(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "intro") {
                Text(
                    stringResource(R.string.calculator_offline),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            item(key = "input") {
                OutlinedTextField(
                    value = TextFieldValue(state.expression, TextRange(state.selectionStart, state.selectionEnd)),
                    onValueChange = { onExpressionChanged(it.text, it.selection.start, it.selection.end) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.calculator_expression)) },
                    placeholder = { Text(stringResource(R.string.calculator_input_hint)) },
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
                                val operation = key in setOf(
                                    CalculatorKey.ADD, CalculatorKey.SUBTRACT, CalculatorKey.MULTIPLY, CalculatorKey.DIVIDE,
                                )
                                Button(
                                    onClick = {
                                        when (key) {
                                            CalculatorKey.CLEAR -> onClear()
                                            CalculatorKey.DELETE -> onDelete()
                                            else -> key.token?.let(onInsert)
                                        }
                                    },
                                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (operation) MaterialTheme.colorScheme.secondaryContainer
                                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = if (operation) MaterialTheme.colorScheme.onSecondaryContainer
                                            else MaterialTheme.colorScheme.onSurface,
                                    ),
                                ) { Text(stringResource(key.label), style = MaterialTheme.typography.titleMedium) }
                            }
                        }
                    }
                    Button(onClick = onCalculate, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.calculator_calculate), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            item(key = "result") {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.calculator_result), fontWeight = FontWeight.Bold)
                        if (state.result == null) {
                            Text(stringResource(R.string.calculator_result_placeholder))
                        } else {
                            SelectionContainer {
                                Text(state.result, style = MaterialTheme.typography.headlineSmall)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.calculator_copy))
                                }
                                OutlinedButton(onClick = onUseResult, modifier = Modifier.weight(1f)) {
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
