package org.kealohalabs.receiptbeat.mail

import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kealohalabs.receiptbeat.core.model.MailFormat

fun resource(name: String): InputStream = checkNotNull(ReadersTest::class.java.getResourceAsStream("/$name")) { name }

class ReadersTest {
    private fun sample() = resource("sample.mbox").use { MailReaders.mbox(it).toList() }

    @Test fun mboxSplitsAndParsesHeaders() {
        val mails = sample()
        assertEquals(6, mails.size)
        val amazon = mails[0].header
        assertEquals("auto-confirm@amazon.com", amazon.from)
        assertEquals("Amazon.com", amazon.fromName)
        assertEquals("<amazon-1@amazon.com>", amazon.messageId)
        assertEquals(MessageIds.hash("<amazon-1@amazon.com>"), amazon.messageIdHash)
        assertEquals(Instant.parse("2026-03-14T19:15:00Z"), amazon.date)
        assertEquals("Uber Receipts", mails[1].header.fromName)
        // RFC 2047 encoded subject
        assertEquals("Invoice № 1042 for March", mails[5].header.subject)
    }

    @Test fun mboxUnescapesFromLinesAndHashesMissingIds() {
        val personal = sample()[4]
        assertEquals("", personal.header.messageId)
        assertEquals(64, personal.header.messageIdHash.length)
        val text = personal.parse().text!!
        assertTrue(text, text.contains("\nFrom now on I am picking"))
        assertTrue(!text.contains(">From"))
    }

    @Test fun mboxResumeSkipsHandledMessages() {
        val rest = resource("sample.mbox").use { MailReaders.mbox(it, skip = 4).toList() }
        assertEquals(listOf("Lunch Saturday?", "Invoice № 1042 for March"), rest.map { it.header.subject })
    }

    @Test fun mboxReportsBytes() {
        var last = 0L
        val size = resource("sample.mbox").use { it.readBytes().size.toLong() }
        resource("sample.mbox").use { MailReaders.mbox(it, onBytes = { n -> last = n }).count() }
        assertEquals(size, last)
    }

    @Test fun fullParseFindsHtmlTextAndPdf() {
        val mails = sample()
        val uber = mails[1].parse()
        assertTrue(uber.html!!.contains("Thanks for riding"))
        assertTrue(uber.text!!.contains("Total $23.45"))
        val invoice = mails[5].parse()
        assertEquals(1, invoice.attachments.size)
        assertEquals("invoice.pdf", invoice.attachments[0].name)
        assertEquals("application/pdf", invoice.attachments[0].mime)
        assertTrue(String(invoice.attachments[0].bytes).startsWith("%PDF"))
    }

    @Test fun emlReads() {
        val mail = resource("invoice.eml").use { MailReaders.eml(it) }
        assertEquals("billing@acme-hosting.io", mail.header.from)
        assertEquals(1, mail.parse().attachments.size)
    }

    @Test fun zipReadsMboxAndEmlEntriesWithResume() {
        val all = resource("export.zip").use { MailReaders.zip(it).toList() }
        // 6 from the Takeout mbox, 2 eml, 1 in an Apple Mail "mbox" file; the json is ignored
        assertEquals(9, all.size)
        val rest = resource("export.zip").use { MailReaders.zip(it, skip = 7).toList() }
        assertEquals(all.drop(7).map { it.header.messageIdHash }, rest.map { it.header.messageIdHash })
    }

    @Test fun olmReadsMessageXml() {
        val mails = resource("sample.olm").use { MailReaders.olm(it).toList() }
        assertEquals(2, mails.size)
        val uber = mails[0]
        assertEquals("noreply@uber.com", uber.header.from)
        assertEquals("Uber Receipts", uber.header.fromName)
        assertEquals("<uber-olm-1@uber.com>", uber.header.messageId)
        assertEquals(Instant.parse("2026-04-03T18:20:00Z"), uber.header.date)
        assertTrue(uber.parse().html!!.contains("Total $31.80"))
        assertEquals("leilani@example.org", mails[1].header.from)
        assertEquals("See you at Waimanalo.", mails[1].parse().text)
        assertEquals(1, resource("sample.olm").use { MailReaders.olm(it, skip = 1).count() })
    }

