package com.cangshuo.toolbox.feature.calculator.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cangshuo.toolbox.R
import kotlinx.coroutines.launch

@Composable
fun CalculatorRoute(factory: ViewModelProvider.Factory) {
    val model: CalculatorViewModel = viewModel(key = "tool.calculator", factory = factory)
    val state by model.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copyLabel = stringResource(R.string.calculator_result)
    val copiedMessage = stringResource(R.string.calculator_copied)
    val copyFailure = stringResource(R.string.calculator_copy_failed)

    Box(Modifier.fillMaxSize()) {
        CalculatorScreen(
            state = state,
            onExpressionChanged = model::editExpression,
            onInsert = model::insert,
            onDelete = model::delete,
            onClear = model::clear,
            onCalculate = {
                keyboard?.hide()
                model.calculate()
            },
            onUseResult = model::useResult,
            onCopy = {
                state.result?.let { result ->
                    val copied = try {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        if (clipboard == null) false else {
                            clipboard.setPrimaryClip(ClipData.newPlainText(copyLabel, result))
                            true
                        }
                    } catch (_: Exception) {
                        false
                    }
                    // Android 13+ provides its own clipboard feedback.
                    if (!copied || Build.VERSION.SDK_INT < 33) {
                        scope.launch { snackbar.showSnackbar(if (copied) copiedMessage else copyFailure) }
                    }
                }
            },
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}
