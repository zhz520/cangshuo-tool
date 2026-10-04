package com.cangshuo.toolbox.feature.favorites.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolboxLoadingIndicator

@Composable
fun FavoriteButton(
    selected: Boolean,
    enabled: Boolean,
    toolName: String,
    onChange: (Boolean) -> Unit,
    saving: Boolean = false,
) {
    val actionLabel = stringResource(if (selected) R.string.favorite_remove_label else R.string.favorite_add_label, toolName)
    val savingLabel = stringResource(R.string.favorite_saving)
    IconToggleButton(
        checked = selected,
        onCheckedChange = onChange,
        enabled = enabled && !saving,
        modifier = Modifier.semantics {
            contentDescription = actionLabel
            if (saving) stateDescription = savingLabel
        },
    ) {
        if (saving) {
            ToolboxLoadingIndicator(compact = true)
        } else {
            Icon(
                painterResource(if (selected) R.drawable.ic_star else R.drawable.ic_star_outline),
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
