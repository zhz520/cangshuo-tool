package com.cangshuo.toolbox.feature.answerbook

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.*
import com.cangshuo.toolbox.core.tool.ToolDefinition
import com.cangshuo.toolbox.feature.answerbook.ui.AnswerBookRoute

class AnswerBookToolDefinition(private val resources: Resources, private val factory: ViewModelProvider.Factory) : ToolDefinition {
    override val metadata get() = ToolMetadata(code = "answer_book", name = resources.getString(R.string.answerbook_name),
        description = resources.getString(R.string.answerbook_description), category = ToolCategory.OTHER, mode = ToolMode.LOCAL,
        icon = "answer_book", keywords = resources.getStringArray(R.array.answerbook_keywords).toList(), sortOrder = 640, isFeatured = false)
    override val requiredPermissions: List<String> = emptyList()
    override fun isAvailable(context: Context) = true
    @Composable override fun Screen() = AnswerBookRoute(factory)
}
