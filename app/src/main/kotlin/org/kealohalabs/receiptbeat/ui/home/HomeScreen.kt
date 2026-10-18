package org.kealohalabs.receiptbeat.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import org.kealohalabs.receiptbeat.core.data.DashboardView
import org.kealohalabs.receiptbeat.core.data.Period
import org.kealohalabs.receiptbeat.core.designsystem.AppCard
import org.kealohalabs.receiptbeat.core.designsystem.EmptyState
import org.kealohalabs.receiptbeat.core.designsystem.LocalExtraColors
import org.kealohalabs.receiptbeat.core.designsystem.MoneyStyle
import org.kealohalabs.receiptbeat.core.designsystem.MoneyText
import org.kealohalabs.receiptbeat.core.designsystem.SectionHeader
import org.kealohalabs.receiptbeat.core.model.Money
import org.kealohalabs.receiptbeat.ui.common.CategoryDot
import org.kealohalabs.receiptbeat.ui.common.ReceiptCard

/** Money flow colors, checked for color blindness in light and dark. Always shown with an icon and a label. */
private object FlowColor {
    @Composable fun toSubmit() = if (LocalExtraColors.current.isDark) Color(0xFF5B93E8) else Color(0xFF1F5FBF)

    @Composable fun waiting() = if (LocalExtraColors.current.isDark) Color(0xFFC4880F) else Color(0xFFB07400)

    @Composable fun paid() = if (LocalExtraColors.current.isDark) Color(0xFF2E9E6B) else Color(0xFF1E7A46)
}

private val MonthShort = DateTimeFormatter.ofPattern("MMM")
private val MonthLong = DateTimeFormatter.ofPattern("MMMM yyyy")

@Composable
fun HomeScreen(
    openList: (String?) -> Unit,
    openReceipt: (String) -> Unit,
    openSettings: () -> Unit,
    openReview: () -> Unit,
    openMoney: () -> Unit,
    openTrips: () -> Unit = {},
    vm: HomeViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val v = s.view
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        greeting() + if (s.name.isNotBlank()) ", ${s.name.substringBefore(' ')}" else "",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        "Stay on top of every receipt.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(openSettings) { Icon(Icons.Rounded.AccountCircle, "Settings", Modifier.size(32.dp)) }
            }
        }
        if (v == null) return@LazyColumn
        item { OwedCard(v, s, openList, openMoney) }
        item { PeriodPicker(v.period, vm::setPeriod) }
        item { PeriodTiles(v, s, openList) }
        attention(s, openList, openReview, openMoney)?.let { rows -> item { Attention(rows) } }
        if (v.byMonth.any { it.cents > 0 }) item { MonthChart(v, s, openList) }
        if (v.byCategory.isNotEmpty()) item { CategoryChart(v, s, openList) }
        item { Trips(s, openTrips, openList) }
        item { SectionHeader("Recent") { if (s.recent.isNotEmpty()) TextButton({ openList(null) }) { Text("See all") } } }
        if (s.recent.isEmpty()) {
            item { EmptyState("No receipts yet", "Tap the coral button to scan your first receipt. It takes about 3 seconds.") }
        }
        items(s.recent, key = { it.id }) { r ->
            ReceiptCard(
                r,
                s.thumbs[r.id],
                s.categories[r.categoryId],
                hideAmounts = s.hideAmounts,
                payerName = s.payers[r.payerId]?.name,
                onClick = { openReceipt(r.id) },
            )
        }
    }
}

/**
 * The hero. Owed to you is a running balance: money spent for someone else and not yet back,
 * submitted or not. The bar splits it into what still needs sending and what is waiting on payment.
 */
@Composable
private fun OwedCard(v: DashboardView, s: HomeState, openList: (String?) -> Unit, openMoney: () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("Owed to you", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MoneyText(v.owedCents, style = MoneyStyle.Hero, currency = s.currency, hidden = s.hideAmounts)
            if (v.owedCents == 0L) {
                Text(
                    "All caught up. Nothing to submit or wait on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            Spacer(Modifier.height(12.dp))
            SplitBar(listOf(v.toSubmitCents to FlowColor.toSubmit(), v.waitingCents to FlowColor.waiting()))
            Spacer(Modifier.height(8.dp))
            MoneyFlowRow(Icons.Rounded.CheckCircleOutline, FlowColor.toSubmit(), "To submit", v.toSubmitCents, v.toSubmitCount, s) {
                openList("ready")
            }
            MoneyFlowRow(Icons.AutoMirrored.Rounded.Send, FlowColor.waiting(), "Waiting on payment", v.waitingCents, v.waitingCount, s) {
                openList("owed")
            }
            val payers = v.byPayer.take(3)
            if (payers.size > 1 || payers.firstOrNull()?.payerId != null) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                payers.forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            s.payers[p.payerId]?.name ?: "No payer yet",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        MoneyText(p.toSubmitCents + p.waitingCents, currency = s.currency, style = MoneyStyle.Small, hidden = s.hideAmounts)
                    }
                }
            }
            if (v.toSubmitCents > 0) {
                FilledTonalButton(
                    openMoney,
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Text(if (s.hideAmounts) "Send a report" else "Send a report for ${Money.format(v.toSubmitCents, s.currency)}")
                }
            }
        }
    }
}

