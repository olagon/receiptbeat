package org.kealohalabs.receiptbeat.capture

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.kealohalabs.receiptbeat.core.data.ReceiptRepository
import org.kealohalabs.receiptbeat.core.data.SettingsRepository
import org.kealohalabs.receiptbeat.core.model.Confidence
import org.kealohalabs.receiptbeat.core.model.ExtractionMethod
import org.kealohalabs.receiptbeat.core.model.FileKind
import org.kealohalabs.receiptbeat.core.model.Receipt
import org.kealohalabs.receiptbeat.core.model.Source
import org.kealohalabs.receiptbeat.core.model.Track
import org.kealohalabs.receiptbeat.extraction.CardMasker
import org.kealohalabs.receiptbeat.extraction.Extracted
import org.kealohalabs.receiptbeat.extraction.OcrLine
import org.kealohalabs.receiptbeat.extraction.Reconcile
import org.kealohalabs.receiptbeat.extraction.RulesParser

/** One pipeline for every receipt: camera, file, share or email. */
@Singleton
class ReceiptPipeline @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: ReceiptRepository,
    private val images: ImageProcessor,
    private val ocr: Ocr,
    private val nano: NanoReader,
    private val cloud: CloudReader,
    private val settings: SettingsRepository,
) {
    /** Camera or photos: one receipt from one or more pages. Returns the new receipt id right away; reading runs in the background. */
    suspend fun fromImages(uris: List<Uri>, source: Source, stitch: Boolean, track: Track? = null): String = withContext(Dispatchers.IO) {
        val r = repo.create(source, track ?: defaultTrack())
        val saved = guard(r.id) { images.saveImages(r.id, uris, stitch) }
        repo.addFiles(saved.files)
        repo.setImageHash(r.id, saved.hash?.toULong()?.toString(16))
        enqueue(r.id)
        r.id
    }

    suspend fun fromPdf(uri: Uri, source: Source): String = withContext(Dispatchers.IO) {
        val r = repo.create(source, defaultTrack())
        val saved = guard(r.id) { images.savePdf(r.id, uri) }
        repo.addFiles(saved.files)
        repo.setImageHash(r.id, saved.hash?.toULong()?.toString(16))
        enqueue(r.id)
        r.id
    }

    /** Share sheet and Import: each image or PDF becomes its own receipt. Files that cannot be read are skipped. */
    suspend fun fromUris(uris: List<Uri>, source: Source): List<String> = uris.mapNotNull { uri ->
        runCatching {
            if (images.mimeOf(uri) == "application/pdf") fromPdf(uri, source) else fromImages(listOf(uri), source, stitch = false)
        }.onFailure { android.util.Log.w("ReceiptBeat", "Could not read $uri", it) }.getOrNull()
    }

    /** If saving the files fails, the empty receipt is removed before the error goes up. */
    private suspend fun <T> guard(id: String, block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        repo.delete(listOf(id))
        throw e
    }

    /** Where new receipts go, from the onboarding choice. Merchant memory and payers can still change it. */
    private suspend fun defaultTrack(): Track {
        val s = settings.current()
        return when {
            s.useWork -> Track.REIMBURSABLE
            s.useTaxes -> Track.TAX
            else -> Track.PERSONAL
        }
    }

    fun enqueue(id: String) {
        val req = OneTimeWorkRequestBuilder<ReadReceiptWorker>()
            .setInputData(workDataOf("id" to id))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("read-$id", ExistingWorkPolicy.REPLACE, req)
    }

    /** OCR every page, then rules, then Nano (if on screen), then optional cloud AI for weak results. */
    suspend fun read(id: String) {
        val receipt = repo.get(id) ?: return
        try {
            val pages = repo.files(id).filter { it.kind == FileKind.DISPLAY }.sortedBy { it.page }
            val lines = mutableListOf<OcrLine>()
            var y = 0
            pages.forEach { f ->
                val bmp = images.decodeStored(f.path)
                lines += ocr.read(bmp, y)
                y += bmp.height
            }
            // Email receipts already carry body text; OCR text from a PDF attachment is added on top.
            val text = CardMasker.mask(
                listOfNotNull(
                    receipt.ocrText?.takeIf {
                        pages.isEmpty()
                    },
                    lines.joinToString("\n") { it.text }
                ).joinToString("\n")
            )
            val rules = if (lines.isNotEmpty()) RulesParser.parse(lines) else RulesParser.parse(text)
            var method = ExtractionMethod.RULES
            var ai: Extracted? = nano.extract(text)?.also { method = ExtractionMethod.NANO }
            if (ai == null && weak(rules)) ai = cloud.extract(text)?.also { method = ExtractionMethod.CLOUD }
            repo.applyExtraction(id, Reconcile.merge(ai, rules), text, method)
        } catch (e: Exception) {
            android.util.Log.w("ReceiptBeat", "Reading failed for $id", e)
            repo.readingFailed(id)
        }
    }

    /** Re-run the AI on receipts read in the background, now that the app is on screen. */
    suspend fun finishWithAi() {
        if (!nano.inForeground) return
        repo.readByRulesOnly().take(MAX_AI_CATCHUP)
            .forEach { r: Receipt ->
                val ai = nano.extract(r.ocrText!!) ?: return
                repo.applyExtraction(r.id, Reconcile.merge(ai, RulesParser.parse(r.ocrText!!)), null, ExtractionMethod.NANO)
            }
    }

    private fun weak(x: Extracted) = x.total.value == null || x.total.confidence == Confidence.LOW || x.merchant.value == null

    private companion object {
        const val MAX_AI_CATCHUP = 20
    }
}

@HiltWorker
class ReadReceiptWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pipeline: ReceiptPipeline
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        pipeline.read(inputData.getString("id") ?: return Result.failure())
        return Result.success()
    }

    override suspend fun getForegroundInfo() = Notifications.working(applicationContext, "Reading receipt")
}
