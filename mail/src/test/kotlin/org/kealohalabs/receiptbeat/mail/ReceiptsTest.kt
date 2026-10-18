package org.kealohalabs.receiptbeat.mail

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptsTest {
    private val mails = resource("sample.mbox").use { MailReaders.mbox(it).map { m -> m.parse() }.toList() }
    private val amazon = mails[0]
    private val uber = mails[1]
    private val square = mails[2]
    private val marketing = mails[3]
    private val personal = mails[4]
    private val invoice = mails[5]

    @Test fun scoreSeparatesReceiptsFromMarketingAndPersonal() {
        for (m in listOf(amazon, uber, square, invoice)) {
            val r = ReceiptScorer.score(m)
            assertTrue("${m.header.subject}: $r", r.score >= ReceiptScorer.KEEP)
        }
        for (m in listOf(marketing, personal)) {
            val r = ReceiptScorer.score(m)
            assertTrue("${m.header.subject}: $r", r.score < ReceiptScorer.DROP)
        }
        val u = ReceiptScorer.score(uber)
        assertEquals(2345L, u.amountCents)
        assertEquals("USD", u.currency)
        assertTrue(u.signals.contains("known sender"))
        assertTrue(ReceiptScorer.score(amazon).signals.contains("schema.org order"))
        assertTrue(ReceiptScorer.score(marketing).signals.contains("marketing"))
    }

    @Test fun extraKnownSendersCount() {
        val plain = ReceiptScorer.score(invoice).score
        assertTrue(ReceiptScorer.score(invoice, setOf("acme-hosting.io")).score >= plain)
        assertFalse(ReceiptScorer.score(personal).signals.contains("known sender"))
        assertTrue(ReceiptScorer.score(personal, setOf("leilani@example.org")).signals.contains("known sender"))
    }

    @Test fun headerScore() {
        assertTrue(ReceiptScorer.headerScore(uber.header) >= ReceiptScorer.KEEP)
        assertTrue(ReceiptScorer.headerScore(marketing.header) < ReceiptScorer.DROP)
        assertTrue(ReceiptScorer.headerScore(personal.header) < ReceiptScorer.DROP)
    }

    @Test fun schemaBeatsBody() {
        val f = EmailExtractor.extract(amazon)
        assertTrue(f.fromSchema)
        assertEquals("Amazon.com", f.merchant)
        assertEquals(4217L, f.totalCents) // body says $45.99, schema.org wins
        assertEquals(LocalDate.of(2026, 3, 14), f.date)
        assertEquals("112-3456789-0123456", f.orderNumber)
        assertEquals("USD", f.currency)
    }

    @Test fun microdataSchema() {
        val html = """<div itemscope itemtype="http://schema.org/Order">
            <div itemprop="seller" itemscope itemtype="http://schema.org/Organization"><span itemprop="name">Hawaiian Airlines</span></div>
            <meta itemprop="orderNumber" content="HA12345"/><meta itemprop="price" content="389.20"/>
            <meta itemprop="priceCurrency" content="USD"/><time itemprop="orderDate" datetime="2026-05-01">May 1</time></div>"""
        val msg = MailMessage(uber.header.copy(subject = "Your booking"), html, null, emptyList())
        val f = EmailExtractor.extract(msg)
        assertTrue(f.fromSchema)
        assertEquals("Hawaiian Airlines", f.merchant)
        assertEquals(38920L, f.totalCents)
        assertEquals("HA12345", f.orderNumber)
        assertEquals(LocalDate.of(2026, 5, 1), f.date)
    }

    @Test fun bodyFields() {
        val u = EmailExtractor.extract(uber)
        assertFalse(u.fromSchema)
        assertEquals(2345L, u.totalCents)
        assertEquals(135L, u.taxCents)
        assertEquals("Uber", u.merchant)
        assertEquals(uber.header.date!!.atZone(ZoneId.systemDefault()).toLocalDate(), u.date)
        assertNull(u.pdf)
        assertEquals("Kona Coffee", EmailExtractor.extract(square).merchant)
        assertEquals(850L, EmailExtractor.extract(square).totalCents)
        val inv = EmailExtractor.extract(invoice)
        assertNotNull(inv.pdf)
        assertEquals(12000L, inv.totalCents)
        assertEquals("INV-1042", inv.orderNumber)
        assertEquals("Acme", inv.merchant)
    }

    @Test fun htmlToTextDropsScriptsAndImages() {
        val text = EmailExtractor.htmlToText(amazon.html!!)
        assertFalse(text.contains("alert"))
        assertFalse(text.contains("color:red"))
        assertFalse(text.contains("pixel"))
        assertTrue(text.contains("Thanks for your order."))
        assertTrue(text.contains("Order Total: $45.99"))
    }

    @Test fun safeHtmlBlocksRemoteContent() {
        val safe = EmailExtractor.safeHtml(
            uber.html!! + amazon.html!! + "<link rel=stylesheet href='https://x/y.css'><div style=\"background:url(https://t/p)\">z</div>"
        )
        assertFalse(safe.contains("track.uber.com"))
        assertFalse(safe.contains("pixel.gif"))
        assertFalse(safe.contains("<script"))
        assertFalse(safe.contains("onload"))
        assertFalse(safe.contains("javascript:"))
        assertFalse(safe.contains("y.css"))
        assertFalse(safe.contains("url("))
        assertTrue(safe.contains("cid:logo"))
        assertTrue(safe.contains("Thanks for riding"))
    }
}
