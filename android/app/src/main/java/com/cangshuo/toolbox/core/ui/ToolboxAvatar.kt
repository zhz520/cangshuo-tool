package com.cangshuo.toolbox.core.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.ui.theme.ToolboxDimens

@Composable
fun ToolboxAvatar(bitmap: Bitmap?, loading: Boolean = false, size: Dp = ToolboxDimens.avatarSize) {
    Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center) {
        when {
            bitmap != null -> Image(bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(size))
            loading -> ToolboxLoadingIndicator(compact = true)
            else -> Icon(painterResource(R.drawable.ic_person), null, Modifier.size(size / 2),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
