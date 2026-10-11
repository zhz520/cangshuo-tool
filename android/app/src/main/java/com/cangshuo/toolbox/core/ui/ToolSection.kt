package com.cangshuo.toolbox.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.ui.theme.ToolboxDimens

/**
 * Shared building blocks for the design system (white-dominant, light-gray
 * surfaces, single blue accent). Icons sit in rounded light-gray containers;
 * content is separated by hairline dividers instead of stacked cards.
 */

/** 1dp hairline divider. [startIndent] aligns it under list-row text. */
@Composable
fun ToolboxDivider(modifier: Modifier = Modifier, startIndent: Dp = 34.dp) {
    HorizontalDivider(
        modifier = modifier.padding(start = startIndent),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** Section title (SemiBold, onSurface) with an optional trailing action. */
@Composable
fun ToolSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        trailing()
    }
}

/** Rounded light-gray icon container reserved for tool grids. */
@Composable
fun ToolIcon(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    size: Dp = ToolboxDimens.gridIconSize,
    iconSize: Dp = 24.dp,
) {
    Box(
        modifier = modifier.size(size).background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(iconSize), tint = MaterialTheme.colorScheme.onSurface)
    }
}

/** Grid tile: icon container + centered name. Used by home / tool library grids. */
@Composable
fun ToolGridTile(
    name: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ToolIcon(icon)
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Minimal list row: bare line icon + [title]/[subtitle] + trailing slot, closed by
 * a hairline divider. Icons stay unboxed to reduce the
 * gray-container treatment; the gray container is reserved for tool grid tiles.
 */
@Composable
fun ToolListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    dividerIndent: Dp = 34.dp,
    trailing: @Composable () -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ToolboxDimens.touchTarget)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing()
        }
        if (showDivider) ToolboxDivider(startIndent = dividerIndent)
    }
}

/** Settings value stays inline when it fits, below the label on narrow/large-font screens. */
@Composable
fun ToolSettingItem(
    title: String,
    value: String?,
    @DrawableRes icon: Int,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    valueColor: Color? = null,
    showDivider: Boolean = true,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val stacked = maxWidth < 320.dp || fontScale > 1.3f
        ToolListItem(
            title = title,
            subtitle = value.takeIf { stacked },
            icon = icon,
            onClick = onClick.takeIf { enabled && !loading },
            showDivider = showDivider,
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.widthIn(max = maxWidth * 0.42f),
                ) {
                    if (!stacked && valueColor != null) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(valueColor))
                    }
                    if (!stacked && value != null) Text(
                        value,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (loading) ToolboxLoadingIndicator(compact = true)
                    else if (onClick != null) ToolboxChevron()
                }
            },
        )
    }
}

/** Trailing chevron for navigable list rows. */
@Composable
fun ToolboxChevron(modifier: Modifier = Modifier) {
    Icon(
        painterResource(com.cangshuo.toolbox.R.drawable.ic_chevron),
        contentDescription = null,
        modifier = modifier.size(18.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Flat grouped container (white surface, 1dp border, 16dp radius). Only for real
 * groupings such as a settings block.
 */
@Composable
fun ToolGroupCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            }
            content()
        }
    }
}

/** Top app bar: leading icon (menu/back), title, trailing actions. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ToolboxTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = navigationIcon,
        actions = actions,
        expandedHeight = ToolboxDimens.topBarHeight,
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** Native 48dp target around a compact unboxed glyph. */
@Composable
fun ToolboxIconButton(
    @DrawableRes icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier.size(ToolboxDimens.touchTarget)) {
        Icon(painterResource(icon), contentDescription = contentDescription, modifier = Modifier.size(ToolboxDimens.iconSize), tint = MaterialTheme.colorScheme.onSurface)
    }
}

/** Small muted caption used under titles (e.g. page subtitles). */
@Composable
fun ToolboxCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
