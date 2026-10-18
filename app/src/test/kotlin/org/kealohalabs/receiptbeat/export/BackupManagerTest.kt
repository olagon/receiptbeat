package org.kealohalabs.receiptbeat.export

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kealohalabs.receiptbeat.core.data.FileStore
import org.kealohalabs.receiptbeat.core.database.ReceiptDatabase
import org.kealohalabs.receiptbeat.core.model.LineItem
import org.kealohalabs.receiptbeat.core.model.Payer
import org.kealohalabs.receiptbeat.core.model.Receipt
import org.kealohalabs.receiptbeat.core.model.Report
import org.kealohalabs.receiptbeat.core.model.ReportItem
import org.kealohalabs.receiptbeat.core.model.Status
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class BackupManagerTest {
    private lateinit var db: ReceiptDatabase
    private lateinit var backup: BackupManager

    @Before fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ReceiptDatabase::class.java).allowMainThreadQueries().build()
        backup = BackupManager(context, db, FileStore(context))
    }

    @After fun close() = db.close()

    @Test fun roundTripsEveryTable() = runTest {
        val r = Receipt(
            merchantName = "Kona Coffee",
            purchaseDate = LocalDate.of(2026, 3, 14),
            totalCents = 1250,
            fxRate = 1.0825,
            status = Status.SUBMITTED,
            locked = true,
            notes = "Line one\n\"quoted\""
        )
        val payer = Payer(name = "Board")
        val report = Report(number = "TR-2026-001", payerId = payer.id, title = "March", totalCents = 1250)
        db.receipts().upsert(r)
        db.receipts().insertLineItems(listOf(LineItem(receiptId = r.id, name = "Latte", qty = 2.0, totalCents = 1250)))
        db.catalog().upsertPayer(payer)
        db.reports().upsert(report)
        db.reports().insertItems(listOf(ReportItem(report.id, r.id)))

        val receipts = db.receipts().all()
        val reports = db.reports().reports()
        val zip = backup.backup()
        // Something added after the backup is gone once it is restored.
        db.receipts().upsert(Receipt(merchantName = "Later"))
        backup.restore(zip)

        assertEquals(receipts, db.receipts().all())
        assertEquals(listOf(payer), db.catalog().payers())
        assertEquals(reports, db.reports().reports())
        assertEquals(listOf(ReportItem(report.id, r.id)), db.reports().allItems())
        assertEquals(2.0, db.receipts().allLineItems().single().qty, 0.0)
        // The search index is rebuilt by Room's triggers.
        assertEquals(listOf(r.id), db.receipts().search("kona*").map { it.id })
    }
}
