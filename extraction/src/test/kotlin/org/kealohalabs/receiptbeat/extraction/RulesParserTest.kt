package org.kealohalabs.receiptbeat.extraction

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.kealohalabs.receiptbeat.core.model.Confidence

class RulesParserTest {
    private val today = LocalDate.of(2026, 10, 17)
    private fun parse(text: String) = RulesParser.parse(text.trimIndent(), today)

    @Test fun usDateAndTime() {
        val r = parse("KONA COFFEE\n10/05/2026 2:32 PM\nTOTAL 4.50")
        assertEquals(LocalDate.of(2026, 10, 5), r.date.value)
        assertEquals(LocalTime.of(14, 32), r.time)
    }

    @Test fun yearSplitByOcr() {
        // Real ML Kit output from a receipt font.
        assertEquals(LocalDate.of(2026, 10, 5), parse("KONA BEAN CAFE\n10/05/2 026 07:42 AM\nTOTAL 13.87").date.value)
        assertEquals(LocalDate.of(2026, 10, 5), parse("Cafe\n10/05/20 26\nTOTAL 1.00").date.value)
        // A short year followed by a time is not a split year.
        assertEquals(LocalDate.of(2020, 10, 5), parse("Cafe\n10/05/20 07:42\nTOTAL 1.00").date.value)
    }

    @Test fun dayFirstWhenDayOver12() = assertEquals(LocalDate.of(2026, 3, 17), parse("Cafe\n17/03/2026\nTOTAL 3.00").date.value)

    @Test fun dotDatesAreDayFirst() = assertEquals(LocalDate.of(2026, 3, 4), parse("Cafe\n04.03.2026\nSUMME 3,00 €").date.value)

    @Test fun isoAndNamedAndShortYearDates() {
        assertEquals(LocalDate.of(2026, 9, 1), parse("Shop\n2026-09-01\nTotal 1.00").date.value)
        assertEquals(LocalDate.of(2026, 10, 17), parse("Shop\nOct 17, 2026\nTotal 1.00").date.value)
        assertEquals(LocalDate.of(2026, 10, 7), parse("Shop\n7 Oct 2026\nTotal 1.00").date.value)
        assertEquals(LocalDate.of(2025, 12, 24), parse("Shop\n12/24/25\nTotal 1.00").date.value)
    }

    @Test fun moneyParsing() {
        assertEquals(1250L, parse("Shop\nSUMME 12,50 €").total.value)
        assertEquals(123456L, parse("Shop\nTOTAL $1,234.56").total.value)
        assertEquals(128000L, parse("Shop\n合計 ¥1,280").total.value)
        assertEquals(1050L, parse("Shop\nTOTAL 1O.5O").total.value)
    }

    @Test fun totalKeywordPriorityAndMath() {
        val r = parse(
            """
            SAFEWAY
            MILK 4.99
            BREAD 3.50
            SUBTOTAL 8.49
            TAX 0.40
            TOTAL 8.89
            TOTAL SAVINGS 2.00
            TOTAL ITEMS 2
            VISA ************1234 8.89
            CHANGE DUE 0.00
            """
        )
        assertEquals(889L, r.total.value)
        assertEquals(Confidence.HIGH, r.total.confidence)
        assertEquals(849L, r.subtotal.value)
        assertEquals(40L, r.tax.value)
        assertEquals("VISA", r.paymentBrand)
        assertEquals("1234", r.last4)
        assertEquals(listOf("MILK", "BREAD"), r.lineItems.map { it.name })
    }

    @Test fun handwrittenTipTotalWins() {
        val r = parse("Bistro\nSubtotal 40.00\nTax 1.88\nAmount 41.88\nTip 8.00\nTotal 49.88")
        assertEquals(4988L, r.total.value)
        assertEquals(800L, r.tip.value)
    }

    @Test fun hawaiiGetTax() {
        val r = parse("HILO HATTIE\nSubtotal 20.00\nGET 4.712% 0.94\nTotal 20.94")
        assertEquals(94L, r.tax.value)
    }

    @Test fun currencyDetection() {
        assertEquals("GBP", parse("Pub\nTotal £5.00").currency.value)
        assertEquals("CAD", parse("Shop\nTotal C$5.00").currency.value)
        assertEquals("EUR", parse("Shop\nTotal 5,00 EUR").currency.value)
        assertEquals("USD", parse("Shop\nTotal 5.00").currency.value)
    }

    @Test fun merchantSkipsNoise() {
        val r = parse("~~~~\nWelcome to Foodland\n123 Main St\nTotal 5.00")
        assertEquals("Foodland", r.merchant.value)
        val s = parse("SQ *KONA COFFEE\n75-5744 Alii Dr\nKailua-Kona, HI 96740\n(808) 555-1234\nTotal 4.50")
        assertEquals("Kona Coffee", s.merchant.value)
        assertEquals("(808) 555-1234", s.phone)
    }

    @Test fun missingTotalIsLow() {
        val r = parse("Nothing here")
        assertNull(r.total.value)
        assertEquals(Confidence.LOW, r.total.confidence)
    }

    @Test fun keepsBoxOfSourceLine() {
        val box = Box(1, 2, 3, 4)
        val r = RulesParser.parse(listOf(OcrLine("Shop"), OcrLine("TOTAL 9.99", box)), today)
        assertEquals(box, r.total.box)
    }
}
