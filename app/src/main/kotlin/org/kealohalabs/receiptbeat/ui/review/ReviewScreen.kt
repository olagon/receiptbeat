package org.kealohalabs.receiptbeat.ui.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlinx.coroutines.launch
import org.kealohalabs.receiptbeat.core.designsystem.AppCard
import org.kealohalabs.receiptbeat.core.designsystem.EmptyState
import org.kealohalabs.receiptbeat.core.designsystem.MoneyStyle
import org.kealohalabs.receiptbeat.core.designsystem.MoneyText
import org.kealohalabs.receiptbeat.core.designsystem.Pill
import org.kealohalabs.receiptbeat.core.model.EmailCandidate
import org.kealohalabs.receiptbeat.core.model.JobStatus
import org.kealohalabs.receiptbeat.core.model.Money
import org.kealohalabs.receiptbeat.ui.common.pretty
import org.kealohalabs.receiptbeat.ui.common.rememberAskNotifications
import org.kealohalabs.receiptbeat.ui.detail.PickSheet

private val DayFmt = DateTimeFormatter.ofPattern("MMM d")

@Composable
fun ReviewScreen(addEmail: () -> Unit, accounts: () -> Unit, vm: ReviewViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var stack by rememberSaveable { mutableStateOf(false) }
    var keepWith by remember { mutableStateOf<List<String>?>(null) }
    var payerFor by remember { mutableStateOf<Pair<List<String>, String?>?>(null) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val skip: (List<String>) -> Unit = { ids ->
        vm.skip(ids)
        scope.launch { if (snack.showSnackbar("Skipped ${ids.size}", "Undo") == SnackbarResult.ActionPerformed) vm.unskip(ids) }
    }
    LaunchedEffect(s.count) { vm.finishWithAi() }
    // Someone with a connected account should see scan notifications. Asked once, when it first matters.
    val askNotifications = rememberAskNotifications()
    LaunchedEffect(s.accounts.isNotEmpty()) { if (s.accounts.isNotEmpty()) askNotifications() }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            AnimatedVisibility(s.selected.isNotEmpty()) {
                BottomAppBar {
                    IconButton(vm::clearSelection) { Icon(Icons.Rounded.Close, "Clear selection") }
                    Text("${s.selected.size} selected", Modifier.weight(1f))
                    TextButton({ skip(s.selected.toList()) }) { Text("Skip") }
                    Button({ keepWith = s.selected.toList() }) { Text("Keep all") }
                }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
            item { Header(s, addEmail, accounts, vm) }
            if (s.count > 0) {
                item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${s.count} found", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (s.aiWorking) {
                            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                            Text(" Checking", style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.width(8.dp))
                        }
                        SingleChoiceSegmentedButtonRow {
                            SegmentedButton(!stack, { stack = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("List") }
                            SegmentedButton(stack, { stack = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Cards") }
                        }
                    }
                }
            } else {
                item {
                    EmptyState(
                        "Nothing to review",
                        if (s.accounts.isEmpty() && s.jobs.isEmpty()) {
                            "Add your email to find receipts you forgot about. It all happens on this phone."
                        } else {
                            "New finds will show up here."
                        },
                    ) { if (s.accounts.isEmpty() && s.jobs.isEmpty()) Button(addEmail) { Text("Add email receipts") } }
                }
            }
            if (stack && s.count > 0) {
                val next = s.groups.flatMap { it.items }.first()
                item {
                    SwipeStack(next, s.count, keep = { vm.keep(listOf(next.id)) }, skip = { skip(listOf(next.id)) })
                }
            } else {
                s.groups.forEach { g ->
                    item(key = g.sender) {
                        Group(
                            g = g,
                            selected = s.selected,
                            toggle = vm::toggle,
                            selectAll = { vm.selectGroup(g) },
                            keepAll = { keepWith = g.items.map { it.id } },
                            mute = { vm.mute(g.sender) },
                            alwaysKeep = { payerFor = g.items.map { it.id } to g.sender },
                            keep = { vm.keep(listOf(it)) },
                            skip = { skip(listOf(it)) },
                        )
                    }
                }
            }
        }
    }

    keepWith?.let { ids ->
        PickSheet(
            "Keep ${ids.size} with category",
            listOf<Pair<String?, String>>(null to "Decide later") + s.categories.map { it.id to it.name },
            null,
            { keepWith = null },
        ) { cat -> vm.keep(ids, cat) }
    }
    payerFor?.let { (ids, sender) ->
        PickSheet(
            "Always keep from ${sender.orEmpty()}. Who pays these back?",
            listOf<Pair<String?, String>>(null to "Nobody, just keep them") + s.payers.map { it.id to it.name },
            null,
            { payerFor = null },
        ) { payer ->
            vm.alwaysKeep(sender!!, null, payer)
            vm.keep(ids, null, payer)
        }
    }
}

