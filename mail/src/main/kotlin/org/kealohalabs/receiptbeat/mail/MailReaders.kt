package org.kealohalabs.receiptbeat.mail

import com.pff.PSTFile
import com.pff.PSTFolder
import com.pff.PSTMessage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.zip.ZipInputStream
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import org.kealohalabs.receiptbeat.core.model.MailFormat

/**
 * Streaming readers. Each returns a lazy Sequence; `skip` = messages already handled (resume).
 * `onBytes` reports bytes consumed so far for progress. Readers never close the stream; the caller owns it.
 */
object MailReaders {
    fun mbox(input: InputStream, skip: Long = 0, onBytes: (Long) -> Unit = {}): Sequence<RawMail> =
        mboxOrSkipped(Counting(input, onBytes), skip).filterNotNull()

    fun eml(input: InputStream): RawMail = BytesMail(input.readNBytes(MAX_MESSAGE_BYTES))

    /** A zip of .mbox and/or .eml files: Google Takeout, Proton export, zipped Apple Mail export folders with "mbox" files inside. */
    fun zip(input: InputStream, skip: Long = 0, onBytes: (Long) -> Unit = {}): Sequence<RawMail> = sequence {
        val zin = ZipInputStream(Counting(input, onBytes))
        var index = 0L
        while (true) {
            val entry = zin.nextEntry ?: break
            val name = entry.name.substringAfterLast('/').lowercase()
            when {
                entry.isDirectory || name.startsWith("._") -> Unit
                name.endsWith(".eml") -> if (index++ >= skip) yield(eml(zin))
                name.endsWith(".mbox") || name == "mbox" -> for (mail in mboxOrSkipped(zin, (skip - index).coerceAtLeast(0))) {
                    index++
                    if (mail != null) yield(mail)
                }
            }
        }
    }

    /** java-libpst needs random access, so this takes a file. The file stays open until the sequence is fully consumed. */
    fun pst(file: File, skip: Long = 0): Sequence<RawMail> = sequence {
        val pst = PSTFile(file)
        try {
            var index = 0L
            val folders = ArrayDeque<PSTFolder>().apply { add(pst.rootFolder) }
            while (folders.isNotEmpty()) {
                val folder = folders.removeFirst()
                runCatching { if (folder.hasSubfolders()) folders.addAll(folder.subFolders) }
                if (folder.contentCount <= 0) continue
                while (true) {
                    val child = runCatching { folder.nextChild }.getOrNull() ?: break
                    if (child !is PSTMessage) continue
                    if (index++ < skip) continue
                    val mail = runCatching { pstMail(child) }.getOrNull() ?: continue
                    yield(mail)
                }
            }
        } finally {
            pst.close()
        }
    }

    /** Outlook for Mac .olm: a zip with one XML file per message. Attachments live in separate entries and are not read. */
    fun olm(input: InputStream, skip: Long = 0, onBytes: (Long) -> Unit = {}): Sequence<RawMail> = sequence {
        val zin = ZipInputStream(Counting(input, onBytes))
        var index = 0L
        val messageEntry = Regex("message_\\d+\\.xml$", RegexOption.IGNORE_CASE)
        while (true) {
            val entry = zin.nextEntry ?: break
            if (entry.isDirectory || !messageEntry.containsMatchIn(entry.name)) continue
            if (index++ < skip) continue
            val doc = Jsoup.parse(String(zin.readNBytes(MAX_MESSAGE_BYTES), Charsets.UTF_8), "", Parser.xmlParser())
            yield(olmMail(doc))
        }
    }

    fun detect(fileName: String, firstBytes: ByteArray): MailFormat? {
        val name = fileName.lowercase()
        val head = String(firstBytes, 0, minOf(firstBytes.size, 512), Charsets.ISO_8859_1)
        return when {
            head.startsWith("!BDN") -> MailFormat.PST
            head.startsWith("PK\u0003\u0004") ->
                if (name.endsWith(".olm") || head.contains("com.microsoft.__") || head.startsWith("Accounts/", 30)) {
                    MailFormat.OLM
                } else {
                    MailFormat.EML_ZIP
                }
            head.startsWith("From ") -> MailFormat.MBOX
            name.endsWith(".pst") -> MailFormat.PST
            name.endsWith(".olm") -> MailFormat.OLM
            name.endsWith(".mbox") || name == "mbox" -> MailFormat.MBOX
            name.endsWith(".zip") -> MailFormat.EML_ZIP
            name.endsWith(".eml") || Regex("^[A-Za-z][A-Za-z0-9-]*:").containsMatchIn(head) -> MailFormat.EML
            else -> null
        }
    }

    /** Splits an mbox stream. Yields null for each skipped message so callers can count across files. */
    private fun mboxOrSkipped(input: InputStream, skip: Long): Sequence<RawMail?> = sequence {
        val lines = LineReader(input)
        var prevBlank = true
        var started = false
        var index = 0L
        var current: Buf? = null
        while (lines.next()) {
            // ponytail: a From_ line is "From " after a blank line with a time in it; unescaped body lines that look the same will split.
            if (prevBlank && lines.startsWith(FROM) && lines.contains(':'.code.toByte())) {
                if (started) yield(current?.let { BytesMail(it.raw(), it.size()) })
                started = true
                current = if (index++ < skip) null else Buf()
                prevBlank = false
                continue
            }
            prevBlank = lines.isBlank()
            val buf = current ?: continue
            if (buf.size() + lines.len > MAX_MESSAGE_BYTES) continue
            // mboxrd: ">From " (any number of '>') loses one '>'.
            val unescape = lines.len > 1 && lines.line[0] == '>'.code.toByte() && lines.isEscapedFrom()
            buf.write(lines.line, if (unescape) 1 else 0, if (unescape) lines.len - 1 else lines.len)
        }
        if (started) yield(current?.let { BytesMail(it.raw(), it.size()) })
    }

