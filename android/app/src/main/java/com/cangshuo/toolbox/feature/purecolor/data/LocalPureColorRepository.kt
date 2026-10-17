package com.cangshuo.toolbox.feature.purecolor.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import com.cangshuo.toolbox.feature.purecolor.domain.PureColorError
import com.cangshuo.toolbox.feature.purecolor.domain.PureColorException
import com.cangshuo.toolbox.feature.purecolor.domain.PureColorLimits
import com.cangshuo.toolbox.feature.purecolor.domain.PureColorParams
import com.cangshuo.toolbox.feature.purecolor.domain.PureColorRepository
import com.cangshuo.toolbox.feature.purecolor.domain.pureColorFileName
import com.cangshuo.toolbox.feature.purecolor.domain.validatePureColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream

class LocalPureColorRepository(private val context: Context) : PureColorRepository {
    private val nativeWork = Mutex()
    private val shareDirectory = File(context.cacheDir, "solid_color_share")

    override suspend fun save(params: PureColorParams, destination: Uri?): Result<Uri> = operation {
        validatePureColor(params, Runtime.getRuntime().maxMemory())
        val resolver = context.contentResolver
        val isDocument = destination != null
        val target = destination ?: run {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) throw PureColorException(PureColorError.SAVE_FAILED)
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, pureColorFileName(params))
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CangshuoToolbox")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                ?: throw PureColorException(PureColorError.SAVE_FAILED)
        }
        var committed = false
        try {
            val stream = resolver.openOutputStream(target, "wt")
                ?: throw PureColorException(PureColorError.SAVE_FAILED)
            stream.use { writePng(params, it) }
            if (!isDocument) {
                val values = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                if (resolver.update(target, values, null, null) != 1) throw PureColorException(PureColorError.SAVE_FAILED)
            }
            committed = true
            target
        } finally {
            if (!committed) withContext(NonCancellable + Dispatchers.IO) {
                try { resolver.delete(target, null, null) } catch (_: Exception) { }
            }
        }
    }

    override suspend fun prepareShare(params: PureColorParams): Result<Uri> = operation {
        validatePureColor(params, Runtime.getRuntime().maxMemory())
        shareDirectory.mkdirs()
        shareDirectory.listFiles()?.forEach { file ->
            if (System.currentTimeMillis() - file.lastModified() > 24L * 60 * 60 * 1000) file.delete()
        }
        val file = File(shareDirectory, pureColorFileName(params))
        var delivered = false
        try {
            file.outputStream().buffered().use { writePng(params, it) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            delivered = true
            uri
        } finally {
            if (!delivered) file.delete()
        }
    }

    private suspend fun writePng(params: PureColorParams, output: OutputStream) {
        validatePureColor(params, Runtime.getRuntime().maxMemory())
        val bitmap = createBitmap(params.width, params.height, Bitmap.Config.ARGB_8888)
        try {
            Canvas(bitmap).drawColor(params.argb)
            currentCoroutineContext().ensureActive()
            val limited = LimitedOutputStream(output)
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, limited)) {
                throw PureColorException(PureColorError.SAVE_FAILED)
            }
            currentCoroutineContext().ensureActive()
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun <T> operation(block: suspend () -> T): Result<T> = try {
        Result.success(withContext(Dispatchers.IO) {
            nativeWork.withLock { currentCoroutineContext().ensureActive(); block() }
        })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: PureColorException) {
        Result.failure(error)
    } catch (_: OutOfMemoryError) {
        Result.failure(PureColorException(PureColorError.MEMORY_LIMIT))
    } catch (_: Exception) {
        Result.failure(PureColorException(PureColorError.SAVE_FAILED))
    }

    private class LimitedOutputStream(output: OutputStream) : FilterOutputStream(output) {
        private var count = 0L
        override fun write(b: Int) {
            if (++count > PureColorLimits.MAX_FILE_BYTES) throw PureColorException(PureColorError.TOO_LARGE)
            out.write(b)
        }
        override fun write(b: ByteArray, off: Int, len: Int) {
            if (count + len > PureColorLimits.MAX_FILE_BYTES) throw PureColorException(PureColorError.TOO_LARGE)
            count += len
            out.write(b, off, len)
        }
    }
}
