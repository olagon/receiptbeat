package org.kealohalabs.receiptbeat.core.data

import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.kealohalabs.receiptbeat.core.database.ReceiptDatabase
import org.kealohalabs.receiptbeat.core.model.AutoScanSettings
import org.kealohalabs.receiptbeat.core.model.CandidateState
import org.kealohalabs.receiptbeat.core.model.EmailCandidate
import org.kealohalabs.receiptbeat.core.model.ImportJob
import org.kealohalabs.receiptbeat.core.model.MailAccount
import org.kealohalabs.receiptbeat.core.model.ScanRange
import org.kealohalabs.receiptbeat.core.model.ScanRun
import org.kealohalabs.receiptbeat.core.model.SenderRule
import org.kealohalabs.receiptbeat.core.model.SenderRuleKind
import org.kealohalabs.receiptbeat.mail.ScanRanges

/** Mail accounts, import jobs, found emails (Review) and the rules that make scans smarter. */
@Singleton
class MailRepository @Inject constructor(
    private val db: ReceiptDatabase,
    private val secrets: SecretStore,
    private val catalog: CatalogRepository
) {
    private val dao = db.mail()

    val accounts: Flow<List<MailAccount>> = dao.observeAccounts()
    val jobs: Flow<List<ImportJob>> = dao.observeJobs()
    val candidates: Flow<List<EmailCandidate>> = dao.observeNewCandidates()
    val reviewCount: Flow<Int> = candidates.map { it.size }
    val runs: Flow<List<ScanRun>> = dao.observeRuns()
    val ranges: Flow<List<ScanRange>> = dao.observeAllRanges()
    val autoScan: Flow<List<AutoScanSettings>> = dao.observeAutoScan()

    suspend fun accountsNow() = dao.accounts()

    suspend fun account(id: String) = dao.account(id)

    suspend fun saveAccount(a: MailAccount, secret: String?) {
        if (secret != null) secrets.put(SecretStore.mail(a.id), secret)
        dao.upsertAccount(a)
        if (dao.autoScan(a.id) == null) dao.upsertAutoScan(AutoScanSettings(a.id))
    }

    fun secret(accountId: String) = secrets.get(SecretStore.mail(accountId))

    /** Removes the account and its password in one tap. Receipts already kept stay. */
    suspend fun removeAccount(a: MailAccount) {
        secrets.remove(SecretStore.mail(a.id))
        dao.deleteAccount(a)
    }

    suspend fun job(id: String) = dao.job(id)

    suspend fun saveJob(j: ImportJob) = dao.upsertJob(j)

    suspend fun seen(hash: String) = dao.seen(hash)

    /** False when this message was already found before. */
    suspend fun addCandidate(c: EmailCandidate): Boolean = dao.insertCandidate(c) != -1L

    suspend fun updateCandidate(c: EmailCandidate) = dao.upsertCandidate(c)

    suspend fun candidatesNeedingAi() = dao.candidatesNeedingAi()

    suspend fun candidates(ids: List<String>) = dao.candidates(ids)

    suspend fun skip(ids: List<String>) = dao.setState(ids, CandidateState.SKIPPED)

    suspend fun markKept(ids: List<String>) = dao.setState(ids, CandidateState.KEPT)

    suspend fun unskip(ids: List<String>) = dao.setState(ids, CandidateState.NEW)

    suspend fun saveSource(s: org.kealohalabs.receiptbeat.core.model.EmailSource) = dao.insertSource(s)

    suspend fun source(receiptId: String) = dao.source(receiptId)

    suspend fun rules(): List<SenderRule> = catalog.senderRules()

    /** Mute: never show this sender again, and skip what is waiting from them now. */
    suspend fun mute(sender: String) {
        catalog.saveSenderRule(SenderRule(senderOrDomain = sender.lowercase(), rule = SenderRuleKind.MUTE))
        skip(dao.allCandidates().filter { it.state == CandidateState.NEW && matches(sender, it.sender) }.map { it.id })
    }

    suspend fun alwaysKeep(sender: String, categoryId: String?, payerId: String?) {
        val old = catalog.senderRules().firstOrNull { it.senderOrDomain == sender.lowercase() }
        catalog.saveSenderRule(
            SenderRule(
                old?.id ?: org.kealohalabs.receiptbeat.core.model.newId(),
                null,
                sender.lowercase(),
                SenderRuleKind.ALWAYS_KEEP,
                categoryId,
                payerId
            )
        )
    }

    suspend fun deleteRule(r: SenderRule) = catalog.deleteSenderRule(r)

    fun ruleFor(rules: List<SenderRule>, sender: String): SenderRule? = rules.firstOrNull { matches(it.senderOrDomain, sender) }

    // Connected accounts: remembered scan ranges.

    suspend fun gaps(accountId: String, folder: String, from: LocalDate, to: LocalDate) = ScanRanges.gaps(
        dao.ranges(accountId, folder).filter { it.queryVersion == QUERY_VERSION }.map { it.fromDate to it.toDate },
        from to to
    )

    suspend fun rangeDone(accountId: String, folder: String, from: LocalDate, to: LocalDate) =
        dao.insertRange(ScanRange(accountId = accountId, folder = folder, fromDate = from, toDate = to, queryVersion = QUERY_VERSION))

    suspend fun saveRun(r: ScanRun) = dao.upsertRun(r)

    suspend fun autoScanFor(accountId: String) = dao.autoScan(accountId) ?: AutoScanSettings(accountId)

    suspend fun saveAutoScan(s: AutoScanSettings) = dao.upsertAutoScan(s)

    companion object {
        /** Bump when the server search words change, so old ranges get searched again. */
        const val QUERY_VERSION = 1

        /** A rule for "uber.com" covers "receipts@uber.com" and "noreply@m.uber.com". */
        fun matches(rule: String, sender: String): Boolean {
            val r = rule.lowercase()
            val s = sender.lowercase()
            val domain = s.substringAfter('@')
            return s == r || domain == r || domain.endsWith(".$r")
        }
    }
}
