package com.cangshuo.toolbox.feature.drawboard.ui

import android.content.ClipData
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.core.net.toUri
import com.cangshuo.toolbox.R
import com.cangshuo.toolbox.core.ui.ToolGroupCard
import com.cangshuo.toolbox.core.ui.ToolSectionHeader
import com.cangshuo.toolbox.core.ui.ToolboxButton
import com.cangshuo.toolbox.core.ui.ToolboxLoadingIndicator
import com.cangshuo.toolbox.core.ui.ToolboxOutlinedButton
import com.cangshuo.toolbox.core.ui.ToolboxSwitch
import com.cangshuo.toolbox.core.ui.ToolboxTextButton
import com.cangshuo.toolbox.feature.drawboard.domain.DrawPoint
import com.cangshuo.toolbox.feature.drawboard.domain.DrawStroke
import com.cangshuo.toolbox.feature.drawboard.domain.DrawingBoardPolicy
import com.cangshuo.toolbox.feature.drawboard.domain.DrawingDocument
import com.cangshuo.toolbox.ui.theme.ToolboxDimens
import kotlin.math.roundToInt
import kotlin.math.sqrt

private data class BrushColor(val argb: Int, val name: Int)

private val brushColors = listOf(
    BrushColor(0xFF111827.toInt(), R.string.drawing_board_color_ink),
    BrushColor(0xFF2563EB.toInt(), R.string.drawing_board_color_blue),
    BrushColor(0xFF16A34A.toInt(), R.string.drawing_board_color_green),
    BrushColor(0xFFE11D48.toInt(), R.string.drawing_board_color_red),
    BrushColor(0xFFF59E0B.toInt(), R.string.drawing_board_color_orange),
    BrushColor(0xFF7C3AED.toInt(), R.string.drawing_board_color_violet),
    BrushColor(0xFFDB2777.toInt(), R.string.drawing_board_color_pink),
    BrushColor(0xFF0F766E.toInt(), R.string.drawing_board_color_teal),
)

