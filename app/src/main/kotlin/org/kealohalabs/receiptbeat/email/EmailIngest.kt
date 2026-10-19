package org.kealohalabs.receiptbeat.email

import com.google.mlkit.genai.common.FeatureStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.kealohalabs.receiptbeat.capture.ImageProcessor
import org.kealohalabs.receiptbeat.capture.NanoReader
import org.kealohalabs.receiptbeat.capture.ReceiptPipeline
import org.kealohalabs.receiptbeat.core.data.FileStore
import org.kealohalabs.receiptbeat.core.data.MailRepository
import org.kealohalabs.receiptbeat.core.data.ReceiptRepository
import org.kealohalabs.receiptbeat.core.model.EmailCandidate
import org.kealohalabs.receiptbeat.core.model.EmailSource
import org.kealohalabs.receiptbeat.core.model.FileKind
import org.kealohalabs.receiptbeat.core.model.Money
import org.kealohalabs.receiptbeat.core.model.Receipt
import org.kealohalabs.receiptbeat.core.model.SenderRule
import org.kealohalabs.receiptbeat.core.model.SenderRuleKind
import org.kealohalabs.receiptbeat.core.model.Source
import org.kealohalabs.receiptbeat.core.model.Track
import org.kealohalabs.receiptbeat.extraction.CardMasker
import org.kealohalabs.receiptbeat.extraction.MerchantNormalizer
import org.kealohalabs.receiptbeat.mail.EmailExtractor
import org.kealohalabs.receiptbeat.mail.RawMail
import org.kealohalabs.receiptbeat.mail.ReceiptScorer

/** What happened to one message. */
enum class Outcome { SKIPPED, NOT_RECEIPT, FOUND, AUTO_KEPT }

/** Settings for one run over a mailbox, from a file or a connected account. */
data class ScanContext(
    val from: LocalDate,
    val to: LocalDate,
    val rules: List<SenderRule>,
    val accountId: String? = null,
    val importJobId: String? = null,
    val autoKeepTrusted: Boolean = false
)

/**
 * The email pipeline, all on the phone: header skim, rules score, field pull, then Review.
 * Borderline emails are marked for the on device AI, which runs when the app is on screen.
 */
