package org.kealohalabs.receiptbeat.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.kealohalabs.receiptbeat.core.designsystem.LocalExtraColors
import org.kealohalabs.receiptbeat.core.designsystem.MoneyStyle
import org.kealohalabs.receiptbeat.core.designsystem.MoneyText
import org.kealohalabs.receiptbeat.core.designsystem.Shimmer
import org.kealohalabs.receiptbeat.core.designsystem.StatusChip
import org.kealohalabs.receiptbeat.core.model.Category
import org.kealohalabs.receiptbeat.core.model.Money
import org.kealohalabs.receiptbeat.core.model.Receipt
import org.kealohalabs.receiptbeat.core.model.Source

fun categoryIcon(name: String?): ImageVector = when (name) {
    "restaurant" -> Icons.Rounded.Restaurant
    "flight" -> Icons.Rounded.Flight
    "hotel" -> Icons.Rounded.Hotel
    "car" -> Icons.Rounded.DirectionsCar
    "pencil" -> Icons.Rounded.Edit
    "cloud" -> Icons.Rounded.Cloud
    "phone" -> Icons.Rounded.PhoneAndroid
    "laptop" -> Icons.Rounded.Laptop
    "briefcase" -> Icons.Rounded.BusinessCenter
    "school" -> Icons.Rounded.School
    "home" -> Icons.Rounded.Home
    "medical" -> Icons.Rounded.LocalHospital
    "heart" -> Icons.Rounded.Favorite
    "gift" -> Icons.Rounded.CardGiftcard
    "bag" -> Icons.Rounded.ShoppingBag
    "money" -> Icons.Rounded.AttachMoney
    "book" -> Icons.Rounded.Book
    else -> Icons.Rounded.Sell
}

val CategoryIconNames = listOf(
    "restaurant", "flight", "hotel", "car", "pencil", "cloud", "phone", "laptop", "briefcase", "school", "home", "medical", "heart",
    "gift", "bag", "money", "book", "tag"
)

private val DayFmt = DateTimeFormatter.ofPattern("MMM d, yyyy")

fun LocalDate?.pretty(): String = this?.format(DayFmt) ?: "No date"

@Composable
fun CategoryDot(category: Category?, modifier: Modifier = Modifier, size: Int = 28) {
    val color = category?.let { Color(it.color) } ?: MaterialTheme.colorScheme.outline
    Box(modifier.size(size.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
        Icon(categoryIcon(category?.icon), category?.name, Modifier.size((size * 0.6).dp), tint = color)
    }
}

/** The receipt card used in every list: thumbnail, merchant, date, category, big amount, status. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReceiptCard(
    r: Receipt,
    thumb: String?,
    category: Category?,
    modifier: Modifier = Modifier,
    lowConfidence: Boolean = false,
    selected: Boolean = false,
    hideAmounts: Boolean = false,
    payerName: String? = null,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null
) {
    val glow = LocalExtraColors.current.glow
    val border = when {
        selected -> MaterialTheme.colorScheme.primary
        lowConfidence || r.duplicateOfId != null -> glow.copy(alpha = 1f)
        else -> Color.Transparent
    }
    val label = buildString {
        append(r.merchantName ?: "Receipt")
        append(", ")
        append(r.purchaseDate.pretty())
        r.totalCents?.let { append(", ").append(Money.spoken(it, r.currency)) }
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
            .border(if (border == Color.Transparent) 0.dp else 1.5.dp, border, RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(12.dp)
            .semantics(mergeDescendants = true) { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            ReceiptImage(thumb, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)))
            if (selected) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary) }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (r.reading) {
                Text("Reading receipt", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Shimmer(Modifier.width(120.dp).height(10.dp))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        r.merchantName ?: "Unknown merchant",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (r.source == Source.EMAIL_FILE || r.source == Source.EMAIL_LIVE) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Rounded.Mail, "From email", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryDot(category, size = 18)
                    Text(
                        listOfNotNull(r.purchaseDate.pretty(), payerName).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (lowConfidence || r.duplicateOfId != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (r.duplicateOfId != null) Icons.Rounded.ContentCopy else Icons.Rounded.PriorityHigh,
                            null,
                            Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (r.duplicateOfId != null) "Possible duplicate" else "Check this one",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            if (r.reading) {
                Shimmer(Modifier.width(60.dp).height(18.dp))
            } else {
                MoneyText(r.totalCents, currency = r.currency, style = MoneyStyle.Row, hidden = hideAmounts)
            }
            Spacer(Modifier.height(6.dp))
            StatusChip(r.status)
        }
    }
}
