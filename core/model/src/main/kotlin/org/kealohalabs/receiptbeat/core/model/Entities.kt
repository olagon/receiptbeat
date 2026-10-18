package org.kealohalabs.receiptbeat.core.model

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

// Plain data classes that Room stores directly. IDs are UUID strings so records can merge later.
// Money is always whole cents in a Long.

fun newId(): String = UUID.randomUUID().toString()

@Entity(
    indices = [
        Index("status"), Index("track"), Index("payerId"), Index("categoryId"),
        Index("purchaseDate"), Index("deletedAt"), Index("imageHash")
    ]
)
data class Receipt(
    @PrimaryKey val id: String = newId(),
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val source: Source = Source.CAMERA,
    val merchantName: String? = null,
    val merchantRaw: String? = null,
    val merchantAddress: String? = null,
    val purchaseDate: LocalDate? = null,
    val purchaseTime: LocalTime? = null,
    val subtotalCents: Long? = null,
    val taxCents: Long? = null,
    val tipCents: Long? = null,
    val totalCents: Long? = null,
    val currency: String = "USD",
    val homeCurrencyTotalCents: Long? = null,
    val fxRate: Double? = null,
    val paymentBrand: String? = null,
    val paymentLast4: String? = null,
    val receiptNumber: String? = null,
    val categoryId: String? = null,
    val payerId: String? = null,
    val projectId: String? = null,
    val track: Track = Track.PERSONAL,
    val status: Status = Status.INBOX,
    val reimbursedCents: Long? = null,
    val taxYear: Int? = null,
    val locked: Boolean = false,
    val notes: String? = null,
    val ocrText: String? = null,
    val emailSubject: String? = null,
    val imageHash: String? = null,
    val duplicateOfId: String? = null,
    /** True while OCR and extraction are still running in the background. */
    val reading: Boolean = false,
    val rejectReason: String? = null,
    val deletedAt: Instant? = null
)

/** Full text search over merchant, notes, receipt text and email subject. Room keeps it in sync. */
@Fts4(contentEntity = Receipt::class)
@Entity
data class ReceiptFts(
    val merchantName: String?,
    val notes: String?,
    val ocrText: String?,
    val emailSubject: String?
)

@Entity(indices = [Index("receiptId")])
data class ReceiptFile(
    @PrimaryKey val id: String = newId(),
    val receiptId: String,
    val kind: FileKind,
    val path: String,
    val mime: String,
    val bytes: Long,
    val page: Int = 0
)

@Entity(primaryKeys = ["receiptId", "field"])
data class FieldConfidence(
    val receiptId: String,
    val field: String,
    val value: String?,
    val confidence: Confidence,
    val method: ExtractionMethod,
    /** "x,y,w,h" on the display image, or null. */
    val box: String? = null
)

@Entity(indices = [Index("receiptId")])
data class LineItem(
    @PrimaryKey val id: String = newId(),
    val receiptId: String,
    val name: String,
    val qty: Double = 1.0,
    val unitCents: Long? = null,
    val totalCents: Long,
    val categoryId: String? = null,
    val payerId: String? = null
)

@Entity(indices = [Index("receiptId")])
data class Split(
    @PrimaryKey val id: String = newId(),
    val receiptId: String,
    val amountCents: Long,
    val categoryId: String,
    val payerId: String? = null,
    val track: Track
)

@Entity
data class Category(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val icon: String,
    val color: Long,
    val taxLineId: String? = null,
    val sortOrder: Int = 0,
    val hidden: Boolean = false
)

@Entity
data class TaxLine(
    @PrimaryKey val id: String,
    val presetId: String,
    val code: String,
    val label: String,
    val deductiblePercent: Int = 100
)

@Entity
data class Payer(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val defaultTrack: Track = Track.REIMBURSABLE,
    val submitDeadlineDays: Int? = null,
    val reminderDays: Int = 30,
    val reportStyle: String = "standard",
    val email: String? = null
)

@Entity
data class Project(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val payerId: String? = null
)

@Entity
data class Report(
    @PrimaryKey val id: String = newId(),
    val number: String,
    val payerId: String?,
    val title: String,
    val createdAt: Instant = Instant.now(),
    val submittedAt: Instant? = null,
    val paidAt: Instant? = null,
    val totalCents: Long,
    val paidCents: Long? = null,
    val pdfPath: String? = null,
    val csvPath: String? = null
)

@Entity(primaryKeys = ["reportId", "receiptId"], indices = [Index("receiptId")])
data class ReportItem(val reportId: String, val receiptId: String)

