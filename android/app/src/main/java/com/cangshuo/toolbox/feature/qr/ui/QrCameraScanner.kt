package com.cangshuo.toolbox.feature.qr.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.feature.qr.data.QrCameraAnalyzer
import com.cangshuo.toolbox.feature.qr.domain.QrDecodeEntry
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * CameraX preview with continuous ZXing analysis for the QR tool.
 *
 * The permission is requested only here, when the visitor opens the scanner; the rest of the tool
 * works without it. Binding stops as soon as the composable leaves, and the lifecycle owner keeps
 * the camera released while the app is in the background.
 */
@Composable
fun QrCameraScanner(
    /** Collect mode keeps the preview open and lets the visitor accumulate several payloads. */
    collect: Boolean,
    collected: Int,
    onDecoded: (QrDecodeEntry) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnDecoded by rememberUpdatedState(onDecoded)
    val hasCamera = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var bindAttempt by rememberSaveable { mutableIntStateOf(0) }
    var bindFailed by remember { mutableStateOf(false) }
    var torchAvailable by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var torchFailed by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    // Held for the lifetime of the scanner composable, so the analysis effect always targets the
    // view that is currently on screen instead of a released surface.
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
        requested = true
        if (result) bindFailed = false
    }

    LaunchedEffect(hasCamera) {
        if (hasCamera && !granted && !requested) {
            requested = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(lifecycleOwner, granted, hasCamera, bindAttempt, previewView) {
        if (!hasCamera || !granted) return@LaunchedEffect
        bindFailed = false
        val executor = Executors.newSingleThreadExecutor()
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(SCAN_RESOLUTION)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        var provider: ProcessCameraProvider? = null
        try {
            provider = awaitCameraProvider(context)
            analysis.setAnalyzer(executor, QrCameraAnalyzer(collect) { text -> currentOnDecoded(text) })
            provider.unbindAll()
            val camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
            torchAvailable = camera.cameraInfo.hasFlashUnit()
            cameraControl = camera.cameraControl
            awaitCancellation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            bindFailed = true
        } finally {
            cameraControl = null
            torchOn = false
            torchAvailable = false
            try {
                analysis.clearAnalyzer()
            } catch (_: Exception) {
                // The use case can already be unbound; nothing else to release here.
            }
            runCatching { provider?.unbindAll() }
            executor.shutdown()
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            !hasCamera -> {
                ScannerMessage(stringResource(R.string.qr_camera_unsupported))
                CloseButton(onClose, finish = false)
            }
            !granted -> {
                ScannerMessage(stringResource(R.string.qr_camera_permission_required))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text(stringResource(R.string.qr_camera_grant))
                    }
                    CloseButton(onClose, finish = false)
                }
            }
            bindFailed -> {
                ScannerMessage(stringResource(R.string.qr_camera_error_start))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { bindAttempt += 1 }) {
                        Text(stringResource(R.string.qr_camera_retry))
                    }
                    CloseButton(onClose, finish = false)
                }
            }
            else -> {
                val previewDescription = stringResource(R.string.qr_camera_preview_description)
                Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize().semantics {
                            contentDescription = previewDescription
                        },
                    )
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .fillMaxSize(0.72f)
                            .border(2.dp, MaterialTheme.colorScheme.onPrimary, MaterialTheme.shapes.medium),
                    )
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.78f),
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    ) {
                        Text(
                            stringResource(R.string.qr_camera_scanning_hint),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                if (collect) {
                    Text(stringResource(R.string.qr_camera_collected, collected),
                        style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.qr_camera_collect_hint),
                        style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (torchAvailable) {
                        TextButton(
                            onClick = {
                                val control = cameraControl ?: return@TextButton
                                val next = !torchOn
                                torchFailed = false
                                torchOn = next
                                val request = control.enableTorch(next)
                                request.addListener(
                                    {
                                        runCatching { request.get() }.onFailure {
                                            torchOn = !next
                                            torchFailed = true
                                        }
                                    },
                                    ContextCompat.getMainExecutor(context),
                                )
                            },
                        ) {
                            Text(stringResource(if (torchOn) R.string.qr_camera_torch_off else R.string.qr_camera_torch_on))
                        }
                    }
                    CloseButton(onClose, finish = collect)
                }
                if (torchFailed) ScannerMessage(stringResource(R.string.qr_camera_torch_error))
            }
        }
    }
}

@Composable
private fun ScannerMessage(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun CloseButton(onClose: () -> Unit, finish: Boolean) {
    TextButton(onClick = onClose) {
        Text(stringResource(if (finish) R.string.qr_btn_finish_camera else R.string.qr_btn_close_camera))
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private suspend fun awaitCameraProvider(context: Context): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                try {
                    continuation.resume(future.get())
                } catch (error: Exception) {
                    continuation.resumeWithException(error)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

private val SCAN_RESOLUTION: ResolutionSelector = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
    .setResolutionStrategy(
        ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
    )
    .build()
