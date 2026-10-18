package org.kealohalabs.receiptbeat

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kealohalabs.receiptbeat.capture.ReminderWorker
import org.kealohalabs.receiptbeat.core.model.Payer
import org.kealohalabs.receiptbeat.core.model.Receipt
import org.kealohalabs.receiptbeat.core.model.Status
import org.kealohalabs.receiptbeat.core.model.Track

class ReminderRulesTest {
    private val today = LocalDate.of(2026, 10, 16)
    private val board = Payer(id = "b", name = "Board travel", submitDeadlineDays = 60)
    private val ri = Payer(id = "r", name = "Resource Innovations")
    private val old = Instant.now().minus(40, ChronoUnit.DAYS)

    @Test fun nudgesForInboxDeadlineAndUnpaid() {
        val receipts = listOf(
            Receipt(status = Status.INBOX, createdAt = old),
            Receipt(status = Status.INBOX, createdAt = old),
            Receipt(status = Status.INBOX), // brand new, no nudge yet
            Receipt(track = Track.REIMBURSABLE, status = Status.READY, payerId = "b", purchaseDate = today.minusDays(58)),
            Receipt(track = Track.REIMBURSABLE, status = Status.SUBMITTED, payerId = "r", totalCents = 28640, updatedAt = old),
        )
        val n = ReminderWorker.nudges(receipts, mapOf("b" to board, "r" to ri), today, 3, 3, 30)
        assertEquals(3, n.size)
        assertEquals("2 receipts need a quick look", n[0].text)
        assertTrue(n[1].text, n[1].text.startsWith("Submit your Board travel receipts by"))
        assertEquals("Resource Innovations still owes you $286.40", n[2].text)
    }

    @Test fun quietWhenNothingIsDue() {
        assertTrue(ReminderWorker.nudges(listOf(Receipt(status = Status.READY)), emptyMap(), today, 3, 3, 30).isEmpty())
    }
}
