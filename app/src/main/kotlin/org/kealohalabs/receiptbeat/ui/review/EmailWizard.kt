package org.kealohalabs.receiptbeat.ui.review

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.LocalDate
import org.kealohalabs.receiptbeat.core.designsystem.AppCard
import org.kealohalabs.receiptbeat.mail.ImapClient
import org.kealohalabs.receiptbeat.ui.common.rememberAskNotifications

/** How to get mail out of each provider, in plain words. From the spec's provider table. */
data class Provider(
    val key: String,
    val name: String,
    val exportSteps: List<String>?,
    val fileHint: String?,
    val canConnect: Boolean,
    val connectSteps: List<String>,
    val passwordUrl: String?,
)

val Providers = listOf(
    Provider(
        "gmail",
        "Gmail",
        listOf(
            "On any browser, go to takeout.google.com.",
            "Tap Deselect all, then tick Mail.",
            "Choose only the labels you want, like Inbox or Purchases. This keeps the file small.",
            "Tap Create export. Google emails you a download link when it is ready.",
            "Download the zip on this phone, or put it in Google Drive. Then pick it below.",
        ),
        ".mbox inside a .zip",
        true,
        listOf(
            "Turn on 2-Step Verification for your Google account.",
            "Create an app password for Receipt Beat.",
            "Paste it below. It stays on this phone."
        ),
        "https://myaccount.google.com/apppasswords",
    ),
    Provider(
        "outlook",
        "Outlook, Hotmail, Live",
        listOf(
            "Microsoft only exports from desktop Outlook.",
            "On Windows: File, Open and Export, Import/Export, Export to a file, Outlook Data File (.pst).",
            "On a Mac: File, Export, and save an .olm file.",
            "Put the file on this phone or in Google Drive, then pick it below.",
        ),
        ".pst or .olm",
        false,
        listOf(
            "Microsoft turned off app passwords for Outlook.com. Sign in with Microsoft is coming in an update. For now, import a file."
        ),
        null,
    ),
    Provider(
        "m365",
        "Microsoft 365 work or school",
        listOf(
            "In classic Outlook on Windows: File, Open and Export, Import/Export, Export to a file, Outlook Data File (.pst).",
            "Many workplaces block exports. Check with IT first."
        ),
        ".pst",
        false,
        listOf("Work accounts need Microsoft sign in, which is coming in an update."),
        null,
    ),
    Provider(
        "yahoo",
        "Yahoo and AOL",
        null,
        null,
        true,
        listOf(
            "Yahoo has no export button. Connect with an app password instead.",
            "In Yahoo account security, choose Generate app password.",
            "Paste it below."
        ),
        "https://login.yahoo.com/account/security",
    ),
    Provider(
        "icloud",
        "iCloud Mail",
        listOf(
            "On a Mac, open Mail and pick the mailbox.",
            "Choose Mailbox, Export Mailbox, and zip the folder it makes.",
            "Put the zip on this phone, then pick it below."
        ),
        ".mbox in a .zip",
        true,
        listOf(
            "Go to appleid.apple.com and sign in.",
            "Under Sign-In and Security, choose App-Specific Passwords.",
            "Create one and paste it below."
        ),
        "https://appleid.apple.com/account/manage",
    ),
    Provider(
        "proton",
        "Proton Mail",
        listOf(
            "On a computer, run Proton's free Export Tool.",
            "It saves every email as an EML file.",
            "Zip the folder, put it on this phone, then pick it below."
        ),
        ".eml files in a .zip",
        false,
        listOf("Proton needs its Bridge app on a computer for connections. Import a file instead."),
        null,
    ),
    Provider(
        "other",
        "Fastmail and others",
        listOf(
            "Install free Thunderbird on a computer and add your account.",
            "Let it download, then save folders as mbox with the ImportExportTools NG add-on.",
            "Put the file on this phone, then pick it below.",
        ),
        ".mbox",
        true,
        listOf("Most providers offer app passwords in their security settings.", "Create one for Receipt Beat and paste it below."),
        null,
    ),
)

private enum class Range(val label: String) { M3("3 months"), M6("6 months"), M12("12 months"), M24("24 months"), TAX("Last tax year") }

