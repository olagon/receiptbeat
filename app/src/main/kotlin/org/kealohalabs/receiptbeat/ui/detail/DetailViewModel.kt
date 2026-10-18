package org.kealohalabs.receiptbeat.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.kealohalabs.receiptbeat.core.data.CatalogRepository
import org.kealohalabs.receiptbeat.core.data.DuplicateAction
import org.kealohalabs.receiptbeat.core.data.Fields
import org.kealohalabs.receiptbeat.core.data.ReceiptDetail
import org.kealohalabs.receiptbeat.core.data.ReceiptRepository
import org.kealohalabs.receiptbeat.core.data.SettingsRepository
import org.kealohalabs.receiptbeat.core.model.Category
import org.kealohalabs.receiptbeat.core.model.Money
import org.kealohalabs.receiptbeat.core.model.Payer
import org.kealohalabs.receiptbeat.core.model.Project
import org.kealohalabs.receiptbeat.core.model.Receipt
import org.kealohalabs.receiptbeat.core.model.Status
import org.kealohalabs.receiptbeat.core.model.Track

data class DetailState(
    val detail: ReceiptDetail? = null,
    val categories: List<Category> = emptyList(),
    val payers: List<Payer> = emptyList(),
    val projects: List<Project> = emptyList(),
    val duplicate: Receipt? = null,
    val duplicateThumb: String? = null,
    val hideAmounts: Boolean = false,
    val loaded: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = DetailViewModel.Factory::class)
class DetailViewModel @AssistedInject constructor(
    @Assisted val id: String,
    private val repo: ReceiptRepository,
    catalog: CatalogRepository,
    settings: SettingsRepository
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(id: String): DetailViewModel
    }

    private val dup = repo.detail(id).flatMapLatest { d ->
        val other = d?.receipt?.duplicateOfId ?: return@flatMapLatest flowOf<Pair<Receipt?, String?>>(null to null)
        repo.detail(other).combine(repo.thumbs) { o, t -> o?.receipt to t[other] }
    }

    val state: StateFlow<DetailState> = combine(
        repo.detail(id),
        combine(catalog.categories, catalog.payers, catalog.projects) { c, p, pr -> Triple(c, p, pr) },
        dup,
        settings.settings.map { it.hideAmounts }
    ) { d, cat, dupe, hide ->
        DetailState(d, cat.first.filter { !it.hidden }, cat.second, cat.third, dupe.first, dupe.second, hide, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState())

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun setMerchant(v: String) = launch { repo.edit(id, Fields.MERCHANT, v) { it.copy(merchantName = v.trim().ifBlank { null }) } }

    fun setDate(v: LocalDate?) = launch { repo.edit(id, Fields.DATE, v?.toString()) { it.copy(purchaseDate = v) } }

    /** Amount fields take text like "12.50". Blank clears the field. */
    fun setAmount(field: String, text: String) = launch {
        val cents = if (text.isBlank()) null else Money.parse(text) ?: return@launch
        repo.edit(id, field, cents?.toString()) {
            when (field) {
                Fields.SUBTOTAL -> it.copy(subtotalCents = cents)
                Fields.TAX -> it.copy(taxCents = cents)
                Fields.TIP -> it.copy(tipCents = cents)
                else -> it.copy(totalCents = cents)
            }
        }
    }

    fun setCurrency(v: String) = launch { repo.edit(id, Fields.CURRENCY, v) { it.copy(currency = v.uppercase().take(3)) } }

    fun setCategory(c: String?) = launch { repo.edit(id, "category", c) { it.copy(categoryId = c) } }

    fun setPayer(p: Payer?) = launch {
        repo.edit(id, "payer", p?.id) { r ->
            r.copy(payerId = p?.id, track = if (p != null && r.track == Track.PERSONAL) p.defaultTrack else r.track)
        }
    }

    fun setProject(p: String?) = launch { repo.update(id) { it.copy(projectId = p) } }

    fun setNotes(v: String) = launch { repo.update(id) { it.copy(notes = v.ifBlank { null }) } }

    fun setTrack(t: Track) = launch { repo.setTrack(listOf(id), t) }

    fun setStatus(s: Status, note: String? = null) = launch { repo.setStatus(listOf(id), s, note) }

    fun markPaid(cents: Long) = launch { repo.markPaid(id, cents) }

    fun delete() = launch { repo.delete(listOf(id)) }

    fun restore() = launch { repo.restore(listOf(id)) }

    fun unlock() = launch { repo.setLocked(listOf(id), false) }

    fun duplicate(action: DuplicateAction) = launch {
        val other = state.value.duplicate?.id ?: return@launch
        repo.resolveDuplicate(id, other, action)
    }
}
