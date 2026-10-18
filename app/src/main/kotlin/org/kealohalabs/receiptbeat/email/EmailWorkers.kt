package org.kealohalabs.receiptbeat.email

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.InputStream
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import org.kealohalabs.receiptbeat.capture.Notifications
import org.kealohalabs.receiptbeat.core.data.MailRepository
import org.kealohalabs.receiptbeat.core.model.ImportJob
import org.kealohalabs.receiptbeat.core.model.JobStatus
import org.kealohalabs.receiptbeat.core.model.MailAccount
import org.kealohalabs.receiptbeat.core.model.MailAuth
import org.kealohalabs.receiptbeat.core.model.MailFormat
import org.kealohalabs.receiptbeat.core.model.ScanFrequency
import org.kealohalabs.receiptbeat.core.model.ScanMode
import org.kealohalabs.receiptbeat.core.model.ScanRun
import org.kealohalabs.receiptbeat.core.model.ScanTrigger
import org.kealohalabs.receiptbeat.core.model.Source
import org.kealohalabs.receiptbeat.mail.ImapClient
import org.kealohalabs.receiptbeat.mail.ImapConfig
import org.kealohalabs.receiptbeat.mail.MailReaders
import org.kealohalabs.receiptbeat.mail.RawMail

/**
 * Imports a mailbox file on the phone. Runs as a foreground job so Android does not stop it,
 * reads the file as a stream, and saves its place so it picks up where it left off.
 */
@HiltWorker
class EmailImportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val mail: MailRepository,
    private val ingest: EmailIngest,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val job = mail.job(inputData.getString("job") ?: return Result.failure()) ?: return Result.failure()
        setForeground(Notifications.working(applicationContext, "Looking for receipts in ${job.fileName}"))
        val ctx = ScanContext(job.fromDate, job.toDate, mail.rules(), importJobId = job.id)
        val uri = Uri.parse(job.uri)
        var current = job.copy(status = JobStatus.RUNNING, error = null)
        mail.saveJob(current)
        return try {
            open(uri, current.format) { input, file ->
                val seq = when (current.format) {
                    MailFormat.MBOX -> MailReaders.mbox(input!!, current.resumeOffset) { current = current.copy(bytesRead = it) }
                    MailFormat.EML -> sequenceOf(MailReaders.eml(input!!))
                    MailFormat.EML_ZIP -> MailReaders.zip(input!!, current.resumeOffset) { current = current.copy(bytesRead = it) }
                    MailFormat.OLM -> MailReaders.olm(input!!, current.resumeOffset) { current = current.copy(bytesRead = it) }
                    MailFormat.PST -> MailReaders.pst(file!!, current.resumeOffset)
                }
                var seen = current.messagesSeen
                for (raw: RawMail in seq) {
                    if (isStopped) break
                    val outcome = ingest.process(raw, ctx, Source.EMAIL_FILE)
                    seen++
                    current = current.copy(
                        messagesSeen = seen,
                        resumeOffset = current.resumeOffset + 1,
                        candidates = current.candidates + if (outcome == Outcome.FOUND || outcome == Outcome.AUTO_KEPT) 1 else 0,
                    )
                    if (seen % SAVE_EVERY == 0) {
                        mail.saveJob(current)
                        val pct = if (current.bytesTotal > 0) (current.bytesRead * 100 / current.bytesTotal).toInt() else null
                        setForeground(
                            Notifications.working(applicationContext, "$seen emails checked, ${current.candidates} receipts found", pct)
                        )
                    }
                }
            }
            current =
                if (isStopped) {
                    current.copy(
                        status = JobStatus.PAUSED
                    )
                } else {
                    current.copy(status = JobStatus.DONE, finishedAt = Instant.now())
                }
            mail.saveJob(current)
            Result.success()
        } catch (e: Exception) {
            android.util.Log.w("ReceiptBeat", "Import failed", e)
            mail.saveJob(current.copy(status = JobStatus.FAILED, error = e.message?.take(200) ?: "Could not read this file"))
            Result.failure()
        }
    }

    /** PST needs random access, so it is copied to private cache first and deleted after. */
    private inline fun open(uri: Uri, format: MailFormat, block: (InputStream?, File?) -> Unit) {
        val resolver = applicationContext.contentResolver
        if (format == MailFormat.PST) {
            val tmp = File(applicationContext.cacheDir, "import.pst")
            try {
                if (!tmp.exists()) resolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { i.copyTo(it) } }
                block(null, tmp)
            } finally {
                tmp.delete()
            }
        } else {
            resolver.openInputStream(uri)!!.buffered(BUFFER).use { block(it, null) }
        }
    }

    override suspend fun getForegroundInfo() = Notifications.working(applicationContext, "Looking for receipts")

    companion object {
        private const val SAVE_EVERY = 25
        private const val BUFFER = 256 * 1024

        /** Start (or resume) an import of a file the user picked. */
        suspend fun start(context: Context, mail: MailRepository, uri: Uri, from: LocalDate, to: LocalDate): String? {
            val resolver = context.contentResolver
            runCatching { resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            var name = "mailbox"
            var size = 0L
            resolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
                }
            }
            val head = resolver.openInputStream(uri)?.use { it.readNBytes(HEAD) } ?: return null
            val format = MailReaders.detect(name, head) ?: return null
            val job = ImportJob(fileName = name, uri = uri.toString(), format = format, fromDate = from, toDate = to, bytesTotal = size)
            mail.saveJob(job)
            enqueue(context, job.id)
            return job.id
        }

        fun enqueue(context: Context, jobId: String) {
            val req = OneTimeWorkRequestBuilder<EmailImportWorker>().setInputData(workDataOf("job" to jobId)).build()
            WorkManager.getInstance(context).enqueueUniqueWork("import-$jobId", ExistingWorkPolicy.KEEP, req)
        }

        private const val HEAD = 4096
    }
}

