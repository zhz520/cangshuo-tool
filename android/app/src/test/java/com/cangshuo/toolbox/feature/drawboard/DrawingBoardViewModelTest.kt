package com.cangshuo.toolbox.feature.drawboard

import androidx.lifecycle.SavedStateHandle
import com.cangshuo.toolbox.feature.drawboard.domain.DrawPoint
import com.cangshuo.toolbox.feature.drawboard.domain.DrawingBoardError
import com.cangshuo.toolbox.feature.drawboard.domain.DrawingBoardException
import com.cangshuo.toolbox.feature.drawboard.domain.DrawingBoardPolicy
import com.cangshuo.toolbox.feature.drawboard.domain.DrawingBoardRepository
import com.cangshuo.toolbox.feature.drawboard.domain.DrawingDocument
import com.cangshuo.toolbox.feature.drawboard.ui.DrawingBoardMessage
import com.cangshuo.toolbox.feature.drawboard.ui.DrawingBoardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DrawingBoardViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeRepository : DrawingBoardRepository {
        var saved: DrawingDocument? = null
        var shared: DrawingDocument? = null
        var fail: DrawingBoardError? = null
        var wait = 0L
        override suspend fun save(document: DrawingDocument, destination: String?): Result<String> {
            delay(wait)
            saved = document
            return fail?.let { Result.failure(DrawingBoardException(it)) } ?: Result.success("content://saved")
        }
        override suspend fun prepareShare(document: DrawingDocument): Result<String> {
            delay(wait)
            shared = document
            return fail?.let { Result.failure(DrawingBoardException(it)) } ?: Result.success("content://drawing")
        }
    }

    private fun model(repo: FakeRepository = FakeRepository(), saved: SavedStateHandle = SavedStateHandle()) =
        DrawingBoardViewModel(repo, saved)

    private val points = listOf(DrawPoint(0.1f, 0.1f), DrawPoint(0.7f, 0.6f))

    @Before fun before() = Dispatchers.setMain(dispatcher)
    @After fun after() = Dispatchers.resetMain()

    @Test fun addUndoRedoAndClearKeepTheExpectedDocument() = runTest {
        val model = model()
        assertTrue(model.addStroke(points, 0.02f, 0xFF000000.toInt(), 0.04f))
        assertEquals(1, model.state.value.document.strokes.size)
        assertTrue(model.state.value.canUndo)
        model.undo(); assertTrue(model.state.value.document.strokes.isEmpty()); assertTrue(model.state.value.canRedo)
        model.redo(); assertEquals(1, model.state.value.document.strokes.size)
        model.clear(); assertTrue(model.state.value.document.strokes.isEmpty()); assertTrue(model.state.value.canUndo)
        model.undo(); assertEquals(1, model.state.value.document.strokes.size)
    }

    @Test fun brushEraserAndSizeSettingsPersist() = runTest {
        val saved = SavedStateHandle()
        val model = model(saved = saved)
        model.setColor(0xFF123456.toInt())
        model.setBrushSize(99)
        model.setEraser(true)
        model.addStroke(points, 0.02f, DrawingBoardPolicy.WHITE, 0.04f)
        assertEquals(DrawingBoardPolicy.MAX_BRUSH_DP, saved.get<Int>("drawWidth"))
        assertEquals(DrawingBoardPolicy.WHITE, model.state.value.document.strokes.single().argb)
        assertEquals(true, saved.get<Boolean>("drawEraser"))
        model.setColor(0xFFABCDEF.toInt())
        assertFalse(model.state.value.eraser)
    }

    @Test fun strokeAndPointBudgetsStopFurtherDraws() = runTest {
        val model = model()
        repeat(DrawingBoardPolicy.MAX_STROKES) { assertTrue(model.addStroke(points, 0.02f, 0xFF000000.toInt(), 0.04f)) }
        assertFalse(model.addStroke(points, 0.02f, 0xFF000000.toInt(), 0.04f))
        assertEquals(DrawingBoardMessage.TOO_LARGE, model.state.value.message)
        assertEquals(DrawingBoardPolicy.MAX_STROKES, model.state.value.document.strokes.size)
    }

    @Test fun documentRestoresFromSavedStateWithoutUndoHistory() = runTest {
        val saved = SavedStateHandle()
        val first = model(saved = saved)
        first.addStroke(points, 0.02f, 0xFF000000.toInt(), 0.04f)
        first.setColor(0xFF445566.toInt())
        val second = DrawingBoardViewModel(FakeRepository(), saved)
        assertEquals(1, second.state.value.document.strokes.size)
        assertEquals(2, second.state.value.document.strokes.single().points.size)
        assertEquals(0xFF445566.toInt(), second.state.value.selectedArgb)
        assertEquals(0.04f, second.state.value.document.clipRadiusFraction, 0.001f)
        assertFalse(second.state.value.canUndo)
    }

    @Test fun saveAndSharePublishBoundedRequests() = runTest {
        val repo = FakeRepository()
        val model = model(repo)
        model.addStroke(points, 0.02f, 0xFF000000.toInt(), 0.04f)
        model.save(null)
        assertTrue(model.state.value.saving)
        advanceUntilIdle()
        assertEquals(DrawingBoardMessage.SAVED, model.state.value.message)
        assertEquals(1, repo.saved?.strokes?.size)
        model.consumeMessage()
        model.share()
        advanceUntilIdle()
        assertEquals("content://drawing", model.state.value.shareRequest)
        assertEquals(1, repo.shared?.strokes?.size)
        model.shareHandled(true)
        assertEquals(null, model.state.value.shareRequest)
    }

    @Test fun saveFailureIsReportedAndBusyStateClears() = runTest {
        val repo = FakeRepository().apply { fail = DrawingBoardError.SAVE_FAILED }
        val model = model(repo)
        model.addStroke(points, 0.02f, 0xFF000000.toInt(), 0.04f)
        model.save(null)
        advanceUntilIdle()
        assertFalse(model.state.value.busy)
        assertEquals(DrawingBoardMessage.SAVE_FAILED, model.state.value.message)
    }

    @Test fun redoIsDiscardedAfterANewStrokeAndTotalPointBudgetIsEnforced() = runTest {
        val model = model()
        val full = List(DrawingBoardPolicy.MAX_POINTS_PER_STROKE) { DrawPoint(0.5f, 0.5f) }
        repeat(DrawingBoardPolicy.MAX_TOTAL_POINTS / full.size) {
            assertTrue(model.addStroke(full, 0.01f, 0xFF000000.toInt(), 0.04f))
        }
        assertFalse(model.addStroke(points, 0.01f, 0xFF000000.toInt(), 0.04f))
        assertEquals(DrawingBoardMessage.TOO_LARGE, model.state.value.message)
        model.undo()
        assertTrue(model.state.value.canRedo)
        assertTrue(model.addStroke(points, 0.01f, 0xFF000000.toInt(), 0.04f))
        assertFalse(model.state.value.canRedo)
    }

    @Test fun corruptSavedDrawingFallsBackEmptyAndGridModePersists() = runTest {
        val saved = SavedStateHandle(mapOf("drawDocument" to "broken|state"))
        val model = model(saved = saved)
        assertTrue(model.state.value.document.strokes.isEmpty())
        model.setGridMode(true)
        assertEquals(true, saved.get<Boolean>("drawGrid"))
        assertTrue(model.state.value.gridMode)
    }

    @Test fun leavingThePageCancelsPendingShareAndClearsOneShotState() = runTest {
        val repo = FakeRepository().apply { wait = 1_000L }
        val model = model(repo)
        model.addStroke(points, 0.02f, 0xFF000000.toInt(), 0.04f)
        model.share()
        assertTrue(model.state.value.sharing)
        model.onLeavePage()
        advanceUntilIdle()
        assertFalse(model.state.value.busy)
        assertEquals(null, model.state.value.shareRequest)
        assertEquals(1, model.state.value.document.strokes.size)
    }
}
