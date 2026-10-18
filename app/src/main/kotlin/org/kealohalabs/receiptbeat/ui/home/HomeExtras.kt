package org.kealohalabs.receiptbeat.ui.home

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.kealohalabs.receiptbeat.core.data.MailRepository
import org.kealohalabs.receiptbeat.core.data.ReportRepository

/** Review count and overdue reports for the Home attention card. */
@Singleton
class HomeExtras @Inject constructor(mail: MailRepository, reports: ReportRepository) {
    val counts: Flow<Pair<Int, Int>> = combine(mail.reviewCount, reports.overdue) { r, o -> r to o.size }
}
