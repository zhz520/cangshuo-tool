package com.cangshuo.toolbox.feature.webtools.data

import android.content.Context
import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.cangshuo.toolbox.feature.webtools.domain.WebToolExport
import com.cangshuo.toolbox.feature.webtools.domain.WebToolExportException
import com.cangshuo.toolbox.feature.webtools.domain.WebToolExportRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class AndroidWebToolExportRepository(context: Context) : WebToolExportRepository {
    private val resolver = context.applicationContext.contentResolver

    override suspend fun save(destination: String, export: WebToolExport) = withContext(Dispatchers.IO) {
        var complete = false
        try {
            ensureActive()
            resolver.openOutputStream(destination.toUri(), "w")?.use { output ->
                var offset = 0
                while (offset < export.bytes.size) {
                    ensureActive()
                    val count = minOf(8192, export.bytes.size - offset)
                    output.write(export.bytes, offset, count)
                    offset += count
                }
                output.flush()
            } ?: throw WebToolExportException()
            ensureActive()
            complete = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw WebToolExportException()
        } finally {
            if (!complete) withContext(NonCancellable + Dispatchers.IO) {
                try { DocumentsContract.deleteDocument(resolver, destination.toUri()) } catch (_: Exception) { }
            }
        }
    }
}
