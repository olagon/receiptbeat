package org.kealohalabs.receiptbeat.extraction

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kealohalabs.receiptbeat.core.model.Confidence

class HelpersTest {
    private val today = LocalDate.of(2026, 10, 17)

    @Test fun merchantNormalizer() {
        assertEquals("Kona Coffee", MerchantNormalizer.normalize("SQ *KONA COFFEE"))
        assertEquals("Ono Grindz", MerchantNormalizer.normalize("TST* ONO GRINDZ LLC"))
        assertEquals("Walgreens", MerchantNormalizer.normalize("WALGREENS #4521"))
        assertEquals("Safeway", MerchantNormalizer.normalize("SAFEWAY STORE 1234"))
        assertEquals("CVS", MerchantNormalizer.normalize("CVS/pharmacy #08832"))
        assertEquals("BBQ Hut", MerchantNormalizer.normalize("BBQ HUT INC"))
        assertEquals("Chevron", MerchantNormalizer.normalize("CHEVR0N"))
        assertEquals("McDonald's", MerchantNormalizer.normalize("McDonald's"))
        assertEquals(MerchantNormalizer.key("Kona Coffee"), MerchantNormalizer.key("PAYPAL *KONACOFFEE"))
    }

    @Test fun cardMaskerUsesLuhn() {
        assertEquals("Card •••• 1111 ok", CardMasker.mask("Card 4111 1111 1111 1111 ok"))
        assertEquals("•••• 0005", CardMasker.mask("3782-822463-10005"))
        assertEquals("Order 4111111111111112", CardMasker.mask("Order 4111111111111112"))
        assertEquals("Phone 808-555-1234", CardMasker.mask("Phone 808-555-1234"))
    }

    @Test fun imageHashDistance() {
        val flat = IntArray(72) { 100 }
        val ramp = IntArray(72) { 255 - it % 9 * 10 }
        assertEquals(0L, ImageHash.dHash(flat))
        assertEquals(-1L, ImageHash.dHash(ramp))
        assertEquals(64, ImageHash.distance(ImageHash.dHash(flat), ImageHash.dHash(ramp)))
        assertEquals(1, ImageHash.distance(0b1010, 0b1000))
    }

    @Test fun duplicates() {
        val d = LocalDate.of(2026, 10, 1)
        assertTrue(Duplicates.isLikely(500, d, "SQ *KONA COFFEE", null, 500, d.plusDays(1), "Kona Coffee", null))
        assertTrue(Duplicates.isLikely(500, d, null, null, 500, d, "Kona Coffee", null))
        assertFalse(Duplicates.isLikely(500, d, "Kona Coffee", null, 500, d.plusDays(2), "Kona Coffee", null))
        assertFalse(Duplicates.isLikely(500, d, "Kona Coffee", null, 500, d, "Safeway", null))
        assertFalse(Duplicates.isLikely(500, d, null, null, 501, d, null, null))
        assertTrue(Duplicates.isLikely(null, null, null, 0b111111L, null, null, null, 0L))
        assertFalse(Duplicates.isLikely(null, null, null, 0b1111111L, null, null, null, 0L))
    }

    private fun extracted(total: Long?, subtotal: Long? = null, tax: Long? = null, date: LocalDate? = null) = Extracted(
        merchant = missing(),
        date = found(date, Confidence.HIGH),
        subtotal = found(subtotal, Confidence.HIGH),
        tax = found(tax, Confidence.HIGH),
        tip = missing(),
        total = found(total, Confidence.HIGH),
        currency = found("USD", Confidence.HIGH)
    )

    @Test fun reconcileMathCheck() {
        assertEquals(Confidence.HIGH, Reconcile.merge(null, extracted(1100, 1000, 100), today).total.confidence)
        assertEquals(Confidence.HIGH, Reconcile.merge(null, extracted(1101, 1000, 100), today).total.confidence)
        assertEquals(Confidence.LOW, Reconcile.merge(null, extracted(1200, 1000, 100), today).total.confidence)
    }

    @Test fun reconcileDisagreement() {
        val r = Reconcile.merge(extracted(1100), extracted(1300), today)
        assertEquals(1100L, r.total.value)
        assertEquals(Confidence.LOW, r.total.confidence)
        assertEquals(listOf(1100L, 1300L), r.totalAlternatives)
    }

    @Test fun reconcilePrefersAiAndFlagsFutureDates() {
        val r = Reconcile.merge(extracted(null, date = today.plusDays(3)), extracted(900, date = today), today)
        assertEquals(900L, r.total.value)
        assertEquals(today.plusDays(3), r.date.value)
        assertEquals(Confidence.LOW, r.date.confidence)
    }

    @Test fun nanoPromptParsesFencedReply() {
        val reply =
            "Sure! {here it is}\n```json\n{\"merchant\":\"SQ *KONA COFFEE\",\"date\":\"2026-10-01\",\"time\":\"9:05\"," +
                "\"subtotal\":\"4.25\",\"tax\":0.20,\"tip\":null,\"total\":\"4.45\",\"currency\":\"usd\",\"card_brand\":\"Visa\"," +
                "\"last4\":\"1234\",\"receipt_number\":null,\"category\":\"meals\"}\n```\nHope that helps."
        val r = NanoPrompt.parse(reply)!!
        assertEquals("Kona Coffee", r.merchant.value)
        assertEquals(Confidence.MEDIUM, r.merchant.confidence)
        assertEquals(LocalDate.of(2026, 10, 1), r.date.value)
        assertEquals(445L, r.total.value)
        assertEquals(20L, r.tax.value)
        assertNull(r.tip.value)
        assertEquals(Confidence.LOW, r.tip.confidence)
        assertEquals("USD", r.currency.value)
        assertEquals("meals", r.categoryGuess)
        assertNull(NanoPrompt.parse("I could not read it."))
        assertNull(NanoPrompt.parse("{not json"))
    }

    @Test fun nanoPromptTruncates() {
        assertTrue(NanoPrompt.build("x".repeat(10_000)).length < 4000)
    }

    @Test fun categoryGuess() {
        assertEquals("meals", CategoryGuess.guess("Kona Coffee", ""))
        assertEquals("lodging", CategoryGuess.guess("Hilton Hawaiian Village", ""))
        assertEquals("car", CategoryGuess.guess("Chevron", ""))
        assertEquals("phone", CategoryGuess.guess("Hawaiian Telcom", ""))
        assertEquals("office", CategoryGuess.guess("Staples", ""))
        assertEquals("medical", CategoryGuess.guess("Longs Drugs", ""))
        assertEquals("travel", CategoryGuess.guess(null, "Flight HA 123 boarding pass fare"))
        assertNull(CategoryGuess.guess(null, "nothing useful"))
    }
}
