package org.kealohalabs.receiptbeat.ui.onboarding

import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.kealohalabs.receiptbeat.R
import org.kealohalabs.receiptbeat.core.data.CatalogRepository
import org.kealohalabs.receiptbeat.core.data.SettingsRepository
import org.kealohalabs.receiptbeat.core.designsystem.Brand
import org.kealohalabs.receiptbeat.core.designsystem.ReceiptBeatTheme
import org.kealohalabs.receiptbeat.core.designsystem.ThemeMode
import org.kealohalabs.receiptbeat.core.model.Payer
import org.kealohalabs.receiptbeat.core.model.Track

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val catalog: CatalogRepository
) : ViewModel() {
    fun finish(work: Boolean, taxes: Boolean, household: Boolean, payer: String, lock: Boolean, done: () -> Unit) {
        viewModelScope.launch {
            if (payer.isNotBlank()) catalog.savePayer(Payer(name = payer.trim(), defaultTrack = Track.REIMBURSABLE))
            settings.update { it.copy(onboarded = true, useWork = work, useTaxes = taxes, useHousehold = household, appLock = lock) }
            done()
        }
    }
}

/** First launch only. Five short steps, all skippable except the first. No account to create. */
@Composable
fun OnboardingScreen(addEmail: () -> Unit, done: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    var step by rememberSaveable { mutableStateOf(0) }
    var work by rememberSaveable { mutableStateOf(true) }
    var taxes by rememberSaveable { mutableStateOf(false) }
    var household by rememberSaveable { mutableStateOf(false) }
    var payer by rememberSaveable { mutableStateOf("") }
    var lock by rememberSaveable { mutableStateOf(false) }
    var video by rememberSaveable { mutableStateOf(false) }
    val finish = { emailNext: Boolean ->
        vm.finish(work, taxes, household, if (work) payer else "", lock) { if (emailNext) addEmail() else done() }
    }
    BackHandler(step > 0) { step-- }

    // Onboarding always uses the dark brand look from the intro video.
    ReceiptBeatTheme(ThemeMode.DARK) {
        Box(Modifier.fillMaxSize().background(Brand.Teal).imePadding()) {
            AnimatedContent(step, label = "step") { st ->
                Column(
                    Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (st) {
                        0 -> {
                            Spacer(Modifier.height(48.dp))
                            Heartbeat(Modifier.size(160.dp))
                            Spacer(Modifier.height(24.dp))
                            Title("Receipt Beat")
                            Body(
                                "Stay on top of every receipt. Scan it, find it in your email, " +
                                    "and track it until you are paid back or it is claimed on your taxes."
                            )
                            Spacer(Modifier.height(16.dp))
                            TextButton({ video = true }) {
                                Icon(Icons.Rounded.PlayCircle, null, tint = Brand.Coral)
                                Spacer(Modifier.size(8.dp))
                                Text("Watch the intro", color = Brand.Paper)
                            }
                        }
                        1 -> {
                            Title("How will you use it?")
                            Body("Pick any. This sets your starting categories and where new receipts go.")
                            Spacer(Modifier.height(16.dp))
                            Choice("Work reimbursement", "Get paid back by an employer, client or board", work) { work = it }
                            Choice("Business taxes", "Freelance or small business deductions", taxes) { taxes = it }
                            Choice("Household records", "Medical, gifts, warranties and returns", household) { household = it }
                        }
                        2 -> {
                            Title("Who pays you back?")
                            Body("Add your first payer, like your employer or a board. You can add more later.")
                            Spacer(Modifier.height(16.dp))
                            OutlinedTextField(payer, {
                                payer = it
                            }, label = { Text("Payer name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                        3 -> {
                            Title("Find receipts in your email")
                            Body("Works with Gmail, Outlook, Yahoo, iCloud, Proton and more. Import an export file or connect the account.")
                            Spacer(Modifier.height(16.dp))
                            Card(
                                Icons.Rounded.Lock,
                                "Private by design",
                                "Your email is read on this phone. Nothing is sent to us or anyone else. Remote images are never loaded.",
                            )
                        }
                        else -> {
                            Title("Lock the app?")
                            Body("Receipts hold card digits and addresses. Use your fingerprint, face or PIN to open Receipt Beat.")
                            Spacer(Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text("App lock", Modifier.weight(1f), color = Brand.Paper, style = MaterialTheme.typography.titleMedium)
                                Switch(lock, { lock = it })
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f).height(32.dp))
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val coral = ButtonDefaults.buttonColors(containerColor = Brand.Coral, contentColor = Brand.Teal)
                        when (st) {
                            0 -> Button({ step = 1 }, Modifier.fillMaxWidth().height(56.dp), colors = coral) { Text("Get started") }
                            2 -> {
                                Button({
                                    step = 3
                                }, Modifier.fillMaxWidth().height(56.dp), colors = coral) { Text(if (payer.isBlank()) "Skip" else "Next") }
                            }
                            3 -> {
                                Button({ step = 4 }, Modifier.fillMaxWidth().height(56.dp), colors = coral) { Text("Next") }
                                TextButton({
                                    finish(true)
                                }, Modifier.fillMaxWidth()) { Text("Add email receipts now", color = Brand.Paper) }
                            }
                            4 -> Button({ finish(false) }, Modifier.fillMaxWidth().height(56.dp), colors = coral) { Text("Start scanning") }
                            else -> Button({
                                step = if (work) 2 else 3
                            }, Modifier.fillMaxWidth().height(56.dp), colors = coral) { Text("Next") }
                        }
                        Text(
                            "Step ${st + 1} of 5",
                            Modifier.fillMaxWidth(),
                            color = Brand.Paper.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            if (video) IntroVideo { video = false }
        }
    }
}

@Composable
private fun Title(t: String) = Text(t, style = MaterialTheme.typography.headlineMedium, color = Brand.Paper, textAlign = TextAlign.Center)

@Composable
private fun Body(t: String) = Text(
    t,
    style = MaterialTheme.typography.bodyLarge,
    color = Brand.Paper.copy(alpha = 0.85f),
    textAlign = TextAlign.Center,
    modifier = Modifier.padding(top = 8.dp),
)

@Composable
private fun Choice(title: String, sub: String, on: Boolean, set: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(Brand.TealSurface).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(on, set)
        Column {
            Text(title, color = Brand.Paper, style = MaterialTheme.typography.titleMedium)
            Text(sub, color = Brand.Paper.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Card(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brand.TealSurface).padding(16.dp)) {
        Icon(icon, null, tint = Brand.Coral)
        Spacer(Modifier.size(12.dp))
        Column {
            Text(title, color = Brand.Paper, style = MaterialTheme.typography.titleMedium)
            Text(body, color = Brand.Paper.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The logo's heartbeat line, drawn over and over across a cream receipt. */
@Composable
fun Heartbeat(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "beat")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "p")
    Canvas(modifier.semantics { contentDescription = "Receipt Beat logo" }) {
        val w = size.width
        val h = size.height
        drawRoundRect(Brand.TealSurface, cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.22f))
        val paper = Path().apply {
            moveTo(w * 0.30f, h * 0.20f)
            lineTo(w * 0.70f, h * 0.20f)
            lineTo(w * 0.70f, h * 0.78f)
            listOf(0.65f, 0.60f, 0.55f, 0.50f, 0.45f, 0.40f, 0.35f, 0.30f).forEachIndexed { i, x ->
                lineTo(w * x, if (i % 2 == 0) h * 0.74f else h * 0.78f)
            }
            close()
        }
        drawPath(paper, Brand.Paper)
        val line = Path().apply {
            moveTo(w * 0.12f, h * 0.50f)
            lineTo(w * 0.38f, h * 0.50f)
            lineTo(w * 0.44f, h * 0.38f)
            lineTo(w * 0.52f, h * 0.64f)
            lineTo(w * 0.58f, h * 0.44f)
            lineTo(w * 0.62f, h * 0.50f)
            lineTo(w * 0.88f, h * 0.50f)
        }
        clipRect(right = w * (0.1f + p * 0.9f)) {
            drawPath(line, Brand.Coral, style = Stroke(w * 0.045f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        drawCircle(Color.White, w * 0.018f, Offset(w * (0.1f + p * 0.78f).coerceAtMost(w * 0.88f), h * 0.50f))
    }
}

@Composable
private fun IntroVideo(close: () -> Unit) {
    BackHandler(onBack = close)
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(Uri.parse("android.resource://${ctx.packageName}/${R.raw.intro}"))
                    setOnCompletionListener { close() }
                    start()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        IconButton(close, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
            Icon(Icons.Rounded.Close, "Close video", tint = Color.White)
        }
    }
}
