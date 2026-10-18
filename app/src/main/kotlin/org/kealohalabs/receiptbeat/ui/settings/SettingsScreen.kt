package org.kealohalabs.receiptbeat.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.kealohalabs.receiptbeat.BuildConfig
import org.kealohalabs.receiptbeat.core.data.AiProvider
import org.kealohalabs.receiptbeat.core.data.label
import org.kealohalabs.receiptbeat.core.designsystem.ThemeMode
import org.kealohalabs.receiptbeat.core.model.Category
import org.kealohalabs.receiptbeat.core.model.Payer
import org.kealohalabs.receiptbeat.core.model.Project
import org.kealohalabs.receiptbeat.core.model.Track
import org.kealohalabs.receiptbeat.ui.common.CategoryDot
import org.kealohalabs.receiptbeat.ui.common.CategoryIconNames
import org.kealohalabs.receiptbeat.ui.common.categoryIcon
import org.kealohalabs.receiptbeat.ui.common.pretty
import org.kealohalabs.receiptbeat.ui.detail.PickSheet
import org.kealohalabs.receiptbeat.ui.detail.TextEditDialog

enum class Page(val title: String) {
    MAIN("Settings"),
    CATEGORIES("Categories and tax"),
    PAYERS("Payers and projects"),
    AI("AI"),
    SECURITY("Security"),
    REMINDERS("Reminders"),
    APPEARANCE("Appearance"),
    TRASH("Trash"),
    ABOUT("About"),
}

/** Things that live in other screens, reached from Settings. */
data class SettingsLinks(
    val mailAccounts: () -> Unit,
    val backup: () -> Unit,
    val exportAll: () -> Unit,
    val restore: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(back: () -> Unit, links: SettingsLinks, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(Page.MAIN) }
    BackHandler(page != Page.MAIN) { page = Page.MAIN }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(page.title) },
                navigationIcon = {
                    IconButton({
                        if (page ==
                            Page.MAIN
                        ) {
                            back()
                        } else {
                            page = Page.MAIN
                        }
                    }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (page) {
                Page.MAIN -> Main(s, { page = it }, links, vm)
                Page.CATEGORIES -> Categories(s, vm)
                Page.PAYERS -> Payers(s, vm)
                Page.AI -> Ai(s, vm)
                Page.SECURITY -> Security(s, vm)
                Page.REMINDERS -> Reminders(s, vm)
                Page.APPEARANCE -> Appearance(s, vm)
                Page.TRASH -> Trash(s, vm)
                Page.ABOUT -> About(vm)
            }
        }
    }
}

@Composable
private fun Main(s: SettingsState, go: (Page) -> Unit, links: SettingsLinks, vm: SettingsViewModel) {
    var editName by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item { Nav("Your name", s.settings.userName.ifBlank { "Shown on reports" }) { editName = true } }
        item { Nav("Categories and tax presets", "${s.categories.count { !it.hidden }} categories") { go(Page.CATEGORIES) } }
        item { Nav("Payers and projects", plural(s.payers.size, "payer") + ", " + plural(s.projects.size, "project")) { go(Page.PAYERS) } }
        item { Nav("Mail accounts", "Connected accounts, auto scan, scan log, sender rules", links.mailAccounts) }
        item { Nav("AI", "On device status and an optional cloud key") { go(Page.AI) } }
        item { Nav("Backup and restore", "Save everything to Google Drive or a file", links.backup) }
        item { Nav("Security", "App lock and privacy") { go(Page.SECURITY) } }
        item { Nav("Reminders", if (s.settings.remindersOn) "On" else "Off") { go(Page.REMINDERS) } }
        item { Nav("Appearance", "Theme, colors, home currency") { go(Page.APPEARANCE) } }
        item { Nav("Trash", plural(s.trash.size, "receipt") + ", kept for 30 days") { go(Page.TRASH) } }
        item { Nav("About", "Version ${BuildConfig.VERSION_NAME}, open source") { go(Page.ABOUT) } }
    }
    if (editName) {
        TextEditDialog("Your name", s.settings.userName, false, { editName = false }) { v ->
            vm.update { it.copy(userName = v.trim()) }
            editName = false
        }
    }
}