    private fun pstMail(msg: PSTMessage): RawMail {
        val header = Headers.header(
            msg.internetMessageId,
            msg.senderEmailAddress,
            msg.senderName,
            msg.subject.orEmpty(),
            (msg.messageDeliveryTime ?: msg.clientSubmitTime)?.toInstant()
        )
        return LazyMail(header) {
            val attachments = (0 until msg.numberOfAttachments).mapNotNull { i ->
                runCatching {
                    val att = msg.getAttachment(i)
                    if (att.filesize > MAX_MESSAGE_BYTES) return@runCatching null
                    MailAttachment(
                        att.longFilename.ifBlank { att.filename }.ifBlank { "attachment" },
                        att.mimeTag.ifBlank { "application/octet-stream" }.lowercase(),
                        att.fileInputStream.use { it.readNBytes(MAX_MESSAGE_BYTES) }
                    )
                }.getOrNull()
            }
            MailMessage(header, msg.bodyHTML?.ifBlank { null }, msg.body?.ifBlank { null }, attachments)
        }
    }

    private fun olmMail(doc: Document): RawMail {
        fun text(tag: String) = doc.getElementsByTag(tag).firstOrNull()?.wholeText()?.trim()?.ifEmpty { null }
        val sender = doc.getElementsByTag("OPFMessageCopySenderAddress").firstOrNull()
        val box = sender?.getElementsByTag("emailAddress")?.firstOrNull()
        val address = box?.attr("OPFContactEmailAddressAddress")?.ifEmpty { null } ?: sender?.text()
        val date = (text("OPFMessageCopySentTime") ?: text("OPFMessageCopyReceivedTime"))?.let { raw ->
            runCatching { Instant.parse(raw) }.getOrNull()
                ?: runCatching { LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC) }.getOrNull()
                ?: Headers.date(raw)
        }
        val header = Headers.header(
            text("OPFMessageCopyMessageID"),
            address,
            box?.attr("OPFContactEmailAddressName"),
            text("OPFMessageCopySubject").orEmpty(),
            date
        )
        val message = MailMessage(header, text("OPFMessageCopyHTMLBody"), text("OPFMessageCopyBody"), emptyList())
        return LazyMail(header) { message }
    }

    private val FROM = "From ".toByteArray()
}

private class LazyMail(override val header: MailHeader, private val full: () -> MailMessage) : RawMail {
    override fun parse() = full()
}

/** ByteArrayOutputStream that hands over its buffer without a copy. */
private class Buf : ByteArrayOutputStream(8192) {
    fun raw(): ByteArray = buf
}

/** Counts bytes read and reports progress every 64 KB and at the end. */
private class Counting(input: InputStream, private val onBytes: (Long) -> Unit) : FilterInputStream(input) {
    private var count = 0L
    private var reported = 0L

    private fun add(n: Long) {
        if (n < 0) {
            if (reported != count) onBytes(count).also { reported = count }
            return
        }
        count += n
        if (count - reported >= 64 * 1024) onBytes(count).also { reported = count }
    }

    override fun read(): Int = super.read().also { add(if (it < 0) -1 else 1) }
    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { add(it.toLong()) }
    override fun skip(n: Long): Long = super.skip(n).also { add(it) }
}

/** Reads raw lines (with their line ending) into a reusable buffer. Lines longer than the message cap are cut. */
private class LineReader(private val input: InputStream) {
    private val buf = ByteArray(64 * 1024)
    private var pos = 0
    private var limit = 0
    var line = ByteArray(1024)
    var len = 0

    fun next(): Boolean {
        len = 0
        while (true) {
            if (pos == limit) {
                limit = input.read(buf).coerceAtLeast(0)
                pos = 0
                if (limit == 0) return len > 0
            }
            var i = pos
            while (i < limit && buf[i] != NL) i++
            val end = if (i < limit) i + 1 else limit
            append(end - pos)
            pos = end
            if (len > 0 && line[len - 1] == NL) return true
        }
    }

    private fun append(n: Int) {
        val room = MAX_MESSAGE_BYTES - len
        val take = minOf(n, room)
        if (take <= 0) {
            if (n > 0 && buf[pos + n - 1] == NL) line[len - 1] = NL
            return
        }
        if (len + take > line.size) line = line.copyOf(maxOf(len + take, line.size * 2))
        System.arraycopy(buf, pos, line, len, take)
        len += take
    }

    fun startsWith(prefix: ByteArray): Boolean = len >= prefix.size && prefix.indices.all { line[it] == prefix[it] }

    fun contains(b: Byte): Boolean = (0 until len).any { line[it] == b }

    fun isBlank(): Boolean = (0 until len).all { line[it] == NL || line[it] == CR || line[it] == ' '.code.toByte() }

    fun isEscapedFrom(): Boolean {
        var i = 0
        while (i < len && line[i] == '>'.code.toByte()) i++
        return i > 0 && len - i >= 5 && (0 until 5).all { line[i + it] == "From "[it].code.toByte() }
    }

    private companion object {
        const val NL = '\n'.code.toByte()
        const val CR = '\r'.code.toByte()
    }
}
