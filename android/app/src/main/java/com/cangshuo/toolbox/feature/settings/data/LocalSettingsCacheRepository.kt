package com.cangshuo.toolbox.feature.settings.data

import android.content.Context
import com.cangshuo.toolbox.feature.settings.domain.SettingsCacheRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.IOException

/** Clear only private temporary files; never traverse links outside cacheDir. */
class LocalSettingsCacheRepository(context: Context) : SettingsCacheRepository {
    private val root = context.cacheDir.canonicalFile
    private val rootPath = root.path + java.io.File.separator
    private fun inside(file: java.io.File) = !java.nio.file.Files.isSymbolicLink(file.toPath()) && file.canonicalPath.startsWith(rootPath)

    override suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        var total = 0L
        root.walkTopDown().onEnter { it == root || inside(it) }.forEach { file ->
            coroutineContext.ensureActive()
            if (file.isFile && inside(file)) total += file.length()
        }
        total
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        root.listFiles().orEmpty().forEach { file -> remove(file) }
    }

    private suspend fun remove(file: java.io.File) {
        coroutineContext.ensureActive()
        if (!inside(file)) return
        file.listFiles()?.forEach { remove(it) }
        if (file.exists() && !file.delete()) throw IOException("Cache could not be cleared")
    }
}
