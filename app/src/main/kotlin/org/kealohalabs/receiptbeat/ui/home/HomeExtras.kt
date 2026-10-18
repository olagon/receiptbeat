package org.kealohalabs.receiptbeat.ui.home

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.kealohalabs.receiptbeat.core.data.MailRepository
import org.kealohalabs.receiptbeat.core.data.ReportRepository
import org.kealohalabs.receiptbeat.email.MailScanWorker

data class Extras(val reviewCount: Int, val overdue: Int, val scanning: Boolean)

/** Review count, overdue reports and whether an email scan is running, for the Home attention card. */
@Singleton
class HomeExtras @Inject constructor(@ApplicationContext context: Context, mail: MailRepository, reports: ReportRepository) {
    val counts: Flow<Extras> = combine(
        mail.reviewCount,
        reports.overdue,
        WorkManager.getInstance(context).getWorkInfosByTagFlow(MailScanWorker.TAG),
    ) { r, o, w -> Extras(r, o.size, w.any { it.state == WorkInfo.State.RUNNING }) }
}
