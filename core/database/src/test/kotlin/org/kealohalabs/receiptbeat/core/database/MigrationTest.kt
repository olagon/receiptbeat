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
}