@Entity
data class MailAccount(
    @PrimaryKey val id: String = newId(),
    val auth: MailAuth,
    val email: String,
    val imapHost: String,
    val imapPort: Int = 993,
    /** Highest UID seen per folder, stored as "folder=uid;folder=uid". */
    val lastUidByFolder: String? = null,
    val lastScanAt: Instant? = null,
    val scanFrom: LocalDate,
    /** Folders to search, one per line. Null means the default (Gmail All Mail, or Inbox plus Archive). */
    val folders: String? = null,
    /** The user's own search words. Null means the built in receipt words. For Gmail, Gmail search syntax. */
    val searchWords: String? = null
)

@Entity
data class ImportJob(
    @PrimaryKey val id: String = newId(),
    val fileName: String,
    val uri: String,
    val format: MailFormat,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val bytesTotal: Long = 0,
    val bytesRead: Long = 0,
    val messagesSeen: Int = 0,
    val candidates: Int = 0,
    val status: JobStatus = JobStatus.RUNNING,
    /** Messages already handled. A resumed job skips this many. */
    val resumeOffset: Long = 0,
    val startedAt: Instant = Instant.now(),
    val finishedAt: Instant? = null,
    val error: String? = null
)

@Entity(indices = [Index("messageIdHash")])
data class EmailSource(
    @PrimaryKey val receiptId: String,
    val accountId: String? = null,
    val importJobId: String? = null,
    val messageIdHash: String,
    val threadId: String? = null,
    val sender: String,
    val subject: String,
    val receivedAt: Instant,
    val score: Int
)

@Entity(indices = [Index(value = ["messageIdHash"], unique = true), Index("state"), Index("sender")])
data class EmailCandidate(
    @PrimaryKey val id: String = newId(),
    val accountId: String? = null,
    val importJobId: String? = null,
    val messageIdHash: String,
    val needsAi: Boolean,
    val sender: String,
    val senderName: String? = null,
    val subject: String,
    val snippet: String,
    val receivedAt: Instant,
    val score: Int,
    val amountCents: Long? = null,
    val currency: String = "USD",
    val merchant: String? = null,
    val purchaseDate: LocalDate? = null,
    val taxCents: Long? = null,
    /** Clean text of the email body, used for extraction and the PDF snapshot. */
    val bodyText: String? = null,
    /** Path of a saved PDF attachment, if any. */
    val filePath: String? = null,
    /** Fields came from schema.org markup in the email, the most trusted source. */
    val fromSchema: Boolean = false,
    val state: CandidateState = CandidateState.NEW
)

@Entity
data class SenderRule(
    @PrimaryKey val id: String = newId(),
    val accountId: String? = null,
    val senderOrDomain: String,
    val rule: SenderRuleKind,
    val defaultCategoryId: String? = null,
    val defaultPayerId: String? = null
)

@Entity(indices = [Index("accountId")])
data class ScanRange(
    @PrimaryKey val id: String = newId(),
    val accountId: String,
    val folder: String,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val queryVersion: Int,
    val completedAt: Instant = Instant.now()
)

@Entity(indices = [Index("accountId")])
data class ScanRun(
    @PrimaryKey val id: String = newId(),
    val accountId: String,
    val startedAt: Instant = Instant.now(),
    val finishedAt: Instant? = null,
    val trigger: ScanTrigger,
    val checked: Int = 0,
    val found: Int = 0,
    val autoKept: Int = 0,
    val error: String? = null,
    val resumeToken: String? = null
)

@Entity
data class AutoScanSettings(
    @PrimaryKey val accountId: String,
    val frequency: ScanFrequency = ScanFrequency.DAILY,
    val preferredHour: Int = 2,
    val wifiOnly: Boolean = true,
    val chargingOnly: Boolean = true,
    val mode: ScanMode = ScanMode.FIND_AND_EXTRACT
)

@Entity
data class MerchantMemory(
    @PrimaryKey val merchantKey: String,
    val lastCategoryId: String?,
    val lastPayerId: String?,
    val lastTrack: Track? = null,
    val timesUsed: Int
)

@Entity(indices = [Index("receiptId")])
data class Correction(
    @PrimaryKey val id: String = newId(),
    val receiptId: String,
    val field: String,
    val oldValue: String?,
    val newValue: String?,
    val at: Instant = Instant.now()
)

/** Receipt history shown at the bottom of the detail screen. */
@Entity(indices = [Index("receiptId")])
data class ReceiptEvent(
    @PrimaryKey val id: String = newId(),
    val receiptId: String,
    val text: String,
    val at: Instant = Instant.now()
)
