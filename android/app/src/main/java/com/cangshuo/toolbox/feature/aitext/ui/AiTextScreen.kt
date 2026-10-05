package com.cangshuo.toolbox.feature.aitext.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolboxLoadingIndicator
import com.cangshuo.toolbox.feature.aitext.domain.AiFailure

@Composable
fun AiTextRoute(factory: ViewModelProvider.Factory) {
    val model: AiTextViewModel=viewModel(factory=factory)
    val state by model.state.collectAsStateWithLifecycle()
    val clipboard=LocalClipboardManager.current
    val context=LocalContext.current
    var shareFailed by remember{mutableStateOf(false)}
    val shareTitle=stringResource(R.string.ai_share)
    DisposableEffect(model){onDispose{model.cancel()}}
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(state.user==null)Text(stringResource(R.string.auth_expired))
        state.status?.let{status->
            Text(if(status.enabled)stringResource(R.string.ai_provider,status.providerName,status.model) else stringResource(R.string.ai_disabled))
            if(status.enabled)Text(stringResource(R.string.ai_usage,status.usedToday,status.dailyLimit))
        }
        TextButton(onClick=model::refresh,enabled=!state.busy){Text(stringResource(R.string.ai_refresh))}
        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            listOf("SUMMARIZE" to R.string.ai_summarize,"REWRITE" to R.string.ai_rewrite,"TRANSLATE" to R.string.ai_translate).forEach{(value,label)->
                FilterChip(selected=state.task==value,onClick={model.task(value)},enabled=!state.busy,label={Text(stringResource(label))})
            }
        }
        if(state.task=="TRANSLATE")Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            listOf("zh" to R.string.ai_chinese,"en" to R.string.ai_english).forEach{(value,label)->
                FilterChip(selected=state.target==value,onClick={model.target(value)},enabled=!state.busy,label={Text(stringResource(label))})
            }
        }
        OutlinedTextField(state.input,model::input,enabled=!state.busy,label={Text(stringResource(R.string.ai_input))},minLines=5,modifier=Modifier.fillMaxWidth(),supportingText={Text("${state.input.length}/8000")})
        Row{Checkbox(state.consent,model::consent,enabled=!state.busy);Text(stringResource(R.string.ai_consent,state.status?.providerName.orEmpty()),Modifier.weight(1f))}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Button(onClick=model::execute,enabled=!state.busy&&state.consent&&state.status?.enabled==true&&state.input.isNotBlank()){Text(stringResource(R.string.ai_run))}
            if(state.busy){ToolboxLoadingIndicator(compact=true);TextButton(onClick=model::cancel){Text(stringResource(R.string.action_cancel))}}
        }
        state.failure?.let{error->Text(stringResource(when(error){
            AiFailure.INPUT->R.string.ai_invalid;AiFailure.LOGIN->R.string.auth_expired;AiFailure.DISABLED->R.string.ai_unavailable
            AiFailure.QUOTA->R.string.ai_quota;AiFailure.LIMITED->R.string.feedback_limited;AiFailure.NETWORK->R.string.ai_network
            AiFailure.SERVICE->R.string.ai_service
        }),color=MaterialTheme.colorScheme.error)}
        state.result?.let{result->
            if(result.truncated)Text(stringResource(R.string.ai_truncated),color=MaterialTheme.colorScheme.error)
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLowest)) {Text(result.text,Modifier.fillMaxWidth().padding(16.dp))}
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                TextButton(onClick={clipboard.setText(AnnotatedString(result.text))}){Text(stringResource(R.string.ai_copy))}
                TextButton(onClick={try{context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,result.text),shareTitle))}
                    catch(_: RuntimeException){shareFailed=true}}){Text(shareTitle)}
            }
        }
        if(shareFailed)Text(stringResource(R.string.ai_share_failed),color=MaterialTheme.colorScheme.error)
    }
}