@Composable
fun DrawingBoardScreen(
    state: DrawingBoardUiState,
    onStroke: (List<DrawPoint>, Float, Int, Float) -> Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    onColor: (Int) -> Unit,
    onEraser: (Boolean) -> Unit,
    onGridMode: (Boolean) -> Unit,
    onBrushSize: (Int) -> Unit,
    onSave: (String?) -> Unit,
    onShare: () -> Unit,
    onShareHandled: (Boolean) -> Unit,
    onMessageConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val shareTitle = stringResource(R.string.drawing_board_share_title)
    val saved = stringResource(R.string.drawing_board_saved)
    val saveFailed = stringResource(R.string.drawing_board_save_failed)
    val shareFailed = stringResource(R.string.drawing_board_share_failed)
    val tooLarge = stringResource(R.string.drawing_board_too_large)
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) onSave(uri.toString())
    }
    val save = {
        if (!state.busy && state.hasContent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) onSave(null)
            else saver.launch(DrawingBoardPolicy.fileName())
        }
    }
    LaunchedEffect(state.shareRequest) {
        val value = state.shareRequest ?: return@LaunchedEffect
        val success = try {
            val uri = value.toUri()
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(shareTitle, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, shareTitle).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            true
        } catch (_: Exception) { false }
        onShareHandled(success)
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(when (message) {
            DrawingBoardMessage.SAVED -> saved
            DrawingBoardMessage.SAVE_FAILED -> saveFailed
            DrawingBoardMessage.SHARE_FAILED -> shareFailed
            DrawingBoardMessage.TOO_LARGE -> tooLarge
        })
        onMessageConsumed()
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = ToolboxDimens.formMaxWidth).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(horizontal = ToolboxDimens.pageGutter, vertical = ToolboxDimens.pageTop),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("canvas") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolSectionHeader(stringResource(R.string.drawing_board_canvas_title))
                    DrawingCanvas(
                        state = state,
                        onStroke = onStroke,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ToolboxOutlinedButton(onClick = onUndo, enabled = state.canUndo && !state.busy) {
                            Text(stringResource(R.string.drawing_board_undo))
                        }
                        ToolboxOutlinedButton(onClick = onRedo, enabled = state.canRedo && !state.busy) {
                            Text(stringResource(R.string.drawing_board_redo))
                        }
                        FilterChip(
                            selected = state.eraser,
                            onClick = { onEraser(!state.eraser) },
                            enabled = !state.busy,
                            label = { Text(stringResource(R.string.drawing_board_eraser)) },
                        )
                        ToolboxTextButton(onClick = onClear, enabled = state.hasContent && !state.busy) {
                            Text(
                                stringResource(R.string.drawing_board_clear),
                                color = if (state.hasContent && !state.busy) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item("brush") {
                ToolGroupCard(title = stringResource(R.string.drawing_board_brush_title)) {
                    Text(stringResource(R.string.drawing_board_colors), style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        brushColors.forEach { brush ->
                            val name = stringResource(brush.name)
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .semantics {
                                        contentDescription = name
                                        this.selected = !state.eraser && state.selectedArgb == brush.argb
                                    }
                                    .clickable(enabled = !state.busy, role = Role.RadioButton) {
                                        onColor(brush.argb)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier
                                        .size(32.dp)
                                        .background(Color(brush.argb), CircleShape)
                                        .border(
                                            width = if (!state.eraser && state.selectedArgb == brush.argb) 3.dp else 1.dp,
                                            color = if (!state.eraser && state.selectedArgb == brush.argb)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant,
                                            shape = CircleShape,
                                        ),
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.drawing_board_brush_size), style = MaterialTheme.typography.labelLarge)
                        Text(
                            stringResource(R.string.drawing_board_brush_value, state.brushSizeDp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val brushAccessibility = stringResource(R.string.drawing_board_brush_size)
                    val brushState = stringResource(R.string.drawing_board_brush_accessibility, state.brushSizeDp)
                    Slider(
                        value = state.brushSizeDp.toFloat(),
                        onValueChange = { onBrushSize(it.roundToInt()) },
                        valueRange = DrawingBoardPolicy.MIN_BRUSH_DP.toFloat()..DrawingBoardPolicy.MAX_BRUSH_DP.toFloat(),
                        steps = DrawingBoardPolicy.MAX_BRUSH_DP - DrawingBoardPolicy.MIN_BRUSH_DP - 1,
                        enabled = !state.busy,
                        modifier = Modifier.semantics {
                            contentDescription = brushAccessibility
                            stateDescription = brushState
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(
                            enabled = !state.busy,
                            role = Role.Switch,
                        ) { onGridMode(!state.gridMode) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(R.string.drawing_board_grid_mode), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.drawing_board_grid_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ToolboxSwitch(checked = state.gridMode, onCheckedChange = null, enabled = !state.busy)
                    }
                }
            }
            item("export") {
                ToolGroupCard(title = stringResource(R.string.drawing_board_export_title)) {
                    Text(
                        stringResource(R.string.drawing_board_export_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ToolboxButton(onClick = save, enabled = state.hasContent && !state.busy) {
                            Text(stringResource(if (state.saving) R.string.drawing_board_saving else R.string.drawing_board_save))
                        }
                        ToolboxOutlinedButton(onClick = onShare, enabled = state.hasContent && !state.busy) {
                            Text(stringResource(if (state.sharing) R.string.drawing_board_sharing else R.string.drawing_board_share))
                        }
                    }
                    if (state.busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ToolboxLoadingIndicator(compact = true)
                        Text(stringResource(R.string.drawing_board_working), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        stringResource(R.string.drawing_board_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).imePadding())
    }
}

@Composable
private fun DrawingCanvas(
    state: DrawingBoardUiState,
    onStroke: (List<DrawPoint>, Float, Int, Float) -> Boolean,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val current = remember { mutableStateListOf<DrawPoint>() }
    var currentArgb by remember { mutableIntStateOf(DrawingBoardPolicy.WHITE) }
    var currentWidthFraction by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val enabled = !state.busy && !state.gridMode
    val widthFraction = with(density) {
        if (canvasSize.width <= 0) 0f else state.brushSizeDp.dp.toPx() / canvasSize.width
    }
    val clipRadiusFraction = with(density) {
        if (canvasSize.width <= 0) 0f else 16.dp.toPx() / canvasSize.width
    }
    val hint = stringResource(R.string.drawing_board_empty_hint)
    val canvasDescription = stringResource(R.string.drawing_board_accessibility, state.document.strokes.size)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val eraserNow by rememberUpdatedState(state.eraser)
    val selectedArgbNow by rememberUpdatedState(state.selectedArgb)
    val widthNow by rememberUpdatedState(widthFraction)
    val clipRadiusNow by rememberUpdatedState(clipRadiusFraction)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = canvasDescription }
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(enabled) {
                        if (!enabled) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            val strokeWidth = widthNow
                            if (strokeWidth <= 0f) return@awaitEachGesture
                            val strokeArgb = if (eraserNow) DrawingBoardPolicy.WHITE else selectedArgbNow
                            currentArgb = strokeArgb
                            currentWidthFraction = strokeWidth
                            current.clear()
                            current.add(normalize(down.position, size.width, size.height))
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (change.pressed) {
                                        change.consume()
                                        val point = normalize(change.position, size.width, size.height)
                                        val previous = current.lastOrNull()
                                        if (previous == null || distance(previous, point) > 0.0015f) {
                                            if (current.size < DrawingBoardPolicy.MAX_POINTS_PER_STROKE) current.add(point)
                                        }
                                    } else {
                                        change.consume()
                                        break
                                    }
                                }
                            } finally {
                                if (current.isNotEmpty()) {
                                    onStroke(current.toList(), strokeWidth, strokeArgb, clipRadiusNow)
                                }
                                current.clear()
                            }
                        }
                    },
            ) {
                if (state.gridMode) {
                    val stroke = 1.dp.toPx()
                    for (index in 1 until 5) {
                        val x = size.width * index / 5f
                        val y = size.height * index / 5f
                        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), stroke)
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), stroke)
                    }
                }
                val allStrokes = state.document.strokes
                allStrokes.forEach { drawStroke(it) }
                if (current.isNotEmpty()) {
                    drawStroke(
                        DrawStroke(current.toList(), currentArgb, currentWidthFraction),
                    )
                }
            }
            if (state.gridMode) {
                Column(Modifier.fillMaxSize()) {
                    repeat(5) { row ->
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            repeat(5) { column ->
                                val label = stringResource(
                                    R.string.drawing_board_grid_cell,
                                    row + 1,
                                    column + 1,
                                )
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .semantics { contentDescription = label }
                                        .clickable(enabled = !state.busy, role = Role.Button) {
                                            val point = DrawPoint((column * 2 + 1) / 10f, (row * 2 + 1) / 10f)
                                            val argb = if (state.eraser) DrawingBoardPolicy.WHITE else state.selectedArgb
                                            onStroke(listOf(point), widthFraction, argb, clipRadiusFraction)
                                        },
                                )
                            }
                        }
                    }
                }
            }
            if (!state.hasContent && current.isEmpty()) {
                Text(
                    hint,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Black.copy(alpha = 0.45f),
                )
            }
        }
    }
}

