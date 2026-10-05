package com.cangshuo.toolbox.core.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.webkit.*
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cangshuo.toolbox.BuildConfig
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.feature.webtools.data.AndroidWebToolExportRepository
import com.cangshuo.toolbox.feature.webtools.domain.SaveWebToolExportUseCase
import com.cangshuo.toolbox.feature.webtools.domain.WebToolExport
import com.cangshuo.toolbox.feature.webtools.domain.WebToolNavigationPolicy
import com.cangshuo.toolbox.feature.webtools.ui.WebExportMessage
import com.cangshuo.toolbox.feature.webtools.ui.WebToolExportViewModel
import java.io.ByteArrayInputStream

object ToolboxWebTools {
    val baseUrl: String get() = BuildConfig.WEB_TOOL_BASE_URL
    fun urlFor(code: String): String = baseUrl.trimEnd('/') + "/tools/" + code + "/"
    fun allowedHosts(): Set<String> = setOfNotNull(baseUrl.toUri().host?.lowercase()?.takeIf { it.isNotEmpty() })
}

private enum class WebToolState { Loading, Ready, NetworkError, BlockedHost }

/** Official-origin container; no file/content access, mixed content or addJavascriptInterface. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ToolboxWebScreen(title: String, code: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val url = remember(code) { ToolboxWebTools.urlFor(code) }
    val policy = remember { WebToolNavigationPolicy(ToolboxWebTools.baseUrl) }
    val host = remember(url) { url.toUri().host.orEmpty() }
    var state by remember(url) { mutableStateOf(WebToolState.Loading) }
    var canGoBack by remember(url) { mutableStateOf(false) }
    var generation by remember(url) { mutableIntStateOf(0) }
    var rendererFailed by remember(url) { mutableStateOf(false) }
    val activeView = remember { mutableStateOf<WebView?>(null) }
    val exportFactory = remember(context) {
        WebToolExportViewModel.factory(SaveWebToolExportUseCase(AndroidWebToolExportRepository(context)))
    }
    val exports: WebToolExportViewModel = viewModel(key = "web.export.$code", factory = exportFactory)
    val exportState by exports.state.collectAsStateWithLifecycle()
    val saveLauncher = rememberLauncherForActivityResult(CreateWebToolDocument()) { exports.complete(it?.toString()) }
    LaunchedEffect(exportState.pending, exportState.pickerRequested) {
        if (!exportState.pickerRequested) exportState.pending?.let {
            exports.markPickerRequested()
            saveLauncher.launch(it)
        }
    }
    val exportMessage = stringResource(if (exportState.message == WebExportMessage.SAVED) R.string.web_export_saved else R.string.web_export_failed)
    LaunchedEffect(exportState.message) {
        if (exportState.message != WebExportMessage.NONE) {
            Toast.makeText(context, exportMessage, Toast.LENGTH_SHORT).show()
            exports.acknowledgeMessage()
        }
    }
    val browser = remember(url, generation) {
        try { WebView(context) } catch (_: RuntimeException) { null }
    }
    BackHandler { if (canGoBack) activeView.value?.goBack() ?: onClose() else onClose() }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.web_btn_close)) }
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Text(stringResource(R.string.web_notice, host), Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (browser != null && !rendererFailed) key(browser) {
                AndroidView(
                    factory = {
                        browser.apply {
                            settings.javaScriptEnabled = true
                            settings.cacheMode = WebSettings.LOAD_NO_CACHE
                            settings.userAgentString += " CangshuoToolboxWebView/1"
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            fun handleExport(target: String): Boolean {
                                if (!target.startsWith("data:")) return false
                                if (policy.allows(this.url.orEmpty())) exports.request(target)
                                return true
                            }
                            setDownloadListener { target, _, _, _, _ -> handleExport(target) }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    if (handleExport(request.url.toString())) return true
                                    if (policy.allows(request.url.toString())) return false
                                    state = WebToolState.BlockedHost
                                    return true
                                }
                                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                    if (policy.allows(request.url.toString())) return null
                                    if (request.isForMainFrame) view.post { state = WebToolState.BlockedHost }
                                    return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(byteArrayOf()))
                                }
                                override fun onPageStarted(view: WebView, loadedUrl: String, favicon: android.graphics.Bitmap?) {
                                    if (policy.allows(loadedUrl)) state = WebToolState.Loading
                                }
                                override fun doUpdateVisitedHistory(view: WebView, loadedUrl: String, isReload: Boolean) {
                                    canGoBack = view.canGoBack()
                                }
                                override fun onPageFinished(view: WebView, loadedUrl: String) {
                                    canGoBack = view.canGoBack()
                                    if (state == WebToolState.Loading) state = WebToolState.Ready
                                }
                                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                    if (request.isForMainFrame) state = WebToolState.NetworkError
                                }
                                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                                    if (request.isForMainFrame) state = WebToolState.NetworkError
                                }
                                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                                    handler.cancel()
                                    state = WebToolState.NetworkError
                                }
                                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                    rendererFailed = true
                                    state = WebToolState.NetworkError
                                    return true
                                }
                            }
                            activeView.value = this
                            loadUrl(url)
                        }
                    },
                    onRelease = { view ->
                        if (activeView.value === view) activeView.value = null
                        view.stopLoading()
                        view.webViewClient = WebViewClient()
                        view.destroy()
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            when (val current = if (browser == null) WebToolState.NetworkError else state) {
                WebToolState.Loading -> ToolboxLoadingState(stringResource(R.string.web_loading))
                WebToolState.Ready -> Unit
                WebToolState.NetworkError, WebToolState.BlockedHost -> WebToolMessage(
                    stringResource(if (current == WebToolState.BlockedHost) R.string.web_error_blocked else R.string.web_error_network),
                    onRetry = {
                        state = WebToolState.Loading
                        canGoBack = false
                        if (browser == null || rendererFailed) {
                            rendererFailed = false
                            generation += 1
                        } else {
                            browser.stopLoading()
                            browser.loadUrl(url)
                        }
                    }, onClose = onClose,
                )
            }
        }
    }
}

@Composable
private fun WebToolMessage(message: String, onRetry: () -> Unit, onClose: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.web_btn_retry)) }
                TextButton(onClick = onClose) { Text(stringResource(R.string.web_btn_close)) }
            }
        }
    }
}

private class CreateWebToolDocument : ActivityResultContract<WebToolExport, Uri?>() {
    override fun createIntent(context: Context, input: WebToolExport): Intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE).setType(input.mimeType).putExtra(Intent.EXTRA_TITLE, input.fileName)
    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = if (resultCode == Activity.RESULT_OK) intent?.data else null
}