/** Stacked bar with a 2dp gap between parts and rounded ends. */
@Composable
private fun SplitBar(parts: List<Pair<Long, Color>>) {
    val total = parts.sumOf { it.first }.coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(Modifier.fillMaxWidth().height(12.dp)) {
        drawRoundRect(track, cornerRadius = CornerRadius(6.dp.toPx()))
        val gap = 2.dp.toPx()
        var x = 0f
        val shown = parts.filter { it.first > 0 }
        shown.forEachIndexed { i, (cents, color) ->
            val w = size.width * cents / total - if (i < shown.lastIndex) gap else 0f
            drawRoundRect(color, Offset(x, 0f), Size(w.coerceAtLeast(2f), size.height), CornerRadius(4.dp.toPx()))
            x += w + gap
        }
    }
}

@Composable
private fun MoneyFlowRow(icon: ImageVector, color: Color, label: String, cents: Long, count: Int, s: HomeState, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = color)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(plural(count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MoneyText(cents, currency = s.currency, style = MoneyStyle.Row, hidden = s.hideAmounts)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PeriodPicker(current: Period, set: (Period) -> Unit) {
    val items = listOf(Period.MONTH to "Month", Period.QUARTER to "Quarter", Period.YEAR to "Year", Period.ALL to "All")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        items.forEachIndexed { i, (p, label) ->
            SegmentedButton(current == p, {
                set(p)
            }, SegmentedButtonDefaults.itemShape(i, items.size), icon = {}) { Text(label, maxLines = 1) }
        }
    }
}

/** What happened in the chosen period: money that came back, money spent, and what is deductible. */
@Composable
private fun PeriodTiles(v: DashboardView, s: HomeState, openList: (String?) -> Unit) {
    val from = v.period.start(LocalDate.now())
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(
                Icons.Rounded.CheckCircle,
                FlowColor.paid(),
                "Paid back",
                v.paidBackCents,
                plural(v.paidBackCount),
                s,
                Modifier.weight(1f)
            ) {
                openList("paid")
            }
            val delta = v.previousSpentCents?.takeIf { it > 0 }?.let { prev ->
                val d = v.spentCents - prev
                (if (d >= 0) "Up " else "Down ") + if (s.hideAmounts) "" else Money.format(abs(d), s.currency)
            }
            Tile(
                Icons.Rounded.ShoppingBag,
                MaterialTheme.colorScheme.primary,
                "Spent",
                v.spentCents,
                delta ?: plural(v.spentCount),
                s,
                Modifier.weight(1f)
            ) {
                openList(from?.let { "from:$it" })
            }
            Tile(
                Icons.Rounded.Savings,
                LocalExtraColors.current.capture,
                "Deductible",
                v.deductibleCents,
                "After limits",
                s,
                Modifier.weight(1f)
            ) {
                openList("deductible")
            }
        }
        Text(
            "${v.period.label}. Owed to you is your balance today.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, start = 4.dp),
        )
    }
}

