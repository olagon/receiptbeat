package org.kealohalabs.receiptbeat.core.data

import androidx.room.withTransaction
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.kealohalabs.receiptbeat.core.database.ReceiptDatabase
import org.kealohalabs.receiptbeat.core.model.Category
import org.kealohalabs.receiptbeat.core.model.Payer
import org.kealohalabs.receiptbeat.core.model.Receipt
import org.kealohalabs.receiptbeat.core.model.Report
import org.kealohalabs.receiptbeat.core.model.ReportItem
import org.kealohalabs.receiptbeat.core.model.Status
import org.kealohalabs.receiptbeat.core.model.TaxLine
import org.kealohalabs.receiptbeat.core.model.Track

enum class ReportState { SUBMITTED, PARTLY_PAID, PAID }

data class ReportRow(
    val report: Report,
    val payer: Payer?,
    val receipts: List<Receipt>,
    val state: ReportState,
    val overdue: Boolean
) {
    val owedCents get() = (report.totalCents - (report.paidCents ?: 0)).coerceAtLeast(0)
}

/** One section on the Reimburse view. [payer] is null for receipts with no payer. */
data class PayerTotals(
    val payer: Payer?,
    val ready: List<Receipt>,
    val readyCents: Long,
    val submittedCents: Long,
    val paidCents: Long
)

/** [line] is null for categories with no tax line yet. */
data class TaxLineTotal(val line: TaxLine?, val totalCents: Long, val deductibleCents: Long, val count: Int)

data class TaxWarning(val receipt: Receipt, val text: String)

data class TaxYearSummary(
    val year: Int,
    val lines: List<TaxLineTotal>,
    val warnings: List<TaxWarning>,
    val receipts: List<Receipt>,
    val locked: Boolean
) {
    val totalCents get() = lines.sumOf { it.totalCents }
    val deductibleCents get() = lines.sumOf { it.deductibleCents }
}

