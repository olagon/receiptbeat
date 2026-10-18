package org.kealohalabs.receiptbeat.ui.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.kealohalabs.receiptbeat.core.data.PayerTotals
import org.kealohalabs.receiptbeat.core.data.ReportRepository
import org.kealohalabs.receiptbeat.core.data.ReportRow
import org.kealohalabs.receiptbeat.core.data.SettingsRepository
import org.kealohalabs.receiptbeat.core.data.TaxYearSummary
import org.kealohalabs.receiptbeat.export.Exporter

data class MoneyState(
    val payers: List<PayerTotals> = emptyList(),
    val reports: List<ReportRow> = emptyList(),
    val years: List<Int> = emptyList(),
    val year: Int = LocalDate.now().year,
    val tax: TaxYearSummary? = null,
    val homeCurrency: String = "USD",
    val hideAmounts: Boolean = false,
    val busy: Boolean = false,
    val celebrate: Boolean = false,
    val message: String? = null
) {
    /** Same as Home: money you are out, whether submitted yet or not. */
    val owedCents get() = toSubmitCents + waitingCents
    val toSubmitCents get() = payers.sumOf { it.readyCents }
    val waitingCents get() = payers.sumOf { it.submittedCents }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MoneyViewModel @Inject constructor(
    private val repo: ReportRepository,
    private val exporter: Exporter,
    settings: SettingsRepository
) : ViewModel() {
    private val year = MutableStateFlow(LocalDate.now().year)
    private val flags = MutableStateFlow(Flags())

    private data class Flags(val busy: Boolean = false, val celebrate: Boolean = false, val message: String? = null)

    val state: StateFlow<MoneyState> = combine(
        combine(repo.payerTotals, repo.reports, repo.taxYears) { p, r, y -> Triple(p, r, y) },
        year,
        year.flatMapLatest { repo.taxYear(it) },
        settings.settings,
        flags
    ) { (p, r, y), yr, tax, s, f ->
        MoneyState(p, r, y, yr, tax, s.homeCurrency, s.hideAmounts, f.busy, f.celebrate, f.message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoneyState())

    fun selectYear(y: Int) = year.update { y }

    fun dismissCelebration() = flags.update { it.copy(celebrate = false) }

    fun clearMessage() = flags.update { it.copy(message = null) }

    /** Creates the report, moves the receipts to Submitted, and hands back the PDF and CSV to share. */
    fun createReport(payerId: String?, ids: List<String>, title: String, share: (List<File>, String) -> Unit) = busy {
        val report = repo.createReport(payerId, ids, title)
        share(exporter.report(report.id), "${report.number} ${report.title}")
    }

    fun shareReport(row: ReportRow, share: (List<File>, String) -> Unit) = busy {
        share(exporter.report(row.report.id), "${row.report.number} ${row.report.title}")
    }

    fun markPaid(reportId: String) = paid { repo.markPaid(reportId) }

    fun markPartlyPaid(reportId: String, cents: Long) = paid { repo.markPartlyPaid(reportId, cents) }

    fun exportTaxes(share: (List<File>, String) -> Unit) = busy {
        val y = year.value
        share(listOf(exporter.taxYear(y)), "Receipts for tax year $y")
    }

    fun lockYear() = busy {
        repo.claimYear(year.value)
        flags.update { it.copy(message = "${year.value} is claimed and locked") }
    }

    fun unlockYear() = busy {
        repo.unlockYear(year.value)
        flags.update { it.copy(message = "${year.value} is unlocked") }
    }

    /** Celebrate when this payment brings Owed to you down to zero. */
    private fun paid(block: suspend () -> Unit) = busy {
        val before = repo.payerTotals.first().sumOf { it.submittedCents }
        block()
        val after = repo.payerTotals.first().sumOf { it.submittedCents }
        if (before > 0 && after == 0L) flags.update { it.copy(celebrate = true) }
    }

    private fun busy(block: suspend () -> Unit) {
        viewModelScope.launch {
            flags.update { it.copy(busy = true) }
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                flags.update { it.copy(message = "Something went wrong. Please try again.") }
            } finally {
                flags.update { it.copy(busy = false) }
            }
        }
    }
}
