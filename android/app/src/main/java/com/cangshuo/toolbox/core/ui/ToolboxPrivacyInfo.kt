package com.cangshuo.toolbox.core.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cangshuo.toolbox.R

@Composable
fun ToolboxPrivacyInfo() {
    var visible by remember { mutableStateOf(false) }
    TextButton(onClick = { visible = true }) { Text(stringResource(R.string.privacy_info_title)) }
    if (visible) AlertDialog(onDismissRequest = { visible = false },
        title = { Text(stringResource(R.string.privacy_info_title)) },
        text = { Text(stringResource(R.string.privacy_info_body), Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { visible = false }) { Text(stringResource(R.string.permission_info_close)) } })
}