/** Reimbursement reports and the tax year. Screens use this, never Room. */
@Singleton
class ReportRepository @Inject constructor(
    private val db: ReceiptDatabase,
    private val receipts: ReceiptRepository,
    private val catalog: CatalogRepository,
    private val settings: SettingsRepository
) {
    private val dao = db.reports()

    val reports: Flow<List<ReportRow>> = combine(
        dao.observeReports(),
        dao.observeItems(),
        receipts.receipts,
        catalog.payers,
        settings.settings
    ) { reports, items, all, payers, s ->
        val byId = all.associateBy { it.id }
        val ids = items.groupBy({ it.reportId }, { it.receiptId })
        val cutoff = Instant.now().minus(Duration.ofDays(s.remindUnpaidDays.toLong()))
        reports.map { r ->
            val state = stateOf(r)
            ReportRow(
                report = r,
                payer = payers.firstOrNull { it.id == r.payerId },
                receipts = ids[r.id].orEmpty().mapNotNull { byId[it] },
                state = state,
                overdue = state != ReportState.PAID && (r.submittedAt ?: r.createdAt) < cutoff
            )
        }
    }

    val overdue: Flow<List<ReportRow>> = reports.map { list -> list.filter { it.overdue } }

    val payerTotals: Flow<List<PayerTotals>> = combine(receipts.receipts, catalog.payers) { all, payers ->
        val mine = all.filter { it.track == Track.REIMBURSABLE }.groupBy { it.payerId }
        (payers.map { it as Payer? } + null).mapNotNull { p ->
            val list = mine[p?.id].orEmpty()
            if (p == null && list.isEmpty()) return@mapNotNull null
            val ready = list.filter { isReady(it) }
            PayerTotals(
                payer = p,
                ready = ready,
                readyCents = ready.sumOf { it.chargeCents ?: 0 },
                submittedCents = list.filter { isOwed(it) }.sumOf { ReceiptRepository.owed(it) },
                paidCents = list.filter { it.status == Status.REIMBURSED || it.status == Status.PARTLY_REIMBURSED }
                    .sumOf { it.reimbursedCents ?: 0 }
            )
        }
    }

    /** Years that have tax receipts, plus this year, newest first. */
    val taxYears: Flow<List<Int>> = receipts.receipts.map { all ->
        (all.filter { it.track == Track.TAX }.map { ReceiptRepository.taxYearOf(it) } + LocalDate.now().year).distinct().sortedDescending()
    }

    fun taxYear(year: Int): Flow<TaxYearSummary> =
        combine(receipts.receipts, catalog.categories, catalog.taxLines) { all, cats, lines -> summarize(year, all, cats, lines) }

    suspend fun reportReceipts(reportId: String): List<Receipt> = receipts.getMany(dao.receiptIds(reportId))

    suspend fun get(reportId: String) = dao.get(reportId)

    /** Builds a report from the picked receipts and moves them to Submitted. */
    suspend fun createReport(payerId: String?, receiptIds: List<String>, title: String, today: LocalDate = LocalDate.now()): Report {
        val list = receipts.getMany(receiptIds).filter { !it.locked }
        val now = Instant.now()
        val report = Report(
            number = nextNumber(today.year),
            payerId = payerId,
            title = title.ifBlank { "Expenses" },
            createdAt = now,
            submittedAt = now,
            totalCents = list.sumOf { homeCents(it) }
        )
        db.withTransaction {
            dao.upsert(report)
            dao.insertItems(list.map { ReportItem(report.id, it.id) })
            receipts.setStatus(list.map { it.id }, Status.SUBMITTED)
            list.forEach { receipts.event(it.id, "Added to report ${report.number}") }
        }
        return report
    }

    /** Next free number for the year, like TR-2026-014. */
    suspend fun nextNumber(year: Int): String {
        val prefix = "TR-$year-"
        val last = dao.reports().mapNotNull {
            it.number.takeIf { n -> n.startsWith(prefix) }?.removePrefix(prefix)?.toIntOrNull()
        }.maxOrNull()
        return prefix + ((last ?: 0) + 1).toString().padStart(3, '0')
    }

    /** Every receipt on the report is reimbursed. */
    suspend fun markPaid(reportId: String) {
        val report = dao.get(reportId) ?: return
        val ids = dao.receiptIds(reportId)
        db.withTransaction {
            receipts.setStatus(ids, Status.REIMBURSED, "report ${report.number} paid")
            dao.upsert(report.copy(paidCents = report.totalCents, paidAt = Instant.now()))
        }
    }

    /** A payment that does not cover the whole report. It pays the oldest receipts first. */
    suspend fun markPartlyPaid(reportId: String, cents: Long) {
        if (cents <= 0) return
        val report = dao.get(reportId) ?: return
        val list = reportReceipts(reportId)
        val paid = (report.paidCents ?: 0) + cents
        if (paid >= report.totalCents) return markPaid(reportId)
        db.withTransaction {
            spread(list, cents).forEach { (id, add) ->
                val r = list.first { it.id == id }
                receipts.markPaid(id, (r.reimbursedCents ?: 0) + add)
            }
            dao.upsert(report.copy(paidCents = paid))
        }
    }

    /** Claims every tax receipt of the year and locks it so totals cannot shift. */
    suspend fun claimYear(year: Int) {
        val ids = yearReceipts(year).map { it.id }
        db.withTransaction {
            receipts.setStatus(ids, Status.CLAIMED)
            receipts.setLocked(ids, true)
            ids.forEach { receipts.event(it, "Locked with tax year $year") }
        }
    }

    suspend fun unlockYear(year: Int) {
        val ids = yearReceipts(year).map { it.id }
        db.withTransaction {
            receipts.setLocked(ids, false)
            ids.forEach { receipts.event(it, "Tax year $year unlocked") }
        }
    }

    private suspend fun yearReceipts(year: Int) =
        receipts.all().filter { it.track == Track.TAX && it.status != Status.ARCHIVED && ReceiptRepository.taxYearOf(it) == year }

    companion object {
        fun isReady(r: Receipt) = !r.reading && (r.status == Status.INBOX || r.status == Status.READY || r.status == Status.REJECTED)

        fun isOwed(r: Receipt) = r.status == Status.SUBMITTED || r.status == Status.PARTLY_REIMBURSED

        /** Charged amount in the home currency when a rate was used, else in the receipt currency. */
        fun homeCents(r: Receipt): Long {
            val charge = r.chargeCents ?: 0
            val home = r.homeCurrencyTotalCents ?: return charge
            val total = r.totalCents ?: return home
            return if (total == 0L) home else home * charge / total
        }

        fun stateOf(r: Report) = when {
            r.paidAt != null -> ReportState.PAID
            (r.paidCents ?: 0) > 0 -> ReportState.PARTLY_PAID
            else -> ReportState.SUBMITTED
        }

        /**
         * Splits a payment across receipts, oldest first. Returns the cents added to each receipt that got some.
         * ponytail: works in receipt currency amounts; mixed currency reports would need per receipt rates.
         */
        fun spread(list: List<Receipt>, cents: Long): Map<String, Long> {
            var left = cents
            val out = LinkedHashMap<String, Long>()
            list.sortedWith(compareBy({ it.purchaseDate ?: LocalDate.MAX }, { it.createdAt })).forEach { r ->
                val due = ReceiptRepository.owed(r)
                val add = minOf(due, left)
                if (add > 0) {
                    out[r.id] = add
                    left -= add
                }
            }
            return out
        }

        fun summarize(year: Int, all: List<Receipt>, cats: List<Category>, lines: List<TaxLine>): TaxYearSummary {
            val list = all.filter { it.track == Track.TAX && it.status != Status.ARCHIVED && ReceiptRepository.taxYearOf(it) == year }
            val lineOf = cats.associate { c -> c.id to lines.firstOrNull { it.id == c.taxLineId } }
            val totals = list.groupBy { lineOf[it.categoryId] }.map { (line, rs) ->
                val total = rs.sumOf { homeCents(it) }
                TaxLineTotal(line, total, total * (line?.deductiblePercent ?: 100) / 100, rs.size)
            }.sortedByDescending { it.totalCents }
            val warnings = list.flatMap { r ->
                listOfNotNull(
                    "No category".takeIf { r.categoryId == null },
                    "No total".takeIf { r.totalCents == null },
                    "Not claimed yet".takeIf { r.status == Status.INBOX || r.status == Status.READY }
                ).map { TaxWarning(r, it) }
            }
            return TaxYearSummary(year, totals, warnings, list, locked = list.isNotEmpty() && list.all { it.locked })
        }
    }
}
