package org.kealohalabs.receiptbeat.ui.settings

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.kealohalabs.receiptbeat.core.data.CatalogRepository
import org.kealohalabs.receiptbeat.core.data.ReceiptRepository
import org.kealohalabs.receiptbeat.core.model.Payer
import org.kealohalabs.receiptbeat.core.model.Receipt
import org.kealohalabs.receiptbeat.core.model.Source
import org.kealohalabs.receiptbeat.core.model.Status
import org.kealohalabs.receiptbeat.core.model.Track

/** Debug builds only: a year of made up receipts, for trying the dashboard and taking screenshots. */
object SampleData {
    suspend fun add(receipts: ReceiptRepository, catalog: CatalogRepository) {
        val ri = Payer(name = "Resource Innovations", submitDeadlineDays = 60).also { catalog.savePayer(it) }
        val board = Payer(name = "Board travel").also { catalog.savePayer(it) }
        val today = LocalDate.now()
        val rows = listOf(
            Triple("Kona Coffee", 1250L, "meals"), Triple("Hawaiian Airlines", 28640L, "travel"), Triple("Hilton Hilo", 41200L, "lodging"),
            Triple("Shell", 6830L, "car"), Triple("Office Depot", 4599L, "office"), Triple("Adobe", 5999L, "software"),
            Triple("Hawaiian Telcom", 8900L, "phone"), Triple("Roy's", 9675L, "meals"), Triple("Enterprise", 21400L, "car"),
            Triple("Longs Drugs", 2310L, "medical"), Triple("KTA Super Stores", 7244L, "other"), Triple("Uber", 3245L, "travel"),
        )
        var n = 0
        for (m in 0..9) {
            for (k in 0..2) {
                val (merchant, cents, cat) = rows[(m * 3 + k) % rows.size]
                val day = today.minusMonths(m.toLong()).withDayOfMonth(1 + (k * 9) % 27).coerceAtMost(today)
                val track = when (k) {
                    0 -> Track.REIMBURSABLE
                    1 -> Track.TAX
                    else -> Track.PERSONAL
                }
                val status = when {
                    track == Track.REIMBURSABLE && m == 0 -> Status.READY
                    track == Track.REIMBURSABLE && m == 1 -> Status.SUBMITTED
                    track == Track.REIMBURSABLE -> Status.REIMBURSED
                    else -> Status.READY
                }
                val paidAt = if (status ==
                    Status.REIMBURSED
                ) {
                    day.plusDays(20).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
                } else {
                    null
                }
                receipts.insert(
                    Receipt(
                        source = Source.CAMERA,
                        merchantName = merchant,
                        purchaseDate = day,
                        totalCents = cents + n * 37,
                        categoryId = cat,
                        payerId = if (track == Track.REIMBURSABLE) (if (m % 2 == 0) ri.id else board.id) else null,
                        track = track,
                        status = status,
                        reimbursedCents = if (status == Status.REIMBURSED) cents + n * 37 else null,
                        reimbursedAt = paidAt?.let { minOf(it, Instant.now().minus(1, ChronoUnit.HOURS)) },
                    ),
                    "Sample receipt",
                )
                n++
            }
        }
    }
}
