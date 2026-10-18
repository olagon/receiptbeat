package org.kealohalabs.receiptbeat.core.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.kealohalabs.receiptbeat.core.model.AutoScanSettings
import org.kealohalabs.receiptbeat.core.model.Category
import org.kealohalabs.receiptbeat.core.model.Correction
import org.kealohalabs.receiptbeat.core.model.EmailCandidate
import org.kealohalabs.receiptbeat.core.model.EmailSource
import org.kealohalabs.receiptbeat.core.model.FieldConfidence
import org.kealohalabs.receiptbeat.core.model.ImportJob
import org.kealohalabs.receiptbeat.core.model.LineItem
import org.kealohalabs.receiptbeat.core.model.MailAccount
import org.kealohalabs.receiptbeat.core.model.MerchantMemory
import org.kealohalabs.receiptbeat.core.model.Payer
import org.kealohalabs.receiptbeat.core.model.Project
import org.kealohalabs.receiptbeat.core.model.Receipt
import org.kealohalabs.receiptbeat.core.model.ReceiptEvent
import org.kealohalabs.receiptbeat.core.model.ReceiptFile
import org.kealohalabs.receiptbeat.core.model.ReceiptFts
import org.kealohalabs.receiptbeat.core.model.Report
import org.kealohalabs.receiptbeat.core.model.ReportItem
import org.kealohalabs.receiptbeat.core.model.ScanRange
import org.kealohalabs.receiptbeat.core.model.ScanRun
import org.kealohalabs.receiptbeat.core.model.SenderRule
import org.kealohalabs.receiptbeat.core.model.Split
import org.kealohalabs.receiptbeat.core.model.TaxLine

@Database(
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
    exportSchema = true,
    entities = [
        Receipt::class, ReceiptFts::class, ReceiptFile::class, FieldConfidence::class, LineItem::class, Split::class,
        Category::class, TaxLine::class, Payer::class, Project::class, Report::class, ReportItem::class,
        MailAccount::class, ImportJob::class, EmailSource::class, EmailCandidate::class, SenderRule::class,
        ScanRange::class, ScanRun::class, AutoScanSettings::class, MerchantMemory::class, Correction::class,
        ReceiptEvent::class
    ]
)
@TypeConverters(Converters::class)
abstract class ReceiptDatabase : RoomDatabase() {
    abstract fun receipts(): ReceiptDao
    abstract fun catalog(): CatalogDao
    abstract fun reports(): ReportDao
    abstract fun mail(): MailDao

    companion object {
        /** Opens the encrypted database. [key] comes from the Android Keystore (see DatabaseKey). */
        fun open(context: Context, key: ByteArray, name: String = "receiptbeat.db"): ReceiptDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, ReceiptDatabase::class.java, name)
                .openHelperFactory(SupportOpenHelperFactory(key))
                .build()
        }
    }
}

class Converters {
    @TypeConverter fun instant(v: Long?): Instant? = v?.let(Instant::ofEpochMilli)

    @TypeConverter fun instant(v: Instant?): Long? = v?.toEpochMilli()

    @TypeConverter fun date(v: Long?): LocalDate? = v?.let(LocalDate::ofEpochDay)

    @TypeConverter fun date(v: LocalDate?): Long? = v?.toEpochDay()

    @TypeConverter fun time(v: Int?): LocalTime? = v?.let { LocalTime.ofSecondOfDay(it.toLong()) }

    @TypeConverter fun time(v: LocalTime?): Int? = v?.toSecondOfDay()
}
