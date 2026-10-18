package org.kealohalabs.receiptbeat.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.genai.common.FeatureStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kealohalabs.receiptbeat.capture.CloudReader
import org.kealohalabs.receiptbeat.capture.NanoReader
import org.kealohalabs.receiptbeat.core.data.AiProvider
import org.kealohalabs.receiptbeat.core.data.CatalogRepository
import org.kealohalabs.receiptbeat.core.data.ReceiptRepository
import org.kealohalabs.receiptbeat.core.data.SecretStore
import org.kealohalabs.receiptbeat.core.data.Settings
import org.kealohalabs.receiptbeat.core.data.SettingsRepository
import org.kealohalabs.receiptbeat.core.model.Category
import org.kealohalabs.receiptbeat.core.model.Payer
import org.kealohalabs.receiptbeat.core.model.Project
import org.kealohalabs.receiptbeat.core.model.Receipt
import org.kealohalabs.receiptbeat.core.model.TaxLine
import org.kealohalabs.receiptbeat.extraction.NanoPrompt
import org.kealohalabs.receiptbeat.extraction.Reconcile
import org.kealohalabs.receiptbeat.extraction.RulesParser

data class SettingsState(
    val settings: Settings = Settings(),
    val categories: List<Category> = emptyList(),
    val taxLines: List<TaxLine> = emptyList(),
    val payers: List<Payer> = emptyList(),
    val projects: List<Project> = emptyList(),
    val trash: List<Receipt> = emptyList(),
    val hasAiKey: Boolean = false,
    val nanoStatus: String = "Checking",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val catalog: CatalogRepository,
    private val receipts: ReceiptRepository,
    private val secrets: SecretStore,
    private val nano: NanoReader,
    private val cloud: CloudReader,
) : ViewModel() {
    private val extra = MutableStateFlow(false to "Checking")

    val state: StateFlow<SettingsState> = combine(
        settings.settings,
        combine(catalog.categories, catalog.taxLines) { c, t -> c to t },
        combine(catalog.payers, catalog.projects) { p, pr -> p to pr },
        receipts.trash,
        extra,
    ) { s, ct, pp, trash, x ->
        SettingsState(s, ct.first, ct.second, pp.first, pp.second, trash, x.first, x.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    init {
        viewModelScope.launch {
            val status = when (nano.status()) {
                FeatureStatus.AVAILABLE -> "Ready on this phone"
                FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> "Downloading to this phone"
                else -> "Not available on this phone. The rules reader still works."
            }
            extra.value = (secrets.get(SecretStore.AI_KEY) != null) to status
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun update(change: (Settings) -> Settings) = launch { settings.update(change) }

    fun saveCategory(c: Category) = launch { catalog.saveCategory(c) }

    fun moveCategory(c: Category, up: Boolean) = launch {
        val list = state.value.categories.toMutableList()
        val i = list.indexOfFirst { it.id == c.id }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in list.indices) return@launch
        list[i] = list[j].also { list[j] = list[i] }
        catalog.reorderCategories(list)
    }

    fun mergeCategory(from: Category, into: Category) = launch { catalog.mergeCategory(from, into) }

    fun saveTaxLine(t: TaxLine) = launch { catalog.saveTaxLine(t) }

    fun savePayer(p: Payer) = launch { catalog.savePayer(p) }

    fun deletePayer(p: Payer) = launch { catalog.deletePayer(p) }

    fun saveProject(p: Project) = launch { catalog.saveProject(p) }

    fun deleteProject(p: Project) = launch { catalog.deleteProject(p) }

    fun restore(r: Receipt) = launch { receipts.restore(listOf(r.id)) }

    fun addSampleData() = launch { SampleData.add(receipts, catalog) }

    fun setAiKey(provider: AiProvider, key: String?) = launch {
        if (key.isNullOrBlank()) secrets.remove(SecretStore.AI_KEY) else secrets.put(SecretStore.AI_KEY, key.trim())
        settings.update { it.copy(aiProvider = if (key.isNullOrBlank()) AiProvider.NONE else provider) }
        extra.value = (!key.isNullOrBlank()) to extra.value.second
    }

    /** Extraction test screen: paste receipt text and see what each reader finds. */
    suspend fun testExtraction(text: String): String = withContext(Dispatchers.Default) {
        val rules = RulesParser.parse(text)
        val ai = nano.extract(text) ?: cloud.extract(text)
        val merged = Reconcile.merge(ai, rules)
        buildString {
            appendLine("Read by: " + if (ai != null) "AI plus rules" else "rules only")
            appendLine("Merchant: ${merged.merchant.value} (${merged.merchant.confidence})")
            appendLine("Date: ${merged.date.value} (${merged.date.confidence})")
            appendLine(
                "Total: ${merged.total.value?.let {
                    org.kealohalabs.receiptbeat.core.model.Money.format(
                        it,
                        merged.currency.value ?: "USD"
                    )
                }} (${merged.total.confidence})"
            )
            appendLine("Tax: ${merged.tax.value} (${merged.tax.confidence})")
            appendLine("Currency: ${merged.currency.value}")
            appendLine("Category guess: ${merged.categoryGuess}")
            if (ai == null) appendLine("\nPrompt sent to the AI when available:\n" + NanoPrompt.build(text).take(400))
        }
    }
}
