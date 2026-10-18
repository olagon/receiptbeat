package org.kealohalabs.receiptbeat.core.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import org.kealohalabs.receiptbeat.core.database.ReceiptDatabase
import org.kealohalabs.receiptbeat.core.model.Receipt

@Singleton
class ReceiptRepository @Inject constructor(private val db: ReceiptDatabase) {
    val receipts: Flow<List<Receipt>> = db.receipts().observeAll()
}
