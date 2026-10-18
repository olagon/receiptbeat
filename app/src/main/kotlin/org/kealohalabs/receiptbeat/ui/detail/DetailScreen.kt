package org.kealohalabs.receiptbeat.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import org.kealohalabs.receiptbeat.core.data.DuplicateAction
import org.kealohalabs.receiptbeat.core.data.Fields
import org.kealohalabs.receiptbeat.core.data.ReceiptRepository
import org.kealohalabs.receiptbeat.core.data.label
import org.kealohalabs.receiptbeat.core.designsystem.AppCard
import org.kealohalabs.receiptbeat.core.designsystem.LocalExtraColors
import org.kealohalabs.receiptbeat.core.designsystem.MoneyStyle
import org.kealohalabs.receiptbeat.core.designsystem.MoneyText
import org.kealohalabs.receiptbeat.core.designsystem.SectionHeader
import org.kealohalabs.receiptbeat.core.designsystem.Shimmer
import org.kealohalabs.receiptbeat.core.designsystem.StatusChip
import org.kealohalabs.receiptbeat.core.designsystem.look
import org.kealohalabs.receiptbeat.core.designsystem.statusColor
import org.kealohalabs.receiptbeat.core.model.Confidence
import org.kealohalabs.receiptbeat.core.model.FieldConfidence
import org.kealohalabs.receiptbeat.core.model.FileKind
import org.kealohalabs.receiptbeat.core.model.Money
import org.kealohalabs.receiptbeat.core.model.Receipt
import org.kealohalabs.receiptbeat.core.model.Status
import org.kealohalabs.receiptbeat.core.model.Track
import org.kealohalabs.receiptbeat.core.model.steps
import org.kealohalabs.receiptbeat.ui.common.CategoryDot
import org.kealohalabs.receiptbeat.ui.common.ReceiptImage
import org.kealohalabs.receiptbeat.ui.common.pretty

private sealed interface Editing {
    data class Text(val field: String, val title: String, val value: String, val number: Boolean) : Editing

    data object Date : Editing

    data object Category : Editing

    data object Payer : Editing

    data object Project : Editing

    data object Track : Editing

    data object Reject : Editing

