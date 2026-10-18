package org.kealohalabs.receiptbeat.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kealohalabs.receiptbeat.core.database.ReceiptDatabase
import org.kealohalabs.receiptbeat.core.model.Payer
import org.kealohalabs.receiptbeat.core.model.Receipt
import org.kealohalabs.receiptbeat.core.model.Report
import org.kealohalabs.receiptbeat.core.model.Status
import org.kealohalabs.receiptbeat.core.model.Track
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReportRepositoryTest {
    private lateinit var db: ReceiptDatabase
    private lateinit var repo: ReportRepository

    @Before fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ReceiptDatabase::class.java).allowMainThreadQueries().build()
        val catalog = CatalogRepository(db)
        repo = ReportRepository(db, ReceiptRepository(db, FileStore(context), catalog), catalog, SettingsRepository(context))
    }

    @After fun close() = db.close()

    private fun day(d: Int) = LocalDate.of(2026, 3, d)

    @Test fun numbersReportsPerYear() = runTest {
        assertEquals("TR-2026-001", repo.nextNumber(2026))
        db.reports().upsert(Report(number = "TR-2026-013", payerId = null, title = "a", totalCents = 1))
        db.reports().upsert(Report(number = "TR-2025-040", payerId = null, title = "b", totalCents = 1))
        assertEquals("TR-2026-014", repo.nextNumber(2026))
        assertEquals("TR-2027-001", repo.nextNumber(2027))
    }

    @Test fun createThenPayInParts() = runTest {
        val payer = Payer(name = "Board")
        db.catalog().upsertPayer(payer)
        val old = Receipt(track = Track.REIMBURSABLE, status = Status.READY, payerId = payer.id, totalCents = 1000, purchaseDate = day(1))
        val mid = Receipt(track = Track.REIMBURSABLE, status = Status.READY, payerId = payer.id, totalCents = 2000, purchaseDate = day(2))
        val new = Receipt(track = Track.REIMBURSABLE, status = Status.READY, payerId = payer.id, totalCents = 3000, purchaseDate = day(3))
        db.receipts().upsertAll(listOf(new, old, mid))
        assertEquals(6000, repo.payerTotals.first().single().readyCents)

        val report = repo.createReport(payer.id, listOf(old.id, mid.id, new.id), "March", today = day(20))
        assertEquals("TR-2026-001", report.number)
        assertEquals(6000, report.totalCents)
        assertTrue(db.receipts().all().all { it.status == Status.SUBMITTED })
        assertTrue(db.receipts().eventsFor(old.id).any { it.text.contains("TR-2026-001") })

        // 2500 pays the oldest (1000) in full and 1500 of the next one.
        repo.markPartlyPaid(report.id, 2500)
        val byId = db.receipts().all().associateBy { it.id }
        assertEquals(Status.REIMBURSED, byId.getValue(old.id).status)
        assertEquals(Status.PARTLY_REIMBURSED, byId.getValue(mid.id).status)
        assertEquals(1500L, byId.getValue(mid.id).reimbursedCents)
        assertEquals(Status.SUBMITTED, byId.getValue(new.id).status)
        assertEquals(ReportState.PARTLY_PAID, repo.reports.first().single().state)
        assertEquals(3500, repo.payerTotals.first().single().submittedCents)

        // The rest arrives: everything is reimbursed and the report is paid.
        repo.markPartlyPaid(report.id, 3500)
        assertTrue(db.receipts().all().all { it.status == Status.REIMBURSED })
        assertEquals(ReportState.PAID, repo.reports.first().single().state)
        assertEquals(6000, repo.payerTotals.first().single().paidCents)
    }

    @Test fun spreadsOldestFirst() {
        val a = Receipt(totalCents = 500, purchaseDate = day(5))
        val b = Receipt(totalCents = 500, purchaseDate = day(1), reimbursedCents = 200)
        assertEquals(mapOf(b.id to 300L, a.id to 100L), ReportRepository.spread(listOf(a, b), 400))
    }

    @Test fun taxTotalsApplyMealLimitAndWarn() = runTest {
        CatalogRepository(db).seedIfEmpty()
        val meal = Receipt(track = Track.TAX, status = Status.READY, categoryId = "meals", totalCents = 5000, purchaseDate = day(4))
        val office = Receipt(track = Track.TAX, status = Status.CLAIMED, categoryId = "office", totalCents = 2000, purchaseDate = day(5))
        val bare = Receipt(track = Track.TAX, status = Status.CLAIMED, purchaseDate = day(6))
        val lastYear = Receipt(track = Track.TAX, categoryId = "office", totalCents = 9999, purchaseDate = LocalDate.of(2025, 5, 1))
        db.receipts().upsertAll(listOf(meal, office, bare, lastYear))

        val t = repo.taxYear(2026).first()
        assertEquals(7000, t.totalCents)
        assertEquals(2500 + 2000, t.deductibleCents)
        assertEquals(2500L, t.lines.first { it.line?.id == "us_c_meals" }.deductibleCents)
        assertEquals(setOf("No category", "No total", "Not claimed yet"), t.warnings.map { it.text }.toSet())
        assertTrue(repo.taxYears.first().containsAll(listOf(2026, 2025)))

        repo.claimYear(2026)
        val claimed = repo.taxYear(2026).first()
        assertTrue(claimed.locked)
        assertTrue(claimed.receipts.all { it.status == Status.CLAIMED && it.taxYear == 2026 })
        assertEquals(false, db.receipts().get(lastYear.id)!!.locked)

        repo.unlockYear(2026)
        assertEquals(false, repo.taxYear(2026).first().locked)
    }
}
