package com.cangshuo.toolbox.feature.recent.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolListItem
import com.cangshuo.toolbox.core.ui.ToolboxButton
import com.cangshuo.toolbox.core.ui.ToolboxChevron
import com.cangshuo.toolbox.core.ui.ToolboxIconButton
import com.cangshuo.toolbox.core.ui.ToolboxLoadingState
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.core.ui.ToolboxTopBar
import com.cangshuo.toolbox.core.ui.ToolboxScaffold
import com.cangshuo.toolbox.feature.home.ui.toolIconResource

/** Full-screen "recently used" page pushed over the tab shell. */
@Composable
fun RecentScreen(
    state: RecentUiState,
    onToolSelected: (String) -> Unit,
    onClear: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    ToolboxScaffold(
        title = stringResource(R.string.profile_recent),
        onBack = onClose,
        actions = {
                        if (state.list is RecentListState.Content) {
                            ToolboxTextButton(onClick = onClear) { Text(stringResource(R.string.home_recent_clear)) }
                        }
        },
    ) { modifier ->
        Box(modifier) {
            when (val list = state.list) {
                RecentListState.Loading -> ToolboxLoadingState(stringResource(R.string.recent_loading), Modifier.padding(16.dp))
                RecentListState.Empty -> CenterMessage(stringResource(R.string.home_recent_description))
                RecentListState.Error -> Column(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(stringResource(R.string.recent_error), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ToolboxButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                }
                is RecentListState.Content -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                ) {
                    items(list.items, key = { it.code }) { item ->
                        val metadata = item.metadata
                        val time = DateUtils.getRelativeTimeSpanString(item.lastUsedAt).toString()
                        if (metadata != null) {
                            ToolListItem(
                                title = metadata.name,
                                subtitle = metadata.description + " · " + time,
                                icon = toolIconResource(metadata.icon),
                                onClick = { onToolSelected(item.code) },
                                trailing = {
                                    ToolboxChevron()
                                },
                            )
                        } else {
                            ToolListItem(
                                title = item.code,
                                icon = null,
                                onClick = { onToolSelected(item.code) },
                                trailing = { ToolboxChevron() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
