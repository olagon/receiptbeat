package org.kealohalabs.receiptbeat.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ReceiptDatabase::class.java)

    /** Version 2 adds folders and search words to mail accounts. Accounts from version 1 must survive. */
    @Test fun migrate1To2KeepsMailAccounts() {
        helper.createDatabase("m.db", 1).apply {
            val cols = "id, auth, email, imapHost, imapPort, scanFrom"
            execSQL("INSERT INTO MailAccount ($cols) VALUES ('a', 'APP_PASSWORD', 'me@gmail.com', 'imap.gmail.com', 993, 20000)")
            close()
        }
        val db = helper.runMigrationsAndValidate("m.db", 2, true)
        db.query("SELECT email, folders, searchWords FROM MailAccount").use { c ->
            c.moveToFirst()
            assertEquals("me@gmail.com", c.getString(0))
            assertEquals(null, c.getString(1))
        }
    }

    /** Version 4 adds the personal part. Old receipts keep their total and have no personal part. */
    @Test fun migrate3To4KeepsReceipts() {
        helper.createDatabase("r.db", 3).apply {
            execSQL(
                "INSERT INTO Receipt (id, createdAt, updatedAt, source, totalCents, currency, track, status, locked, reading) " +
                    "VALUES ('r', 0, 0, 'CAMERA', 1250, 'USD', 'REIMBURSABLE', 'READY', 0, 0)"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate("r.db", 4, true)
        db.query("SELECT totalCents, personalCents FROM Receipt").use { c ->
            c.moveToFirst()
            assertEquals(1250L, c.getLong(0))
            assertEquals(true, c.isNull(1))
        }
    }
}
