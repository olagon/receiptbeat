package org.kealohalabs.receiptbeat.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kealohalabs.receiptbeat.core.model.Receipt
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReceiptDaoTest {
    private lateinit var db: ReceiptDatabase

    @Before fun open() {
        // Plain SQLite in tests. SQLCipher needs its native library, which only runs on a device.
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReceiptDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    @Test fun savesSearchesAndFindsDuplicates() = runTest {
        val day = LocalDate.of(2026, 3, 14)
        val a = Receipt(merchantName = "Kona Coffee", totalCents = 1250, purchaseDate = day, ocrText = "latte hilo")
        val b = Receipt(merchantName = "Kona Coffee", totalCents = 1250, purchaseDate = day.plusDays(1))
        db.receipts().upsert(a)
        db.receipts().upsert(b)

        assertEquals(2, db.receipts().observeAll().first().size)
        assertEquals(listOf(a.id), db.receipts().search("hilo*").map { it.id })
        val dupes = db.receipts().possibleDuplicates(a.id, 1250, day.minusDays(1).toEpochDay(), day.plusDays(1).toEpochDay(), null)
        assertEquals(listOf(b.id), dupes.map { it.id })
    }
}
