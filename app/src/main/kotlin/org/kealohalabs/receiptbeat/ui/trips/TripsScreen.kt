package org.kealohalabs.receiptbeat.ui.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.kealohalabs.receiptbeat.core.data.CatalogRepository
import org.kealohalabs.receiptbeat.core.data.ReceiptRepository
import org.kealohalabs.receiptbeat.core.data.SettingsRepository
import org.kealohalabs.receiptbeat.core.data.TripMath
import org.kealohalabs.receiptbeat.core.data.TripSummary
import org.kealohalabs.receiptbeat.core.designsystem.AppCard
import org.kealohalabs.receiptbeat.core.designsystem.EmptyState
import org.kealohalabs.receiptbeat.core.designsystem.MoneyStyle
import org.kealohalabs.receiptbeat.core.designsystem.MoneyText
import org.kealohalabs.receiptbeat.core.model.Payer
import org.kealohalabs.receiptbeat.core.model.Project
import org.kealohalabs.receiptbeat.export.Exporter
import org.kealohalabs.receiptbeat.ui.common.pretty
import org.kealohalabs.receiptbeat.ui.settings.ProjectDialog

data class TripsState(
    val trips: List<TripSummary> = emptyList(),
    val payers: List<Payer> = emptyList(),
    val currency: String = "USD",
    val hideAmounts: Boolean = false,
)

@HiltViewModel
class TripsViewModel @Inject constructor(
    private val repo: ReceiptRepository,
    private val catalog: CatalogRepository,
    settings: SettingsRepository,
    private val exporter: Exporter,
) : ViewModel() {
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    val state: StateFlow<TripsState> = combine(catalog.projects, repo.receipts, catalog.payers, settings.settings) { pr, r, p, s ->
        TripsState(TripMath.summarize(pr, r), p, s.homeCurrency, s.hideAmounts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripsState())

    fun save(p: Project) = viewModelScope.launch { catalog.saveProject(p) }

    /** Deleting a trip keeps its receipts. They just come off the trip. */
    fun delete(p: Project) = viewModelScope.launch {
        repo.setProject(repo.all().filter { it.projectId == p.id }.map { it.id }, null, null)
        catalog.deleteProject(p)
    }

    fun addInDates(t: TripSummary) = viewModelScope.launch {
        repo.setProject(t.unassignedInRange, t.project.id, t.project.name)
        message.value = "Added ${t.unassignedInRange.size} to ${t.project.name}."
    }

    fun share(t: TripSummary, share: (List<File>, String) -> Unit) = viewModelScope.launch {
        busy.value = true
        runCatching { exporter.trip(t.project.id) }
            .onSuccess { share(it, t.project.name) }
            .onFailure { message.value = "Could not make the PDF. Please try again." }
        busy.value = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(
    back: () -> Unit,
    openList: (String) -> Unit,
    shareFiles: (List<File>, String) -> Unit,
    vm: TripsViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<Project?>(null) }
    LaunchedEffect(message) {
        message?.let {
            snack.showSnackbar(it)
            vm.message.value = null
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trips") },
                navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("New trip") },
                icon = { Icon(Icons.Rounded.Add, null) },
                onClick = { editing = Project(name = "") },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (s.trips.isEmpty()) {
                item {
                    EmptyState(
                        "No trips yet",
                        "Make a trip with its dates. Then add its receipts in one tap and share one PDF with every receipt in it.",
                    )
                }
            }
            items(s.trips, key = { it.project.id }) { t ->
                TripCard(
                    t,
                    s,
                    edit = { editing = t.project },
                    open = { openList("project:${t.project.id}") },
                    addInDates = { vm.addInDates(t) },
                    share = { vm.share(t, shareFiles) },
                )
            }
        }
    }
    editing?.let { p -> ProjectDialog(p, s.payers, vm::save, vm::delete) { editing = null } }
}

@Composable
private fun TripCard(t: TripSummary, s: TripsState, edit: () -> Unit, open: () -> Unit, addInDates: () -> Unit, share: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = open) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.project.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(edit) { Icon(Icons.Rounded.Edit, "Edit ${t.project.name}") }
            }
            val dates = when {
                t.first == null -> "No dates yet"
                t.last == null || t.last == t.first -> t.first.pretty()
                else -> t.first.pretty() + " to " + t.last.pretty()
            }
            val payer = s.payers.firstOrNull { it.id == t.project.payerId }?.name
            Text(
                listOfNotNull(dates, payer).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (t.count ==
                        1
                    ) {
                        "1 receipt"
                    } else {
                        "${t.count} receipts"
                    },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                MoneyText(t.totalCents, currency = s.currency, style = MoneyStyle.Row, hidden = s.hideAmounts)
            }
            if (t.unassignedInRange.isNotEmpty()) {
                val n = t.unassignedInRange.size
                FilledTonalButton(addInDates, Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(if (n == 1) "Add 1 receipt from these dates" else "Add $n receipts from these dates")
                }
            }
            if (t.count > 0) {
                OutlinedButton(share, Modifier.fillMaxWidth()) { Text("Share trip PDF") }
            }
        }
    }
}
