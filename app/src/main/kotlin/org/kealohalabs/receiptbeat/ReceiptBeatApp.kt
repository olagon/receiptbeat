package org.kealohalabs.receiptbeat

import android.app.Application
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.kealohalabs.receiptbeat.capture.Notifications
import org.kealohalabs.receiptbeat.capture.ReceiptPipeline
import org.kealohalabs.receiptbeat.capture.ReminderWorker
import org.kealohalabs.receiptbeat.core.data.CatalogRepository
import org.kealohalabs.receiptbeat.core.data.ReceiptRepository
import org.kealohalabs.receiptbeat.widget.ReceiptWidget

@OptIn(kotlinx.coroutines.FlowPreview::class)
@HiltAndroidApp
class ReceiptBeatApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var catalog: CatalogRepository

    @Inject lateinit var receipts: ReceiptRepository

    @Inject lateinit var pipeline: ReceiptPipeline

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        ReminderWorker.schedule(this)
        scope.launch {
            // Plain copies made for sharing never outlive a session.
            File(cacheDir, "share").deleteRecursively()
            catalog.seedIfEmpty()
            receipts.purgeTrash()
            receipts.recoverStuck().forEach(pipeline::enqueue)
        }
        // Keep the home screen widget in step with the money numbers.
        scope.launch {
            receipts.dashboard().map { it.owedCents to it.inboxCount }.distinctUntilChanged().debounce(2_000).collect {
                ReceiptWidget().updateAll(this@ReceiptBeatApp)
            }
        }
    }
}