    @Test fun detectsFormats() {
        val zip = resource("export.zip").use { it.readNBytes(512) }
        val olm = resource("sample.olm").use { it.readNBytes(512) }
        assertEquals(MailFormat.MBOX, MailReaders.detect("x.bin", "From a@b Sat Jan 1 00:00:00 2026\n".toByteArray()))
        assertEquals(MailFormat.EML_ZIP, MailReaders.detect("takeout.zip", zip))
        assertEquals(MailFormat.OLM, MailReaders.detect("export.olm", olm))
        assertEquals(MailFormat.OLM, MailReaders.detect("renamed.zip", olm))
        assertEquals(MailFormat.PST, MailReaders.detect("outlook.pst", "!BDN....".toByteArray()))
        assertEquals(MailFormat.EML, MailReaders.detect("message", "Return-Path: <a@b>\n".toByteArray()))
        assertNull(MailReaders.detect("photo.jpg", byteArrayOf(-1, -40, -1)))
    }

    @Test fun pstFailsCleanlyOnBadFile() {
        val f = kotlin.io.path.createTempFile(suffix = ".pst").toFile().apply { writeText("not a pst") }
        assertTrue(runCatching { MailReaders.pst(f).toList() }.isFailure)
        f.delete()
    }

    @Test fun messageIdHash() {
        assertEquals(MessageIds.hash(" <ABC@x.com> "), MessageIds.hash("<abc@x.com>"))
        assertNotEquals(MessageIds.hash("<a@x>"), MessageIds.hash("<b@x>"))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", MessageIds.hash("abc"))
    }

    @Test fun scanRangeGaps() {
        fun d(s: String) = LocalDate.parse(s)
        val done = listOf(d("2026-03-01") to d("2026-03-31"), d("2026-01-01") to d("2026-01-31"))
        assertEquals(
            listOf(d("2026-02-01") to d("2026-02-28"), d("2026-04-01") to d("2026-06-30")),
            ScanRanges.gaps(done, d("2026-01-01") to d("2026-06-30"))
        )
        assertEquals(emptyList<Any>(), ScanRanges.gaps(done, d("2026-03-05") to d("2026-03-10")))
        assertEquals(listOf(d("2025-12-01") to d("2025-12-31")), ScanRanges.gaps(done, d("2025-12-01") to d("2026-01-15")))
        assertEquals(listOf(d("2026-05-01") to d("2026-05-02")), ScanRanges.gaps(emptyList(), d("2026-05-01") to d("2026-05-02")))
    }

    /** ~200 MB of mbox generated on the fly, including one 40 MB message, streamed with a 256 MB heap. */
    @Test fun streamsHugeMboxWithSmallHeap() {
        val body = "x".repeat(99) + "\n"
        val count = 4000
        val gen = object : InputStream() {
            var i = 0
            var chunk = ByteArray(0)
            var pos = 0
            var bodyLeft = 0

            fun refill(): Boolean {
                if (bodyLeft > 0) {
                    val lines = minOf(bodyLeft, 1000)
                    chunk = body.repeat(lines).toByteArray()
                    bodyLeft -= lines
                } else {
                    if (i == count) return false
                    val head = "\nFrom gen@example.com Sat Jan 1 00:00:00 2026\nFrom: Gen <gen@example.com>\n"
                    chunk = "${head}Subject: msg $i\nMessage-ID: <$i@gen>\n\n".toByteArray()
                    bodyLeft = if (i == 10) 400_000 else 500
                    i++
                }
                pos = 0
                return true
            }

            override fun read(): Int {
                if (pos == chunk.size && !refill()) return -1
                return chunk[pos++].toInt() and 0xff
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (pos == chunk.size && !refill()) return -1
                val n = minOf(len, chunk.size - pos)
                System.arraycopy(chunk, pos, b, off, n)
                pos += n
                return n
            }
        }
        var bytes = 0L
        var seen = 0
        for (mail in MailReaders.mbox(gen, onBytes = { bytes = it })) {
            assertEquals("msg $seen", mail.header.subject)
            seen++
        }
        assertEquals(count, seen)
        assertTrue("streamed $bytes bytes", bytes > 200_000_000)
        assertTrue("heap is capped for this test", Runtime.getRuntime().maxMemory() < 300L shl 20)
    }
}