@Composable
private fun Nav(title: String, sub: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = sub?.let { { Text(it) } },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun Toggle(title: String, sub: String?, on: Boolean, set: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = sub?.let { { Text(it) } },
        trailingContent = { Switch(on, set) },
        modifier = Modifier.clickable { set(!on) },
    )
}

@Composable
private fun NumberRow(title: String, value: Int, unit: String, set: (Int) -> Unit) {
    var edit by remember { mutableStateOf(false) }
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text("$value $unit") }, modifier = Modifier.clickable { edit = true })
    if (edit) {
        TextEditDialog(title, value.toString(), true, { edit = false }) { v ->
            v.trim().toIntOrNull()?.let(set)
            edit = false
        }
    }
}

// Categories

@Composable
private fun Categories(s: SettingsState, vm: SettingsViewModel) {
    var editing by remember { mutableStateOf<Category?>(null) }
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            Text(
                "Each category maps to a tax line (US sole proprietor preset). This is not tax advice.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        items(s.categories, key = { it.id }) { c ->
            val line = s.taxLines.firstOrNull { it.id == c.taxLineId }
            ListItem(
                leadingContent = { CategoryDot(c, size = 36) },
                headlineContent = { Text(c.name, color = if (c.hidden) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified) },
                supportingContent = {
                    Text(
                        (line?.let { "${it.label}, ${it.deductiblePercent}%" } ?: "No tax line") + if (c.hidden) " · hidden" else ""
                    )
                },
                trailingContent = {
                    Row {
                        IconButton({ vm.moveCategory(c, true) }) { Icon(Icons.Rounded.ArrowUpward, "Move up") }
                        IconButton({ vm.moveCategory(c, false) }) { Icon(Icons.Rounded.ArrowDownward, "Move down") }
                    }
                },
                modifier = Modifier.clickable { editing = c },
            )
        }
        item {
            TextButton({
                editing = Category(name = "", icon = "tag", color = 0xFF3BA3A8, sortOrder = s.categories.size)
            }, Modifier.padding(8.dp)) {
                Icon(Icons.Rounded.Add, null)
                Text("Add category")
            }
        }
    }
    editing?.let { c -> CategoryDialog(c, s, vm) { editing = null } }
}

