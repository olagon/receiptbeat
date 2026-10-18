package org.kealohalabs.receiptbeat.core.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.kealohalabs.receiptbeat.core.model.Category
import org.kealohalabs.receiptbeat.core.model.Receipt
import org.kealohalabs.receiptbeat.core.model.Status
import org.kealohalabs.receiptbeat.core.model.TaxLine
import org.kealohalabs.receiptbeat.core.model.Track

enum class Period(val label: String) {
    MONTH("This month"),
    QUARTER("This quarter"),
    YEAR("Year to date"),
    ALL("All time"),
    ;

    /** Inclusive start date, or null for all time. */
    fun start(today: LocalDate): LocalDate? = when (this) {
        MONTH -> today.withDayOfMonth(1)
        QUARTER -> today.withMonth((today.monthValue - 1) / 3 * 3 + 1).withDayOfMonth(1)
        YEAR -> today.withDayOfYear(1)
        ALL -> null
    }

    /** The same stretch of time just before, for "vs last month" style comparisons. */
    fun previous(today: LocalDate): Pair<LocalDate, LocalDate>? {
        val s = start(today) ?: return null
        return when (this) {
            MONTH -> s.minusMonths(1) to s.minusDays(1)
            QUARTER -> s.minusMonths(3) to s.minusDays(1)
            YEAR -> s.minusYears(1) to today.minusYears(1)
            ALL -> null
        }
    }
}

data class PayerMoney(val payerId: String?, val toSubmitCents: Long, val waitingCents: Long)

data class CategorySpend(val categoryId: String?, val cents: Long)

data class MonthSpend(val month: YearMonth, val cents: Long)

/** Everything the Home dashboard shows, for one period. Owed is a running balance and ignores the period. */
data class DashboardView(
    val period: Period,
    val toSubmitCents: Long,
    val toSubmitCount: Int,
    val waitingCents: Long,
    val waitingCount: Int,
    val paidBackCents: Long,
    val paidBackCount: Int,
    val spentCents: Long,
    val spentCount: Int,
    val previousSpentCents: Long?,
    val deductibleCents: Long,
    val byPayer: List<PayerMoney>,
    val byCategory: List<CategorySpend>,
    val byMonth: List<MonthSpend>,
) {
    /** Owed to you = not yet submitted plus submitted and waiting. Both are money you are out. */
    val owedCents get() = toSubmitCents + waitingCents
}

object DashboardMath {
    fun build(
        all: List<Receipt>,
        categories: List<Category>,
        taxLines: List<TaxLine>,
        period: Period,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): DashboardView {
        val live = all.filter { it.deletedAt == null && !it.reading }
        val start = period.start(today)
        fun inPeriod(d: LocalDate?) = start == null || (d != null && !d.isBefore(start) && !d.isAfter(today))
        fun day(r: Receipt) = r.purchaseDate ?: r.createdAt.atZone(zone).toLocalDate()

        val reimbursable = live.filter { it.track == Track.REIMBURSABLE }
        val toSubmit = reimbursable.filter { it.status == Status.INBOX || it.status == Status.READY || it.status == Status.REJECTED }
        val waiting = reimbursable.filter { it.status == Status.SUBMITTED || it.status == Status.PARTLY_REIMBURSED }
        val paid = reimbursable.filter {
            (it.status == Status.REIMBURSED || it.status == Status.PARTLY_REIMBURSED) &&
                inPeriod((it.reimbursedAt ?: it.updatedAt).atZone(zone).toLocalDate())
        }
        val spent = live.filter { inPeriod(day(it)) }
        val prev = period.previous(today)?.let { (a, b) -> live.filter { day(it) in a..b }.sumOf { it.totalCents ?: 0 } }
        val pct = categories.associate { c -> c.id to (taxLines.firstOrNull { it.id == c.taxLineId }?.deductiblePercent ?: 100) }

        val payers = (toSubmit + waiting).groupBy { it.payerId }.map { (pid, list) ->
            PayerMoney(
                pid,
                list.filter { it in toSubmit }.sumOf { it.totalCents ?: 0 },
                list.filter { it in waiting }.sumOf { ReceiptRepository.owed(it) },
            )
        }.sortedByDescending { it.toSubmitCents + it.waitingCents }

        // Months shown: the period's months, or the last 12 for all time. At least 3 so a trend reads.
        val first = YearMonth.from(start ?: today.minusMonths(11)).let {
            if (period ==
                Period.MONTH
            ) {
                YearMonth.from(today).minusMonths(2)
            } else {
                it
            }
        }
        val months = generateSequence(first) { it.plusMonths(1) }.takeWhile { !it.isAfter(YearMonth.from(today)) }.toList()
        val monthSpend = live.groupBy { YearMonth.from(day(it)) }

        return DashboardView(
            period = period,
            toSubmitCents = toSubmit.sumOf { it.totalCents ?: 0 },
            toSubmitCount = toSubmit.size,
            waitingCents = waiting.sumOf { ReceiptRepository.owed(it) },
            waitingCount = waiting.size,
            paidBackCents = paid.sumOf { it.reimbursedCents ?: it.totalCents ?: 0 },
            paidBackCount = paid.size,
            spentCents = spent.sumOf { it.totalCents ?: 0 },
            spentCount = spent.size,
            previousSpentCents = prev,
            deductibleCents = spent.filter { it.track == Track.TAX && it.status != Status.ARCHIVED }
                .sumOf { (it.totalCents ?: 0) * (pct[it.categoryId] ?: 100) / 100 },
            byPayer = payers,
            byCategory = spent.groupBy { it.categoryId }.map { (c, l) -> CategorySpend(c, l.sumOf { it.totalCents ?: 0 }) }
                .filter { it.cents > 0 }.sortedByDescending { it.cents },
            byMonth = months.map { m -> MonthSpend(m, monthSpend[m].orEmpty().sumOf { it.totalCents ?: 0 }) },
        )
    }
}
