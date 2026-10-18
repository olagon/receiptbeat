package org.kealohalabs.receiptbeat.ui.money

import android.content.ComponentName
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
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
import org.kealohalabs.receiptbeat.core.model.Payer
import org.kealohalabs.receiptbeat.core.model.Receipt
import org.kealohalabs.receiptbeat.core.model.Status
import org.kealohalabs.receiptbeat.core.model.Track
import org.kealohalabs.receiptbeat.export.Exporter
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class MoneyScreenTest {
    private val compose = createComposeRule()

    // The test activity is only in the debug manifest. Register it so the release unit tests run too.
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            shadowOf(context.packageManager).addActivityIfNotPresent(ComponentName(context, ComponentActivity::class.java))
        }
    }).around(compose)
    private lateinit var db: ReceiptDatabase
    private lateinit var vm: MoneyViewModel

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ReceiptDatabase::class.java).allowMainThreadQueries().build()
        val store = FileStore(context)
        val catalog = CatalogRepository(db)
        val receipts = ReceiptRepository(db, store, catalog)
        val settings = SettingsRepository(context)
        val reports = ReportRepository(db, receipts, catalog, settings)
        vm = MoneyViewModel(reports, Exporter(context, reports, receipts, catalog, settings, store), settings)
        runBlocking {
            val payer = Payer(name = "Board travel")
            db.catalog().upsertPayer(payer)
            db.receipts().upsert(
                Receipt(
                    merchantName = "Kona Coffee",
                    track = Track.REIMBURSABLE,
                    status = Status.READY,
                    payerId = payer.id,
                    totalCents = 1250
                )
            )
        }
    }

    @After fun close() = db.close()

    @Test fun showsPayerAndOpensReportSheetThenTaxes() {
        compose.setContent { ReceiptBeatTheme { MoneyScreen(openReceipt = {}, shareFiles = { _, _ -> }, startOnTaxes = false, vm = vm) } }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Create report (1)")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Board travel").assertExists()

        compose.onNodeWithText("Create report (1)").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Create and share")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Kona Coffee").assertExists()
    }

    @Test fun taxesShowNoAdviceNote() {
        compose.setContent { ReceiptBeatTheme { MoneyScreen(openReceipt = {}, shareFiles = { _, _ -> }, startOnTaxes = true, vm = vm) } }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Deductible in", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("This is not tax advice.", substring = true))
        compose.onNodeWithText("Export for my accountant").assertExists()
    }
}