    data object Paid : Editing
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    id: String,
    back: () -> Unit,
    share: (String) -> Unit,
    showBack: Boolean = true,
    vm: DetailViewModel = hiltViewModel<DetailViewModel, DetailViewModel.Factory>(key = id) { it.create(id) },
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val d = s.detail
    var editing by remember { mutableStateOf<Editing?>(null) }
    var highlight by remember { mutableStateOf<String?>(null) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(d?.receipt?.merchantName ?: "Receipt", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { if (showBack) IconButton(back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    if (d?.receipt?.locked == true) IconButton(vm::unlock) { Icon(Icons.Rounded.LockOpen, "Unlock receipt") }
                    IconButton({ share(vm.id) }) { Icon(Icons.Rounded.Share, "Share") }
                    IconButton({
                        vm.delete()
                        scope.launch {
                            if (snack.showSnackbar("Moved to trash", "Undo") == SnackbarResult.ActionPerformed) vm.restore() else back()
                        }
                    }) { Icon(Icons.Rounded.Delete, "Delete") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        if (d == null) {
            if (s.loaded) Text("This receipt is gone.", Modifier.padding(pad).padding(24.dp))
            return@Scaffold
        }
        val r = d.receipt
        val pages = d.files.filter { it.kind == FileKind.DISPLAY }.sortedBy { it.page }
        val box = highlight?.let { d.confidence[it]?.box }
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            ImagePager(pages.map { it.path }, box)
            Column(Modifier.padding(horizontal = 16.dp)) {
                if (r.duplicateOfId != null && s.duplicate != null) DuplicateCard(r, s.duplicate!!, s.duplicateThumb, vm::duplicate)
                if (r.locked) {
                    Text(
                        "Claimed for ${r.taxYear}. Locked so totals do not change by accident.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                Stepper(r, onTrack = { editing = Editing.Track }, onStatus = { st ->
                    when (st) {
                        Status.REIMBURSED -> editing = Editing.Paid
                        else -> vm.setStatus(st)
                    }
                })
                if (r.status == Status.SUBMITTED) {
                    TextButton({ editing = Editing.Reject }) { Text("Payer rejected it") }
                }
                if (r.status == Status.REJECTED && r.rejectReason != null) {
                    Text(
                        "Rejected: ${r.rejectReason}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                SectionHeader("Details")
                if (r.reading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Reading receipt", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(12.dp))
                        Shimmer(Modifier.width(120.dp).height(12.dp))
                    }
                }
                val alt = d.confidence[Fields.TOTAL_ALTERNATIVES]?.value?.split(",")?.mapNotNull { it.toLongOrNull() }.orEmpty()
                if (alt.size > 1 && !r.locked) {
                    AppCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Two possible totals. Which is right?", style = MaterialTheme.typography.titleSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                alt.distinct().forEach { c ->
                                    FilledTonalButton({
                                        vm.setAmount(Fields.TOTAL, Money.format(c, r.currency))
                                    }) { Text(Money.format(c, r.currency)) }
                                }
                            }
                        }
                    }
                }
                val edit: (String, String, String?, Boolean) -> Unit = { f, title, v, num ->
                    highlight = f
                    if (!r.locked) editing = Editing.Text(f, title, v ?: "", num)
                }
                FieldRow("Merchant", r.merchantName ?: "Add merchant", d.confidence[Fields.MERCHANT]) {
                    edit(Fields.MERCHANT, "Merchant", r.merchantName, false)
                }
                FieldRow("Date", r.purchaseDate.pretty(), d.confidence[Fields.DATE]) {
                    highlight = Fields.DATE
                    if (!r.locked) editing = Editing.Date
                }
                MoneyRow("Total", r.totalCents, r.currency, d.confidence[Fields.TOTAL], big = true, hide = s.hideAmounts) {
                    edit(Fields.TOTAL, "Total", r.totalCents?.let { Money.format(it, r.currency) }, true)
                }
                MoneyRow("Subtotal", r.subtotalCents, r.currency, d.confidence[Fields.SUBTOTAL], hide = s.hideAmounts) {
                    edit(Fields.SUBTOTAL, "Subtotal", r.subtotalCents?.let { Money.format(it, r.currency) }, true)
                }
                MoneyRow("Tax", r.taxCents, r.currency, d.confidence[Fields.TAX], hide = s.hideAmounts) {
                    edit(Fields.TAX, "Tax", r.taxCents?.let { Money.format(it, r.currency) }, true)
                }
                MoneyRow("Tip", r.tipCents, r.currency, d.confidence[Fields.TIP], hide = s.hideAmounts) {
                    edit(Fields.TIP, "Tip", r.tipCents?.let { Money.format(it, r.currency) }, true)
                }
                FieldRow("Currency", r.currency, d.confidence[Fields.CURRENCY]) {
                    edit(Fields.CURRENCY, "Currency code", r.currency, false)
                }
                val cat = s.categories.firstOrNull { it.id == r.categoryId }
                FieldRow("Category", cat?.name ?: "Pick a category", null, leading = { CategoryDot(cat, size = 24) }) {
                    if (!r.locked) editing = Editing.Category
                }
                FieldRow("Payer", s.payers.firstOrNull { it.id == r.payerId }?.name ?: "Nobody", null) {
                    if (!r.locked) {
                        editing =
                            Editing.Payer
                    }
                }
                FieldRow("Project or trip", s.projects.firstOrNull { it.id == r.projectId }?.name ?: "None", null) {
                    if (!r.locked) editing = Editing.Project
                }
                if (r.paymentLast4 !=
                    null
                ) {
                    FieldRow("Paid with", listOfNotNull(r.paymentBrand, "ending ${r.paymentLast4}").joinToString(" "), null) {}
                }
                if (r.receiptNumber != null) FieldRow("Receipt number", r.receiptNumber!!, null) {}
                if (r.reimbursedCents != null && r.track == Track.REIMBURSABLE) {
                    MoneyRow("Paid back", r.reimbursedCents, r.currency, null, hide = s.hideAmounts) {}
                }
                FieldRow("Notes", r.notes ?: "Add a note", null) { edit("notes", "Notes", r.notes, false) }

                if (d.lineItems.isNotEmpty()) {
                    SectionHeader("Items")
                    d.lineItems.forEach { li ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                li.name,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            MoneyText(li.totalCents, currency = r.currency, style = MoneyStyle.Small, hidden = s.hideAmounts)
                        }
                    }
                }

                SectionHeader("History")
                d.events.filter { !it.text.startsWith(ReceiptRepository.NOT_DUPLICATE) }.forEach { e ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text(
                            e.at.atZone(ZoneId.systemDefault()).format(HistoryFmt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(110.dp)
                        )
                        Text(e.text, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(48.dp))
            }
        }

        when (val e = editing) {
            is Editing.Text -> TextEditDialog(e.title, e.value, e.number, { editing = null }) { v ->
                when (e.field) {
                    Fields.MERCHANT -> vm.setMerchant(v)
                    Fields.CURRENCY -> vm.setCurrency(v)
                    "notes" -> vm.setNotes(v)
                    else -> vm.setAmount(e.field, v)
                }
                editing = null
            }
            Editing.Date -> DateDialog(r.purchaseDate, { editing = null }) {
                vm.setDate(it)
                editing = null
            }
            Editing.Category -> PickSheet(
                "Category",
                s.categories.map { it.id to it.name },
                r.categoryId,
                { editing = null }
            ) { vm.setCategory(it) }
            Editing.Payer -> PickSheet(
                "Who pays you back?",
                listOf<Pair<String?, String>>(null to "Nobody") + s.payers.map { it.id to it.name },
                r.payerId,
                {
                    editing = null
                }
            ) { id -> vm.setPayer(s.payers.firstOrNull { it.id == id }) }
            Editing.Project -> PickSheet(
                "Project or trip",
                listOf<Pair<String?, String>>(null to "None") + s.projects.map { it.id to it.name },
                r.projectId,
                {
                    editing = null
                }
            ) { vm.setProject(it) }
            Editing.Track -> PickSheet(
                "Where is it headed?",
                Track.entries.map {
                    it.name to it.label()
                },
                r.track.name,
                { editing = null }
            ) {
                vm.setTrack(Track.valueOf(it!!))
            }
            Editing.Reject -> TextEditDialog("Why was it rejected?", "", false, { editing = null }) {
                vm.setStatus(Status.REJECTED, it.ifBlank { null })
                editing = null
            }
            Editing.Paid -> TextEditDialog("How much was paid back?", r.totalCents?.let { Money.format(it, r.currency) } ?: "", true, {
                editing = null
            }) { v ->
                Money.parse(v)?.let(vm::markPaid)
                editing = null
            }
            null -> Unit
        }
    }
}

private val HistoryFmt = DateTimeFormatter.ofPattern("MMM d, h:mm a")

@Composable
private fun ImagePager(paths: List<String>, box: String?) {
    if (paths.isEmpty()) {
        Box(
            Modifier.fillMaxWidth().height(180.dp).background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            Text("No image", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val pager = rememberPagerState { paths.size }
    Box {
        HorizontalPager(pager, Modifier.fillMaxWidth().height(380.dp).background(Color(0xFF0B1F22))) { i ->
            ZoomImage(paths[i], if (i == 0) box else null)
        }
        if (paths.size > 1) {
            Text(
                "${pager.currentPage + 1} of ${paths.size}",
                Modifier.align(
                    Alignment.BottomCenter
                ).padding(8.dp).clip(CircleShape).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 2.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/** Pinch to zoom. When a field is tapped, its spot on the image is outlined. */
@Composable
private fun ZoomImage(path: String, box: String?) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val t = rememberTransformableState { z, pan, _ ->
        scale = (scale * z).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    BoxWithConstraints(Modifier.fillMaxSize().transformable(t), contentAlignment = Alignment.Center) {
        val loader = org.kealohalabs.receiptbeat.ui.common.LocalImageLoader.current
        val bmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, path) { value = loader.load(path) }
        val b = bmp ?: return@BoxWithConstraints
        val density = LocalDensity.current
        val fit = minOf(constraints.maxWidth.toFloat() / b.width, constraints.maxHeight.toFloat() / b.height)
        val w = with(density) { (b.width * fit).toDp() }
        val h = with(density) { (b.height * fit).toDp() }
        Box(Modifier.size(w, h).graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)) {
            ReceiptImage(path, Modifier.fillMaxSize(), ContentScale.Fit, "Receipt image")
            val parts = box?.split(",")?.mapNotNull { it.toIntOrNull() }
            if (parts?.size == 4) {
                val (x, y, bw, bh) = parts
                with(density) {
                    Box(
                        Modifier.offset((x * fit).toDp() - 4.dp, (y * fit).toDp() - 4.dp)
                            .size((bw * fit).toDp() + 8.dp, (bh * fit).toDp() + 8.dp)
                            .border(2.dp, LocalExtraColors.current.capture, RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun Stepper(r: Receipt, onTrack: () -> Unit, onStatus: (Status) -> Unit) {
    val steps = r.track.steps()
    val current = steps.indexOf(r.status).let { if (it < 0) steps.indexOf(Status.READY) else it }
    Column(Modifier.padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.track.label(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onTrack, enabled = !r.locked) { Text("Change") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            steps.forEachIndexed { i, st ->
                val done = i <= current
                val color = if (done) statusColor(st) else MaterialTheme.colorScheme.outlineVariant
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(enabled = !r.locked && i != current) {
                        onStatus(st)
                    }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(
                            28.dp
                        ).clip(
                            CircleShape
                        ).background(if (done) color.copy(alpha = 0.18f) else Color.Transparent).border(2.dp, color, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(st.look().icon, null, Modifier.size(16.dp), tint = color)
                    }
                    Text(
                        if (i == current && r.status !in steps) r.status.label() else st.label(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (i < steps.lastIndex) {
                    Box(Modifier.weight(0.4f).height(2.dp).background(if (i < current) color else MaterialTheme.colorScheme.outlineVariant))
                }
            }
        }
        Row(Modifier.padding(top = 4.dp)) { StatusChip(r.status) }
    }
}

@Composable
private fun FieldRow(label: String, value: String, conf: FieldConfidence?, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val low = conf?.confidence == Confidence.LOW
    val glow = LocalExtraColors.current.glow
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp))
            .background(if (low) glow else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                label + if (low) "  ·  check this" else "",
                style = MaterialTheme.typography.labelMedium,
                color = if (low) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun MoneyRow(
    label: String,
    cents: Long?,
    currency: String,
    conf: FieldConfidence?,
    big: Boolean = false,
    hide: Boolean,
    onClick: () -> Unit
) {
    val low = conf?.confidence == Confidence.LOW
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp))
            .background(if (low) LocalExtraColors.current.glow else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label + if (low) "  ·  check this" else "",
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = if (low) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        MoneyText(cents, currency = currency, style = if (big) MoneyStyle.Large else MoneyStyle.Row, hidden = hide)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun DuplicateCard(r: Receipt, other: Receipt, otherThumb: String?, act: (DuplicateAction) -> Unit) {
    AppCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("Looks like a duplicate of this one", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ReceiptImage(otherThumb, Modifier.size(56.dp).aspectRatio(1f).clip(RoundedCornerShape(10.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(other.merchantName ?: "Receipt", style = MaterialTheme.typography.bodyLarge)
                    Text(other.purchaseDate.pretty() + " · " + other.status.label(), style = MaterialTheme.typography.bodySmall)
                }
                MoneyText(other.totalCents, currency = other.currency)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ act(DuplicateAction.KEEP_BOTH) }) { Text("Keep both") }
                OutlinedButton({ act(DuplicateAction.MERGE) }) { Text("Merge") }
                OutlinedButton({ act(DuplicateAction.DELETE_THIS) }) { Text("Delete this") }
            }
            if (r.source != other.source) {
                Text(
                    "One came from email and one from paper.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TextEditDialog(title: String, initial: String, number: Boolean, dismiss: () -> Unit, save: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                v,
                { v = it },
                singleLine = !title.startsWith("Notes"),
                keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton({ save(v) }) { Text("Save") } },
        dismissButton = { TextButton(dismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDialog(initial: LocalDate?, dismiss: () -> Unit, save: (LocalDate?) -> Unit) {
    val st = rememberDatePickerState(initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
    DatePickerDialog(
        onDismissRequest = dismiss,
        confirmButton = {
            TextButton({
                save(st.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() })
            }) { Text("Save") }
        },
        dismissButton = { TextButton(dismiss) { Text("Cancel") } }
    ) { DatePicker(st) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <K> PickSheet(title: String, options: List<Pair<K, String>>, current: K, dismiss: () -> Unit, pick: (K) -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            options.forEach { (k, label) ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        pick(k)
                        dismiss()
                    }.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    if (k ==
                        current
                    ) {
                        Text("Selected", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
