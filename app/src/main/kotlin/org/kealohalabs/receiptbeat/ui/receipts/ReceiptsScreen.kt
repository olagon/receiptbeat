package org.kealohalabs.receiptbeat.ui.receipts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import org.kealohalabs.receiptbeat.core.data.ReceiptRepository
import org.kealohalabs.receiptbeat.core.data.label
import org.kealohalabs.receiptbeat.core.designsystem.EmptyState
import org.kealohalabs.receiptbeat.core.designsystem.MoneyStyle
import org.kealohalabs.receiptbeat.core.designsystem.MoneyText
import org.kealohalabs.receiptbeat.core.designsystem.look
import org.kealohalabs.receiptbeat.core.model.Project
import org.kealohalabs.receiptbeat.core.model.Receipt
import org.kealohalabs.receiptbeat.core.model.Status
import org.kealohalabs.receiptbeat.core.model.Track
import org.kealohalabs.receiptbeat.ui.common.ReceiptCard

private val MonthFmt = DateTimeFormatter.ofPattern("MMMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptsScreen(open: (String) -> Unit, vm: ReceiptsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val run: (Collection<String>, String, suspend (ReceiptRepository) -> Unit) -> Unit = { ids, msg, change ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        vm.act(ids, change) { undo ->
            scope.launch {
                if (snack.showSnackbar(msg, "Undo", withDismissAction = true) == SnackbarResult.ActionPerformed) vm.undo(undo)
            }
        }
    }

    Scaffold(
        topBar = {
            if (s.selected.isNotEmpty()) {
                TopAppBar(
                    title = { Text("${s.selected.size} selected") },
                    navigationIcon = { IconButton(vm::clearSelection) { Icon(Icons.Rounded.Close, "Clear selection") } },
                    actions = { IconButton(vm::selectAll) { Icon(Icons.Rounded.SelectAll, "Select all") } }
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(s.selected.isNotEmpty()) { BulkBar(s.selected, s.projects.values.sortedBy { it.name }, run) }
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column0(s.selected.isEmpty()) {
                    OutlinedTextField(
                        value = s.filter.query,
                        onValueChange = vm::query,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search receipts") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (s.filter.query.isNotEmpty()) IconButton({ vm.query("") }) { Icon(Icons.Rounded.Close, "Clear search") }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp)
                    )
                }
            }
            item { FilterRow(s, vm::setFilter) }
            if (s.loaded && s.count == 0) {
                item {
                    EmptyState(
                        if (s.filter.active || s.filter.query.isNotEmpty()) "Nothing matches" else "No receipts yet",
                        if (s.filter.active ||
                            s.filter.query.isNotEmpty()
                        ) {
                            "Try a different search or clear the filters."
                        } else {
                            "Tap the capture button to scan your first one."
                        }
                    ) {
                        if (s.filter.active) TextButton({ vm.setFilter(ReceiptFilter()) }) { Text("Clear filters") }
                    }
                }
            }
            s.groups.forEach { g ->
                item(key = "h-${g.month}") {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            g.month?.format(MonthFmt) ?: if (s.filter.sort == Sort.DATE) "No date" else "${g.items.size} receipts",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        MoneyText(
                            g.totalCents,
                            style = MoneyStyle.Small,
                            hidden = s.hideAmounts,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(g.items, key = { it.id }) { r ->
                    SwipeCard(
                        r = r,
                        s = s,
                        selecting = s.selected.isNotEmpty(),
                        open = { if (s.selected.isNotEmpty()) vm.toggle(r.id) else open(r.id) },
                        select = { vm.toggle(r.id) },
                        forward = { run(listOf(r.id), nextLabel(r)) { it.advance(listOf(r.id)) } },
                        archive = { run(listOf(r.id), "Archived") { it.setStatus(listOf(r.id), Status.ARCHIVED) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun Column0(visible: Boolean, content: @Composable () -> Unit) {
    if (visible) Box(Modifier.statusBarsPadding().padding(top = 8.dp)) { content() }
}

private fun nextLabel(r: Receipt) = when (r.status) {
    Status.INBOX -> "Marked Ready"
    Status.READY, Status.REJECTED -> when (r.track) {
        Track.REIMBURSABLE -> "Marked Submitted"
        Track.TAX -> "Marked Claimed"
        Track.PERSONAL -> "Archived"
    }
    else -> "Marked Reimbursed"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeCard(
    r: Receipt,
    s: ReceiptsState,
    selecting: Boolean,
    open: () -> Unit,
    select: () -> Unit,
    forward: () -> Unit,
    archive: () -> Unit
) {
    val canForward = r.status !in setOf(Status.REIMBURSED, Status.CLAIMED, Status.ARCHIVED) && !r.locked
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            when (it) {
                SwipeToDismissBoxValue.StartToEnd -> if (canForward) forward()
                SwipeToDismissBoxValue.EndToStart -> if (!r.locked && r.status != Status.ARCHIVED) archive()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        }
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = canForward && !selecting,
        enableDismissFromEndToStart = !r.locked && !selecting,
        backgroundContent = {
            val toEnd = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Row(
                Modifier.fillMaxSize().background(
                    if (toEnd) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    RoundedCornerShape(20.dp)
                ).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toEnd) Arrangement.Start else Arrangement.End
            ) {
                Icon(if (toEnd) Icons.AutoMirrored.Rounded.ArrowForward else Icons.Rounded.Archive, null)
                Spacer(Modifier.width(8.dp))
                Text(if (toEnd) nextLabel(r).removePrefix("Marked ") else "Archive")
            }
        }
    ) {
        ReceiptCard(
            r = r,
            thumb = s.thumbs[r.id],
            category = s.categories[r.categoryId],
            lowConfidence = r.id in s.low,
            selected = r.id in s.selected,
            hideAmounts = s.hideAmounts,
            payerName = s.payers[r.payerId]?.name,
            onClick = open,
            onLongClick = select,
            // Every swipe also lives in the accessibility actions menu.
            modifier = Modifier.semantics {
                customActions = listOfNotNull(
                    if (canForward) {
                        CustomAccessibilityAction(nextLabel(r).removePrefix("Marked ")) {
                            forward()
                            true
                        }
                    } else {
                        null
                    },
                    CustomAccessibilityAction("Archive") {
                        archive()
                        true
                    },
                    CustomAccessibilityAction("Select") {
                        select()
                        true
                    }
                )
            }
        )
    }
}

@Composable
private fun BulkBar(
    ids: Set<String>,
    trips: List<Project>,
    run: (Collection<String>, String, suspend (ReceiptRepository) -> Unit) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    BottomAppBar {
        TextButton({ run(ids, "Moved forward") { it.advance(ids) } }) { Text("Move forward") }
        TextButton({ run(ids, "Marked Reimbursed") { it.setStatus(ids, Status.REIMBURSED) } }) { Text("Paid") }
        Spacer(Modifier.weight(1f))
        IconButton({ run(ids, "Moved to trash") { it.delete(ids) } }) { Icon(Icons.Rounded.Delete, "Delete") }
        Box {
            IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "More actions") }
            DropdownMenu(menu, { menu = false }) {
                Status.entries.forEach { st ->
                    DropdownMenuItem({ Text("Mark ${st.label()}") }, {
                        menu = false
                        run(ids, "Marked ${st.label()}") { it.setStatus(ids, st) }
                    })
                }
                if (trips.isNotEmpty()) {
                    HorizontalDivider()
                    trips.forEach { t ->
                        DropdownMenuItem({ Text("Add to ${t.name}") }, {
                            menu = false
                            run(ids, "Added to ${t.name}") { it.setProject(ids, t.id, t.name) }
                        })
                    }
                    DropdownMenuItem({ Text("Take off trip") }, {
                        menu = false
                        run(ids, "Taken off trip") { it.setProject(ids, null, null) }
                    })
                }
                HorizontalDivider()
                Track.entries.forEach { t ->
                    DropdownMenuItem({ Text("Move to ${t.label()}") }, {
                        menu = false
                        run(ids, "Moved to ${t.label()}") { it.setTrack(ids, t) }
                    })
                }
            }
        }
    }
}

@Composable
private fun FilterRow(s: ReceiptsState, set: (ReceiptFilter) -> Unit) {
    val f = s.filter
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (f.needsLook) FilterChip(true, { set(f.copy(needsLook = false)) }, { Text("Needs a look") })
        MenuChip(
            label = if (f.statuses.isEmpty()) "Status" else f.statuses.joinToString { it.look().label },
            on = f.statuses.isNotEmpty(),
            options = listOf("Any status" to null) + Status.entries.map { it.label() to it }
        ) { set(f.copy(statuses = it?.let(::setOf) ?: emptySet())) }
        MenuChip(f.track?.label() ?: "Track", f.track != null, listOf("Any track" to null) + Track.entries.map { it.label() to it }) {
            set(f.copy(track = it))
        }
        MenuChip(
            s.categories[f.categoryId]?.name ?: "Category",
            f.categoryId != null,
            listOf("Any category" to null) + s.categories.values.filter { !it.hidden }.sortedBy { it.sortOrder }.map { it.name to it.id }
        ) { set(f.copy(categoryId = it)) }
        if (s.payers.isNotEmpty()) {
            MenuChip(
                s.payers[f.payerId]?.name ?: "Payer",
                f.payerId != null,
                listOf("Any payer" to null) + s.payers.values.map { it.name to it.id }
            ) {
                set(f.copy(payerId = it))
            }
        }
        if (s.projects.isNotEmpty()) {
            MenuChip(
                s.projects[f.projectId]?.name ?: "Trip",
                f.projectId != null,
                listOf("Any trip" to null) + s.projects.values.sortedBy { it.name }.map { it.name to it.id }
            ) { set(f.copy(projectId = it)) }
        }
        val today = LocalDate.now()
        val ranges = listOf(
            "Any time" to (null to null),
            "This month" to (today.withDayOfMonth(1) to null),
            "Last 3 months" to (today.minusMonths(3) to null),
            "This year" to (today.withDayOfYear(1) to null),
            "Last year" to (today.minusYears(1).withDayOfYear(1) to today.withDayOfYear(1).minusDays(1))
        )
        MenuChip(ranges.firstOrNull { it.second == (f.from to f.to) && f.from != null }?.first ?: "Date", f.from != null, ranges) {
            set(f.copy(from = it?.first, to = it?.second))
        }
        MenuChip(
            when (f.source) {
                SourceFilter.CAMERA -> "Camera"
                SourceFilter.EMAIL -> "Email"
                SourceFilter.FILE -> "Files"
                null -> "Source"
            },
            f.source != null,
            listOf("Any source" to null, "Camera" to SourceFilter.CAMERA, "Email" to SourceFilter.EMAIL, "Files" to SourceFilter.FILE)
        ) { set(f.copy(source = it)) }
        MenuChip(
            "Sort: " + f.sort.name.lowercase().replaceFirstChar { it.uppercase() },
            false,
            Sort.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } to it }
        ) { set(f.copy(sort = it ?: Sort.DATE)) }
    }
}

@Composable
private fun <T> MenuChip(label: String, on: Boolean, options: List<Pair<String, T?>>, pick: (T?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(on, { open = true }, { Text(label) }, trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null) })
        DropdownMenu(open, { open = false }) {
            options.forEach { (text, v) ->
                DropdownMenuItem({ Text(text) }, {
                    open = false
                    pick(v)
                })
            }
        }
    }
}
