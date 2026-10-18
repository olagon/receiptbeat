package org.kealohalabs.receiptbeat.core.data

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.kealohalabs.receiptbeat.core.model.Project
import org.kealohalabs.receiptbeat.core.model.Receipt

class TripMathTest {
    private val oct = { d: Int -> LocalDate.of(2026, 10, d) }

    @Test fun rollsUpTripsAndFindsLooseReceiptsInTheDates() {
        val kauai = Project(id = "k", name = "Kauai", startDate = oct(5), endDate = oct(9))
        val open = Project(id = "o", name = "No dates")
        val list = listOf(
            Receipt(id = "a", projectId = "k", totalCents = 2000, purchaseDate = oct(5)),
            Receipt(id = "b", projectId = "k", totalCents = 500, homeCurrencyTotalCents = 700, currency = "CAD", purchaseDate = oct(6)),
            Receipt(id = "gone", projectId = "k", totalCents = 9999, purchaseDate = oct(6), deletedAt = Instant.EPOCH),
            Receipt(id = "loose", totalCents = 100, purchaseDate = oct(9)),
            Receipt(id = "after", totalCents = 100, purchaseDate = oct(10)),
            Receipt(id = "other", projectId = "o", totalCents = 100, purchaseDate = oct(7)),
        )
        val (k, o) = TripMath.summarize(listOf(open, kauai), list)
        assertEquals("k", k.project.id)
        assertEquals(2, k.count)
        assertEquals(2700L, k.totalCents)
        assertEquals(listOf("loose"), k.unassignedInRange)
        // A trip with no dates takes them from its receipts and never pulls in loose ones.
        assertEquals(oct(7), o.first)
        assertEquals(emptyList<String>(), o.unassignedInRange)
    }
}
