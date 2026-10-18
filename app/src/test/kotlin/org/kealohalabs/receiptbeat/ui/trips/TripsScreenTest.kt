package org.kealohalabs.receiptbeat.ui.trips

import android.content.ComponentName
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.kealohalabs.receiptbeat.core.data.CatalogRepository
import org.kealohalabs.receiptbeat.core.data.FileStore
import org.kealohalabs.receiptbeat.core.data.ReceiptRepository
import org.kealohalabs.receiptbeat.core.data.ReportRepository
import org.kealohalabs.receiptbeat.core.data.SettingsRepository
import org.kealohalabs.receiptbeat.core.database.ReceiptDatabase
import org.kealohalabs.receiptbeat.core.designsystem.ReceiptBeatTheme
import org.kealohalabs.receiptbeat.core.model.Project
import org.kealohalabs.receiptbeat.core.model.Receipt
import org.kealohalabs.receiptbeat.export.Exporter
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class TripsScreenTest {
    private val compose = createComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            shadowOf(context.packageManager).addActivityIfNotPresent(ComponentName(context, ComponentActivity::class.java))
        }
    }).around(compose)
    private lateinit var db: ReceiptDatabase
    private lateinit var vm: TripsViewModel

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ReceiptDatabase::class.java).allowMainThreadQueries().build()
        val store = FileStore(context)
        val catalog = CatalogRepository(db)
        val receipts = ReceiptRepository(db, store, catalog)
        val settings = SettingsRepository(context)
        val reports = ReportRepository(db, receipts, catalog, settings)
        vm = TripsViewModel(receipts, catalog, settings, Exporter(context, reports, receipts, catalog, settings, store))
        runBlocking {
            db.catalog().upsertProject(
                Project(id = "k", name = "Kauai site visit", startDate = LocalDate.of(2026, 10, 5), endDate = LocalDate.of(2026, 10, 9))
            )
            db.receipts().upsert(
                Receipt(merchantName = "Hanalei Inn", projectId = "k", totalCents = 21000, purchaseDate = LocalDate.of(2026, 10, 5))
            )
            db.receipts().upsert(Receipt(merchantName = "Poke shop", totalCents = 1800, purchaseDate = LocalDate.of(2026, 10, 7)))
        }
    }

    @After fun close() = db.close()

    @Test fun addsReceiptsFromTheTripDates() {
        compose.setContent { ReceiptBeatTheme { TripsScreen(back = {}, openList = {}, shareFiles = { _, _ -> }, vm = vm) } }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Add 1 receipt from these dates")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("1 receipt").assertExists()

        compose.onNodeWithText("Add 1 receipt from these dates").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("2 receipts")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("k", runBlocking { db.receipts().all() }.first { it.merchantName == "Poke shop" }.projectId)
    }

    @Test fun opensTheTripsReceipts() {
        var opened: String? = null
        compose.setContent { ReceiptBeatTheme { TripsScreen(back = {}, openList = { opened = it }, shareFiles = { _, _ -> }, vm = vm) } }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Kauai site visit")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Kauai site visit").performClick()
        assertEquals("project:k", opened)
    }
}
