package org.kealohalabs.receiptbeat.ui.home

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Review count and overdue reports for the Home attention card. */
@Singleton
class HomeExtras @Inject constructor() {
    val counts: Flow<Pair<Int, Int>> = flowOf(0 to 0)
}
