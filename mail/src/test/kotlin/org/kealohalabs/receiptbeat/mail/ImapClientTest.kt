package org.kealohalabs.receiptbeat.mail

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import java.time.LocalDate
import java.util.Properties
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ImapClientTest {
    private val greenMail = GreenMail(ServerSetupTest.IMAP.dynamicPort())
    private lateinit var client: ImapClient

    @Before fun setUp() {
        greenMail.start()
        val user = greenMail.setUser("kai@example.com", "kai", "secret")
        val session = Session.getInstance(Properties())
        for (name in listOf("square.eml", "invoice.eml", "amazon.eml")) {
            user.deliver(resource(name).use { MimeMessage(session, it) })
        }
        user.deliver(
            MimeMessage(session).apply {
                setFrom("news@deals.islandshop.example")
                subject = "Fall Sale: 50% off"
                setText("Shop now. Limited time deal.")
            }
        )
        client = ImapClient(ImapConfig("127.0.0.1", greenMail.imap.port, "kai", "secret", secure = false))
        client.connect()
    }

    @After fun tearDown() {
        client.close()
        greenMail.stop()
    }

    @Test fun searchHeadersFetchReadOnly() {
        assertTrue(client.folders().contains("INBOX"))
        val today = LocalDate.now()
        val uids = client.search("INBOX", today.minusDays(1), today.plusDays(1))
        assertEquals(3, uids.size) // the sale email has no receipt words
        assertEquals(uids.sorted(), uids)
        assertEquals(emptyList<Long>(), client.search("INBOX", today.minusYears(2), today.minusYears(1)))
        assertEquals(Folder.READ_ONLY, client.current!!.mode)

        val headers = client.headers("INBOX", uids)
        assertEquals(
            listOf("receipts@messaging.squareup.com", "billing@acme-hosting.io", "auto-confirm@amazon.com"),
            headers.map {
                it.second.from
            }
        )
        assertEquals("Invoice № 1042 for March", headers[1].second.subject)
        assertEquals(MessageIds.hash("<square-1@squareup.com>"), headers[0].second.messageIdHash)

        val mail = client.fetch("INBOX", uids[1])
        assertEquals("billing@acme-hosting.io", mail.header.from)
        assertEquals("invoice.pdf", mail.parse().attachments.single().name)
        assertEquals(Folder.READ_ONLY, client.current!!.mode)

        // Nothing got marked as seen on the server.
        val store = Session.getInstance(Properties()).getStore("imap")
        store.connect("127.0.0.1", greenMail.imap.port, "kai", "secret")
        val inbox = store.getFolder("INBOX").apply { open(Folder.READ_ONLY) }
        assertFalse(inbox.messages.any { it.isSet(Flags.Flag.SEEN) })
        store.close()
    }

    @Test fun uidsAfter() {
        val all = client.uidsAfter("INBOX", 0)
        assertEquals(4, all.size)
        assertEquals(all.drop(2), client.uidsAfter("INBOX", all[1]))
        assertEquals(emptyList<Long>(), client.uidsAfter("INBOX", all.last()))
    }

    @Test fun hostsAndGmailQuery() {
        assertEquals("imap.gmail.com", ImapClient.hostFor("a@Gmail.com"))
        assertEquals("outlook.office365.com", ImapClient.hostFor("a@hotmail.com"))
        assertEquals("imap.mail.me.com", ImapClient.hostFor("a@me.com"))
        assertNull(ImapClient.hostFor("a@proton.me"))
        assertEquals("imap.example.org", ImapClient.hostFor("a@example.org"))
        assertEquals(
            "after:2026/01/01 before:2026/07/01 " +
                "(receipt OR invoice OR order OR \"your trip\" OR payment OR total OR paid OR category:purchases)",
            ImapClient.gmailQuery(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30))
        )
    }
}