@Composable
private fun Tile(
    icon: ImageVector,
    color: Color,
    label: String,
    cents: Long,
    note: String,
    s: HomeState,
    modifier: Modifier,
    onClick: () -> Unit
) {
    AppCard(modifier, onClick = onClick) {
        Column(Modifier.padding(12.dp)) {
            Icon(icon, null, Modifier.size(20.dp), tint = color)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            MoneyText(cents, currency = s.currency, style = MoneyStyle.Row, hidden = s.hideAmounts)
            Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

private fun plural(n: Int) = if (n == 1) "1 receipt" else "$n receipts"

private fun attention(s: HomeState, openList: (String?) -> Unit, openReview: () -> Unit, openMoney: () -> Unit) = listOfNotNull(
    s.inboxCount.takeIf { it > 0 }?.let { Triple("$it in your Inbox", "Give them a quick look") { openList("inbox") } },
    s.lowCount.takeIf { it > 0 }?.let { Triple("$it need a check", "Some fields were hard to read") { openList("attention") } },
    if (s.scanning) Triple("Checking email now", "Tap to see progress") { openReview() } else null,
    s.reviewCount.takeIf { it > 0 }?.let { Triple("$it found in email", "Keep or skip them") { openReview() } },
    s.overdueReports.takeIf { it > 0 }?.let { Triple("$it reports unpaid", "Time to follow up") { openMoney() } },
).takeIf { it.isNotEmpty() }

@Composable
private fun Attention(rows: List<Triple<String, String, () -> Unit>>) {
    Column {
        SectionHeader("Needs your attention")
        AppCard(Modifier.fillMaxWidth()) {
            Column {
                rows.forEachIndexed { i, (title, sub, go) ->
                    ListItem(
                        headlineContent = { Text(title) },
                        supportingContent = { Text(sub) },
                        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.clickable(onClick = go),
                    )
                    if (i < rows.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}

/** Spending by month: one series, one hue. Tap a bar to read its month; tap it again to open those receipts. */
@Composable
private fun MonthChart(v: DashboardView, s: HomeState, openList: (String?) -> Unit) {
    val months = v.byMonth
    var picked by remember(months) { mutableIntStateOf(months.lastIndex) }
    val bar = MaterialTheme.colorScheme.primary
    val base = MaterialTheme.colorScheme.outlineVariant
    val max = months.maxOf { it.cents }.coerceAtLeast(1)
    val summary = months.joinToString(". ") { "${it.month.format(MonthLong)}, ${Money.spoken(it.cents, s.currency)}" }
    Column {
        SectionHeader("Spending by month")
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                val m = months[picked.coerceIn(0, months.lastIndex)]
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        m.month.format(MonthLong),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    MoneyText(m.cents, currency = s.currency, style = MoneyStyle.Row, hidden = s.hideAmounts)
                }
                Spacer(Modifier.height(12.dp))
                Canvas(
                    Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = "Spending by month. $summary" }
                        .pointerInput(months) {
                            detectTapGestures { o ->
                                val i = (o.x / (size.width / months.size)).toInt().coerceIn(0, months.lastIndex)
                                if (i == picked) openList("month:${months[i].month}") else picked = i
                            }
                        },
                ) {
                    val slot = size.width / months.size
                    val w = (slot * 0.56f).coerceAtMost(28.dp.toPx())
                    val r = 4.dp.toPx()
                    months.forEachIndexed { i, ms ->
                        val h = (size.height - 2.dp.toPx()) * ms.cents / max
                        if (h <= 0f) return@forEachIndexed
                        val x = i * slot + (slot - w) / 2
                        val c = bar.copy(alpha = if (i == picked) 1f else 0.45f)
                        // Rounded at the top, square on the baseline: draw past the baseline and clip it off.
                        clipRect(bottom = size.height) {
                            drawRoundRect(c, Offset(x, size.height - h), Size(w, h + r), CornerRadius(r))
                        }
                    }
                    drawLine(base, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    months.forEachIndexed { i, ms ->
                        // Every month when there are few, else every third so labels never collide.
                        val show = months.size <= 6 || i % 3 == 0 || i == months.lastIndex
                        Text(
                            if (show) ms.month.format(MonthShort) else "",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (i == picked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Top categories in the period as ranked bars. The category icon carries identity, the bar carries size. */
@Composable
private fun CategoryChart(v: DashboardView, s: HomeState, openList: (String?) -> Unit) {
    val top = v.byCategory.take(5)
    val other = v.byCategory.drop(5).sumOf { it.cents }
    val max = (top.maxOfOrNull { it.cents } ?: 1).coerceAtLeast(1)
    val bar = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Column {
        SectionHeader("Where it went")
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                top.forEach { c ->
                    val cat = s.categories[c.categoryId]
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                            openList("category:${c.categoryId.orEmpty()}")
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryDot(cat, size = 22)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                cat?.name ?: "No category",
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            MoneyText(c.cents, currency = s.currency, style = MoneyStyle.Small, hidden = s.hideAmounts)
                        }
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(track)) {
                            Box(Modifier.fillMaxWidth(c.cents.toFloat() / max).height(6.dp).clip(CircleShape).background(bar))
                        }
                    }
                }
                if (other > 0) {
                    Row {
                        Text(
                            "Everything else",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MoneyText(
                            other,
                            currency = s.currency,
                            style = MoneyStyle.Small,
                            hidden = s.hideAmounts,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 4..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

/** The latest trips, or a nudge to make one. */
@Composable
private fun Trips(s: HomeState, openTrips: () -> Unit, openList: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Trips") { TextButton(openTrips) { Text(if (s.tripCount == 0) "Add a trip" else "See all") } }
        if (s.trips.isEmpty()) {
            AppCard(Modifier.fillMaxWidth(), onClick = openTrips) {
                Text(
                    "Group receipts by trip or project, then share one PDF with all of them.",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        s.trips.forEach { t ->
            AppCard(Modifier.fillMaxWidth(), onClick = { openList("project:${t.project.id}") }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.project.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (t.count == 1) "1 receipt" else "${t.count} receipts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MoneyText(t.totalCents, currency = s.currency, style = MoneyStyle.Small, hidden = s.hideAmounts)
                }
            }
        }
    }
}
