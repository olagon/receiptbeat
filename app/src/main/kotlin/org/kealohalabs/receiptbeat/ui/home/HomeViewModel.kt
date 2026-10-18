package org.kealohalabs.receiptbeat.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.kealohalabs.receiptbeat.core.data.CatalogRepository
import org.kealohalabs.receiptbeat.core.data.Dashboard
import org.kealohalabs.receiptbeat.core.data.ReceiptRepository
import org.kealohalabs.receiptbeat.core.data.SettingsRepository
import org.kealohalabs.receiptbeat.core.model.Category
import org.kealohalabs.receiptbeat.core.model.Payer
import org.kealohalabs.receiptbeat.core.model.Receipt

data class HomeState(
    val dash: Dashboard? = null,
    val recent: List<Receipt> = emptyList(),
    val thumbs: Map<String, String> = emptyMap(),
    val categories: Map<String, Category> = emptyMap(),
    val payers: Map<String, Payer> = emptyMap(),
    val name: String = "",
    val hideAmounts: Boolean = false,
    val reviewCount: Int = 0,
    val overdueReports: Int = 0,
    val scanning: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    repo: ReceiptRepository,
    catalog: CatalogRepository,
    settings: SettingsRepository,
    extras: HomeExtras
) : ViewModel() {
    val state: StateFlow<HomeState> = combine(
        repo.dashboard(),
        combine(repo.receipts, repo.thumbs) { r, t -> r.take(5) to t },
        combine(catalog.categories, catalog.payers) { c, p -> c.associateBy { it.id } to p.associateBy { it.id } },
        settings.settings,
        extras.counts
    ) { d, rt, cp, s, x ->
        HomeState(d, rt.first, rt.second, cp.first, cp.second, s.userName, s.hideAmounts, x.reviewCount, x.overdue, x.scanning)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())
}
