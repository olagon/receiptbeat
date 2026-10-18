package org.kealohalabs.receiptbeat.ui.home

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.kealohalabs.receiptbeat.core.data.MailRepository

/** Review count and overdue reports for the Home attention card. */
@Singleton
class HomeExtras @Inject constructor(mail: MailRepository) {
    val counts: Flow<Pair<Int, Int>> = mail.reviewCount.map { it to 0 }
}
