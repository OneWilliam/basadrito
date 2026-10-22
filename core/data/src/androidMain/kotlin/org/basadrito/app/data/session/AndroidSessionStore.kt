package org.basadrito.app.data.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import org.basadrito.app.data.http.AppJson

/** AES-256-GCM envelope, private no-backup file, non-exportable Android Keystore key. */
class AndroidSessionStore(context: Context, storageName: String = "academic-session") : SessionStore {
    init { require(storageName.matches(Regex("[a-zA-Z0-9._-]{1,80}"))) }
    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, "$storageName.bin"))
    private val keyAlias = "org.basadrito.$storageName.v1"
    private val lock = Any()

    override suspend fun load(): SavedSession? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@synchronized null
            try {
                val bytes = file.openRead().use {
                    require(file.baseFile.length() <= MAX_SIZE)
                    it.readBytes()
                }
                require(bytes.size in MIN_SIZE..MAX_SIZE && bytes[0] == VERSION)
                val key = existingKey() ?: run { file.delete(); return@synchronized null }
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
                cipher.updateAAD(AAD)
                val plaintext = cipher.doFinal(bytes, 13, bytes.size - 13)
                try {
                    AppJson.decodeFromString<SavedSession>(plaintext.decodeToString()).also {
                        require(it.version == 1 && it.moodle.token.isNotBlank() && it.moodle.userId > 0)
                    }
                } finally { plaintext.fill(0) }
            } catch (_: GeneralSecurityException) {
                eraseLocked()
                null
            } catch (_: SerializationException) {
                eraseLocked()
                null
            } catch (_: IllegalArgumentException) {
                eraseLocked()
                null
            }
        }
    }

    override suspend fun save(session: SavedSession, isCurrent: () -> Boolean): Boolean = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (!isCurrent()) return@synchronized false
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, existingKey() ?: createKey())
            cipher.updateAAD(AAD)
            val plaintext = AppJson.encodeToString(session).encodeToByteArray()
            val encrypted = try { cipher.doFinal(plaintext) } finally { plaintext.fill(0) }
            val envelope = byteArrayOf(VERSION) + cipher.iv + encrypted
            require(cipher.iv.size == 12 && envelope.size <= MAX_SIZE)
            val output = file.startWrite()
            try {
                output.write(envelope)
                file.finishWrite(output)
            } catch (error: Exception) {
                file.failWrite(output)
                throw error
            }
            true
        }
    }

    override fun clear(isCurrent: () -> Boolean) = synchronized(lock) {
        if (isCurrent()) eraseLocked()
    }

    private fun eraseLocked() {
        // Removing the key also makes any surviving envelope unusable after logout.
        try {
            val keyStore = keyStore()
            if (keyStore.containsAlias(keyAlias)) keyStore.deleteEntry(keyAlias)
        } finally { file.delete() }
    }

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun existingKey() = keyStore().getKey(keyAlias, null) as? SecretKey

    private fun createKey(): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
        init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build())
        generateKey()
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VERSION: Byte = 1
        const val MIN_SIZE = 1 + 12 + 16
        const val MAX_SIZE = 512 * 1024
        val AAD = "org.basadrito.academic-session.v1".encodeToByteArray()
    }
}
