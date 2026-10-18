package org.kealohalabs.receiptbeat.ui.review

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kealohalabs.receiptbeat.core.data.CatalogRepository
import org.kealohalabs.receiptbeat.core.data.MailRepository
import org.kealohalabs.receiptbeat.core.model.AutoScanSettings
import org.kealohalabs.receiptbeat.core.model.Category
import org.kealohalabs.receiptbeat.core.model.EmailCandidate
import org.kealohalabs.receiptbeat.core.model.ImportJob
import org.kealohalabs.receiptbeat.core.model.MailAccount
import org.kealohalabs.receiptbeat.core.model.MailAuth
import org.kealohalabs.receiptbeat.core.model.Payer
import org.kealohalabs.receiptbeat.core.model.ScanRange
import org.kealohalabs.receiptbeat.core.model.ScanRun
import org.kealohalabs.receiptbeat.core.model.SenderRule
import org.kealohalabs.receiptbeat.email.EmailImportWorker
import org.kealohalabs.receiptbeat.email.EmailIngest
import org.kealohalabs.receiptbeat.email.MailScanWorker
import org.kealohalabs.receiptbeat.mail.ImapClient
import org.kealohalabs.receiptbeat.mail.ImapConfig

data class SenderGroup(val sender: String, val name: String, val items: List<EmailCandidate>) {
    val totalCents get() = items.sumOf { it.amountCents ?: 0 }
}

data class ReviewState(
    val groups: List<SenderGroup> = emptyList(),
    val count: Int = 0,
    val categories: List<Category> = emptyList(),
    val payers: List<Payer> = emptyList(),
    val accounts: List<MailAccount> = emptyList(),
    val jobs: List<ImportJob> = emptyList(),
    val ranges: List<ScanRange> = emptyList(),
    val runs: List<ScanRun> = emptyList(),
    val rules: List<SenderRule> = emptyList(),
    val autoScan: Map<String, AutoScanSettings> = emptyMap(),
    val selected: Set<String> = emptySet(),
    val aiWorking: Boolean = false,
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mail: MailRepository,
    private val ingest: EmailIngest,
    catalog: CatalogRepository,
) : ViewModel() {
    private val selected = MutableStateFlow<Set<String>>(emptySet())
    private val ai = MutableStateFlow(false)

    val state: StateFlow<ReviewState> = combine(
        mail.candidates,
        combine(catalog.categories, catalog.payers, catalog.senderRules) { c, p, r -> Triple(c, p, r) },
        combine(mail.accounts, mail.jobs, mail.ranges, mail.runs, mail.autoScan) { a, j, r, runs, auto -> listOf(a, j, r, runs, auto) },
        selected,
        ai,
    ) { cands, cat, m, sel, aiOn ->
        @Suppress("UNCHECKED_CAST")
        val groups = cands.groupBy { it.sender.substringAfter('@') }
            .map { (domain, items) -> SenderGroup(domain, items.firstNotNullOfOrNull { it.merchant ?: it.senderName } ?: domain, items) }
            .sortedByDescending { it.items.size }
        ReviewState(
            groups = groups,
            count = cands.size,
            categories = cat.first.filter { !it.hidden },
            payers = cat.second,
            rules = cat.third,
            accounts = m[0] as List<MailAccount>,
            jobs = m[1] as List<ImportJob>,
            ranges = m[2] as List<ScanRange>,
            runs = m[3] as List<ScanRun>,
            autoScan = (m[4] as List<AutoScanSettings>).associateBy { it.accountId },
            selected = sel,
            aiWorking = aiOn,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewState())

    /** The app is on screen, so the on device AI can decide on borderline emails now. */
    fun finishWithAi() {
        if (ai.value) return
        viewModelScope.launch {
            ai.value = true
            runCatching { ingest.finishWithAi() }
            ai.value = false
        }
    }

    fun toggle(id: String) = selected.update { if (id in it) it - id else it + id }

    fun selectGroup(g: SenderGroup) = selected.update { it + g.items.map { c -> c.id } }

    fun clearSelection() = selected.update { emptySet() }

    fun keep(ids: List<String>, categoryId: String? = null, payerId: String? = null) {
        viewModelScope.launch {
            ingest.keep(ids, categoryId, payerId)
            selected.update { it - ids.toSet() }
        }
    }

    fun skip(ids: List<String>) {
        viewModelScope.launch {
            mail.skip(ids)
            selected.update { it - ids.toSet() }
        }
    }

    fun unskip(ids: List<String>) {
        viewModelScope.launch { mail.unskip(ids) }
    }

    fun mute(sender: String) {
        viewModelScope.launch { mail.mute(sender) }
    }

    fun alwaysKeep(sender: String, categoryId: String?, payerId: String?) {
        viewModelScope.launch { mail.alwaysKeep(sender, categoryId, payerId) }
    }

    fun deleteRule(r: SenderRule) {
        viewModelScope.launch { mail.deleteRule(r) }
    }

    fun importFile(uri: Uri, from: LocalDate, to: LocalDate, done: (Boolean) -> Unit) {
        viewModelScope.launch { done(EmailImportWorker.start(context, mail, uri, from, to) != null) }
    }

    fun resume(job: ImportJob) = EmailImportWorker.enqueue(context, job.id)

    /** Tries the password first, so a typo is caught before anything is saved. */
    fun connect(email: String, host: String, password: String, from: LocalDate, done: (String?) -> Unit) {
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching { ImapClient(ImapConfig(host, user = email, secret = password)).use { it.connect() } }
                    .exceptionOrNull()?.let { MailScanWorker.friendly(it as? Exception ?: Exception(it)) }
            }
            if (error == null) {
                val account = MailAccount(auth = MailAuth.APP_PASSWORD, email = email.trim().lowercase(), imapHost = host, scanFrom = from)
                mail.saveAccount(account, password)
                MailScanWorker.schedule(context, account, mail.autoScanFor(account.id))
                MailScanWorker.scanNow(context, account.id)
            }
            done(error)
        }
    }

    fun scanNow(account: MailAccount) = MailScanWorker.scanNow(context, account.id)

    /** Extend back: search older months too. Only the missing stretch gets searched. */
    fun extendBack(account: MailAccount, months: Long) {
        viewModelScope.launch {
            val from = account.scanFrom.minusMonths(months)
            mail.saveAccount(account.copy(scanFrom = from), null)
            MailScanWorker.scanNow(context, account.id, from)
        }
    }

    fun saveAutoScan(account: MailAccount, s: AutoScanSettings) {
        viewModelScope.launch {
            mail.saveAutoScan(s)
            MailScanWorker.schedule(context, account, s)
        }
    }

    fun remove(account: MailAccount) {
        viewModelScope.launch {
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork("scan-${account.id}")
            mail.removeAccount(account)
        }
    }
}
