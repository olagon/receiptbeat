package org.kealohalabs.receiptbeat.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.kealohalabs.receiptbeat.core.data.CatalogRepository
import org.kealohalabs.receiptbeat.core.data.DashboardMath
import org.kealohalabs.receiptbeat.core.data.DashboardView
import org.kealohalabs.receiptbeat.core.data.Period
import org.kealohalabs.receiptbeat.core.data.ReceiptRepository
import org.kealohalabs.receiptbeat.core.data.SettingsRepository
import org.kealohalabs.receiptbeat.core.data.TripMath
import org.kealohalabs.receiptbeat.core.data.TripSummary
import org.kealohalabs.receiptbeat.core.model.Category
import org.kealohalabs.receiptbeat.core.model.Payer
import org.kealohalabs.receiptbeat.core.model.Project
import org.kealohalabs.receiptbeat.core.model.Receipt
import org.kealohalabs.receiptbeat.core.model.Status
import org.kealohalabs.receiptbeat.core.model.TaxLine

data class HomeState(
    val view: DashboardView? = null,
    val recent: List<Receipt> = emptyList(),
    val thumbs: Map<String, String> = emptyMap(),
    val categories: Map<String, Category> = emptyMap(),
    val payers: Map<String, Payer> = emptyMap(),
    val name: String = "",
    val hideAmounts: Boolean = false,
    val currency: String = "USD",
    val inboxCount: Int = 0,
    val lowCount: Int = 0,
    val reviewCount: Int = 0,
    val overdueReports: Int = 0,
    val scanning: Boolean = false,
    val trips: List<TripSummary> = emptyList(),
    val tripCount: Int = 0,
)

private data class Catalog(val c: List<Category>, val tl: List<TaxLine>, val p: List<Payer>, val pr: List<Project>)

@HiltViewModel
class HomeViewModel @Inject constructor(
    repo: ReceiptRepository,
    catalog: CatalogRepository,
    private val settings: SettingsRepository,
    extras: HomeExtras,
) : ViewModel() {
    val state: StateFlow<HomeState> = combine(
        combine(repo.receipts, repo.thumbs, repo.lowConfidence) { r, t, l -> Triple(r, t, l) },
        combine(catalog.categories, catalog.taxLines, catalog.payers, catalog.projects) { c, tl, p, pr -> Catalog(c, tl, p, pr) },
        settings.settings,
        extras.counts,
    ) { (all, thumbs, low), (cats, lines, payers, projects), s, x ->
        val period = runCatching { Period.valueOf(s.dashPeriod) }.getOrDefault(Period.MONTH)
        HomeState(
            view = DashboardMath.build(all, cats, lines, period),
            recent = all.take(5),
            thumbs = thumbs,
            categories = cats.associateBy { it.id },
            payers = payers.associateBy { it.id },
            name = s.userName,
            hideAmounts = s.hideAmounts,
            currency = s.homeCurrency,
            inboxCount = all.count { it.status == Status.INBOX && !it.reading },
            lowCount = all.count { it.id in low && it.status == Status.INBOX },
            reviewCount = x.reviewCount,
            overdueReports = x.overdue,
            scanning = x.scanning,
            trips = TripMath.summarize(projects, all).take(2),
            tripCount = projects.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun setPeriod(p: Period) {
        viewModelScope.launch { settings.update { it.copy(dashPeriod = p.name) } }
    }
}
