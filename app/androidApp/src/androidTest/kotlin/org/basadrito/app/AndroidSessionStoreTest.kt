package org.basadrito.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.basadrito.app.data.moodle.dto.MoodleSession
import org.basadrito.app.data.session.AndroidSessionStore
import org.basadrito.app.data.session.SavedSession
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidSessionStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun newStoreInstanceDecryptsAccessAndLogoutRemovesIt() = runBlocking {
        val name = "test-session-${UUID.randomUUID()}"
        val store = AndroidSessionStore(context, name)
        try {
            assertTrue(store.save(saved) { true })
            val file = File(context.noBackupFilesDir, "$name.bin")
            assertFalse(file.readBytes().toString(Charsets.UTF_8).contains(saved.moodle.token))
            assertEquals(saved, AndroidSessionStore(context, name).load())
            store.clear()
            assertFalse(file.exists())
            assertNull(AndroidSessionStore(context, name).load())
        } finally { store.clear() }
    }

    @Test
    fun tamperedCiphertextIsRejectedAndRemoved() = runBlocking {
        val name = "test-session-${UUID.randomUUID()}"
        val store = AndroidSessionStore(context, name)
        try {
            store.save(saved) { true }
            val file = File(context.noBackupFilesDir, "$name.bin")
            val bytes = file.readBytes()
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            file.writeBytes(bytes)
            assertNull(AndroidSessionStore(context, name).load())
            assertFalse(file.exists())
        } finally { store.clear() }
    }

    @Test
    fun missingKeystoreKeyCannotRestoreAccessAndEveryWriteUsesANewIv() = runBlocking {
        val name = "test-session-${UUID.randomUUID()}"
        val store = AndroidSessionStore(context, name)
        try {
            val file = File(context.noBackupFilesDir, "$name.bin")
            store.save(saved) { true }
            val first = file.readBytes()
            store.save(saved) { true }
            assertFalse(first.contentEquals(file.readBytes()))
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("org.basadrito.$name.v1") }
            assertNull(AndroidSessionStore(context, name).load())
            assertFalse(file.exists())
        } finally { store.clear() }
    }

    private companion object {
        val saved = SavedSession(MoodleSession("fictitious-token", 12, "Estudiante ficticio", "student"), emptyList())
    }
}
