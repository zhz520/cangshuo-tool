package com.cangshuo.toolbox.feature.auth.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first

internal data class StoredAuthSession(val refreshToken: String = "", val refreshExpiresAt: String = "",
    val pendingLogout: Boolean = false) {
    override fun toString() = "StoredAuthSession[redacted]"
}

internal interface AuthSessionStore {
    suspend fun read(): StoredAuthSession
    suspend fun write(session: StoredAuthSession)
}

internal class MemoryAuthSessionStore : AuthSessionStore {
    private var value = StoredAuthSession()
    override suspend fun read() = value
    override suspend fun write(session: StoredAuthSession) { value = session }
}

/** Only encrypted refresh state is persisted; neither passwords nor access tokens are written. */
internal class EncryptedAuthSessionStore(context: Context, endpoint: String, scope: CoroutineScope) : AuthSessionStore {
    private val app = context.applicationContext
    private val storage = DataStoreFactory.create(
        serializer = AuthSessionSerializer(endpoint.toByteArray(Charsets.UTF_8)),
        corruptionHandler = ReplaceFileCorruptionHandler { StoredAuthSession() },
        scope = scope,
        produceFile = { File(app.noBackupFilesDir, "auth/session.bin") },
    )
    override suspend fun read() = storage.data.first()
    override suspend fun write(session: StoredAuthSession) { storage.updateData { session } }
}

private class AuthSessionSerializer(private val associatedData: ByteArray) : Serializer<StoredAuthSession> {
    private val adapter = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build().adapter(StoredAuthSession::class.java)
    override val defaultValue = StoredAuthSession()
    override suspend fun readFrom(input: InputStream): StoredAuthSession {
        val buffer = ByteArray(4097)
        var count = 0
        while (count < buffer.size) {
            val read = input.read(buffer, count, buffer.size - count)
            if (read < 0) break
            if (read > 0) count += read
        }
        val bytes = buffer.copyOf(count)
        if (bytes.isEmpty()) return defaultValue
        if (bytes.size !in 30..4096 || bytes[0] != 1.toByte()) throw CorruptionException("Invalid encrypted session")
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1,13)))
            cipher.updateAAD(associatedData)
            return adapter.fromJson(String(cipher.doFinal(bytes.copyOfRange(13,bytes.size)), Charsets.UTF_8))
                ?: throw CorruptionException("Invalid encrypted session")
        } catch (_: Exception) { throw CorruptionException("Unable to decrypt session") }
    }
    override suspend fun writeTo(t: StoredAuthSession, output: OutputStream) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(associatedData)
        output.write(byteArrayOf(1)); output.write(cipher.iv)
        output.write(cipher.doFinal(adapter.toJson(t).toByteArray(Charsets.UTF_8)))
    }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("toolbox.auth.aes.v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("toolbox.auth.aes.v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
}
