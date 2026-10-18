package org.kealohalabs.receiptbeat.mail

import jakarta.mail.FetchProfile
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.Store
import jakarta.mail.UIDFolder
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import jakarta.mail.search.AndTerm
import jakarta.mail.search.BodyTerm
import jakarta.mail.search.ComparisonTerm
import jakarta.mail.search.OrTerm
import jakarta.mail.search.ReceivedDateTerm
import jakarta.mail.search.SearchTerm
import jakarta.mail.search.SubjectTerm
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Properties
import org.eclipse.angus.mail.iap.Argument
import org.eclipse.angus.mail.imap.IMAPFolder
import org.eclipse.angus.mail.imap.protocol.IMAPResponse

/** `secure = false` means plain IMAP, only for local test servers. */
data class ImapConfig(
    val host: String,
    val port: Int = 993,
    val user: String,
    val secret: String,
    val xoauth2: Boolean = false,
    val secure: Boolean = true
)

/** Read-only IMAP. Folders are only ever opened READ_ONLY and nothing is sent, flagged, moved or deleted. */
class ImapClient(private val config: ImapConfig) : Closeable {
    private var store: Store? = null
    internal var current: IMAPFolder? = null
        private set
    private val gmail = config.host.equals("imap.gmail.com", ignoreCase = true)

    fun connect() {
        val proto = if (config.secure) "imaps" else "imap"
        val props = Properties().apply {
            put("mail.store.protocol", proto)
            put("mail.$proto.peek", "true")
            put("mail.$proto.connectiontimeout", "30000")
            put("mail.$proto.timeout", "120000")
            if (config.secure) put("mail.$proto.ssl.checkserveridentity", "true")
            if (config.xoauth2) put("mail.$proto.auth.mechanisms", "XOAUTH2")
        }
        store = Session.getInstance(props).getStore(proto).apply { connect(config.host, config.port, config.user, config.secret) }
    }

    fun folders(): List<String> = store().defaultFolder.list("*").filter { it.type and Folder.HOLDS_MESSAGES != 0 }.map { it.fullName }

    /** Date-bounded search (inclusive dates) with server-side narrowing. Returns UIDs ascending. */
    fun search(folder: String, from: LocalDate, to: LocalDate): List<Long> {
        val f = open(folder)
        if (gmail) return gmailRaw(f, gmailQuery(from, to))
        val words = SEARCH_WORDS.flatMap { listOf<SearchTerm>(SubjectTerm(it), BodyTerm(it)) }
        val term = AndTerm(
            arrayOf(
                ReceivedDateTerm(ComparisonTerm.GE, date(from)),
                ReceivedDateTerm(ComparisonTerm.LT, date(to.plusDays(1))),
                OrTerm(words.toTypedArray())
            )
        )
        return uids(f, f.search(term))
    }

    fun uidsAfter(folder: String, lastUid: Long): List<Long> {
        val f = open(folder)
        return uids(f, f.getMessagesByUID(lastUid + 1, UIDFolder.MAXUID)).filter { it > lastUid }
    }

    /** Headers only, in batches. */
    fun headers(folder: String, uids: List<Long>): List<Pair<Long, MailHeader>> {
        val f = open(folder)
        val profile = FetchProfile().apply {
            add(FetchProfile.Item.ENVELOPE)
            add(UIDFolder.FetchProfileItem.UID)
        }
        return uids.chunked(BATCH).flatMap { chunk ->
            val msgs = f.getMessagesByUID(chunk.toLongArray()).filterNotNull().toTypedArray()
            f.fetch(msgs, profile)
            msgs.map { m ->
                val from = m.from?.firstOrNull() as? InternetAddress
                f.getUID(m) to Headers.header(
                    (m as? MimeMessage)?.messageID,
                    from?.address,
                    from?.personal,
                    m.subject.orEmpty(),
                    (m.sentDate ?: m.receivedDate)?.toInstant()
                )
            }
        }
    }

    /** Downloads the whole message (BODY.PEEK, folder is read-only so nothing is marked seen). */
    fun fetch(folder: String, uid: Long): RawMail {
        val msg = open(folder).getMessageByUID(uid) ?: throw NoSuchElementException("No message with UID $uid in $folder")
        val out = ByteArrayOutputStream()
        msg.writeTo(out)
        return BytesMail(out.toByteArray())
    }

    override fun close() {
        runCatching { current?.takeIf { it.isOpen }?.close(false) }
        runCatching { store?.close() }
        current = null
        store = null
    }

    private fun store(): Store = checkNotNull(store) { "Call connect() first" }

    private fun open(name: String): IMAPFolder {
        current?.let {
            if (it.fullName == name && it.isOpen) return it
            if (it.isOpen) it.close(false)
        }
        return (store().getFolder(name) as IMAPFolder).also {
            it.open(Folder.READ_ONLY)
            current = it
        }
    }

    private fun uids(f: IMAPFolder, msgs: Array<Message>): List<Long> {
        f.fetch(msgs, FetchProfile().apply { add(UIDFolder.FetchProfileItem.UID) })
        return msgs.map { f.getUID(it) }.sorted()
    }

    private fun gmailRaw(f: IMAPFolder, query: String): List<Long> {
        @Suppress("UNCHECKED_CAST")
        return f.doCommand { p ->
            val responses = p.command("UID SEARCH", Argument().writeAtom("X-GM-RAW").writeString(query))
            val out = mutableListOf<Long>()
            responses.filterIsInstance<IMAPResponse>().filter { it.keyEquals("SEARCH") }.forEach { r ->
                while (true) {
                    val n = r.readLong()
                    if (n < 0) break
                    out += n
                }
            }
            p.notifyResponseHandlers(responses)
            p.handleResult(responses.last())
            out
        } as List<Long>
    }

    private fun date(d: LocalDate): Date = Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant())

    companion object {
        private const val BATCH = 200
        private val SEARCH_WORDS = listOf("receipt", "invoice", "order", "total", "paid", "payment")
        private val GMAIL_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd")

        internal fun gmailQuery(from: LocalDate, to: LocalDate): String =
            "after:${from.format(GMAIL_DATE)} before:${to.plusDays(1).format(GMAIL_DATE)} " +
                "(receipt OR invoice OR order OR \"your trip\" OR payment OR total OR paid OR category:purchases)"

        fun hostFor(email: String): String? {
            val domain = email.substringAfterLast('@').trim().lowercase()
            return when (domain) {
                "gmail.com", "googlemail.com" -> "imap.gmail.com"
                "yahoo.com", "ymail.com" -> "imap.mail.yahoo.com"
                "aol.com" -> "imap.aol.com"
                "icloud.com", "me.com", "mac.com" -> "imap.mail.me.com"
                "outlook.com", "hotmail.com", "live.com", "msn.com" -> "outlook.office365.com"
                "fastmail.com" -> "imap.fastmail.com"
                "proton.me", "protonmail.com" -> null
                else -> "imap.$domain"
            }
        }
    }
}
