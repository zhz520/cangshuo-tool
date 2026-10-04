package com.cangshuo.toolbox.feature.favorites.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.cangshuo.toolbox.R

@Composable
fun FavoriteButton(selected: Boolean, enabled: Boolean, toolName: String, onChange: (Boolean) -> Unit) {
    IconToggleButton(checked = selected, onCheckedChange = onChange, enabled = enabled) {
        Icon(
            painterResource(if (selected) R.drawable.ic_star else R.drawable.ic_star_outline),
            contentDescription = stringResource(
                if (selected) R.string.favorite_remove_label else R.string.favorite_add_label, toolName,
            ),
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
