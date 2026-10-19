package org.kealohalabs.receiptbeat.ui.money

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import org.kealohalabs.receiptbeat.core.data.PayerTotals
import org.kealohalabs.receiptbeat.core.data.ReportRow
import org.kealohalabs.receiptbeat.core.data.ReportState
import org.kealohalabs.receiptbeat.core.data.TaxYearSummary
import org.kealohalabs.receiptbeat.core.designsystem.AppCard
import org.kealohalabs.receiptbeat.core.designsystem.Brand
import org.kealohalabs.receiptbeat.core.designsystem.EmptyState
import org.kealohalabs.receiptbeat.core.designsystem.MoneyStyle
import org.kealohalabs.receiptbeat.core.designsystem.MoneyText
import org.kealohalabs.receiptbeat.core.designsystem.Pill
import org.kealohalabs.receiptbeat.core.designsystem.SectionHeader
import org.kealohalabs.receiptbeat.core.designsystem.StatusChip
import org.kealohalabs.receiptbeat.core.model.Money
import org.kealohalabs.receiptbeat.core.model.Receipt

private val DayFmt = DateTimeFormatter.ofPattern("MMM d, yyyy")

@Composable
fun MoneyScreen(
    openReceipt: (String) -> Unit,
    shareFiles: (List<File>, String) -> Unit,
    startOnTaxes: Boolean,
    vm: MoneyViewModel = hiltViewModel()
) {
    val s by vm.state.collectAsStateWithLifecycle()
    var taxes by rememberSaveable { mutableStateOf(startOnTaxes) }
    var sheetFor by remember { mutableStateOf<PayerTotals?>(null) }
    var partFor by remember { mutableStateOf<ReportRow?>(null) }
    var confirmUnlock by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(s.message) {
        s.message?.let {
            snack.showSnackbar(it, withDismissAction = true)
            vm.clearMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snack) }) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Column(Modifier.statusBarsPadding().padding(top = 12.dp)) {
                        Text("Money", style = MaterialTheme.typography.headlineSmall)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            listOf("Reimburse", "Taxes").forEachIndexed { i, label ->
                                SegmentedButton(
                                    selected = taxes == (i == 1),
                                    onClick = { taxes = i == 1 },
                                    shape = SegmentedButtonDefaults.itemShape(i, 2)
                                ) { Text(label) }
                            }
                        }
                        if (s.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                }
                if (taxes) {
                    taxItems(s, openReceipt, onExport = { vm.exportTaxes(shareFiles) }, onLock = vm::lockYear, onUnlock = {
                        confirmUnlock = true
                    }, onYear = vm::selectYear)
                } else {
                    reimburseItems(
                        s,
                        openReceipt,
                        onCreate = { sheetFor = it },
                        onPaid = { vm.markPaid(it.report.id) },
                        onPart = { partFor = it },
                        onShare = { vm.shareReport(it, shareFiles) }
                    )
                }
            }
            if (s.celebrate) Celebration(vm::dismissCelebration)
        }
    }

    sheetFor?.let { p ->
        CreateReportSheet(p, s.hideAmounts, onDismiss = { sheetFor = null }) { ids, title ->
            sheetFor = null
            vm.createReport(p.payer?.id, ids, title, shareFiles)
        }
    }
    partFor?.let { row ->
        PartPaidDialog(row, s.homeCurrency, onDismiss = { partFor = null }) { cents ->
            partFor = null
            vm.markPartlyPaid(row.report.id, cents)
        }
    }
    if (confirmUnlock) {
        AlertDialog(
            onDismissRequest = { confirmUnlock = false },
            title = { Text("Unlock ${s.year}?") },
            text = { Text("Receipts in this year can be changed again. Your totals may shift.") },
            confirmButton = {
                TextButton({
                    confirmUnlock = false
                    vm.unlockYear()
                }) { Text("Unlock") }
            },
            dismissButton = { TextButton({ confirmUnlock = false }) { Text("Keep locked") } }
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.reimburseItems(
    s: MoneyState,
    openReceipt: (String) -> Unit,
    onCreate: (PayerTotals) -> Unit,
    onPaid: (ReportRow) -> Unit,
    onPart: (ReportRow) -> Unit,
    onShare: (ReportRow) -> Unit
) {
    item {
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("Owed to you", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MoneyText(s.owedCents, style = MoneyStyle.Hero, currency = s.homeCurrency, hidden = s.hideAmounts)
                Text(
                    if (s.hideAmounts) {
                        "To submit and waiting on payment"
                    } else {
                        "${Money.format(
                            s.toSubmitCents,
                            s.homeCurrency
                        )} to submit · ${Money.format(s.waitingCents, s.homeCurrency)} waiting on payment"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (s.payers.isEmpty()) {
        item { EmptyState("Nothing to pay back yet", "Mark a receipt as reimbursable and pick who pays you back.") }
    }
    items(s.payers, key = { "p-" + (it.payer?.id ?: "none") }) { p -> PayerCard(p, s, onCreate) }
    if (s.reports.isNotEmpty()) item { SectionHeader("Reports") }
    items(s.reports, key = { it.report.id }) { row -> ReportCard(row, s, openReceipt, onPaid, onPart, onShare) }
}

@Composable
private fun PayerCard(p: PayerTotals, s: MoneyState, onCreate: (PayerTotals) -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(p.payer?.name ?: "No payer", style = MaterialTheme.typography.titleMedium)
            AmountRow(
                listOf("Ready" to p.readyCents, "Submitted" to p.submittedCents, "Paid" to p.paidCents),
                s.homeCurrency,
                s.hideAmounts
            )
            Button(
                onClick = { onCreate(p) },
                enabled = p.ready.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp)
            ) { Text(if (p.ready.isEmpty()) "Nothing ready" else "Create report (${p.ready.size})") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmountRow(values: List<Pair<String, Long>>, currency: String, hidden: Boolean) {
    // Wraps at large font sizes instead of squeezing the amounts.
    FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        values.forEach { (label, cents) ->
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MoneyText(cents, style = MoneyStyle.Small, currency = currency, hidden = hidden)
            }
        }
    }
}

@Composable
private fun ReportCard(
    row: ReportRow,
    s: MoneyState,
    openReceipt: (String) -> Unit,
    onPaid: (ReportRow) -> Unit,
    onPart: (ReportRow) -> Unit,
    onShare: (ReportRow) -> Unit
) {
    var open by rememberSaveable(row.report.id) { mutableStateOf(false) }
    val r = row.report
    AppCard(Modifier.fillMaxWidth(), onClick = { open = !open }) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${r.number}  ${r.title}", style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    val day = (r.submittedAt ?: r.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
                    Text(
                        "${row.payer?.name ?: "No payer"}, sent ${day.format(DayFmt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                MoneyText(r.totalCents, currency = s.homeCurrency, hidden = s.hideAmounts)
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (row.state) {
                    ReportState.SUBMITTED -> Pill("Submitted", color = Color(0xFF8A5A00))
                    ReportState.PARTLY_PAID -> Pill("Partly paid", color = Color(0xFF1E7A46))
                    ReportState.PAID -> Pill("Paid", color = Color(0xFF1E7A46))
                }
                if (row.overdue) Pill("Overdue", color = MaterialTheme.colorScheme.error)
            }
            if (open) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                row.receipts.forEach { rc -> ReceiptLine(rc, s.hideAmounts) { openReceipt(rc.id) } }
                if (row.state == ReportState.PARTLY_PAID) {
                    Row(Modifier.padding(top = 4.dp)) {
                        Text("Still owed", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        MoneyText(row.owedCents, style = MoneyStyle.Small, currency = s.homeCurrency, hidden = s.hideAmounts)
                    }
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (row.state != ReportState.PAID) {
                        Button({ onPaid(row) }, Modifier.heightIn(min = 48.dp)) { Text("Mark report paid") }
                        OutlinedButton({ onPart(row) }, Modifier.heightIn(min = 48.dp)) { Text("Part paid") }
                    }
                    TextButton({ onShare(row) }, Modifier.heightIn(min = 48.dp)) { Text("Share again") }
                }
            }
        }
    }
}

