package org.kealohalabs.receiptbeat.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.kealohalabs.receiptbeat.core.model.AutoScanSettings
import org.kealohalabs.receiptbeat.core.model.CandidateState
import org.kealohalabs.receiptbeat.core.model.Category
import org.kealohalabs.receiptbeat.core.model.Correction
import org.kealohalabs.receiptbeat.core.model.EmailCandidate
import org.kealohalabs.receiptbeat.core.model.EmailSource
import org.kealohalabs.receiptbeat.core.model.FieldConfidence
import org.kealohalabs.receiptbeat.core.model.ImportJob
import org.kealohalabs.receiptbeat.core.model.LineItem
import org.kealohalabs.receiptbeat.core.model.MailAccount
import org.kealohalabs.receiptbeat.core.model.MerchantMemory
import org.kealohalabs.receiptbeat.core.model.Payer
import org.kealohalabs.receiptbeat.core.model.Project
import org.kealohalabs.receiptbeat.core.model.Receipt
import org.kealohalabs.receiptbeat.core.model.ReceiptEvent
import org.kealohalabs.receiptbeat.core.model.ReceiptFile
import org.kealohalabs.receiptbeat.core.model.Report
import org.kealohalabs.receiptbeat.core.model.ReportItem
import org.kealohalabs.receiptbeat.core.model.ScanRange
import org.kealohalabs.receiptbeat.core.model.ScanRun
import org.kealohalabs.receiptbeat.core.model.SenderRule
import org.kealohalabs.receiptbeat.core.model.Split
import org.kealohalabs.receiptbeat.core.model.TaxLine

@Dao
interface ReceiptDao {
    @Query("SELECT * FROM Receipt WHERE deletedAt IS NULL ORDER BY COALESCE(purchaseDate, createdAt / 86400000) DESC, createdAt DESC")
    fun observeAll(): Flow<List<Receipt>>

    @Query("SELECT * FROM Receipt WHERE deletedAt IS NULL")
    suspend fun all(): List<Receipt>

    @Query("SELECT * FROM Receipt WHERE id = :id")
    fun observe(id: String): Flow<Receipt?>

    @Query("SELECT * FROM Receipt WHERE id = :id")
    suspend fun get(id: String): Receipt?

    @Query("SELECT * FROM Receipt WHERE id IN (:ids)")
    suspend fun getMany(ids: List<String>): List<Receipt>

    @Query(
        """SELECT Receipt.* FROM Receipt JOIN ReceiptFts ON Receipt.rowid = ReceiptFts.rowid
           WHERE ReceiptFts MATCH :query AND Receipt.deletedAt IS NULL"""
    )
    suspend fun search(query: String): List<Receipt>

    @Upsert suspend fun upsert(receipt: Receipt)

    @Upsert suspend fun upsertAll(receipts: List<Receipt>)

    @Query("SELECT * FROM Receipt WHERE deletedAt IS NOT NULL")
    fun observeTrash(): Flow<List<Receipt>>

