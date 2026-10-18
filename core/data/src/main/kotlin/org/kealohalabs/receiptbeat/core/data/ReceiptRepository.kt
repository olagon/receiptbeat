package org.kealohalabs.receiptbeat.core.data

import androidx.room.withTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.kealohalabs.receiptbeat.core.database.ReceiptDatabase
import org.kealohalabs.receiptbeat.core.model.Confidence
import org.kealohalabs.receiptbeat.core.model.Correction
import org.kealohalabs.receiptbeat.core.model.ExtractionMethod
import org.kealohalabs.receiptbeat.core.model.FieldConfidence
import org.kealohalabs.receiptbeat.core.model.FileKind
import org.kealohalabs.receiptbeat.core.model.LineItem
import org.kealohalabs.receiptbeat.core.model.Money
import org.kealohalabs.receiptbeat.core.model.Receipt
import org.kealohalabs.receiptbeat.core.model.ReceiptEvent
import org.kealohalabs.receiptbeat.core.model.ReceiptFile
import org.kealohalabs.receiptbeat.core.model.Source
import org.kealohalabs.receiptbeat.core.model.Split
import org.kealohalabs.receiptbeat.core.model.Status
import org.kealohalabs.receiptbeat.core.model.Track
import org.kealohalabs.receiptbeat.core.model.isDone
import org.kealohalabs.receiptbeat.extraction.Box
import org.kealohalabs.receiptbeat.extraction.Duplicates
import org.kealohalabs.receiptbeat.extraction.Extracted
import org.kealohalabs.receiptbeat.extraction.Field
import org.kealohalabs.receiptbeat.extraction.MerchantNormalizer

/** Everything the detail screen needs about one receipt. */
data class ReceiptDetail(
    val receipt: Receipt,
    val files: List<ReceiptFile>,
    val confidence: Map<String, FieldConfidence>,
    val lineItems: List<LineItem>,
    val splits: List<Split>,
    val events: List<ReceiptEvent>
)

data class Dashboard(
    val owedCents: Long,
    val owedByPayer: Map<String?, Long>,
    val readyCents: Long,
    val readyCount: Int,
    val deductibleCents: Long,
    val inboxCount: Int,
    val lowConfidenceCount: Int
)

/** Fields the user can edit on a receipt. Names match FieldConfidence.field. */
object Fields {
    const val MERCHANT = "merchant"
    const val DATE = "date"
    const val SUBTOTAL = "subtotal"
    const val TAX = "tax"
    const val TIP = "tip"
    const val TOTAL = "total"
    const val CURRENCY = "currency"
    const val TOTAL_ALTERNATIVES = "total_alternatives"
}