@Composable
private fun ReceiptLine(r: Receipt, hidden: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(r.merchantName ?: "Receipt", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(
                r.purchaseDate?.format(DayFmt) ?: "No date",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        StatusChip(r.status, Modifier.padding(horizontal = 8.dp))
        MoneyText(r.chargeCents, style = MoneyStyle.Small, currency = r.currency, hidden = hidden)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.taxItems(
    s: MoneyState,
    openReceipt: (String) -> Unit,
    onExport: () -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onYear: (Int) -> Unit
) {
    val t: TaxYearSummary = s.tax ?: return
    item {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            s.years.forEach { y -> FilterChip(selected = y == s.year, onClick = { onYear(y) }, label = { Text(y.toString()) }) }
        }
    }
    item {
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Deductible in ${t.year}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (t.locked) Pill("Locked")
                }
                MoneyText(t.deductibleCents, style = MoneyStyle.Hero, currency = s.homeCurrency, hidden = s.hideAmounts)
                Row {
                    Text("${t.receipts.size} receipts, spent ", style = MaterialTheme.typography.bodySmall)
                    MoneyText(t.totalCents, style = MaterialTheme.typography.bodySmall, currency = s.homeCurrency, hidden = s.hideAmounts)
                }
            }
        }
    }
    if (t.lines.isEmpty()) {
        item { EmptyState("No tax receipts in ${t.year}", "Move a receipt to the tax track and it shows up here.") }
    } else {
        item { SectionHeader("By tax line") }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    t.lines.forEachIndexed { i, l ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(l.line?.label ?: "Not mapped to a tax line", style = MaterialTheme.typography.bodyMedium)
                                val pct = l.line?.deductiblePercent ?: 100
                                Text(
                                    "${l.count} receipts" + if (pct < 100) ", $pct% deductible" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                MoneyText(l.deductibleCents, style = MoneyStyle.Small, currency = s.homeCurrency, hidden = s.hideAmounts)
                                if (l.deductibleCents != l.totalCents) {
                                    Row {
                                        Text("of ", style = MaterialTheme.typography.bodySmall)
                                        MoneyText(
                                            l.totalCents,
                                            style = MaterialTheme.typography.bodySmall,
                                            currency = s.homeCurrency,
                                            hidden = s.hideAmounts,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        if (i < t.lines.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
    }
    if (t.warnings.isNotEmpty()) {
        item { SectionHeader("Needs a look (${t.warnings.size})") }
        items(t.warnings, key = { "w-" + it.receipt.id + it.text }) { w ->
            AppCard(Modifier.fillMaxWidth(), onClick = { openReceipt(w.receipt.id) }) {
                Row(Modifier.padding(16.dp).heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(w.receipt.merchantName ?: "Receipt", style = MaterialTheme.typography.bodyMedium)
                        Text(w.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    MoneyText(w.receipt.totalCents, style = MoneyStyle.Small, currency = w.receipt.currency, hidden = s.hideAmounts)
                }
            }
        }
    }
    item {
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onExport, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = t.receipts.isNotEmpty() && !s.busy) {
                Text("Export for my accountant")
            }
            if (t.locked) {
                OutlinedButton(onUnlock, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Unlock year") }
            } else {
                OutlinedButton(onLock, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = t.receipts.isNotEmpty() && !s.busy) {
                    Text("Lock year")
                }
            }
            Text(
                "Locking claims every receipt in ${t.year} so totals do not change by accident. This is not tax advice.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateReportSheet(p: PayerTotals, hidden: Boolean, onDismiss: () -> Unit, onCreate: (List<String>, String) -> Unit) {
    val picked = remember(p) { mutableStateListOf<String>().apply { addAll(p.ready.map { it.id }) } }
    var title by rememberSaveable {
        mutableStateOf("${p.payer?.name ?: "Expenses"} ${LocalDate.now().format(DateTimeFormatter.ofPattern("MMM yyyy"))}")
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("New report for ${p.payer?.name ?: "no payer"}", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                title,
                { title = it },
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                label = { Text("Title") },
                singleLine = true
            )
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(p.ready, key = { it.id }) { r ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(
                            value = r.id in picked,
                            role = Role.Checkbox,
                            onValueChange = { on -> if (on) picked.add(r.id) else picked.remove(r.id) }
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = r.id in picked, onCheckedChange = null)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(r.merchantName ?: "Receipt", style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            Text(
                                r.purchaseDate?.format(DayFmt) ?: "No date",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        MoneyText(r.chargeCents, style = MoneyStyle.Small, currency = r.currency, hidden = hidden)
                    }
                }
            }
            val total = p.ready.filter { it.id in picked }.sumOf { it.chargeCents ?: 0 }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${picked.size} receipts", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                MoneyText(total, hidden = hidden)
            }
            Button(
                { onCreate(p.ready.map { it.id }.filter { it in picked }, title) },
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                enabled = picked.isNotEmpty()
            ) { Text("Create and share") }
            Text(
                "We make a PDF and a CSV. The receipts move to Submitted.",
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PartPaidDialog(row: ReportRow, currency: String, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val cents = Money.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How much came in?") },
        text = {
            Column {
                Text("Still owed on ${row.report.number}: ${Money.format(row.owedCents, currency)}. We pay off the oldest receipts first.")
                OutlinedTextField(
                    text,
                    { text = it },
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    label = { Text("Amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        },
        confirmButton = { TextButton({ cents?.let(onSave) }, enabled = cents != null && cents > 0) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}

/** A short burst of coral and teal confetti with a check, when everything owed is paid back. */
@Composable
private fun Celebration(done: () -> Unit) {
    val burst = remember { Animatable(0f) }
    val check = remember { Animatable(0f) }
    val pieces = remember { List(36) { Triple(Random.nextFloat() * 6.283f, 0.5f + Random.nextFloat() * 0.5f, it % 3) } }
    LaunchedEffect(Unit) {
        check.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        burst.animateTo(1f, tween(1200, easing = FastOutSlowInEasing))
        delay(1200)
        done()
    }
    val colors = listOf(Brand.Coral, Brand.TealSurface, Color(0xFFF7C04A))
    Box(Modifier.fillMaxSize().clickable(onClick = done), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val p = burst.value
            pieces.forEach { (angle, speed, c) ->
                val dist = size.minDimension * 0.45f * speed * p
                val o = Offset(center.x + cos(angle) * dist, center.y + sin(angle) * dist + 200f * p * p)
                drawCircle(colors[c].copy(alpha = 1f - p), radius = 7f, center = o)
            }
        }
        Column(
            Modifier
                .graphicsLayer {
                    scaleX = check.value
                    scaleY = check.value
                }
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                .padding(28.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Rounded.CheckCircle, null, Modifier.size(56.dp), tint = Color(0xFF1E7A46))
            Text("All paid back", style = MaterialTheme.typography.titleMedium)
        }
    }
}
