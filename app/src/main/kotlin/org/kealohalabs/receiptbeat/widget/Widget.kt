package org.kealohalabs.receiptbeat.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import org.kealohalabs.receiptbeat.MainActivity
import org.kealohalabs.receiptbeat.R
import org.kealohalabs.receiptbeat.core.data.ReceiptRepository
import org.kealohalabs.receiptbeat.core.data.SettingsRepository
import org.kealohalabs.receiptbeat.core.model.Money

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntry {
    fun receipts(): ReceiptRepository

    fun settings(): SettingsRepository
}

private val Teal = Color(0xFF093A40)
private val Coral = Color(0xFFFE6B49)
private val Paper = Color(0xFFFEF7EC)

private fun open(context: Context, route: String) = Intent(context, MainActivity::class.java).putExtra("route", route).addFlags(
    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
)

/** Small: a capture button. Medium: also what you are owed and the Inbox count. */
class ReceiptWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(60.dp, 60.dp), DpSize(200.dp, 100.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = EntryPointAccessors.fromApplication(context, WidgetEntry::class.java)
        val dash = entry.receipts().dashboard().first()
        val hide = entry.settings().current().hideAmounts
        provideContent { Content(context, if (hide) "•••" else Money.format(dash.owedCents), dash.inboxCount) }
    }

    @Composable
    private fun Content(context: Context, owed: String, inbox: Int) {
        val wide = LocalSize.current.width >= 180.dp
        Row(
            GlanceModifier.fillMaxSize().background(Teal).cornerRadius(24.dp).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally,
        ) {
            if (wide) {
                Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(open(context, "owed")))) {
                    Text("Owed to you", style = TextStyle(color = ColorProvider(Paper, Paper), fontSize = 12.sp))
                    Text(owed, style = TextStyle(color = ColorProvider(Paper, Paper), fontSize = 24.sp, fontWeight = FontWeight.Bold))
                    Text(
                        if (inbox == 1) "1 in Inbox" else "$inbox in Inbox",
                        style = TextStyle(color = ColorProvider(Coral, Coral), fontSize = 12.sp),
                        modifier = GlanceModifier.clickable(actionStartActivity(open(context, "inbox"))),
                    )
                }
                Spacer(GlanceModifier.width(8.dp))
            }
            Box(
                GlanceModifier.size(56.dp).background(Coral).cornerRadius(18.dp).clickable(actionStartActivity(open(context, "capture"))),
                contentAlignment = Alignment.Center,
            ) {
                Image(ImageProvider(R.drawable.ic_widget_scan), "Scan a receipt", GlanceModifier.size(30.dp))
            }
        }
    }
}

class ReceiptWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ReceiptWidget()
}

/** Quick Settings tile that opens the scanner. */
class ScanTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = open(this, "capture")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
