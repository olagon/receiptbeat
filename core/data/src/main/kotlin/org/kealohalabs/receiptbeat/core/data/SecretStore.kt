package org.kealohalabs.receiptbeat.core.data

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App passwords, sign in tokens and the optional AI key. Encrypted with a Keystore backed key,
 * never written to the database, and removable in one tap.
 */
@Singleton
class SecretStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("secrets", Context.MODE_PRIVATE)

    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, "secret_keyset", "secret_keyset_prefs")
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri("android-keystore://receiptbeat_secret_master")
            .build()
            .keysetHandle
            .getPrimitive(Aead::class.java)
    }

    fun put(name: String, value: String) {
        val enc = aead.encrypt(value.toByteArray(), name.toByteArray())
        prefs.edit().putString(name, Base64.encodeToString(enc, Base64.NO_WRAP)).apply()
    }

    fun get(name: String): String? = prefs.getString(name, null)?.let {
        runCatching { String(aead.decrypt(Base64.decode(it, Base64.NO_WRAP), name.toByteArray())) }.getOrNull()
    }

    fun remove(name: String) = prefs.edit().remove(name).apply()

    companion object {
        fun mail(accountId: String) = "mail:$accountId"
        const val AI_KEY = "ai_key"
    }
}
