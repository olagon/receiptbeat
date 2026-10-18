package org.kealohalabs.receiptbeat.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.kealohalabs.receiptbeat.core.designsystem.AppCard
import org.kealohalabs.receiptbeat.core.designsystem.SectionHeader
import org.kealohalabs.receiptbeat.core.model.AutoScanSettings
import org.kealohalabs.receiptbeat.core.model.MailAccount
import org.kealohalabs.receiptbeat.core.model.ScanFrequency
import org.kealohalabs.receiptbeat.core.model.ScanMode
import org.kealohalabs.receiptbeat.core.model.ScanRange
import org.kealohalabs.receiptbeat.core.model.SenderRuleKind
import org.kealohalabs.receiptbeat.ui.detail.DateDialog

private val RunFmt = DateTimeFormatter.ofPattern("MMM d, h:mm a")
private val MonthFmt = DateTimeFormatter.ofPattern("MMM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailAccountsScreen(back: () -> Unit, add: () -> Unit, vm: ReviewViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text("Mail accounts")
            }, navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } })
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 48.dp)) {
            if (s.accounts.isEmpty()) {
                item {
                    Text("No connected accounts. You can still import email files.", Modifier.padding(vertical = 12.dp))
                    OutlinedButton(add) { Text("Add email receipts") }
                }
            }
            items(s.accounts, key = { it.id }) { a ->
                Account(a, s.autoScan[a.id] ?: AutoScanSettings(a.id), s.ranges.filter { it.accountId == a.id }, vm)
            }
            item { SectionHeader("Scan log") }
            if (s.runs.isEmpty()) item { Text("No scans yet.", style = MaterialTheme.typography.bodySmall) }
            items(s.runs.take(30), key = { it.id }) { r ->
                val acct = s.accounts.firstOrNull { it.id == r.accountId }?.email ?: "Removed account"
                ListItem(
                    headlineContent = {
                        Text("${r.startedAt.atZone(ZoneId.systemDefault()).format(RunFmt)} · ${r.trigger.name.lowercase()}")
                    },
                    supportingContent = {
                        Text(
                            "$acct. ${r.checked} checked, ${r.found} found, ${r.autoKept} auto kept" +
                                (r.error?.let { ". $it" } ?: if (r.finishedAt == null) ". Running" else ""),
                        )
                    },
                )
            }
            item { SectionHeader("Sender rules") }
            if (s.rules.isEmpty()) {
                item { Text("Mute a sender or always keep one from the Review list.", style = MaterialTheme.typography.bodySmall) }
            }
            items(s.rules, key = { it.id }) { r ->
                ListItem(
                    headlineContent = { Text(r.senderOrDomain) },
                    supportingContent = {
                        Text(
                            if (r.rule == SenderRuleKind.MUTE) {
                                "Muted"
                            } else {
                                "Always keep" + (s.payers.firstOrNull { it.id == r.defaultPayerId }?.let { ", payer ${it.name}" } ?: "")
                            },
                        )
                    },
                    trailingContent = { IconButton({ vm.deleteRule(r) }) { Icon(Icons.Rounded.Delete, "Remove rule") } },
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Account(a: MailAccount, auto: AutoScanSettings, ranges: List<ScanRange>, vm: ReviewViewModel) {
    val set: (AutoScanSettings) -> Unit = { vm.saveAutoScan(a, it) }
    AppCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(a.email, style = MaterialTheme.typography.titleMedium)
            Text(
                "Searching from ${a.scanFrom.format(DateTimeFormatter.ofPattern("MMM yyyy"))}. Last checked " +
                    (a.lastScanAt?.atZone(ZoneId.systemDefault())?.format(RunFmt) ?: "never") + ".",
                style = MaterialTheme.typography.bodySmall,
            )
            Months(ranges)
            SearchSettings(a, vm)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ vm.scanNow(a) }) { Text("Scan now") }
                OutlinedButton({ vm.extendBack(a, 12) }) { Text("Extend back a year") }
            }
            Text("Auto scan", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    ScanFrequency.OFF to "Off",
                    ScanFrequency.EVERY_12H to "Every 12 hours",
                    ScanFrequency.DAILY to "Daily",
                    ScanFrequency.WEEKLY to "Weekly"
                )
                    .forEach { (f, n) -> FilterChip(auto.frequency == f, { set(auto.copy(frequency = f)) }, { Text(n) }) }
            }
            if (auto.frequency != ScanFrequency.OFF) {
                Text("Around", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 2, 4, 6, 12, 18, 22).forEach { h ->
                        FilterChip(auto.preferredHour == h, { set(auto.copy(preferredHour = h)) }, { Text(hour(h)) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Wi-Fi only", Modifier.weight(1f))
                    Switch(auto.wifiOnly, { set(auto.copy(wifiOnly = it)) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("While charging only", Modifier.weight(1f))
                    Switch(auto.chargingOnly, { set(auto.copy(chargingOnly = it)) })
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        ScanMode.FIND_ONLY to "Find only",
                        ScanMode.FIND_AND_EXTRACT to "Find and extract",
                        ScanMode.AUTO_KEEP_TRUSTED to "Auto keep trusted senders"
                    )
                        .forEach { (m, n) -> FilterChip(auto.mode == m, { set(auto.copy(mode = m)) }, { Text(n) }) }
                }
            }
            TextButton({ vm.remove(a) }) {
                Icon(Icons.Rounded.Delete, null)
                Text("Remove account and password")
            }
        }
    }
}

