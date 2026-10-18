package org.kealohalabs.receiptbeat.core.data

import android.content.Context
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Receipt images and PDFs, encrypted at rest with Tink. The keyset is wrapped by a Keystore key.
 * Paths stored in the database are relative to the app's private files folder.
 */
@Singleton
class FileStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val root = File(context.filesDir, "receipts").apply { mkdirs() }

    private val aead: StreamingAead by lazy {
        StreamingAeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, "file_keyset", "file_keyset_prefs")
            .withKeyTemplate(KeyTemplates.get("AES256_GCM_HKDF_1MB"))
            .withMasterKeyUri("android-keystore://receiptbeat_file_master")
            .build()
            .keysetHandle
            .getPrimitive(StreamingAead::class.java)
    }

    fun file(path: String) = File(root, path)

    fun write(path: String, bytes: ByteArray): Long {
        output(path).use { it.write(bytes) }
        return bytes.size.toLong()
    }

    fun output(path: String): OutputStream {
        val f = file(path).apply { parentFile?.mkdirs() }
        return aead.newEncryptingStream(f.outputStream(), path.toByteArray())
    }

    fun input(path: String): InputStream = aead.newDecryptingStream(file(path).inputStream(), path.toByteArray())

    fun read(path: String): ByteArray = input(path).use { it.readBytes() }

    fun exists(path: String) = file(path).exists()

    fun delete(path: String) {
        file(path).delete()
    }

    /** A plain (decrypted) copy in the cache, for sharing or exporting. Deleted on next app start. */
    fun decryptToCache(path: String, name: String): File {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val out = File(dir, name)
        input(path).use { i -> out.outputStream().use { i.copyTo(it) } }
        return out
    }
}