@Composable
private fun Header(s: ReviewState, addEmail: () -> Unit, accounts: () -> Unit, vm: ReviewViewModel) {
    Column(Modifier.statusBarsPadding().padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Review", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(accounts) { Icon(Icons.Rounded.Settings, "Mail accounts and rules") }
        }
        if (s.accounts.isNotEmpty() ||
            s.jobs.isNotEmpty() ||
            s.count > 0
        ) {
            FilledTonalButton(addEmail, Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Add email receipts")
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            s.accounts.forEach { a ->
                val last = a.lastScanAt?.atZone(ZoneId.systemDefault())?.format(DayFmt) ?: "never"
                AssistChip(
                    onClick = { vm.scanNow(a) },
                    label = { Text("${a.email} · checked $last") },
                    trailingIcon = { Icon(Icons.Rounded.Refresh, "Scan now", Modifier.size(18.dp)) },
                )
            }
        }
        s.scanning?.let { live -> ScanningCard(live) }
        s.jobs.filter { it.status == JobStatus.RUNNING || it.status == JobStatus.PAUSED || it.status == JobStatus.FAILED }.forEach { j ->
            AppCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(j.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when (j.status) {
                            JobStatus.FAILED -> j.error ?: "Stopped"
                            JobStatus.PAUSED -> "Paused at ${j.messagesSeen} emails"
                            else -> "${j.messagesSeen} emails checked, ${j.candidates} receipts found. Nothing leaves your phone."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (j.status == JobStatus.RUNNING) {
                        if (j.bytesTotal > 0) {
                            LinearProgressIndicator({
                                (j.bytesRead.toFloat() / j.bytesTotal).coerceIn(0f, 1f)
                            }, Modifier.fillMaxWidth().padding(top = 6.dp))
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                        }
                    } else {
                        TextButton({ vm.resume(j) }) { Text("Pick up where it left off") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Group(
    g: SenderGroup,
    selected: Set<String>,
    toggle: (String) -> Unit,
    selectAll: () -> Unit,
    keepAll: () -> Unit,
    mute: () -> Unit,
    alwaysKeep: () -> Unit,
    keep: (String) -> Unit,
    skip: (String) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(g.items.size <= 3) }
    var menu by remember { mutableStateOf(false) }
    AppCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column {
            Row(
                Modifier.clickable {
                    open = !open
                }.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(g.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${g.items.size} ${if (g.items.size == 1) "receipt" else "receipts"}, ${Money.format(g.totalCents)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (open) "Collapse" else "Expand")
                Box {
                    IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Sender actions") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Keep all") }, {
                            menu = false
                            keepAll()
                        })
                        DropdownMenuItem({ Text("Select all") }, {
                            menu = false
                            selectAll()
                        })
                        DropdownMenuItem({ Text("Always keep from ${g.sender}") }, {
                            menu = false
                            alwaysKeep()
                        })
                        DropdownMenuItem({ Text("Mute ${g.sender}") }, {
                            menu = false
                            mute()
                        })
                    }
                }
            }
            if (open) {
                g.items.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { toggle(c.id) }.padding(start = 4.dp, end = 8.dp, bottom = 6.dp)
                            .semantics {
                                customActions = listOf(
                                    CustomAccessibilityAction("Keep") {
                                        keep(c.id)
                                        true
                                    },
                                    CustomAccessibilityAction("Skip") {
                                        skip(c.id)
                                        true
                                    },
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(c.id in selected, { toggle(c.id) })
                        Column(Modifier.weight(1f)) {
                            Text(c.subject, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                (c.purchaseDate ?: c.receivedAt.atZone(ZoneId.systemDefault()).toLocalDate()).pretty() +
                                    if (c.needsAi) " · checking" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        MoneyText(c.amountCents, currency = c.currency, style = MoneyStyle.Small)
                        IconButton({ keep(c.id) }) { Icon(Icons.Rounded.Check, "Keep") }
                    }
                }
            }
        }
    }
}

/** One by one: swipe right to keep, left to skip. Buttons do the same for people who cannot swipe. */
@Composable
private fun SwipeStack(c: EmailCandidate, left: Int, keep: () -> Unit, skip: () -> Unit) {
    val x = remember(c.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AppCard(
            Modifier.fillMaxWidth().height(320.dp).padding(top = 8.dp)
                .graphicsLayer {
                    translationX = x.value
                    rotationZ = x.value / 40f
                }
                .pointerInput(c.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                when {
                                    x.value > size.width / 3 -> {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        keep()
                                    }
                                    x.value < -size.width / 3 -> skip()
                                    else -> x.animateTo(0f)
                                }
                            }
                        },
                    ) { _, d -> scope.launch { x.snapTo(x.value + d) } }
                },
        ) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        c.merchant ?: c.senderName ?: c.sender,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    Pill("${c.score}%")
                }
                Text(c.sender, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                MoneyText(c.amountCents, currency = c.currency, style = MoneyStyle.Hero)
                Text(
                    (c.purchaseDate ?: c.receivedAt.atZone(ZoneId.systemDefault()).toLocalDate()).pretty(),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Text(c.subject, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    c.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                val hint = if (abs(x.value) > 40) (if (x.value > 0) "Keep" else "Skip") else "$left left"
                Text(hint, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            }
        }
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(skip, Modifier.width(140.dp).height(52.dp)) { Text("Skip") }
            Button(keep, Modifier.width(140.dp).height(52.dp)) { Text("Keep") }
        }
    }
}

/** Shows the scan is alive: what it is doing, how far along, and what it found so far. */
@Composable
private fun ScanningCard(live: LiveScan) {
    AppCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Checking email", style = MaterialTheme.typography.titleSmall)
            }
            Text(
                if (live.total > 0) "${live.stage}: ${live.done} of ${live.total} emails" else "${live.stage}...",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                "${live.found} receipts found so far. Nothing leaves your phone. You can leave this screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (live.total > 0) {
                LinearProgressIndicator({ live.done.toFloat() / live.total }, Modifier.fillMaxWidth().padding(top = 6.dp))
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        }
    }
}
