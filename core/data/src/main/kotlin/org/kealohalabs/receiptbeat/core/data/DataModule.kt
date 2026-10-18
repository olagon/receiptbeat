package org.kealohalabs.receiptbeat.core.data

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.kealohalabs.receiptbeat.core.database.DatabaseKey
import org.kealohalabs.receiptbeat.core.database.ReceiptDatabase

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): ReceiptDatabase = ReceiptDatabase.open(context, DatabaseKey.get(context))
}
