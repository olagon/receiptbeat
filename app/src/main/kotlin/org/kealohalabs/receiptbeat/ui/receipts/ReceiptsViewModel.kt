package org.kealohalabs.receiptbeat.ui.receipts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.kealohalabs.receiptbeat.core.data.CatalogRepository
import org.kealohalabs.receiptbeat.core.data.ReceiptRepository
import org.kealohalabs.receiptbeat.core.data.SettingsRepository
import org.kealohalabs.receiptbeat.core.model.Category
import org.kealohalabs.receiptbeat.core.model.Money
import org.kealohalabs.receiptbeat.core.model.Payer
import org.kealohalabs.receiptbeat.core.model.Receipt
import org.kealohalabs.receiptbeat.core.model.Source
import org.kealohalabs.receiptbeat.core.model.Status
import org.kealohalabs.receiptbeat.core.model.Track
import org.kealohalabs.receiptbeat.ui.ReceiptsRoute

enum class Sort { DATE, AMOUNT, MERCHANT }

enum class SourceFilter { CAMERA, EMAIL, FILE }

data class ReceiptFilter(
    val query: String = "",
    val statuses: Set<Status> = emptySet(),
    val track: Track? = null,
    val categoryId: String? = null,
    val payerId: String? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val source: SourceFilter? = null,
    val needsLook: Boolean = false,
    val sort: Sort = Sort.DATE
) {
    val active get() = statuses.isNotEmpty() ||
        track != null ||
        categoryId != null ||
        payerId != null ||
        from != null ||
        source != null ||
        needsLook

    companion object {
        /** Deep links from Home tiles and reminders. */
        fun preset(name: String?): ReceiptFilter = when (name) {
            "inbox" -> ReceiptFilter(statuses = setOf(Status.INBOX))
            "ready" -> ReceiptFilter(statuses = setOf(Status.INBOX, Status.READY, Status.REJECTED), track = Track.REIMBURSABLE)
            "owed" -> ReceiptFilter(statuses = setOf(Status.SUBMITTED, Status.PARTLY_REIMBURSED), track = Track.REIMBURSABLE)
            "deductible" -> ReceiptFilter(track = Track.TAX, from = LocalDate.now().withDayOfYear(1))
            "attention" -> ReceiptFilter(needsLook = true)
            "paid" -> ReceiptFilter(statuses = setOf(Status.REIMBURSED, Status.PARTLY_REIMBURSED), track = Track.REIMBURSABLE)
            else -> when {
                // "category:meals" or "from:2026-10-01" from the dashboard charts
                name?.startsWith("category:") == true -> ReceiptFilter(categoryId = name.substringAfter(':').ifBlank { null })
                name?.startsWith("from:") == true -> ReceiptFilter(
                    from = runCatching {
                        LocalDate.parse(name.substringAfter(':'))
                    }.getOrNull()
                )
                name?.startsWith("month:") == true -> runCatching { YearMonth.parse(name.substringAfter(':')) }.getOrNull()
                    ?.let { ReceiptFilter(from = it.atDay(1), to = it.atEndOfMonth()) } ?: ReceiptFilter()
                else -> ReceiptFilter()
            }
        }
    }
}

data class MonthGroup(val month: YearMonth?, val totalCents: Long, val items: List<Receipt>)