    @Query("SELECT id FROM Receipt WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun trashedBefore(before: Long): List<String>

    @Query("DELETE FROM Receipt WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeDeleted(before: Long)

    @Query("SELECT * FROM Receipt WHERE reading = 1 AND deletedAt IS NULL")
    suspend fun stillReading(): List<Receipt>

    @Query(
        """SELECT * FROM Receipt WHERE deletedAt IS NULL AND id != :id AND (
             (totalCents = :total AND purchaseDate BETWEEN :fromDay AND :toDay) OR (imageHash IS NOT NULL AND imageHash = :hash))"""
    )
    suspend fun possibleDuplicates(id: String, total: Long?, fromDay: Long?, toDay: Long?, hash: String?): List<Receipt>

    @Query("SELECT * FROM Receipt WHERE deletedAt IS NULL AND imageHash IS NOT NULL AND id != :id")
    suspend fun withHashes(id: String): List<Receipt>

    // Files, confidences, line items, history

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<ReceiptFile>)

    @Query("SELECT * FROM ReceiptFile WHERE receiptId = :receiptId ORDER BY kind, page")
    fun observeFiles(receiptId: String): Flow<List<ReceiptFile>>

    @Query("SELECT * FROM ReceiptFile WHERE receiptId = :receiptId ORDER BY kind, page")
    suspend fun files(receiptId: String): List<ReceiptFile>

    @Query("SELECT * FROM ReceiptFile WHERE kind = 'THUMB'")
    fun observeThumbs(): Flow<List<ReceiptFile>>

    @Query("SELECT * FROM ReceiptFile")
    suspend fun allFiles(): List<ReceiptFile>

    @Query("DELETE FROM ReceiptFile WHERE receiptId = :receiptId")
    suspend fun deleteFiles(receiptId: String)

    @Upsert suspend fun upsertConfidences(items: List<FieldConfidence>)

    @Query("SELECT * FROM FieldConfidence WHERE receiptId = :receiptId")
    fun observeConfidences(receiptId: String): Flow<List<FieldConfidence>>

    @Query("DELETE FROM FieldConfidence WHERE receiptId = :receiptId AND field = :field")
    suspend fun deleteConfidence(receiptId: String, field: String)

    @Query("SELECT * FROM FieldConfidence WHERE confidence = 'LOW'")
    fun observeLowConfidence(): Flow<List<FieldConfidence>>

    @Query("SELECT * FROM FieldConfidence")
    suspend fun allConfidences(): List<FieldConfidence>

    @Query("DELETE FROM LineItem WHERE receiptId = :receiptId")
    suspend fun deleteLineItems(receiptId: String)

    @Insert suspend fun insertLineItems(items: List<LineItem>)

    @Query("SELECT * FROM LineItem WHERE receiptId = :receiptId")
    fun observeLineItems(receiptId: String): Flow<List<LineItem>>

    @Query("SELECT * FROM LineItem")
    suspend fun allLineItems(): List<LineItem>

    @Query("SELECT * FROM Split WHERE receiptId = :receiptId")
    fun observeSplits(receiptId: String): Flow<List<Split>>

    @Query("SELECT * FROM Split")
    fun observeAllSplits(): Flow<List<Split>>

    @Query("SELECT * FROM Split")
    suspend fun allSplits(): List<Split>

    @Query("DELETE FROM Split WHERE receiptId = :receiptId")
    suspend fun deleteSplits(receiptId: String)

    @Insert suspend fun insertSplits(items: List<Split>)

    @Insert suspend fun insertEvent(event: ReceiptEvent)

    @Query("SELECT * FROM ReceiptEvent WHERE receiptId = :receiptId ORDER BY at DESC")
    fun observeEvents(receiptId: String): Flow<List<ReceiptEvent>>

    @Query("SELECT * FROM ReceiptEvent WHERE receiptId = :receiptId")
    suspend fun eventsFor(receiptId: String): List<ReceiptEvent>

    @Query("SELECT * FROM ReceiptEvent")
    suspend fun allEvents(): List<ReceiptEvent>

    @Insert suspend fun insertCorrection(correction: Correction)

    @Query("SELECT * FROM Correction")
    suspend fun allCorrections(): List<Correction>
}

@Dao
interface CatalogDao {
    @Query("SELECT * FROM Category ORDER BY sortOrder, name")
    fun observeCategories(): Flow<List<Category>>

    @Query("SELECT * FROM Category")
    suspend fun categories(): List<Category>

    @Upsert suspend fun upsertCategories(items: List<Category>)

    @Delete suspend fun deleteCategory(item: Category)

    @Query("UPDATE Receipt SET categoryId = :into WHERE categoryId = :from")
    suspend fun moveCategory(from: String, into: String)

    @Query("SELECT * FROM TaxLine")
    fun observeTaxLines(): Flow<List<TaxLine>>

    @Query("SELECT * FROM TaxLine")
    suspend fun taxLines(): List<TaxLine>

    @Upsert suspend fun upsertTaxLines(items: List<TaxLine>)

    @Query("SELECT * FROM Payer ORDER BY name")
    fun observePayers(): Flow<List<Payer>>

    @Query("SELECT * FROM Payer")
    suspend fun payers(): List<Payer>

    @Upsert suspend fun upsertPayer(item: Payer)

    @Delete suspend fun deletePayer(item: Payer)

    @Query("SELECT * FROM Project ORDER BY name")
    fun observeProjects(): Flow<List<Project>>

    @Query("SELECT * FROM Project")
    suspend fun projects(): List<Project>

    @Upsert suspend fun upsertProject(item: Project)

    @Delete suspend fun deleteProject(item: Project)

    @Query("SELECT * FROM MerchantMemory WHERE merchantKey = :key")
    suspend fun merchantMemory(key: String): MerchantMemory?

    @Query("SELECT * FROM MerchantMemory")
    suspend fun allMerchantMemory(): List<MerchantMemory>

    @Upsert suspend fun upsertMerchantMemory(item: MerchantMemory)

    @Query("SELECT * FROM SenderRule")
    fun observeSenderRules(): Flow<List<SenderRule>>

    @Query("SELECT * FROM SenderRule")
    suspend fun senderRules(): List<SenderRule>