private fun Range.dates(): Pair<LocalDate, LocalDate> {
    val today = LocalDate.now()
    return when (this) {
        Range.M3 -> today.minusMonths(3) to today
        Range.M6 -> today.minusMonths(6) to today
        Range.M12 -> today.minusMonths(12) to today
        Range.M24 -> today.minusMonths(24) to today
        Range.TAX -> LocalDate.of(today.year - 1, 1, 1) to LocalDate.of(today.year - 1, 12, 31)
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EmailWizard(start: String?, back: () -> Unit, done: () -> Unit, vm: ReviewViewModel = hiltViewModel()) {
    var provider by rememberSaveable { mutableStateOf(start) }
    var range by rememberSaveable { mutableStateOf(Range.M12) }
    var connecting by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    val p = Providers.firstOrNull { it.key == provider }
    val askNotifications = rememberAskNotifications()
    BackHandler(p != null) { provider = null }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val (from, to) = range.dates()
            vm.importFile(uri, from, to) { ok ->
                if (ok) {
                    done()
                } else {
                    error =
                        "That file does not look like a mailbox. Try an .mbox, .eml, .zip, .pst or .olm file."
                }
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(p?.name ?: "Add email receipts") },
                navigationIcon = {
                    IconButton({
                        if (p !=
                            null
                        ) {
                            provider = null
                        } else {
                            back()
                        }
                    }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (p == null) {
                Privacy()
                Text("Which email do you use?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 12.dp))
                Providers.forEach { pr ->
                    ListItem(
                        headlineContent = { Text(pr.name) },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    pr.fileHint?.let {
                                        "Import $it"
                                    },
                                    if (pr.canConnect) "or connect" else null
                                ).joinToString(" ").ifBlank { "Connect" },
                            )
                        },
                        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                        modifier = Modifier.clickable { provider = pr.key },
                    )
                    HorizontalDivider()
                }
                return@Column
            }
            Text("How far back?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Range.entries.forEach { r -> FilterChip(range == r, { range = r }, { Text(r.label) }) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
            if (p.exportSteps != null && !connecting) {
                Section(Icons.Rounded.FileOpen, "Import a file", "Best for catching up on months or years at once.")
                Steps(p.exportSteps)
                Button({
                    picker.launch(arrayOf("*/*"))
                }, Modifier.fillMaxWidth().height(52.dp).padding(top = 8.dp)) { Text("Pick the file") }
                if (p.canConnect) {
                    TextButton({ connecting = true }, Modifier.padding(top = 8.dp)) { Text("Or connect the account instead") }
                }
            }
            if (p.exportSteps == null || connecting) {
                Section(Icons.Rounded.Link, "Connect the account", "New receipts are found on a schedule, hands free. Read only.")
                Steps(p.connectSteps)
                if (p.canConnect) {
                    Connect(p, range, vm, {
                        busy = it
                        if (it) askNotifications()
                    }, busy, done) { error = it }
                }
            }
            if (!p.canConnect && p.exportSteps != null) {
                Text(
                    p.connectSteps.first(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun Privacy() {
    AppCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(Modifier.padding(16.dp)) {
            Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(12.dp))
            Text(
                "Your email is read on this phone. Nothing is sent to us or anyone else. Only likely receipts are saved, " +
                    "and remote images in emails never load.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun Section(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String) {
    Row(Modifier.padding(top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Steps(steps: List<String>) {
    steps.forEachIndexed { i, s ->
        Row(Modifier.padding(vertical = 4.dp)) {
            Text("${i + 1}.", Modifier.width(24.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Text(s, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Connect(
    p: Provider,
    range: Range,
    vm: ReviewViewModel,
    setBusy: (Boolean) -> Unit,
    busy: Boolean,
    done: () -> Unit,
    error: (String?) -> Unit
) {
    val context = LocalContext.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var host by rememberSaveable { mutableStateOf("") }
    val guess = ImapClient.hostFor(email)
    if (p.passwordUrl != null) {
        OutlinedButton({ context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.passwordUrl))) }, Modifier.padding(vertical = 8.dp)) {
            Text("Open the app password page")
        }
    }
    OutlinedTextField(
        email,
        { email = it.trim() },
        label = { Text("Email address") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        password,
        { password = it },
        label = { Text("App password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    OutlinedTextField(
        host.ifBlank { guess.orEmpty() },
        { host = it.trim() },
        label = { Text("Mail server (filled in for you)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    Button(
        {
            setBusy(true)
            error(null)
            vm.connect(email, host.ifBlank { guess.orEmpty() }, password.replace(" ", ""), range.dates().first) { e ->
                setBusy(false)
                if (e == null) done() else error(e)
            }
        },
        Modifier.fillMaxWidth().height(52.dp).padding(top = 12.dp),
        enabled = !busy && email.contains('@') && password.isNotBlank() && host.ifBlank { guess.orEmpty() }.isNotBlank(),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Connect and scan")
    }
    Text(
        "Read only. Receipt Beat never sends, deletes or moves your mail. The password stays in this phone's secure storage.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}
