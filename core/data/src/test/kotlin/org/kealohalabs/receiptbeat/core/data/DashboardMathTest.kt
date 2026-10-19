package org.kealohalabs.receiptbeat.core.data

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test
import org.kealohalabs.receiptbeat.core.model.Category
import org.kealohalabs.receiptbeat.core.model.Receipt
import org.kealohalabs.receiptbeat.core.model.Status
import org.kealohalabs.receiptbeat.core.model.TaxLine
import org.kealohalabs.receiptbeat.core.model.Track

class DashboardMathTest {
    private val today = LocalDate.of(2026, 10, 18)
    private val utc = ZoneOffset.UTC
    private fun at(d: LocalDate) = d.atStartOfDay(utc).toInstant()

    @Test fun owedIncludesUnsubmittedAndPaidBackFollowsThePeriod() {
        val receipts = listOf(
            // The case from the field: nothing submitted yet, $104.71 waiting to go in.
            Receipt(
                track = Track.REIMBURSABLE,
                status = Status.READY,
                payerId = "ri",
                totalCents = 10471,
                purchaseDate = today.minusDays(3)
            ),
            Receipt(
                track = Track.REIMBURSABLE,
                status = Status.SUBMITTED,
                payerId = "ri",
                totalCents = 5000,
                purchaseDate = today.minusDays(20)
            ),
            Receipt(
                track = Track.REIMBURSABLE,
                status = Status.PARTLY_REIMBURSED,
                payerId = "board",
                totalCents = 3000,
                reimbursedCents = 1000,
                reimbursedAt = at(today.minusDays(2)),
                purchaseDate = today.minusMonths(2),
            ),
            Receipt(
                track = Track.REIMBURSABLE,
                status = Status.REIMBURSED,
                totalCents = 2000,
                reimbursedCents = 2000,
                reimbursedAt = at(today.minusMonths(5)),
                purchaseDate = today.minusMonths(6),
            ),
            Receipt(track = Track.TAX, status = Status.READY, categoryId = "meals", totalCents = 4000, purchaseDate = today.minusDays(1)),
        )
        val cats = listOf(Category("meals", "Meals", "restaurant", 0, "m"))
        val lines = listOf(TaxLine("m", "us", "24b", "Meals", 50))

        val month = DashboardMath.build(receipts, cats, lines, Period.MONTH, today, utc)
        assertEquals(10471L, month.toSubmitCents)
        assertEquals(5000L + 2000L, month.waitingCents) // submitted plus the unpaid part of the partial
        assertEquals(10471L + 7000L, month.owedCents)
        assertEquals(1000L, month.paidBackCents) // only this month's money in
        assertEquals(2000L, month.deductibleCents) // meals at 50%
        assertEquals("ri", month.byPayer.first().payerId)

        val year = DashboardMath.build(receipts, cats, lines, Period.YEAR, today, utc)
        assertEquals(3000L, year.paidBackCents)
        assertEquals(10, year.byMonth.size) // January to October
    }

    @Test fun quarterStartsOnTheQuarter() {
        assertEquals(LocalDate.of(2026, 10, 1), Period.QUARTER.start(today))
        assertEquals(LocalDate.of(2026, 7, 1), Period.QUARTER.start(LocalDate.of(2026, 9, 30)))
    }

    @Test fun personalPartIsNeverCharged() {
        // $120 at the store, $35 of it was personal: only $85 is owed, reported and paid back.
        val r = Receipt(track = Track.REIMBURSABLE, status = Status.READY, totalCents = 12000, personalCents = 3500, purchaseDate = today)
        assertEquals(8500L, r.chargeCents)
        assertEquals(8500L, DashboardMath.build(listOf(r), emptyList(), emptyList(), Period.MONTH, today, utc).owedCents)
        assertEquals(8500L, ReceiptRepository.owed(r.copy(status = Status.SUBMITTED)))
        // Foreign receipt: the home amount is cut by the same share.
        assertEquals(17000L, ReportRepository.homeCents(r.copy(totalCents = 10000, personalCents = 1500, homeCurrencyTotalCents = 20000)))
    }
}
