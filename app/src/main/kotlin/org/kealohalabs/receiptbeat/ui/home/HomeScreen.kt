package org.kealohalabs.receiptbeat.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalTime
import org.kealohalabs.receiptbeat.core.designsystem.AppCard
import org.kealohalabs.receiptbeat.core.designsystem.Brand
import org.kealohalabs.receiptbeat.core.designsystem.EmptyState
import org.kealohalabs.receiptbeat.core.designsystem.MoneyStyle
import org.kealohalabs.receiptbeat.core.designsystem.MoneyText
import org.kealohalabs.receiptbeat.core.designsystem.MoneyTile
import org.kealohalabs.receiptbeat.core.designsystem.SectionHeader
import org.kealohalabs.receiptbeat.ui.common.ReceiptCard

@Composable
fun HomeScreen(
    openList: (String?) -> Unit,
    openReceipt: (String) -> Unit,
    openSettings: () -> Unit,
    openReview: () -> Unit,
    openMoney: () -> Unit,
    vm: HomeViewModel = hiltViewModel()
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val d = s.dash
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        greeting() + if (s.name.isNotBlank()) ", ${s.name.substringBefore(' ')}" else "",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        "Stay on top of every receipt.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(openSettings) { Icon(Icons.Rounded.AccountCircle, "Settings", Modifier.height(32.dp).width(32.dp)) }
            }
        }
        item {
            // The hero: what you are owed.
            AppCard(Modifier.fillMaxWidth(), onClick = { openList("owed") }) {
                Column(Modifier.padding(20.dp)) {
                    Text("Owed to you", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyText(d?.owedCents ?: 0, style = MoneyStyle.Hero, hidden = s.hideAmounts)
                    val payers = d?.owedByPayer?.entries?.sortedByDescending { it.value }?.take(3).orEmpty()
                    if (payers.isEmpty()) {
                        Text(
                            "Nothing waiting. Nice.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    payers.forEach { (pid, cents) ->
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Text(s.payers[pid]?.name ?: "No payer", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            MoneyText(cents, style = MoneyStyle.Small, hidden = s.hideAmounts)
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyTile(
                    "Ready to submit",
                    d?.readyCents ?: 0,
                    Modifier.weight(1f),
                    note = "${d?.readyCount ?: 0} receipts",
                    accent = MaterialTheme.colorScheme.primary,
                    hidden = s.hideAmounts
                ) { openList("ready") }
                MoneyTile(
                    "Deductible this year",
                    d?.deductibleCents ?: 0,
                    Modifier.weight(1f),
                    note = "After limits",
                    accent = Brand.Coral,
                    hidden = s.hideAmounts
                ) { openList("deductible") }
            }
        }
        val attention = listOfNotNull(
            (d?.inboxCount ?: 0).takeIf { it > 0 }?.let { Triple("$it in your Inbox", "Give them a quick look") { openList("inbox") } },
            (d?.lowConfidenceCount ?: 0).takeIf {
                it > 0
            }?.let { Triple("$it need a check", "Some fields were hard to read") { openList("attention") } },
            s.reviewCount.takeIf { it > 0 }?.let { Triple("$it found in email", "Keep or skip them") { openReview() } },
            s.overdueReports.takeIf { it > 0 }?.let { Triple("$it reports unpaid", "Time to follow up") { openMoney() } }
        )
        if (attention.isNotEmpty()) {
            item { SectionHeader("Needs your attention") }
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column {
                        attention.forEachIndexed { i, (title, sub, go) ->
                            ListItem(
                                headlineContent = { Text(title) },
                                supportingContent = { Text(sub) },
                                trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                modifier = Modifier.clickable(onClick = go)
                            )
                            if (i < attention.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
        }
        item {
            SectionHeader("Recent") { if (s.recent.isNotEmpty()) TextButton({ openList(null) }) { Text("See all") } }
        }
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

private fun greeting(): String = when (LocalTime.now().hour) {
    in 4..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}