data class ReceiptsState(
    val groups: List<MonthGroup> = emptyList(),
    val count: Int = 0,
    val thumbs: Map<String, String> = emptyMap(),
    val low: Set<String> = emptySet(),
    val categories: Map<String, Category> = emptyMap(),
    val payers: Map<String, Payer> = emptyMap(),
    val filter: ReceiptFilter = ReceiptFilter(),
    val selected: Set<String> = emptySet(),
    val hideAmounts: Boolean = false,
    val loaded: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ReceiptsViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val repo: ReceiptRepository,
    catalog: CatalogRepository,
    settings: SettingsRepository
) : ViewModel() {
    private val filter = MutableStateFlow(ReceiptFilter.preset(saved.toRoute<ReceiptsRoute>().filter))
    private val selected = MutableStateFlow<Set<String>>(emptySet())

    /** Search hits by id, or null when the search box is empty. */
    private val hits = filter.map { it.query.trim() }.debounce(200).flatMapLatest { q ->
        if (q.isEmpty()) {
            flowOf(null)
        } else {
            repo.receipts.map {
                // "12.50" or "$12.50" also matches amounts.
                val cents = if (q.any { c -> c.isDigit() } && q.none { c -> c.isLetter() }) Money.parse(q) else null
                repo.search(q).map { r -> r.id }.toSet() + it.filter { r -> cents != null && r.totalCents == cents }.map { r -> r.id }
            }
        }
    }

    private val base = combine(repo.receipts, repo.thumbs, repo.lowConfidence, catalog.categories, catalog.payers) { r, t, l, c, p ->
        Base(r, t, l, c.associateBy { it.id }, p.associateBy { it.id })
    }

    val state: StateFlow<ReceiptsState> = combine(base, filter, hits, selected, settings.settings) { b, f, h, sel, s ->
        val list = b.receipts.filter { r -> matches(r, f, h, b.low) }.let { sort(it, f.sort) }
        val groups = if (f.sort == Sort.DATE) {
            list.groupBy {
                it.purchaseDate?.let(YearMonth::from)
            }.map { (m, items) -> MonthGroup(m, items.sumOf { it.totalCents ?: 0 }, items) }
        } else {
            listOf(MonthGroup(null, list.sumOf { it.totalCents ?: 0 }, list))
        }
        ReceiptsState(groups, list.size, b.thumbs, b.low, b.categories, b.payers, f, sel, s.hideAmounts, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReceiptsState())

    private data class Base(
        val receipts: List<Receipt>,
        val thumbs: Map<String, String>,
        val low: Set<String>,
        val categories: Map<String, Category>,
        val payers: Map<String, Payer>
    )

    private fun matches(r: Receipt, f: ReceiptFilter, hits: Set<String>?, low: Set<String>): Boolean {
        if (hits != null && r.id !in hits) return false
        if (f.statuses.isNotEmpty() && r.status !in f.statuses) return false
        if (f.track != null && r.track != f.track) return false
        if (f.categoryId != null && r.categoryId != f.categoryId) return false
        if (f.payerId != null && r.payerId != f.payerId) return false
        val d = r.purchaseDate
        if (f.from != null && (d == null || d < f.from)) return false
        if (f.to != null && (d == null || d > f.to)) return false
        when (f.source) {
            SourceFilter.CAMERA -> if (r.source != Source.CAMERA) return false
            SourceFilter.EMAIL -> if (r.source != Source.EMAIL_FILE && r.source != Source.EMAIL_LIVE) return false
            SourceFilter.FILE -> if (r.source != Source.IMPORT && r.source != Source.SHARE) return false
            null -> Unit
        }
        if (f.needsLook && !(r.status == Status.INBOX || r.id in low || r.duplicateOfId != null)) return false
        return true
    }

    private fun sort(list: List<Receipt>, s: Sort) = when (s) {
        Sort.DATE -> list
        Sort.AMOUNT -> list.sortedByDescending { it.totalCents ?: 0 }
        Sort.MERCHANT -> list.sortedBy { it.merchantName?.lowercase() ?: "~" }
    }

    fun setFilter(f: ReceiptFilter) = filter.update { f }

    fun query(q: String) = filter.update { it.copy(query = q) }

    fun toggle(id: String) = selected.update { if (id in it) it - id else it + id }

    fun clearSelection() = selected.update { emptySet() }

    fun selectAll() = selected.update { state.value.groups.flatMap { g -> g.items.map { it.id } }.toSet() }

    /** Runs a change and hands back an undo for the snackbar. */
    fun act(ids: Collection<String>, change: suspend (ReceiptRepository) -> Unit, done: (suspend () -> Unit) -> Unit) {
        viewModelScope.launch {
            val snapshot = repo.getMany(ids.toList())
            change(repo)
            selected.update { emptySet() }
            done { repo.revert(snapshot) }
        }
    }

    fun undo(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