private val Palette =
    listOf(
        0xFFE8875B, 0xFF4F8FD8, 0xFF7A6AD8, 0xFF5D9C6B, 0xFFB0884A, 0xFF3BA3A8,
        0xFF6A8CA8, 0xFFD85B7A, 0xFFD8A23B, 0xFFB85BD8, 0xFF8A9396
    )

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CategoryDialog(c: Category, s: SettingsState, vm: SettingsViewModel, close: () -> Unit) {
    var name by remember { mutableStateOf(c.name) }
    var icon by remember { mutableStateOf(c.icon) }
    var color by remember { mutableStateOf(c.color) }
    var hidden by remember { mutableStateOf(c.hidden) }
    var taxLine by remember { mutableStateOf(c.taxLineId) }
    var pickLine by remember { mutableStateOf(false) }
    var merge by remember { mutableStateOf(false) }
    val line = s.taxLines.firstOrNull { it.id == taxLine }
    var percent by remember { mutableStateOf(line?.deductiblePercent?.toString() ?: "100") }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (c.name.isEmpty()) "New category" else "Edit category") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                Text("Icon", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CategoryIconNames.forEach { n ->
                        IconButton({ icon = n }) {
                            Icon(categoryIcon(n), n, tint = if (n == icon) Color(color) else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text("Color", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Palette.forEach { p ->
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(Color(p)).clickable { color = p }
                                .then(
                                    if (p ==
                                        color
                                    ) {
                                        Modifier.padding(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.6f))
                                    } else {
                                        Modifier
                                    }
                                ),
                        )
                    }
                }
                ListItem(
                    headlineContent = { Text("Tax line") },
                    supportingContent = { Text(line?.label ?: "None") },
                    modifier = Modifier.clickable { pickLine = true },
                )
                if (line != null) {
                    OutlinedTextField(
                        percent,
                        { percent = it.filter(Char::isDigit).take(3) },
                        label = { Text("Deductible %") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hidden", Modifier.weight(1f))
                    Switch(hidden, { hidden = it })
                }
                if (c.name.isNotEmpty()) TextButton({ merge = true }) { Text("Merge into another category") }
            }
        },
        confirmButton = {
            TextButton({
                if (name.isNotBlank()) {
                    vm.saveCategory(c.copy(name = name.trim(), icon = icon, color = color, hidden = hidden, taxLineId = taxLine))
                    if (line != null) percent.toIntOrNull()?.let { vm.saveTaxLine(line.copy(deductiblePercent = it.coerceIn(0, 100))) }
                }
                close()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(close) { Text("Cancel") } },
    )
    if (pickLine) {
        PickSheet("Tax line", listOf<Pair<String?, String>>(null to "None") + s.taxLines.map { it.id to it.label }, taxLine, {
            pickLine =
                false
        }) {
            taxLine = it
        }
    }
    if (merge) {
        PickSheet(
            "Merge ${c.name} into",
            s.categories.filter {
                it.id != c.id
            }.map { it.id to it.name },
            null as String?,
            { merge = false }
        ) { id ->
            s.categories.firstOrNull { it.id == id }?.let { vm.mergeCategory(c, it) }
            close()
        }
    }
}

// Payers and projects

@Composable
private fun Payers(s: SettingsState, vm: SettingsViewModel) {
    var payer by remember { mutableStateOf<Payer?>(null) }
    var project by remember { mutableStateOf<Project?>(null) }
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        item { Header("Payers", "Whoever pays you back: an employer, a client, a board.") }
        items(s.payers, key = { it.id }) { p ->
            ListItem(
                headlineContent = { Text(p.name) },
                supportingContent = {
                    Text(listOfNotNull(p.defaultTrack.label(), p.submitDeadlineDays?.let { "submit within $it days" }).joinToString(", "))
                },
                modifier = Modifier.clickable { payer = p },
            )
        }
        item {
            TextButton({ payer = Payer(name = "") }, Modifier.padding(horizontal = 8.dp)) {
                Icon(Icons.Rounded.Add, null)
                Text("Add payer")
            }
        }
        item { Header("Projects and trips", "A free tag for grouping receipts, like a site visit or a conference.") }
        items(s.projects, key = { it.id }) { p ->
            ListItem(
                headlineContent = { Text(p.name) },
                supportingContent = {
                    Text(
                        listOfNotNull(
                            s.payers.firstOrNull { it.id == p.payerId }?.name,
                            p.startDate?.let { it.pretty() + (p.endDate?.let { e -> " to " + e.pretty() } ?: "") },
                        ).joinToString(", ").ifBlank { "No dates" },
                    )
                },
                modifier = Modifier.clickable { project = p },
            )
        }
        item {
            TextButton({ project = Project(name = "") }, Modifier.padding(horizontal = 8.dp)) {
                Icon(Icons.Rounded.Add, null)
                Text("Add project or trip")
            }
        }
    }
    payer?.let { p -> PayerDialog(p, vm) { payer = null } }
    project?.let { p -> ProjectDialog(p, s, vm) { project = null } }
}

@Composable
private fun Header(title: String, sub: String) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PayerDialog(p: Payer, vm: SettingsViewModel, close: () -> Unit) {
    var name by remember { mutableStateOf(p.name) }
    var track by remember { mutableStateOf(p.defaultTrack) }
    var deadline by remember { mutableStateOf(p.submitDeadlineDays?.toString() ?: "") }
    var remind by remember { mutableStateOf(p.reminderDays.toString()) }
    var email by remember { mutableStateOf(p.email ?: "") }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (p.name.isEmpty()) "New payer" else "Edit payer") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                Text("Default track", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Track.entries.forEach { t -> FilterChip(track == t, { track = t }, { Text(t.label()) }) }
                }
                OutlinedTextField(
                    deadline,
                    { deadline = it.filter(Char::isDigit) },
                    label = { Text("Submit within days (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    remind,
                    { remind = it.filter(Char::isDigit) },
                    label = { Text("Remind me if unpaid after days") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    email,
                    { email = it },
                    label = { Text("Email for reports (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                if (p.name.isNotEmpty()) {
                    TextButton({
                        vm.deletePayer(p)
                        close()
                    }) {
                        Icon(Icons.Rounded.Delete, null)
                        Text("Delete payer")
                    }
                }
            }
        },
        confirmButton = {
            TextButton({
                if (name.isNotBlank()) {
                    vm.savePayer(
                        p.copy(
                            name = name.trim(),
                            defaultTrack = track,
                            submitDeadlineDays = deadline.toIntOrNull(),
                            reminderDays = remind.toIntOrNull() ?: 30,
                            email = email.trim().ifBlank { null },
                        ),
                    )
                }
                close()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(close) { Text("Cancel") } },
    )
}

@Composable
private fun ProjectDialog(p: Project, s: SettingsState, vm: SettingsViewModel, close: () -> Unit) {
    var name by remember { mutableStateOf(p.name) }
    var payerId by remember { mutableStateOf(p.payerId) }
    var start by remember { mutableStateOf(p.startDate) }
    var end by remember { mutableStateOf(p.endDate) }
    var pick by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (p.name.isEmpty()) "New project or trip" else "Edit project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                ListItem(
                    headlineContent = { Text("Payer") },
                    supportingContent = { Text(s.payers.firstOrNull { it.id == payerId }?.name ?: "None") },
                    modifier = Modifier.clickable { pick = "payer" },
                )
                ListItem(
                    headlineContent = {
                        Text("Starts")
                    },
                    supportingContent = { Text(start?.pretty() ?: "Not set") },
                    modifier = Modifier.clickable {
                        pick =
                            "start"
                    }
                )
                ListItem(
                    headlineContent = {
                        Text("Ends")
                    },
                    supportingContent = { Text(end?.pretty() ?: "Not set") },
                    modifier = Modifier.clickable {
                        pick =
                            "end"
                    }
                )
                if (p.name.isNotEmpty()) {
                    TextButton({
                        vm.deleteProject(p)
                        close()
                    }) { Text("Delete project") }
                }
            }
        },
        confirmButton = {
            TextButton({
                if (name.isNotBlank()) vm.saveProject(p.copy(name = name.trim(), payerId = payerId, startDate = start, endDate = end))
                close()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(close) { Text("Cancel") } },
    )
    when (pick) {
        "payer" -> PickSheet("Payer", listOf<Pair<String?, String>>(null to "None") + s.payers.map { it.id to it.name }, payerId, {
            pick =
                null
        }) {
            payerId = it
        }
        "start" -> org.kealohalabs.receiptbeat.ui.detail.DateDialog(start, { pick = null }) {
            start = it
            pick = null
        }
        "end" -> org.kealohalabs.receiptbeat.ui.detail.DateDialog(end, { pick = null }) {
            end = it
            pick = null
        }
    }
}

// AI

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Ai(s: SettingsState, vm: SettingsViewModel) {
    var provider by remember { mutableStateOf(s.settings.aiProvider.takeIf { it != AiProvider.NONE } ?: AiProvider.GEMINI) }
    var key by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var testText by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("On device AI", style = MaterialTheme.typography.titleMedium)
        Text("Gemini Nano: ${s.nanoStatus}", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Reading always happens on your phone first. Nothing leaves it unless you add a key below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()
        Text("Optional cloud AI", style = MaterialTheme.typography.titleMedium)
        Text(
            "For hard receipts like faded paper or handwriting. Uses your own key, kept in the Android Keystore. " +
                "Only the receipt text is sent, never the photo" + if (s.settings.aiSendImages) ", unless you allow images." else ".",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(AiProvider.GEMINI to "Gemini", AiProvider.CLAUDE to "Claude", AiProvider.OPENAI to "OpenAI").forEach { (p, n) ->
                FilterChip(provider == p, { provider = p }, { Text(n) })
            }
        }
        OutlinedTextField(
            key,
            { key = it },
            label = { Text(if (s.hasAiKey) "Key saved. Paste a new one to replace it" else "API key") },
            singleLine = true,
            visualTransformation = if (show) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton({ show = !show }) { Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show key") }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({
                vm.setAiKey(provider, key)
                key = ""
            }, enabled = key.isNotBlank()) { Text("Save key") }
            if (s.hasAiKey) TextButton({ vm.setAiKey(provider, null) }) { Text("Remove key") }
        }
        HorizontalDivider()
        Text("Test extraction", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(testText, {
            testText = it
        }, label = { Text("Paste receipt text") }, modifier = Modifier.fillMaxWidth().height(160.dp))
        Button({ scope.launch { result = vm.testExtraction(testText) } }, enabled = testText.isNotBlank()) { Text("Read it") }
        result?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

// Security

@Composable
private fun Security(s: SettingsState, vm: SettingsViewModel) {
    val x = s.settings
    LazyColumn {
        item { Toggle("App lock", "Fingerprint, face or PIN to open the app", x.appLock) { on -> vm.update { it.copy(appLock = on) } } }
        if (x.appLock) {
            item {
                NumberRow("Lock after", x.lockTimeoutSec, "seconds away") { v ->
                    vm.update { it.copy(lockTimeoutSec = v.coerceAtLeast(0)) }
                }
            }
        }
        item {
            Toggle("Hide in recent apps", "Blocks screenshots and blanks the app in the recents screen", x.secureScreen) { on ->
                vm.update { it.copy(secureScreen = on) }
            }
        }
        item {
            Toggle("Hide amounts", "Show dots instead of money, for use in public", x.hideAmounts) { on ->
                vm.update { it.copy(hideAmounts = on) }
            }
        }
        item {
            Text(
                "Receipts are encrypted on this phone. Card numbers are never stored in full. No tracking of any kind.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun Reminders(s: SettingsState, vm: SettingsViewModel) {
    val x = s.settings
    LazyColumn {
        item {
            Toggle("Reminders", "Quiet nudges so nothing falls through", x.remindersOn) { on -> vm.update { it.copy(remindersOn = on) } }
        }
        if (x.remindersOn) {
            item { NumberRow("Inbox receipts waiting", x.remindInboxDays, "days") { v -> vm.update { it.copy(remindInboxDays = v) } } }
            item {
                NumberRow("Before a payer deadline", x.remindSubmitBeforeDays, "days") { v ->
                    vm.update { it.copy(remindSubmitBeforeDays = v) }
                }
            }
            item { NumberRow("Submitted and unpaid", x.remindUnpaidDays, "days") { v -> vm.update { it.copy(remindUnpaidDays = v) } } }
            item { Toggle("Tax export in January", null, x.remindTaxJanuary) { on -> vm.update { it.copy(remindTaxJanuary = on) } } }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Appearance(s: SettingsState, vm: SettingsViewModel) {
    val x = s.settings
    var currency by remember { mutableStateOf(false) }
    Column(Modifier.padding(vertical = 8.dp)) {
        Text("Theme", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp))
        FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                ThemeMode.SYSTEM to "Follow phone",
                ThemeMode.LIGHT to "Light",
                ThemeMode.DARK to "Dark",
                ThemeMode.BLACK to "True black"
            ).forEach { (m, n) ->
                FilterChip(x.theme == m.name, { vm.update { it.copy(theme = m.name) } }, { Text(n) })
            }
        }
        Toggle("Dynamic color", "Use colors from your wallpaper instead of the Receipt Beat look", x.dynamicColor) { on ->
            vm.update { it.copy(dynamicColor = on) }
        }
        ListItem(
            headlineContent = { Text("Home currency") },
            supportingContent = { Text(x.homeCurrency) },
            modifier = Modifier.clickable {
                currency =
                    true
            }
        )
    }
    if (currency) {
        TextEditDialog("Home currency code, like USD", x.homeCurrency, false, { currency = false }) { v ->
            if (v.trim().length == 3) vm.update { it.copy(homeCurrency = v.trim().uppercase()) }
            currency = false
        }
    }
}

@Composable
private fun Trash(s: SettingsState, vm: SettingsViewModel) {
    if (s.trash.isEmpty()) {
        Text("Trash is empty.", Modifier.padding(16.dp))
        return
    }
    LazyColumn {
        items(s.trash, key = { it.id }) { r ->
            ListItem(
                headlineContent = { Text(r.merchantName ?: "Receipt") },
                supportingContent = { Text(r.purchaseDate.pretty()) },
                trailingContent = { TextButton({ vm.restore(r) }) { Text("Restore") } },
            )
        }
    }
}

@Composable
private fun About(vm: SettingsViewModel) {
    val context = LocalContext.current
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Receipt Beat", style = MaterialTheme.typography.headlineSmall)
        Text("Version ${BuildConfig.VERSION_NAME}. Free and open source from Kealoha Labs, under the Apache 2.0 license.")
        Text("No ads, no tracking, no account. Bugs and ideas are welcome on GitHub.")
        TextButton({ open(context, "https://github.com/olagon/receiptbeat") }) { Text("github.com/olagon/receiptbeat") }
        TextButton({ open(context, "https://github.com/olagon/receiptbeat/issues") }) { Text("Report a problem") }
        TextButton({ copyLog(context) }) { Text("Copy debug log") }
        if (BuildConfig.DEBUG) TextButton(vm::addSampleData) { Text("Add sample receipts (test builds only)") }
        HorizontalDivider()
        Text("Open source licenses", style = MaterialTheme.typography.titleSmall)
        Text(Licenses, style = MaterialTheme.typography.bodySmall)
    }
}

private val Licenses = """
    AndroidX, Jetpack Compose, Room, WorkManager, DataStore, Glance, Hilt: Apache 2.0
    ML Kit Document Scanner, Text Recognition and GenAI: Google APIs Terms
    SQLCipher for Android: BSD style license, Zetetic LLC
    Google Tink: Apache 2.0
    Apache James Mime4j: Apache 2.0
    java-libpst: Apache 2.0
    Eclipse Angus Mail: EPL 2.0 or GPL 2.0 with Classpath Exception
    jsoup: MIT
    Kotlin and kotlinx: Apache 2.0
    Poppins and Inter fonts: SIL Open Font License 1.1
    Intro music "Life of Riley" by Kevin MacLeod (incompetech.com), CC BY 4.0
""".trimIndent()

private fun open(context: Context, url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

/** The local log, copied so the user can read it before choosing to share. Nothing is sent anywhere. */
private fun copyLog(context: Context) {
    val log = runCatching {
        Runtime.getRuntime().exec(
            arrayOf("logcat", "-d", "-t", "400", "ReceiptBeat:V", "AndroidRuntime:E", "*:S")
        ).inputStream.bufferedReader().readText()
    }.getOrDefault("No log available")
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newPlainText("Receipt Beat log", "Receipt Beat ${BuildConfig.VERSION_NAME}\n" + log))
}

private fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"