/**
 * Scans a connected account over read-only IMAP. Remembers which dates were searched,
 * so the same stretch of mail is never searched twice, and only asks for new mail between scans.
 */
@HiltWorker
class MailScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val mail: MailRepository,
    private val ingest: EmailIngest,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val accountId = inputData.getString("account") ?: return Result.failure()
        val trigger = if (inputData.getBoolean("manual", false)) ScanTrigger.MANUAL else ScanTrigger.SCHEDULED
        val account = mail.account(accountId) ?: return Result.success()
        if (trigger == ScanTrigger.MANUAL) setForeground(Notifications.working(applicationContext, "Checking ${account.email}"))
        val auto = mail.autoScanFor(accountId)
        val from = inputData.getString("from")?.let(LocalDate::parse) ?: account.scanFrom
        // A scan that stopped partway left its place: "folder:uid". Skip what it already checked.
        val stopped = mail.lastRun(accountId)?.takeIf { it.finishedAt == null || it.error != null }
        if (stopped != null && stopped.finishedAt == null) {
            mail.saveRun(stopped.copy(finishedAt = Instant.now(), error = "Stopped partway. Picked up by the next scan."))
        }
        val resume = stopped?.resumeToken?.split(":")?.takeIf { it.size == 2 }?.let { it[0] to (it[1].toLongOrNull() ?: 0L) }
        var run = ScanRun(accountId = accountId, trigger = trigger, resumeToken = resume?.let { "${it.first}:${it.second}" })
        mail.saveRun(run)
        val ctx = ScanContext(
            from,
            LocalDate.now(),
            mail.rules(),
            accountId = accountId,
            autoKeepTrusted = auto.mode == ScanMode.AUTO_KEEP_TRUSTED,
        )
        val secret = mail.secret(accountId) ?: return fail(run, "Sign in again: the password is missing")
        return try {
            ImapClient(
                ImapConfig(account.imapHost, account.imapPort, account.email, secret, xoauth2 = account.auth == MailAuth.MICROSOFT)
            ).use { imap ->
                report(run, "", 0, "Connecting to ${account.email}")
                imap.connect()
                val lastUids = parseUids(account.lastUidByFolder).toMutableMap()
                for (folder in folders(imap)) {
                    report(run, folder, 0, "Searching $folder")
                    // First fill any date gaps not searched before, then ask only for mail newer than the last seen.
                    val uids = linkedSetOf<Long>()
                    val gaps = mail.gaps(accountId, folder, from, LocalDate.now())
                    gaps.forEach { (a, b) -> uids += imap.search(folder, a, b) }
                    lastUids[folder]?.let { uids += imap.uidsAfter(folder, it) }
                    if (resume?.first == folder) uids.removeAll { it <= resume.second }
                    val toCheck = uids.size
                    var done = 0
                    for (batch in uids.sorted().chunked(BATCH)) {
                        if (isStopped) break
                        report(run, folder, toCheck, "Reading $folder", done)
                        run = scanBatch(imap, folder, batch, ctx, run)
                        done += batch.size
                        lastUids[folder] = maxOf(lastUids[folder] ?: 0, batch.max())
                        mail.saveRun(run)
                    }
                    if (!isStopped) gaps.forEach { (a, b) -> mail.rangeDone(accountId, folder, a, b) }
                }
                mail.saveAccount(
                    account.copy(
                        lastUidByFolder = lastUids.entries.joinToString(";") {
                            "${it.key}=${it.value}"
                        },
                        lastScanAt = Instant.now()
                    ),
                    null
                )
            }
            mail.saveRun(run.copy(finishedAt = Instant.now()))
            if (trigger == ScanTrigger.SCHEDULED) weeklySummary(run.found)
            Result.success()
        } catch (e: Exception) {
            android.util.Log.w("ReceiptBeat", "Scan failed", e)
            fail(run, friendly(e))
        }
    }

    /** Live progress for the Review screen and the notification. */
    private suspend fun report(run: ScanRun, folder: String, total: Int, stage: String, done: Int = 0) {
        setProgress(
            workDataOf(
                "checked" to run.checked,
                "found" to run.found,
                "total" to total,
                "done" to done,
                "folder" to folder,
                "stage" to stage,
            ),
        )
        if (inputData.getBoolean("manual", false)) {
            val text = if (total > 0) "$done of $total emails checked, ${run.found} receipts found" else stage
            setForeground(Notifications.working(applicationContext, text, if (total > 0) done * 100 / total else null))
        }
    }

    private suspend fun scanBatch(imap: ImapClient, folder: String, batch: List<Long>, ctx: ScanContext, start: ScanRun): ScanRun {
        var run = start
        for ((uid, h) in imap.headers(folder, batch)) {
            run = run.copy(checked = run.checked + 1)
            if (!ingest.skim(h, ctx)) continue
            when (ingest.process(imap.fetch(folder, uid), ctx, Source.EMAIL_LIVE)) {
                Outcome.FOUND -> run = run.copy(found = run.found + 1)
                Outcome.AUTO_KEPT -> run = run.copy(found = run.found + 1, autoKept = run.autoKept + 1)
                else -> Unit
            }
        }
        return run.copy(resumeToken = "$folder:${batch.max()}")
    }

    private suspend fun fail(run: ScanRun, error: String): Result {
        mail.saveRun(run.copy(finishedAt = Instant.now(), error = error))
        return Result.success() // Logged in the scan log. Retrying a wrong password would only lock the account.
    }

    /** Inbox plus any folder that looks like it holds purchases. */
    private fun folders(imap: ImapClient): List<String> {
        val all = imap.folders()
        return (listOf("INBOX") + all.filter { f -> FolderWords.any { f.contains(it, ignoreCase = true) } }).distinct().filter {
            it in all ||
                it == "INBOX"
        }
    }

    /** At most one quiet summary a week. New finds also show as a badge on the Review tab. */
    private suspend fun weeklySummary(found: Int) {
        if (found == 0) return
        val prefs = applicationContext.getSharedPreferences("scan", Context.MODE_PRIVATE)
        val last = prefs.getLong("summary", 0)
        val pending = mail.reviewCount.first()
        if (Duration.between(Instant.ofEpochMilli(last), Instant.now()).toDays() >= WEEK && pending > 0) {
            Notifications.nudge(applicationContext, SUMMARY_ID, "Receipts found in email", "$pending are waiting in Review", "review")
            prefs.edit().putLong("summary", System.currentTimeMillis()).apply()
        }
    }

    override suspend fun getForegroundInfo() = Notifications.working(applicationContext, "Checking email")

    companion object {
        const val TAG = "mailscan"
        private const val BATCH = 50
        private const val WEEK = 7
        private const val SUMMARY_ID = 3001
        private val FolderWords = listOf("receipt", "purchase", "order", "invoice")

        fun parseUids(s: String?): Map<String, Long> = s?.split(";")?.mapNotNull { e ->
            e.split("=").takeIf { it.size == 2 }?.let { it[0] to (it[1].toLongOrNull() ?: 0) }
        }?.toMap().orEmpty()

        fun friendly(e: Exception): String = when {
            e.message?.contains("auth", true) == true || e.message?.contains("credential", true) == true ->
                "Sign in failed. Check the app password."
            e is java.net.UnknownHostException -> "No connection"
            else -> e.message?.take(160) ?: "Scan stopped"
        }

        fun scanNow(context: Context, accountId: String, from: LocalDate? = null) {
            val req = OneTimeWorkRequestBuilder<MailScanWorker>()
                .addTag(TAG)
                .addTag(accountId)
                .setInputData(workDataOf("account" to accountId, "manual" to true, "from" to from?.toString()))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("scan-now-$accountId", ExistingWorkPolicy.KEEP, req)
        }

        /** Android runs it close to the chosen hour, not to the minute. */
        fun schedule(context: Context, account: MailAccount, s: org.kealohalabs.receiptbeat.core.model.AutoScanSettings) {
            val wm = WorkManager.getInstance(context)
            val name = "scan-${account.id}"
            if (s.frequency == ScanFrequency.OFF) {
                wm.cancelUniqueWork(name)
                return
            }
            val hours = when (s.frequency) {
                ScanFrequency.EVERY_12H -> 12L
                ScanFrequency.WEEKLY -> 24L * 7
                else -> 24L
            }
            val now = LocalDateTime.now()
            var next = now.withHour(s.preferredHour).withMinute(0)
            if (next.isBefore(now)) next = next.plusDays(1)
            val req = PeriodicWorkRequestBuilder<MailScanWorker>(hours, TimeUnit.HOURS)
                .addTag(TAG)
                .addTag(account.id)
                .setInitialDelay(Duration.between(now, next))
                .setInputData(workDataOf("account" to account.id))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (s.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .setRequiresCharging(s.chargingOnly)
                        .build(),
                )
                .build()
            wm.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
