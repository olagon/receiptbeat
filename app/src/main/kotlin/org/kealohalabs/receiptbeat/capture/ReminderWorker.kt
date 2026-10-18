package org.kealohalabs.receiptbeat.capture

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import org.kealohalabs.receiptbeat.core.data.CatalogRepository
import org.kealohalabs.receiptbeat.core.data.ReceiptRepository
import org.kealohalabs.receiptbeat.core.data.SettingsRepository
import org.kealohalabs.receiptbeat.core.model.Money
import org.kealohalabs.receiptbeat.core.model.Receipt
import org.kealohalabs.receiptbeat.core.model.Status
import org.kealohalabs.receiptbeat.core.model.Track

/** The follow through: once a day, nudge about receipts that are about to cost the user money. */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val receipts: ReceiptRepository,
    private val catalog: CatalogRepository,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val s = settings.current()
        if (!s.remindersOn) return Result.success()
        val today = LocalDate.now()
        nudges(
            receipts.all(),
            catalog.payers().associate {
                it.id to it
            },
            today,
            s.remindInboxDays,
            s.remindSubmitBeforeDays,
            s.remindUnpaidDays
        )
            .forEachIndexed { i, n -> Notifications.nudge(applicationContext, NUDGE_ID + i, n.title, n.text, n.route) }
        if (s.remindTaxJanuary && today.monthValue == 1 && today.dayOfMonth == JANUARY_DAY) {
            Notifications.nudge(applicationContext, NUDGE_ID + 50, "Tax time", "Your ${today.year - 1} tax export is ready", "taxes")
        }
        return Result.success()
    }

    data class Nudge(val title: String, val text: String, val route: String)

    companion object {
        private const val NUDGE_ID = 2000
        private const val JANUARY_DAY = 5
        private val DayFmt = DateTimeFormatter.ofPattern("EEEE, MMM d")

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.ofHours(1))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("reminders", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        /** Pure, so it can be tested. */
        fun nudges(
            all: List<Receipt>,
            payers: Map<String, org.kealohalabs.receiptbeat.core.model.Payer>,
            today: LocalDate,
            inboxDays: Int,
            beforeDays: Int,
            unpaidDays: Int,
        ): List<Nudge> = buildList {
            val now = Instant.now()
            val stale = all.count { it.status == Status.INBOX && !it.reading && Duration.between(it.createdAt, now).toDays() >= inboxDays }
            if (stale >
                0
            ) {
                add(
                    Nudge(
                        "Receipt Beat",
                        if (stale ==
                            1
                        ) {
                            "1 receipt needs a quick look"
                        } else {
                            "$stale receipts need a quick look"
                        },
                        "inbox"
                    )
                )
            }

            all.filter { it.track == Track.REIMBURSABLE && (it.status == Status.READY || it.status == Status.INBOX) }
                .mapNotNull { r ->
                    val p = r.payerId?.let(payers::get) ?: return@mapNotNull null
                    val due = (r.purchaseDate ?: return@mapNotNull null).plusDays((p.submitDeadlineDays ?: return@mapNotNull null).toLong())
                    if (!today.isBefore(due.minusDays(beforeDays.toLong())) && !today.isAfter(due)) p to due else null
                }
                .groupBy({ it.first }, { it.second })
                .forEach { (p, dues) ->
                    add(Nudge("Deadline coming up", "Submit your ${p.name} receipts by ${dues.min().format(DayFmt)}", "ready"))
                }

            all.filter { it.status == Status.SUBMITTED || it.status == Status.PARTLY_REIMBURSED }
                .filter { Duration.between(it.updatedAt, now).toDays() >= unpaidDays }
                .groupBy { it.payerId }
                .forEach { (pid, list) ->
                    val owed = list.sumOf { ReceiptRepository.owed(it) }
                    val who = pid?.let(payers::get)?.name ?: "Someone"
                    add(Nudge("Still unpaid", "$who still owes you ${Money.format(owed, list.first().currency)}", "owed"))
                }
        }
    }
}