@Singleton
class EmailIngest @Inject constructor(
    private val mail: MailRepository,
    private val receipts: ReceiptRepository,
    private val store: FileStore,
    private val images: ImageProcessor,
    private val pipeline: ReceiptPipeline,
    private val nano: NanoReader
) {
    /** Cheap checks on headers only. Most of a mailbox drops out here. */
    suspend fun skim(h: org.kealohalabs.receiptbeat.mail.MailHeader, ctx: ScanContext): Boolean {
        val day = h.date?.atZone(ZoneId.systemDefault())?.toLocalDate()
        if (day != null && (day < ctx.from || day > ctx.to)) return false
        if (mail.ruleFor(ctx.rules, h.from)?.rule == SenderRuleKind.MUTE) return false
        return !mail.seen(h.messageIdHash)
    }

    suspend fun process(raw: RawMail, ctx: ScanContext, source: Source): Outcome = withContext(Dispatchers.IO) {
        val h = raw.header
        if (!skim(h, ctx)) return@withContext Outcome.SKIPPED
        val msg = runCatching { raw.parse() }.getOrNull() ?: return@withContext Outcome.SKIPPED
        val rule = mail.ruleFor(ctx.rules, h.from)
        val trusted = rule?.rule == SenderRuleKind.ALWAYS_KEEP
        val score = ReceiptScorer.score(msg, ctx.rules.filter { it.rule == SenderRuleKind.ALWAYS_KEEP }.map { it.senderOrDomain }.toSet())
        if (score.score < ReceiptScorer.DROP && !trusted) return@withContext Outcome.NOT_RECEIPT
        val f = EmailExtractor.extract(msg)
        val pdfPath = f.pdf?.let { a -> "email/${h.messageIdHash.take(24)}.pdf".also { store.write(it, a.bytes) } }
        val body = CardMasker.mask(f.bodyText).take(MAX_BODY)
        val c = EmailCandidate(
            accountId = ctx.accountId,
            importJobId = ctx.importJobId,
            messageIdHash = h.messageIdHash,
            needsAi = score.score < ReceiptScorer.KEEP && !trusted,
            sender = h.from,
            senderName = h.fromName,
            subject = h.subject.ifBlank { "(no subject)" },
            snippet = body.replace(Regex("\\s+"), " ").take(SNIPPET),
            receivedAt = h.date ?: Instant.now(),
            score = score.score,
            amountCents = f.totalCents ?: score.amountCents,
            currency = f.currency,
            merchant = f.merchant?.let(MerchantNormalizer::normalize),
            purchaseDate = f.date,
            taxCents = f.taxCents,
            bodyText = body,
            filePath = pdfPath,
            fromSchema = f.fromSchema
        )
        if (!mail.addCandidate(c)) return@withContext Outcome.SKIPPED
        if (trusted && ctx.autoKeepTrusted) {
            keep(listOf(c.id), rule.defaultCategoryId, rule.defaultPayerId, source)
            return@withContext Outcome.AUTO_KEPT
        }
        Outcome.FOUND
    }

    /** Keep found emails: each becomes a receipt in the Inbox, with a PDF (attachment or snapshot) and its email source. */
    suspend fun keep(ids: List<String>, categoryId: String?, payerId: String?, source: Source = Source.EMAIL_FILE): List<String> =
        withContext(Dispatchers.IO) {
            mail.candidates(ids).map { c ->
                val rule = mail.ruleFor(mail.rules(), c.sender)
                val r = Receipt(
                    source = if (c.accountId != null) Source.EMAIL_LIVE else source,
                    merchantName = c.merchant,
                    purchaseDate = c.purchaseDate ?: c.receivedAt.atZone(ZoneId.systemDefault()).toLocalDate(),
                    totalCents = c.amountCents,
                    taxCents = c.taxCents,
                    currency = c.currency,
                    categoryId = categoryId ?: rule?.defaultCategoryId,
                    payerId = payerId ?: rule?.defaultPayerId,
                    track = if ((payerId ?: rule?.defaultPayerId) != null) Track.REIMBURSABLE else Track.PERSONAL,
                    ocrText = c.bodyText,
                    emailSubject = c.subject,
                    reading = !c.fromSchema
                )
                receipts.insert(r, "Kept from email: ${c.subject}")
                val saved = if (c.filePath != null) {
                    images.savePdf(r.id, store.read(c.filePath!!))
                } else {
                    val pdf = SnapshotPdf.make(
                        listOfNotNull(c.senderName, "<${c.sender}>").joinToString(" "),
                        c.subject,
                        c.receivedAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")),
                        c.bodyText.orEmpty()
                    )
                    images.savePdf(r.id, pdf).let { s ->
                        s.copy(
                            files = s.files.map {
                                if (it.kind ==
                                    FileKind.PDF
                                ) {
                                    it.copy(kind = FileKind.EMAIL_SNAPSHOT)
                                } else {
                                    it
                                }
                            }
                        )
                    }
                }
                receipts.addFiles(saved.files)
                mail.saveSource(
                    EmailSource(r.id, c.accountId, c.importJobId, c.messageIdHash, null, c.sender, c.subject, c.receivedAt, c.score)
                )
                if (c.fromSchema) {
                    // Schema markup is the most trusted source: fill in without a second read, then check for duplicates.
                    receipts.markDuplicate(r)
                } else {
                    pipeline.enqueue(r.id)
                }
                r.id
            }.also { mail.markKept(ids) }
        }

    /** The on device AI decides on borderline emails. Only runs while the app is on screen. */
    suspend fun finishWithAi(limit: Int = 25): Int {
        if (!nano.inForeground) return 0
        // No on device AI on this phone: stop saying "checking" and let the user decide in Review.
        // Email text never goes to a cloud AI.
        if (nano.status() == FeatureStatus.UNAVAILABLE) {
            mail.candidatesNeedingAi().forEach { mail.updateCandidate(it.copy(needsAi = false)) }
            return 0
        }
        var done = 0
        for (c in mail.candidatesNeedingAi().take(limit)) {
            val reply = nano.ask(prompt(c)) ?: break
            val json = runCatching { JSONObject(reply.substring(reply.indexOf('{'), reply.lastIndexOf('}') + 1)) }.getOrNull()
            if (json == null) {
                mail.updateCandidate(c.copy(needsAi = false))
            } else if (!json.optBoolean("is_receipt", true)) {
                mail.updateCandidate(c.copy(needsAi = false))
                mail.skip(listOf(c.id))
            } else {
                mail.updateCandidate(
                    c.copy(
                        needsAi = false,
                        merchant =
                        json.optString("merchant").takeIf { it.isNotBlank() && it != "null" }?.let(MerchantNormalizer::normalize)
                            ?: c.merchant,
                        amountCents = json.optString("total").let(Money::parse) ?: c.amountCents,
                        taxCents = json.optString("tax").let(Money::parse) ?: c.taxCents,
                        purchaseDate = runCatching { LocalDate.parse(json.optString("date")) }.getOrNull() ?: c.purchaseDate,
                        currency = json.optString("currency").takeIf { it.length == 3 }?.uppercase() ?: c.currency
                    )
                )
            }
            done++
        }
        return done
    }

    private fun prompt(c: EmailCandidate) = """
        You read one email and decide if it is a real receipt or invoice for money already paid or owed.
        Marketing, shipping updates without a price, and newsletters are not receipts.
        Reply with ONLY JSON: {"is_receipt":true|false,"merchant":string|null,"date":"YYYY-MM-DD"|null,"total":"12.34"|null,"tax":"1.23"|null,"currency":"USD"|null}
        From: ${c.senderName ?: ""} <${c.sender}>
        Subject: ${c.subject}
        Body:
        ${c.bodyText.orEmpty().take(2500)}
    """.trimIndent()

    companion object {
        const val MAX_BODY = 20_000
        const val SNIPPET = 160
    }
}
