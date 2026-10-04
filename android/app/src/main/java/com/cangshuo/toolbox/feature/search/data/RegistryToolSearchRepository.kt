package com.cangshuo.toolbox.feature.search.data

import android.content.res.Resources
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.model.ToolCategory
import com.cangshuo.toolbox.core.tool.ToolRegistry
import com.cangshuo.toolbox.feature.search.domain.ToolSearchDocument
import com.cangshuo.toolbox.feature.search.domain.ToolSearchRepository

class RegistryToolSearchRepository(private val registry: ToolRegistry, resources: Resources) : ToolSearchRepository {
    private val categoryNames = ToolCategory.entries.associateWith { category ->
        val resource = when (category) {
            ToolCategory.CALC -> R.array.search_category_calc
            ToolCategory.CONVERT -> R.array.search_category_convert
            ToolCategory.TEXT -> R.array.search_category_text
            ToolCategory.DEV -> R.array.search_category_dev
            ToolCategory.QR -> R.array.search_category_qr
            ToolCategory.IMAGE -> R.array.search_category_image
            ToolCategory.PDF -> R.array.search_category_pdf
            ToolCategory.DEVICE -> R.array.search_category_device
            ToolCategory.SENSOR -> R.array.search_category_sensor
            ToolCategory.NETWORK -> R.array.search_category_network
            ToolCategory.LIFE -> R.array.search_category_life
            ToolCategory.AI -> R.array.search_category_ai
            ToolCategory.OTHER -> R.array.search_category_other
        }
        resources.getStringArray(resource).toList()
    }

    override fun getDocuments(): List<ToolSearchDocument> = registry.enabledTools().map { definition ->
        val metadata = definition.metadata
        ToolSearchDocument(metadata.copy(keywords = metadata.keywords.toList()), categoryNames.getValue(metadata.category))
    }
}
