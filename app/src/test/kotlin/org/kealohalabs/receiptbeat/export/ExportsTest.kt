package org.kealohalabs.receiptbeat.export

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.kealohalabs.receiptbeat.core.model.FileKind
import org.kealohalabs.receiptbeat.core.model.Receipt
import org.kealohalabs.receiptbeat.core.model.ReceiptFile

class ExportsTest {
    private val kona = Receipt(merchantName = "Kona Coffee", purchaseDate = LocalDate.of(2026, 3, 14), totalCents = 1250)

    @Test fun quotesCsvCells() {
        assertEquals("plain", Csv.cell("plain"))
        assertEquals("\"Smith, Jones\"", Csv.cell("Smith, Jones"))
        assertEquals("\"say \"\"hi\"\"\"", Csv.cell("say \"hi\""))
        assertEquals("\"two\nlines\"", Csv.cell("two\nlines"))
        assertEquals("'=SUM(A1)", Csv.cell("=SUM(A1)"))
        assertEquals("-5.00", Csv.cell("-5.00"))
    }

    @Test fun writesOneRowPerReceipt() {
        val names = Lookup(mapOf("meals" to "Meals"), emptyMap(), emptyMap())
        val csv = Csv.receipts(
            listOf(kona.copy(categoryId = "meals", taxCents = 104, notes = "Team, lunch")),
            names,
            mapOf(
                kona.id to "a.jpg"
            )
        )
        val lines = csv.trimEnd().split("\r\n")
        assertEquals("Date,Merchant,Category,Amount,Tax,Currency,Payer,Project,Notes,File", lines[0])
        assertEquals("2026-03-14,Kona Coffee,Meals,12.50,1.04,USD,,,\"Team, lunch\",a.jpg", lines[1])
    }

    @Test fun namesTaxZipFiles() {
        assertEquals("2026-03-14_Kona-Coffee_12.50.jpg", ExportNames.fileName(kona, "jpg"))
        assertEquals("no-date_Receipt.pdf", ExportNames.fileName(Receipt(merchantName = "  "), "pdf"))
        val files = listOf(
            ReceiptFile(receiptId = kona.id, kind = FileKind.DISPLAY, path = "d", mime = "image/webp", bytes = 1),
            ReceiptFile(receiptId = kona.id, kind = FileKind.ORIGINAL, path = "o1", mime = "image/jpeg", bytes = 1, page = 1),
            ReceiptFile(receiptId = kona.id, kind = FileKind.ORIGINAL, path = "o0", mime = "image/jpeg", bytes = 1, page = 0)
        )
        val best = ExportNames.bestFiles(files)
        assertEquals(listOf("o0", "o1"), best.map { it.path })
        val used = mutableSetOf<String>()
        assertEquals(
            listOf("2026-03-14_Kona-Coffee_12.50.jpg", "2026-03-14_Kona-Coffee_12.50_p2.jpg"),
            ExportNames.names(kona, best, used).map { it.second }
        )
        // Same receipt details again: names stay unique inside one ZIP.
        assertEquals("2026-03-14_Kona-Coffee_12.50_2.jpg", ExportNames.names(kona, best.take(1), used).single().second)
    }
}
