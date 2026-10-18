package org.kealohalabs.receiptbeat.ui.capture

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.kealohalabs.receiptbeat.capture.ReceiptPipeline
import org.kealohalabs.receiptbeat.core.data.CatalogRepository
import org.kealohalabs.receiptbeat.core.data.ReceiptRepository
import org.kealohalabs.receiptbeat.core.model.Category
import org.kealohalabs.receiptbeat.core.model.Payer
import org.kealohalabs.receiptbeat.core.model.Receipt
import org.kealohalabs.receiptbeat.core.model.Source
import org.kealohalabs.receiptbeat.core.model.Track

data class CaptureState(
    val session: List<Receipt> = emptyList(),
    val focus: Receipt? = null,
    val thumbs: Map<String, String> = emptyMap(),
    val categories: Map<String, Category> = emptyMap(),
    val payers: List<Payer> = emptyList()
)

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    private val pipeline: ReceiptPipeline,
    private val repo: ReceiptRepository,
    catalog: CatalogRepository
) : ViewModel() {
    private val ids = MutableStateFlow(saved.get<List<String>>("ids").orEmpty())
    private val focusId = MutableStateFlow<String?>(null)

    val state: StateFlow<CaptureState> = combine(
        repo.receipts,
        repo.thumbs,
        ids,
        focusId,
        combine(catalog.categories, catalog.payers) {
                c,
                p
            ->
            c to
                p
        }
    ) { all, t, i, f, cp ->
        val byId = all.associateBy { it.id }
        CaptureState(i.mapNotNull { byId[it] }.reversed(), f?.let { byId[it] }, t, cp.first.associateBy { it.id }, cp.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CaptureState())

    private fun add(new: List<String>) = ids.update { (it + new).also { l -> saved["ids"] = l } }

    val error = MutableStateFlow<String?>(null)

    fun scanned(pages: List<Uri>, mode: CaptureMode) {
        viewModelScope.launch {
            runCatching { save(pages, mode) }.onFailure { error.value = "That scan could not be saved. Please try again." }
        }
    }

    private suspend fun save(pages: List<Uri>, mode: CaptureMode) {
        when (mode) {
            CaptureMode.SINGLE -> pipeline.fromImages(pages, Source.CAMERA, stitch = false).also {
                add(listOf(it))
                focusId.value = it
            }
            CaptureMode.LONG -> add(listOf(pipeline.fromImages(pages, Source.CAMERA, stitch = true)))
            CaptureMode.BATCH -> add(pages.map { pipeline.fromImages(listOf(it), Source.CAMERA, stitch = false) })
        }
    }

    fun imported(uris: List<Uri>) {
        viewModelScope.launch {
            val ids = pipeline.fromUris(uris, Source.IMPORT)
            add(ids)
            if (ids.size < uris.size) error.value = "${uris.size - ids.size} files could not be read."
        }
    }

    fun clearFocus() {
        focusId.value = null
    }

    fun setTrack(id: String, t: Track) {
        viewModelScope.launch { repo.update(id) { it.copy(track = t) } }
    }

    fun setPayer(id: String, p: Payer) {
        viewModelScope.launch { repo.update(id) { it.copy(payerId = p.id, track = p.defaultTrack) } }
    }
}
