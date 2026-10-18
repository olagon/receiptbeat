package org.kealohalabs.receiptbeat.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kealohalabs.receiptbeat.export.BackupManager

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val backup: BackupManager,
) : ViewModel() {
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    private fun run(done: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            message.value = runCatching { block() }.fold({ done }, { "That did not work: ${it.message ?: "unknown error"}" })
            busy.value = false
        }
    }

    /** Writes the backup ZIP to a place the user picked, like Google Drive or a USB drive. */
    fun saveTo(uri: Uri) = run("Backup saved.") {
        val zip: File = backup.backup()
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)!!.use { out -> zip.inputStream().use { it.copyTo(out) } }
            zip.delete()
        }
    }

    fun share(share: (List<File>) -> Unit) = run("Backup ready to share.") { share(listOf(backup.backup())) }

    fun restore(uri: Uri) = run("Restored. Everything is back.") { backup.restore(uri) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(back: () -> Unit, share: (List<File>, String) -> Unit, vm: BackupViewModel = hiltViewModel()) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<Uri?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(vm::saveTo) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { confirm = it }
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text("Backup and restore")
            }, navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } })
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "A backup is one ZIP with every receipt, photo and setting you need to move phones. " +
                    "Save it to Google Drive, a USB drive or anywhere you like. It is your data.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "The ZIP is not encrypted, so keep it somewhere private.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button({ save.launch("Receipt-Beat-backup-${LocalDate.now()}.zip") }, Modifier.fillMaxWidth().height(52.dp), enabled = !busy) {
                Text("Save a backup")
            }
            OutlinedButton({ vm.share { share(it, "Receipt Beat backup") } }, Modifier.fillMaxWidth().height(52.dp), enabled = !busy) {
                Text("Export everything and share")
            }
            OutlinedButton({
                pick.launch(arrayOf("application/zip", "application/octet-stream"))
            }, Modifier.fillMaxWidth().height(52.dp), enabled = !busy) {
                Text("Restore from a backup")
            }
            if (busy) CircularProgressIndicator(Modifier.size(32.dp))
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
    confirm?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Replace everything?") },
            text = { Text("Restoring replaces all receipts on this phone with the ones in the backup. This cannot be undone.") },
            confirmButton = {
                TextButton({
                    vm.restore(uri)
                    confirm = null
                }) { Text("Replace") }
            },
            dismissButton = { TextButton({ confirm = null }) { Text("Cancel") } },
        )
    }
}
