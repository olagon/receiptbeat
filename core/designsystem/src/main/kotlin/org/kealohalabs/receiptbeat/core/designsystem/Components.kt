package org.kealohalabs.receiptbeat.core.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.kealohalabs.receiptbeat.core.model.Money
import org.kealohalabs.receiptbeat.core.model.Status

data class StatusLook(val label: String, val icon: ImageVector, val light: Color, val dark: Color)

/** Every status has a color and an icon, so it never relies on color alone. */
fun Status.look(): StatusLook = when (this) {
    Status.INBOX -> StatusLook("Inbox", Icons.Rounded.Inbox, Color(0xFF5F6B6D), Color(0xFFB5C2C4))
    Status.READY -> StatusLook("Ready", Icons.Rounded.CheckCircleOutline, Color(0xFF1F5FBF), Color(0xFF9CC3FF))
    Status.SUBMITTED -> StatusLook("Submitted", Icons.AutoMirrored.Rounded.Send, Color(0xFF8A5A00), Color(0xFFF7C04A))
    Status.REIMBURSED -> StatusLook("Reimbursed", Icons.Rounded.CheckCircle, Color(0xFF1E7A46), Color(0xFF5DD39E))
    Status.PARTLY_REIMBURSED -> StatusLook("Partly paid", Icons.Rounded.Timelapse, Color(0xFF1E7A46), Color(0xFF5DD39E))
    Status.REJECTED -> StatusLook("Rejected", Icons.Rounded.ErrorOutline, Color(0xFFB3261E), Color(0xFFFF8A80))
    Status.CLAIMED -> StatusLook("Claimed", Icons.Rounded.Lock, Color(0xFF6B3FA0), Color(0xFFC9A7F5))
    Status.ARCHIVED -> StatusLook("Archived", Icons.Rounded.Archive, Color(0xFF6E7476), Color(0xFF9AA3A5))
}

@Composable
fun statusColor(status: Status): Color = status.look().let { if (LocalExtraColors.current.isDark) it.dark else it.light }

@Composable
fun StatusChip(status: Status, modifier: Modifier = Modifier) {
    val look = status.look()
    val color = statusColor(status)
    Row(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics { contentDescription = "Status ${look.label}" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(look.icon, null, Modifier.size(14.dp), tint = color)
        Spacer(Modifier.width(4.dp))
        Text(look.label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** An amount, read out loud as money by TalkBack. */
@Composable
fun MoneyText(
    cents: Long?,
    modifier: Modifier = Modifier,
    currency: String = "USD",
    style: TextStyle = MoneyStyle.Row,
    color: Color = LocalExtraColors.current.money,
    hidden: Boolean = false,
    emptyText: String = "No total"
) {
    val text = when {
        cents == null -> emptyText
        hidden -> "•••"
        else -> Money.format(cents, currency)
    }
    val spoken = when {
        cents == null -> "No amount"
        hidden -> "Amount hidden"
        else -> Money.spoken(cents, currency)
    }
    Text(text, modifier.semantics { contentDescription = spoken }, style = style, color = color, maxLines = 1)
}

@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    val shape = MaterialTheme.shapes.large
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors) { content() }
    } else {
        Card(modifier = modifier, shape = shape, colors = colors) { content() }
    }
}

/** Soft pulsing block shown while a receipt is being read. */
@Composable
fun Shimmer(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val a by t.animateFloat(0.25f, 0.6f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    Box(modifier.alpha(a).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)))
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        trailing?.invoke()
    }
}

/** A money tile for the Home dashboard. */
@Composable
fun MoneyTile(
    label: String,
    cents: Long,
    modifier: Modifier = Modifier,
    note: String? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    hidden: Boolean = false,
    onClick: () -> Unit = {}
) {
    AppCard(modifier, onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Box(Modifier.size(width = 24.dp, height = 4.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.height(10.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MoneyText(cents, style = MoneyStyle.Large, hidden = hidden)
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Surface(modifier, shape = CircleShape, color = color.copy(alpha = 0.14f)) {
        Text(
            text,
            Modifier.padding(PaddingValues(horizontal = 10.dp, vertical = 4.dp)),
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}
