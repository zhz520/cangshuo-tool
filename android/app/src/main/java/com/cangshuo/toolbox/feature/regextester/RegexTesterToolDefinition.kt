package com.cangshuo.toolbox.feature.regextester

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.model.ToolMetadata
import com.cangshuo.toolbox.core.model.ToolMode
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.regextester.ui.RegexTesterScreen
import com.cangshuo.toolbox.feature.regextester.ui.RegexTesterViewModel

class RegexTesterToolDefinition(private val resources: Resources, private val factory: ViewModelProvider.Factory) : ToolDefinition {
    override val metadata get() = ToolMetadata(
        code = "regex_tester", name = resources.getString(R.string.regex_tester_name),
        description = resources.getString(R.string.regex_tester_description), category = ToolCategory.DEV,
        mode = ToolMode.LOCAL, icon = "regex_tester",
        keywords = resources.getStringArray(R.array.regex_tester_keywords).toList(), sortOrder = 640,
    )
    override val requiredPermissions: List<String> = emptyList()
    override fun isAvailable(context: Context) = true
    @Composable override fun Screen() {
        val model: RegexTesterViewModel = viewModel(factory = factory)
        val state by model.uiState.collectAsStateWithLifecycle()
        RegexTesterScreen(state, model)
    }
}
