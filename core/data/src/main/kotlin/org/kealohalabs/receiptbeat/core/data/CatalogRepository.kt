package org.kealohalabs.receiptbeat.core.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import org.kealohalabs.receiptbeat.core.database.ReceiptDatabase
import org.kealohalabs.receiptbeat.core.model.Category
import org.kealohalabs.receiptbeat.core.model.MerchantMemory
import org.kealohalabs.receiptbeat.core.model.Payer
import org.kealohalabs.receiptbeat.core.model.Project
import org.kealohalabs.receiptbeat.core.model.SenderRule
import org.kealohalabs.receiptbeat.core.model.TaxLine
import org.kealohalabs.receiptbeat.core.model.Track
import org.kealohalabs.receiptbeat.extraction.MerchantNormalizer

/** Category ids match the keys CategoryGuess returns, so a guess maps straight to a category. */
object Presets {
    const val US = "us_sole_prop"

    val taxLines = listOf(
        TaxLine("us_c_meals", US, "Sch C line 24b", "Schedule C, meals", 50),
        TaxLine("us_c_travel", US, "Sch C line 24a", "Schedule C, travel"),
        TaxLine("us_c_car", US, "Sch C line 9", "Schedule C, car and truck"),
        TaxLine("us_c_supplies", US, "Sch C line 22", "Schedule C, supplies"),
        TaxLine("us_c_other", US, "Sch C line 27a", "Schedule C, other expenses"),
        TaxLine("us_c_utilities", US, "Sch C line 25", "Schedule C, utilities"),
        TaxLine("us_c_depreciation", US, "Sch C line 13", "Schedule C, depreciation"),
        TaxLine("us_c_legal", US, "Sch C line 17", "Schedule C, legal and professional"),
        TaxLine("us_8829", US, "Form 8829", "Form 8829, home office"),
        TaxLine("us_a_medical", US, "Sch A line 1", "Schedule A, medical"),
        TaxLine("us_a_charity", US, "Sch A line 11", "Schedule A, gifts to charity"),
        TaxLine("us_c_gifts", US, "Sch C line 27a", "Schedule C, gifts (limit $25 each)")
    )

    val categories = listOf(
        Category("meals", "Meals", "restaurant", 0xFFE8875B, "us_c_meals", 0),
        Category("travel", "Travel and airfare", "flight", 0xFF4F8FD8, "us_c_travel", 1),
        Category("lodging", "Lodging", "hotel", 0xFF7A6AD8, "us_c_travel", 2),
        Category("car", "Car, fuel, parking, tolls", "car", 0xFF5D9C6B, "us_c_car", 3),
        Category("office", "Office supplies", "pencil", 0xFFB0884A, "us_c_supplies", 4),
        Category("software", "Software and subscriptions", "cloud", 0xFF3BA3A8, "us_c_other", 5),
        Category("phone", "Phone and internet", "phone", 0xFF6A8CA8, "us_c_utilities", 6),
        Category("equipment", "Equipment", "laptop", 0xFF8A7F73, "us_c_depreciation", 7),
        Category("professional", "Professional services", "briefcase", 0xFF5B6E9C, "us_c_legal", 8),
        Category("education", "Education and training", "school", 0xFFC0794E, "us_c_other", 9),
        Category("home_office", "Home office", "home", 0xFF9C7A5B, "us_8829", 10),
        Category("medical", "Medical", "medical", 0xFFD85B7A, "us_a_medical", 11),
        Category("charity", "Charitable gift", "heart", 0xFFD8A23B, "us_a_charity", 12),
        Category("gifts", "Gifts to clients", "gift", 0xFFB85BD8, "us_c_gifts", 13),
        Category("other", "Other", "tag", 0xFF8A9396, null, 14)
    )
}

@Singleton
class CatalogRepository @Inject constructor(private val db: ReceiptDatabase) {
    private val dao = db.catalog()

    val categories: Flow<List<Category>> = dao.observeCategories()
    val taxLines: Flow<List<TaxLine>> = dao.observeTaxLines()
    val payers: Flow<List<Payer>> = dao.observePayers()
    val projects: Flow<List<Project>> = dao.observeProjects()
    val senderRules: Flow<List<SenderRule>> = dao.observeSenderRules()

    /** First launch: load the US preset. Safe to call every start. */
    suspend fun seedIfEmpty() {
        if (dao.categories().isEmpty()) {
            dao.upsertTaxLines(Presets.taxLines)
            dao.upsertCategories(Presets.categories)
        }
    }

    suspend fun saveCategory(c: Category) = dao.upsertCategories(listOf(c))

    suspend fun reorderCategories(ordered: List<Category>) = dao.upsertCategories(ordered.mapIndexed { i, c -> c.copy(sortOrder = i) })

    /** Merge [from] into [into]: every receipt moves over, then [from] is removed. */
    suspend fun mergeCategory(from: Category, into: Category) {
        dao.moveCategory(from.id, into.id)
        dao.deleteCategory(from)
    }

    suspend fun saveTaxLine(t: TaxLine) = dao.upsertTaxLines(listOf(t))

    suspend fun savePayer(p: Payer) = dao.upsertPayer(p)

    suspend fun deletePayer(p: Payer) = dao.deletePayer(p)

    suspend fun saveProject(p: Project) = dao.upsertProject(p)

    suspend fun deleteProject(p: Project) = dao.deleteProject(p)

    suspend fun saveSenderRule(r: SenderRule) = dao.upsertSenderRule(r)

    suspend fun deleteSenderRule(r: SenderRule) = dao.deleteSenderRule(r)

    suspend fun senderRules() = dao.senderRules()

    suspend fun payers() = dao.payers()

    suspend fun categories() = dao.categories()

    suspend fun taxLines() = dao.taxLines()

    suspend fun projects() = dao.projects()

    suspend fun memoryFor(merchant: String?): MerchantMemory? =
        merchant?.let { MerchantNormalizer.key(it) }?.takeIf { it.isNotEmpty() }?.let { dao.merchantMemory(it) }

    /** Remember the user's choice for this merchant. Powers the "same merchant as before" suggestion. */
    suspend fun learn(merchant: String?, categoryId: String?, payerId: String?, track: Track?) {
        val key = merchant?.let { MerchantNormalizer.key(it) }?.takeIf { it.isNotEmpty() } ?: return
        val old = dao.merchantMemory(key)
        dao.upsertMerchantMemory(
            MerchantMemory(
                key,
                categoryId ?: old?.lastCategoryId,
                payerId ?: old?.lastPayerId,
                track ?: old?.lastTrack,
                (old?.timesUsed ?: 0) + 1
            )
        )
    }
}
