package com.cangshuo.toolbox.feature.settings.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import com.cangshuo.toolbox.ui.theme.ToolboxAccent
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Local-only accent preference. Deliberately outside the synced settings whitelist, so the
 * server contract (theme/language/grid_columns/startup_page) stays unchanged.
 */
class AccentPreferenceRepository(context: Context, scope: CoroutineScope) {
    private val storage: DataStore<String> = DataStoreFactory.create(
        serializer = AccentSerializer,
        scope = scope,
        produceFile = { java.io.File(context.filesDir, "accent.preferences") },
    )

    fun observe(): Flow<ToolboxAccent> = storage.data.map(ToolboxAccent::of)

    suspend fun save(accent: ToolboxAccent) {
        storage.updateData { accent.storageKey }
    }
}

private object AccentSerializer : Serializer<String> {
    override val defaultValue = ToolboxAccent.BLUE.storageKey

    override suspend fun readFrom(input: InputStream): String {
        val bytes = try {
            java.io.ByteArrayOutputStream().use { buffer ->
                val chunk = ByteArray(32)
                while (true) {
                    val count = input.read(chunk)
                    if (count < 0) break
                    buffer.write(chunk, 0, count)
                    if (buffer.size() > 32) throw IOException("Accent preference too large")
                }
                buffer.toByteArray()
            }
        } catch (error: IOException) {
            throw CorruptionException("Invalid accent preference", error)
        }
        return bytes.toString(Charsets.UTF_8).trim()
    }

    override suspend fun writeTo(t: String, output: OutputStream) {
        output.write(t.toByteArray(Charsets.UTF_8))
    }
}