@Singleton
class ReceiptRepository @Inject constructor(
    private val db: ReceiptDatabase,
    private val files: FileStore,
    private val catalog: CatalogRepository
) {
    private val dao = db.receipts()

    val receipts: Flow<List<Receipt>> = dao.observeAll()
    val thumbs: Flow<Map<String, String>> = dao.observeThumbs().map { list -> list.associate { it.receiptId to it.path } }
    val lowConfidence: Flow<Set<String>> = dao.observeLowConfidence().map { l -> l.map { it.receiptId }.toSet() }
    val trash: Flow<List<Receipt>> = dao.observeTrash()

    fun dashboard(year: Int = LocalDate.now().year): Flow<Dashboard> =
        combine(receipts, lowConfidence, catalog.categories, catalog.taxLines) { all, low, cats, lines ->
            val pct = cats.associate { c -> c.id to (lines.firstOrNull { it.id == c.taxLineId }?.deductiblePercent ?: 100) }
            val owedItems = all.filter {
                it.track == Track.REIMBURSABLE &&
                    (it.status == Status.SUBMITTED || it.status == Status.PARTLY_REIMBURSED)
            }
            val ready = all.filter {
                it.track == Track.REIMBURSABLE &&
                    (it.status == Status.READY || it.status == Status.INBOX || it.status == Status.REJECTED)
            }
            Dashboard(
                owedCents = owedItems.sumOf { owed(it) },
                owedByPayer = owedItems.groupBy { it.payerId }.mapValues { (_, v) -> v.sumOf { owed(it) } },
                readyCents = ready.sumOf { it.totalCents ?: 0 },
                readyCount = ready.size,
                deductibleCents = all.filter { it.track == Track.TAX && taxYearOf(it) == year && it.status != Status.ARCHIVED }
                    .sumOf { (it.totalCents ?: 0) * (pct[it.categoryId] ?: 100) / 100 },
                inboxCount = all.count { it.status == Status.INBOX && !it.reading },
                lowConfidenceCount = all.count { it.id in low && it.status == Status.INBOX }
            )
        }

    fun detail(id: String): Flow<ReceiptDetail?> = combine(
        dao.observe(id),
        dao.observeFiles(id),
        dao.observeConfidences(id),
        dao.observeLineItems(id),
        combine(dao.observeSplits(id), dao.observeEvents(id)) { s, e -> s to e }
    ) { r, f, c, li, se ->
        r?.let { ReceiptDetail(it, f, c.associateBy { x -> x.field }, li, se.first, se.second) }
    }

    suspend fun get(id: String) = dao.get(id)

    suspend fun getMany(ids: List<String>) = dao.getMany(ids)

    suspend fun files(id: String) = dao.files(id)

    suspend fun all() = dao.all()

    /** Inbox receipts read in the background by rules alone. The AI finishes them when the app opens. */
    suspend fun readByRulesOnly(): List<Receipt> {
        val rulesOnly = dao.allConfidences().filter {
            it.field == Fields.TOTAL && it.method == ExtractionMethod.RULES
        }.map { it.receiptId }.toSet()
        return dao.all().filter { it.id in rulesOnly && it.status == Status.INBOX && it.ocrText != null && !it.reading }
    }

    /** Full text search. Each word is a prefix match, so "kona cof" finds "Kona Coffee". */
    suspend fun search(text: String): List<Receipt> {
        val words = text.split(Regex("\\s+")).map { it.replace(Regex("[^\\p{L}\\p{N}]"), "") }.filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return dao.search(words.joinToString(" ") { "$it*" })
    }

    /** A new receipt in the Inbox, marked as still being read. Files are added by the capture pipeline. */
    suspend fun create(source: Source, track: Track = Track.PERSONAL): Receipt {
        val r = Receipt(source = source, track = track, reading = true)
        dao.upsert(r)
        event(r.id, "Added from ${source.label()}")
        return r
    }

    suspend fun insert(receipt: Receipt, event: String) {
        dao.upsert(receipt)
        event(receipt.id, event)
    }

    suspend fun addFiles(list: List<ReceiptFile>) = dao.insertFiles(list)

    suspend fun setImageHash(id: String, hash: String?) {
        dao.get(id)?.let { dao.upsert(it.copy(imageHash = hash, updatedAt = Instant.now())) }
    }

    /**
     * Fill a receipt with what extraction found, then apply smart suggestions (merchant memory first, then the guess).
     * Fields the user already edited are kept.
     */
    suspend fun applyExtraction(id: String, x: Extracted, ocrText: String?, method: ExtractionMethod) {
        val r = dao.get(id) ?: return
        val edited = dao.allCorrections().filter { it.receiptId == id }.map { it.field }.toSet()
        val memory = catalog.memoryFor(x.merchant.value)
        val payer = memory?.lastPayerId?.let { pid -> catalog.payers().firstOrNull { it.id == pid } }
        val updated = r.copy(
            merchantName = if (Fields.MERCHANT in edited) r.merchantName else x.merchant.value ?: r.merchantName,
            merchantRaw = x.merchantRaw ?: r.merchantRaw,
            merchantAddress = x.address ?: r.merchantAddress,
            purchaseDate = if (Fields.DATE in edited) r.purchaseDate else x.date.value ?: r.purchaseDate,
            purchaseTime = x.time ?: r.purchaseTime,
            subtotalCents = if (Fields.SUBTOTAL in edited) r.subtotalCents else x.subtotal.value ?: r.subtotalCents,
            taxCents = if (Fields.TAX in edited) r.taxCents else x.tax.value ?: r.taxCents,
            tipCents = if (Fields.TIP in edited) r.tipCents else x.tip.value ?: r.tipCents,
            totalCents = if (Fields.TOTAL in edited) r.totalCents else x.total.value ?: r.totalCents,
            currency = if (Fields.CURRENCY in edited) r.currency else x.currency.value ?: r.currency,
            paymentBrand = x.paymentBrand ?: r.paymentBrand,
            paymentLast4 = x.last4 ?: r.paymentLast4,
            receiptNumber = x.receiptNumber ?: r.receiptNumber,
            categoryId = r.categoryId ?: memory?.lastCategoryId ?: x.categoryGuess,
            payerId = r.payerId ?: memory?.lastPayerId,
            track = if (r.payerId == null) memory?.lastTrack ?: payer?.defaultTrack ?: r.track else r.track,
            ocrText = ocrText ?: r.ocrText,
            reading = false,
            updatedAt = Instant.now()
        )
        db.withTransaction {
            dao.upsert(updated)
            dao.upsertConfidences(
                listOf(
                    conf(id, Fields.MERCHANT, x.merchant, method),
                    conf(id, Fields.DATE, x.date, method),
                    conf(id, Fields.SUBTOTAL, x.subtotal, method),
                    conf(id, Fields.TAX, x.tax, method),
                    conf(id, Fields.TIP, x.tip, method),
                    conf(id, Fields.TOTAL, x.total, method),
                    conf(id, Fields.CURRENCY, x.currency, method)
                ).filter { it.field !in edited } +
                    listOfNotNull(
                        // AI and rules disagreed: keep both so the user can pick.
                        x.totalAlternatives.takeIf { it.isNotEmpty() && Fields.TOTAL !in edited }?.let {
                            FieldConfidence(id, Fields.TOTAL_ALTERNATIVES, it.joinToString(","), Confidence.LOW, method)
                        }
                    )
            )
            if (x.lineItems.isNotEmpty()) {
                dao.deleteLineItems(id)
                dao.insertLineItems(x.lineItems.map { LineItem(receiptId = id, name = it.name, qty = it.qty, totalCents = it.totalCents) })
            }
        }
        if (x.totalAlternatives.isNotEmpty()) {
            event(id, "Two possible totals found: " + x.totalAlternatives.joinToString(" or ") { Money.format(it, updated.currency) })
        }
        markDuplicate(updated)
    }

    /** After a crash or a killed job: re-read receipts that have files, drop empty ones. */
    suspend fun recoverStuck(): List<String> = dao.stillReading().mapNotNull { r ->
        if (dao.files(r.id).isEmpty()) {
            dao.upsert(r.copy(deletedAt = Instant.now(), reading = false))
            null
        } else {
            r.id
        }
    }

    /** When reading fails, the card stops shimmering and waits for the user. */
    suspend fun readingFailed(id: String) {
        dao.get(id)?.let { dao.upsert(it.copy(reading = false, updatedAt = Instant.now())) }
    }

    private fun conf(id: String, field: String, f: Field<*>, method: ExtractionMethod) = FieldConfidence(
        receiptId = id,
        field = field,
        value = f.value?.toString(),
        // A missing tip, tax or subtotal is normal. Only a missing merchant, date or total needs a look.
        confidence = when {
            f.value != null -> f.confidence
            field == Fields.MERCHANT || field == Fields.DATE || field == Fields.TOTAL -> Confidence.LOW
            else -> Confidence.HIGH
        },
        method = method,
        box = f.box?.let { b: Box -> "${b.x},${b.y},${b.w},${b.h}" }
    )

    /** Save a user edit. Records the correction so suggestions improve, and marks the field as sure. */
    suspend fun edit(id: String, field: String, newValue: String?, change: (Receipt) -> Receipt) {
        val r = dao.get(id) ?: return
        if (r.locked) return
        val old = when (field) {
            Fields.MERCHANT -> r.merchantName
            Fields.DATE -> r.purchaseDate?.toString()
            Fields.SUBTOTAL -> r.subtotalCents?.toString()
            Fields.TAX -> r.taxCents?.toString()
            Fields.TIP -> r.tipCents?.toString()
            Fields.TOTAL -> r.totalCents?.toString()
            Fields.CURRENCY -> r.currency
            else -> null
        }
        val updated = change(r).copy(updatedAt = Instant.now())
        db.withTransaction {
            dao.upsert(updated)
            dao.insertCorrection(Correction(receiptId = id, field = field, oldValue = old, newValue = newValue))
            dao.upsertConfidences(listOf(FieldConfidence(id, field, newValue, Confidence.HIGH, ExtractionMethod.USER)))
            if (field == Fields.TOTAL) dao.deleteConfidence(id, Fields.TOTAL_ALTERNATIVES)
        }
        if (field == Fields.MERCHANT || field == "category" || field == "payer") {
            catalog.learn(updated.merchantName, updated.categoryId, updated.payerId, updated.track)
        }
        if (field == Fields.TOTAL || field == Fields.DATE || field == Fields.MERCHANT) markDuplicate(updated)
    }

    /** Edit that is not a reading correction (notes, project, track). */
    suspend fun update(id: String, eventText: String? = null, change: (Receipt) -> Receipt) {
        val r = dao.get(id) ?: return
        if (r.locked) return
        val updated = change(r).copy(updatedAt = Instant.now())
        dao.upsert(updated)
        if (eventText != null) event(id, eventText)
        if (updated.categoryId != r.categoryId || updated.payerId != r.payerId || updated.track != r.track) {
            catalog.learn(updated.merchantName, updated.categoryId, updated.payerId, updated.track)
        }
    }

    suspend fun setStatus(ids: Collection<String>, status: Status, note: String? = null) {
        db.withTransaction {
            dao.getMany(ids.toList()).filter { !it.locked }.forEach { r ->
                val reimbursed = when (status) {
                    Status.REIMBURSED -> r.totalCents
                    Status.READY, Status.INBOX, Status.SUBMITTED -> null
                    else -> r.reimbursedCents
                }
                val taxYear = if (status == Status.CLAIMED) r.taxYear ?: taxYearOf(r) else r.taxYear
                dao.upsert(
                    r.copy(
                        status = status,
                        reimbursedCents = reimbursed,
                        taxYear = taxYear,
                        rejectReason = if (status == Status.REJECTED) note else r.rejectReason,
                        updatedAt = Instant.now()
                    )
                )
                event(r.id, "Marked ${status.label()}" + (note?.let { ": $it" } ?: ""))
            }
        }
    }

    /** Move forward one step on the receipt's track (swipe right). */
    suspend fun advance(ids: Collection<String>) {
        dao.getMany(ids.toList()).groupBy { next(it) }.forEach { (status, list) ->
            if (status != null) setStatus(list.map { it.id }, status)
        }
    }

    fun next(r: Receipt): Status? = when (r.status) {
        Status.INBOX -> Status.READY
        Status.READY, Status.REJECTED -> when (r.track) {
            Track.REIMBURSABLE -> Status.SUBMITTED
            Track.TAX -> Status.CLAIMED
            Track.PERSONAL -> Status.ARCHIVED
        }
        Status.SUBMITTED, Status.PARTLY_REIMBURSED -> Status.REIMBURSED
        else -> null
    }

    suspend fun markPaid(id: String, cents: Long) {
        val r = dao.get(id) ?: return
        val total = r.totalCents ?: cents
        val status = if (cents >= total) Status.REIMBURSED else Status.PARTLY_REIMBURSED
        dao.upsert(r.copy(status = status, reimbursedCents = cents, updatedAt = Instant.now()))
        event(id, "Paid ${Money.format(cents, r.currency)}")
    }

    suspend fun setTrack(ids: Collection<String>, track: Track, payerId: String? = null) {
        dao.getMany(ids.toList()).filter { !it.locked }.forEach { r ->
            val status = if (r.status.isDone || r.status == Status.SUBMITTED) Status.READY else r.status
            dao.upsert(r.copy(track = track, payerId = payerId ?: r.payerId, status = status, updatedAt = Instant.now()))
            event(r.id, "Moved to ${track.label()}")
            catalog.learn(r.merchantName, r.categoryId, payerId ?: r.payerId, track)
        }
    }

    suspend fun setLocked(ids: Collection<String>, locked: Boolean) {
        dao.getMany(ids.toList()).forEach { dao.upsert(it.copy(locked = locked, updatedAt = Instant.now())) }
    }

    suspend fun delete(ids: Collection<String>) {
        dao.getMany(ids.toList()).filter { !it.locked }.forEach { dao.upsert(it.copy(deletedAt = Instant.now())) }
    }

    /** Undo: put receipts back exactly as they were. */
    suspend fun revert(snapshot: List<Receipt>) = dao.upsertAll(snapshot)

    suspend fun restore(ids: Collection<String>) {
        dao.getMany(ids.toList()).forEach { dao.upsert(it.copy(deletedAt = null)) }
    }

    /** Trash keeps receipts 30 days. */
    suspend fun purgeTrash() {
        val cutoff = Instant.now().minus(30, ChronoUnit.DAYS).toEpochMilli()
        dao.trashedBefore(cutoff).forEach { id -> dao.files(id).forEach { files.delete(it.path) } }
        dao.purgeDeleted(cutoff)
    }

    suspend fun saveSplits(id: String, splits: List<Split>) {
        db.withTransaction {
            dao.deleteSplits(id)
            dao.insertSplits(splits)
        }
        event(id, if (splits.isEmpty()) "Split removed" else "Split into ${splits.size} parts")
    }

    /** Look for a likely duplicate and link it, so the UI can show both side by side. */
    suspend fun markDuplicate(r: Receipt) {
        val day = r.purchaseDate
        val candidates = dao.possibleDuplicates(r.id, r.totalCents, day?.minusDays(1)?.toEpochDay(), day?.plusDays(1)?.toEpochDay(), null) +
            (r.imageHash?.let { dao.withHashes(r.id) } ?: emptyList())
        val skip = notDuplicateOf(r.id)
        val match = candidates.firstOrNull { o ->
            o.id !in skip &&
                o.duplicateOfId != r.id &&
                Duplicates.isLikely(
                    r.totalCents,
                    r.purchaseDate,
                    r.merchantName?.let(MerchantNormalizer::key),
                    r.imageHash?.toULongOrNull(16)?.toLong(),
                    o.totalCents,
                    o.purchaseDate,
                    o.merchantName?.let(MerchantNormalizer::key),
                    o.imageHash?.toULongOrNull(16)?.toLong()
                )
        }
        if (match?.id != r.duplicateOfId) dao.upsert(r.copy(duplicateOfId = match?.id))
    }

    suspend fun resolveDuplicate(keepId: String, otherId: String, action: DuplicateAction) {
        when (action) {
            DuplicateAction.KEEP_BOTH -> {
                listOf(keepId, otherId).forEach { id -> dao.get(id)?.let { dao.upsert(it.copy(duplicateOfId = null)) } }
                // Remember the decision so the pair is not flagged again.
                event(keepId, NOT_DUPLICATE + otherId)
                event(otherId, NOT_DUPLICATE + keepId)
            }
            DuplicateAction.DELETE_THIS -> delete(listOf(keepId))
            DuplicateAction.MERGE -> {
                val a = dao.get(keepId) ?: return
                val b = dao.get(otherId) ?: return
                // Keep the older record, fill its gaps from the newer one, move the newer one's files over.
                val merged = b.copy(
                    merchantName = b.merchantName ?: a.merchantName,
                    purchaseDate = b.purchaseDate ?: a.purchaseDate,
                    taxCents = b.taxCents ?: a.taxCents,
                    totalCents = b.totalCents ?: a.totalCents,
                    notes = listOfNotNull(b.notes, a.notes).joinToString("\n").ifBlank { null },
                    ocrText = listOfNotNull(b.ocrText, a.ocrText).joinToString("\n").ifBlank { null },
                    emailSubject = b.emailSubject ?: a.emailSubject,
                    duplicateOfId = null,
                    updatedAt = Instant.now()
                )
                db.withTransaction {
                    dao.upsert(merged)
                    dao.insertFiles(dao.files(a.id).map { it.copy(receiptId = b.id, page = it.page + 100) })
                    dao.deleteFiles(a.id)
                    dao.upsert(a.copy(deletedAt = Instant.now(), duplicateOfId = null))
                }
                event(b.id, "Merged with a duplicate copy")
            }
        }
    }

    private suspend fun notDuplicateOf(id: String): Set<String> =
        dao.eventsFor(id).filter { it.text.startsWith(NOT_DUPLICATE) }.map { it.text.removePrefix(NOT_DUPLICATE) }.toSet()

    suspend fun event(id: String, text: String) = dao.insertEvent(ReceiptEvent(receiptId = id, text = text))

    companion object {
        /** History entries that start with this are hidden markers, not shown to the user. */
        const val NOT_DUPLICATE = "not-duplicate:"

        fun owed(r: Receipt) = ((r.totalCents ?: 0) - (r.reimbursedCents ?: 0)).coerceAtLeast(0)
        fun taxYearOf(r: Receipt): Int = r.taxYear ?: r.purchaseDate?.year ?: LocalDate.ofEpochDay(r.createdAt.epochSecond / 86400).year
    }
}

enum class DuplicateAction { KEEP_BOTH, MERGE, DELETE_THIS }

fun Source.label() = when (this) {
    Source.CAMERA -> "camera"
    Source.IMPORT -> "a file"
    Source.SHARE -> "another app"
    Source.EMAIL_FILE -> "an email export"
    Source.EMAIL_LIVE -> "email"
}

fun Status.label() = when (this) {
    Status.INBOX -> "Inbox"
    Status.READY -> "Ready"
    Status.SUBMITTED -> "Submitted"
    Status.REIMBURSED -> "Reimbursed"
    Status.PARTLY_REIMBURSED -> "Partly reimbursed"
    Status.REJECTED -> "Rejected"
    Status.CLAIMED -> "Claimed"
    Status.ARCHIVED -> "Archived"
}

fun Track.label() = when (this) {
    Track.REIMBURSABLE -> "Reimbursable"
    Track.TAX -> "Tax deductible"
    Track.PERSONAL -> "Personal record"
}

val FileKind.isImage get() = this == FileKind.DISPLAY || this == FileKind.ORIGINAL || this == FileKind.THUMB