/** A strip of the last 12 months, filled in where mail was already searched. */
@Composable
private fun Months(ranges: List<ScanRange>) {
    val now = YearMonth.now()
    val months = (11 downTo 0).map { now.minusMonths(it.toLong()) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        months.forEach { m ->
            val done = ranges.any { !it.fromDate.isAfter(m.atEndOfMonth()) && !it.toDate.isBefore(m.atDay(1)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                        .semantics { contentDescription = m.format(MonthFmt) + if (done) " searched" else " not searched" },
                )
                Text(m.format(MonthFmt).take(1), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    Spacer(Modifier.size(0.dp))
}

private fun hour(h: Int) = when {
    h == 0 -> "12 AM"
    h < 12 -> "$h AM"
    h == 12 -> "12 PM"
    else -> "${h - 12} PM"
}

/** What to search: folders, search words and how far back. Saving starts a scan with the new settings. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SearchSettings(a: MailAccount, vm: ReviewViewModel) {
    val gmail = a.imapHost.equals("imap.gmail.com", true)
    var editing by rememberSaveable { mutableStateOf(false) }
    Text("What to search", style = MaterialTheme.typography.titleSmall)
    Text(
        "Folders: " + (a.folders?.lines()?.joinToString(", ") ?: if (gmail) "All Mail (includes archived mail)" else "Inbox and Archive") +
            "\nWords: " + (a.searchWords ?: "receipt, invoice, order, payment and similar") +
            "\nFrom: " + a.scanFrom.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
        style = MaterialTheme.typography.bodySmall,
    )
    TextButton({ editing = true }) { Text("Change what to search") }
    if (editing) SearchDialog(a, gmail, vm) { editing = false }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SearchDialog(a: MailAccount, gmail: Boolean, vm: ReviewViewModel, close: () -> Unit) {
    var all by remember { mutableStateOf<List<String>?>(null) }
    var defaults by remember { mutableStateOf(emptyList<String>()) }
    var picked by remember { mutableStateOf(a.folders?.lines()?.toSet()) }
    var error by remember { mutableStateOf<String?>(null) }
    var words by remember { mutableStateOf(a.searchWords.orEmpty()) }
    var from by remember { mutableStateOf(a.scanFrom) }
    var pickDate by remember { mutableStateOf(false) }
    LaunchedEffect(a.id) {
        vm.loadFolders(a) { list, def, e ->
            all = list
            defaults = def
            error = e
        }
    }
    val chosen = picked ?: defaults.toSet()
    AlertDialog(
        onDismissRequest = close,
        title = { Text("What to search") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Folders", style = MaterialTheme.typography.titleSmall)
                when {
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    all == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    else -> {
                        if (gmail) {
                            Text(
                                "All Mail holds everything, including archived mail. Spam and Trash are always left out.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        all!!.forEach { f ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(f in chosen, { on -> picked = if (on) chosen + f else chosen - f })
                                Text(f, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                Text("Search words", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    words,
                    { words = it },
                    placeholder = { Text(if (gmail) "from:amazon.com OR label:receipts" else "receipt invoice order") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (gmail) {
                        "Leave empty to use the built in receipt words. You can use Gmail search, like from:uber.com, " +
                            "label:receipts or subject:invoice. Join with OR."
                    } else {
                        "Leave empty to use the built in receipt words. Separate words with spaces. " +
                            "Any email with one of them in the subject or body is checked."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Search from", style = MaterialTheme.typography.titleSmall)
                TextButton({ pickDate = true }) { Text(from.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))) }
            }
        },
        confirmButton = {
            TextButton({
                vm.saveSearch(a, picked?.toList()?.takeIf { it.toSet() != defaults.toSet() }, words, from)
                close()
            }, enabled = chosen.isNotEmpty() || all == null) { Text("Save and scan") }
        },
        dismissButton = { TextButton(close) { Text("Cancel") } },
    )
    if (pickDate) {
        DateDialog(from, { pickDate = false }) {
            if (it != null) from = it
            pickDate = false
        }
    }
}