private fun DrawScope.drawStroke(stroke: DrawStroke) {
    if (stroke.points.isEmpty()) return
    val points = stroke.points.map { Offset(it.x * size.width, it.y * size.height) }
    val width = (stroke.widthFraction * size.width).coerceAtLeast(1f)
    if (points.size == 1) {
        drawCircle(
            color = Color(stroke.argb),
            radius = width / 2f,
            center = points[0],
        )
        return
    }
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val current = points[index]
            val middle = Offset((previous.x + current.x) / 2f, (previous.y + current.y) / 2f)
            if (index == 1) lineTo(middle.x, middle.y) else quadraticTo(previous.x, previous.y, middle.x, middle.y)
        }
        val last = points.last()
        lineTo(last.x, last.y)
    }
    drawPath(
        path = path,
        color = Color(stroke.argb),
        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun normalize(offset: Offset, width: Int, height: Int): DrawPoint = DrawPoint(
    x = (offset.x / width.coerceAtLeast(1)).coerceIn(0f, 1f),
    y = (offset.y / height.coerceAtLeast(1)).coerceIn(0f, 1f),
)

private fun distance(first: DrawPoint, second: DrawPoint): Float =
    sqrt((first.x - second.x) * (first.x - second.x) + (first.y - second.y) * (first.y - second.y))