    @Upsert suspend fun upsertSenderRule(item: SenderRule)

    @Delete suspend fun deleteSenderRule(item: SenderRule)
}

@Dao
interface ReportDao {
    @Query("SELECT * FROM Report ORDER BY createdAt DESC")
    fun observeReports(): Flow<List<Report>>

    @Query("SELECT * FROM Report")
    suspend fun reports(): List<Report>

    @Query("SELECT * FROM Report WHERE id = :id")
    suspend fun get(id: String): Report?

    @Query("SELECT COUNT(*) FROM Report WHERE number LIKE :prefix || '%'")
    suspend fun countWithPrefix(prefix: String): Int

    @Upsert suspend fun upsert(report: Report)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItems(items: List<ReportItem>)

    @Query("SELECT * FROM ReportItem")
    fun observeItems(): Flow<List<ReportItem>>

    @Query("SELECT * FROM ReportItem")
    suspend fun allItems(): List<ReportItem>

    @Query("SELECT receiptId FROM ReportItem WHERE reportId = :reportId")
    suspend fun receiptIds(reportId: String): List<String>
}

@Dao
interface MailDao {
    @Query("SELECT * FROM MailAccount")
    fun observeAccounts(): Flow<List<MailAccount>>

    @Query("SELECT * FROM MailAccount")
    suspend fun accounts(): List<MailAccount>

    @Query("SELECT * FROM MailAccount WHERE id = :id")
    suspend fun account(id: String): MailAccount?

    @Upsert suspend fun upsertAccount(item: MailAccount)

    @Delete suspend fun deleteAccount(item: MailAccount)

    @Query("SELECT * FROM ImportJob ORDER BY startedAt DESC")
    fun observeJobs(): Flow<List<ImportJob>>

    @Query("SELECT * FROM ImportJob WHERE id = :id")
    suspend fun job(id: String): ImportJob?

    @Upsert suspend fun upsertJob(item: ImportJob)

    @Query("SELECT * FROM EmailCandidate WHERE state = 'NEW' ORDER BY receivedAt DESC")
    fun observeNewCandidates(): Flow<List<EmailCandidate>>

    @Query("SELECT * FROM EmailCandidate WHERE state = 'NEW' AND needsAi = 1")
    suspend fun candidatesNeedingAi(): List<EmailCandidate>

    @Query("SELECT * FROM EmailCandidate WHERE id IN (:ids)")
    suspend fun candidates(ids: List<String>): List<EmailCandidate>

    @Query("SELECT * FROM EmailCandidate")
    suspend fun allCandidates(): List<EmailCandidate>

    @Query("SELECT EXISTS(SELECT 1 FROM EmailCandidate WHERE messageIdHash = :hash)")
    suspend fun seen(hash: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCandidate(item: EmailCandidate): Long

    @Upsert suspend fun upsertCandidate(item: EmailCandidate)

    @Query("UPDATE EmailCandidate SET state = :state WHERE id IN (:ids)")
    suspend fun setState(ids: List<String>, state: CandidateState)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(item: EmailSource)

    @Query("SELECT * FROM EmailSource WHERE receiptId = :receiptId")
    suspend fun source(receiptId: String): EmailSource?

    @Query("SELECT * FROM EmailSource")
    suspend fun allSources(): List<EmailSource>

    @Query("SELECT * FROM ScanRange WHERE accountId = :accountId")
    fun observeRanges(accountId: String): Flow<List<ScanRange>>

    @Query("SELECT * FROM ScanRange")
    fun observeAllRanges(): Flow<List<ScanRange>>

    @Query("SELECT * FROM ScanRange WHERE accountId = :accountId AND folder = :folder")
    suspend fun ranges(accountId: String, folder: String): List<ScanRange>

    @Insert suspend fun insertRange(item: ScanRange)

    @Query("SELECT * FROM ScanRun ORDER BY startedAt DESC LIMIT 100")
    fun observeRuns(): Flow<List<ScanRun>>

    @Upsert suspend fun upsertRun(item: ScanRun)

    @Query("SELECT * FROM ScanRun WHERE accountId = :accountId ORDER BY startedAt DESC LIMIT 1")
    suspend fun lastRun(accountId: String): ScanRun?

    @Query("SELECT * FROM AutoScanSettings WHERE accountId = :accountId")
    suspend fun autoScan(accountId: String): AutoScanSettings?

    @Query("SELECT * FROM AutoScanSettings")
    fun observeAutoScan(): Flow<List<AutoScanSettings>>

    @Upsert suspend fun upsertAutoScan(item: AutoScanSettings)
}
