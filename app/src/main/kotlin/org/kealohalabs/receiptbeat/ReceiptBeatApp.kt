package org.kealohalabs.receiptbeat

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.kealohalabs.receiptbeat.capture.Notifications
import org.kealohalabs.receiptbeat.capture.ReceiptPipeline
import org.kealohalabs.receiptbeat.core.data.CatalogRepository
import org.kealohalabs.receiptbeat.core.data.ReceiptRepository

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
        scope.launch {
            // Plain copies made for sharing never outlive a session.
            File(cacheDir, "share").deleteRecursively()
            catalog.seedIfEmpty()
            receipts.purgeTrash()
            receipts.recoverStuck().forEach(pipeline::enqueue)
        }
    }
}
