package org.kealohalabs.receiptbeat

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kealohalabs.receiptbeat.core.data.FileStore
import org.kealohalabs.receiptbeat.core.data.ReceiptRepository
import org.kealohalabs.receiptbeat.core.model.FileKind

/** Hands files to the Android share sheet. Plain copies live in the cache and are cleared on the next start. */
@Singleton
class Sharing @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: ReceiptRepository,
    private val store: FileStore
) {
    suspend fun receipt(activity: Context, id: String) {
        val r = repo.get(id) ?: return
        val files = withContext(Dispatchers.IO) {
            val all = repo.files(id)
            val pick = all.filter { it.kind == FileKind.PDF }.ifEmpty { all.filter { it.kind == FileKind.DISPLAY } }
            val base = listOfNotNull(
                r.purchaseDate?.toString(),
                r.merchantName?.replace(Regex("[^A-Za-z0-9]+"), "-")
            ).joinToString("_").ifBlank {
                "receipt"
            }
            pick.mapIndexed { i, f ->
                val ext = if (f.kind == FileKind.PDF) "pdf" else "webp"
                store.decryptToCache(f.path, if (pick.size > 1) "${base}_${i + 1}.$ext" else "$base.$ext")
            }
        }
        files(activity, files, r.merchantName ?: "Receipt")
    }

    fun files(activity: Context, files: List<File>, subject: String) {
        if (files.isEmpty()) return
        val uris = files.map { uri(it) }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        intent.type = if (files.all { it.extension == "pdf" }) "application/pdf" else "*/*"
        intent.putExtra(Intent.EXTRA_SUBJECT, subject)
        intent.clipData = ClipData.newRawUri(subject, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(intent, "Share")
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(android.content.ComponentName(activity, MainActivity::class.java)))
        activity.startActivity(chooser)
    }

    fun uri(f: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
}
