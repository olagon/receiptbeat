package org.kealohalabs.receiptbeat.mail

import java.io.ByteArrayInputStream
import java.io.StringReader
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.apache.james.mime4j.codec.DecodeMonitor
import org.apache.james.mime4j.codec.DecoderUtil
import org.apache.james.mime4j.dom.Body
import org.apache.james.mime4j.dom.Entity
import org.apache.james.mime4j.dom.Multipart
import org.apache.james.mime4j.dom.SingleBody
import org.apache.james.mime4j.dom.TextBody
import org.apache.james.mime4j.field.address.LenientAddressParser
import org.apache.james.mime4j.field.datetime.parser.DateTimeParser
import org.apache.james.mime4j.message.DefaultMessageBuilder
import org.apache.james.mime4j.stream.MimeConfig

/** Cheap header-only view, used for the skim pass. */
data class MailHeader(
    val messageId: String,
    val messageIdHash: String,
    /** Lowercase address. */
    val from: String,
    val fromName: String?,
    val subject: String,
    val date: Instant?
)

data class MailAttachment(val name: String, val mime: String, val bytes: ByteArray) {
    override fun equals(other: Any?) =
        other is MailAttachment && name == other.name && mime == other.mime && bytes.contentEquals(other.bytes)
    override fun hashCode() = 31 * (31 * name.hashCode() + mime.hashCode()) + bytes.contentHashCode()
}

data class MailMessage(val header: MailHeader, val html: String?, val text: String?, val attachments: List<MailAttachment>)

/** One message as read from a source. parse() does the full MIME parse lazily, only for messages that pass the skim. */
interface RawMail {
    val header: MailHeader
    fun parse(): MailMessage
}

object MessageIds {
    /** SHA-256 hex of the trimmed, lowercased id. When there is no Message-ID, hash from+date+subject instead. */
    fun hash(messageId: String): String = MessageDigest.getInstance("SHA-256").digest(messageId.trim().lowercase().toByteArray())
        .joinToString("") { "%02x".format(it) }
}

/** Remembered scan ranges. Ranges are inclusive on both ends. */
object ScanRanges {
    fun gaps(done: List<Pair<LocalDate, LocalDate>>, wanted: Pair<LocalDate, LocalDate>): List<Pair<LocalDate, LocalDate>> {
        val out = mutableListOf<Pair<LocalDate, LocalDate>>()
        var cursor = wanted.first
        for ((from, to) in done.sortedBy { it.first }) {
            if (cursor > wanted.second) break
            if (to < cursor) continue
            if (from > cursor) out += cursor to minOf(from.minusDays(1), wanted.second)
            if (to >= cursor) cursor = to.plusDays(1)
        }
        if (cursor <= wanted.second) out += cursor to wanted.second
        return out
    }
}

/** Largest single message we buffer. Bigger ones are cut, which drops their tail (usually attachments). */
internal const val MAX_MESSAGE_BYTES = 25 * 1024 * 1024

/** Header parse plus lazy full parse, over one message's bytes. */
internal class BytesMail(private val bytes: ByteArray, private val length: Int = bytes.size) : RawMail {
    override val header: MailHeader = Headers.parse(bytes, length)
    override fun parse(): MailMessage = Mime.parse(ByteArrayInputStream(bytes, 0, length), header)
}

internal object Headers {
    /** Reads the header block (up to the first blank line) and pulls Message-ID, From, Subject and Date. */
    fun parse(bytes: ByteArray, length: Int = bytes.size): MailHeader {
        var end = length
        var i = 0
        while (i < length) {
            if (bytes[i] == '\n'.code.toByte()) {
                val next = i + 1
                if (next < length && bytes[next] == '\n'.code.toByte()) {
                    end = next
                    break
                }
                if (next + 1 < length && bytes[next] == '\r'.code.toByte() && bytes[next + 1] == '\n'.code.toByte()) {
                    end = next
                    break
                }
            }
            i++
        }
        val fields = HashMap<String, String>()
        String(bytes, 0, end, Charsets.UTF_8).replace("\r", "").replace(Regex("\n[ \t]+"), " ").lineSequence().forEach { line ->
            val colon = line.indexOf(':')
            if (colon > 0) fields.putIfAbsent(line.substring(0, colon).trim().lowercase(), line.substring(colon + 1).trim())
        }
        return build(fields["message-id"], fields["from"], fields["subject"], date(fields["date"]))
    }

    fun build(messageId: String?, from: String?, subject: String?, date: Instant?): MailHeader {
        val box = from?.let { runCatching { LenientAddressParser.DEFAULT.parseMailbox(it) }.getOrNull() }
        val address = box?.address ?: from?.let { Regex("[^<\\s]+@[^>\\s]+").find(it)?.value }
        return header(messageId, address, box?.name?.let { decode(it) }, decode(subject ?: ""), date)
    }

    fun header(messageId: String?, address: String?, name: String?, subject: String, date: Instant?): MailHeader {
        val from = address.orEmpty().trim().lowercase()
        val id = messageId?.trim().orEmpty()
        val hash = MessageIds.hash(id.ifEmpty { "$from|$date|$subject" })
        return MailHeader(id, hash, from, name?.trim()?.trim('"')?.takeIf { it.isNotBlank() }, subject, date)
    }

    fun decode(s: String): String = runCatching { DecoderUtil.decodeEncodedWords(s, DecodeMonitor.SILENT) }.getOrDefault(s)

    fun date(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        runCatching { return DateTimeParser(StringReader(raw)).parseAll().date.toInstant() }
        val cleaned = raw.replace(Regex("\\(.*?\\)"), "").replace(Regex("\\s+"), " ").trim()
        return runCatching { ZonedDateTime.parse(cleaned, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()
    }
}

internal object Mime {
    private val config = MimeConfig.Builder().setMaxLineLen(-1).setMaxHeaderLen(-1).setMaxHeaderCount(-1).build()

    fun parse(input: java.io.InputStream, header: MailHeader): MailMessage {
        val builder = DefaultMessageBuilder().apply {
            setMimeEntityConfig(config)
            setDecodeMonitor(DecodeMonitor.SILENT)
        }
        val message = builder.parseMessage(input)
        var html: String? = null
        var text: String? = null
        val attachments = mutableListOf<MailAttachment>()
        fun walk(entity: Entity) {
            val body: Body = entity.body ?: return
            if (body is Multipart) return body.bodyParts.forEach { walk(it) }
            val isAttachment = entity.filename != null || entity.dispositionType.equals("attachment", true)
            when {
                !isAttachment && body is TextBody && entity.mimeType.equals("text/html", true) && html == null ->
                    html = body.reader.use { it.readText() }
                !isAttachment && body is TextBody && entity.mimeType.equals("text/plain", true) && text == null ->
                    text = body.reader.use { it.readText() }
                body is org.apache.james.mime4j.dom.Message -> walk(body)
                isAttachment && body is SingleBody -> attachments += MailAttachment(
                    entity.filename?.let { Headers.decode(it) } ?: "attachment",
                    entity.mimeType.lowercase(),
                    body.inputStream.use { it.readBytes() }
                )
            }
        }
        try {
            walk(message)
        } finally {
            message.dispose()
        }
        return MailMessage(header, html, text, attachments)
    }
}
