package org.kealohalabs.receiptbeat.ui.capture

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import org.kealohalabs.receiptbeat.core.data.label
import org.kealohalabs.receiptbeat.core.model.Track
import org.kealohalabs.receiptbeat.ui.common.ReceiptCard
import org.kealohalabs.receiptbeat.ui.common.ReceiptImage

enum class CaptureMode(val label: String, val pages: Int) {
    SINGLE("Single", 1),
    BATCH("Batch", 30),
    LONG("Long", 10)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(startMode: String, startImport: Boolean, back: () -> Unit, done: () -> Unit, vm: CaptureViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val s by vm.state.collectAsStateWithLifecycle()
    var mode by rememberSaveable {
        mutableStateOf(CaptureMode.entries.firstOrNull { it.name.equals(startMode, true) } ?: CaptureMode.SINGLE)
    }
    var launched by rememberSaveable { mutableStateOf(false) }
    var scannerFailed by rememberSaveable { mutableStateOf(false) }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res: ActivityResult ->
        if (res.resultCode == Activity.RESULT_OK) {
            val result = GmsDocumentScanningResult.fromActivityResultIntent(res.data)
            val pages = result?.pages?.map { it.imageUri }.orEmpty()
            if (pages.isNotEmpty()) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.scanned(pages, mode)
            }
        } else if (s.session.isEmpty()) {
            back()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            vm.imported(uris)
        } else if (s.session.isEmpty() && startImport) {
            back()
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = cameraUri
        if (ok && u != null) vm.scanned(listOf(u), CaptureMode.SINGLE)
    }

    fun scan() {
        val opts = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(mode.pages)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(opts).getStartScanIntent(context as Activity)
            .addOnSuccessListener { scanLauncher.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener { scannerFailed = true }
    }

    fun camera() {
        val u = cameraFile(context)
        cameraUri = u
        cameraLauncher.launch(u)
    }

    val importTypes = arrayOf("image/*", "application/pdf")
    LaunchedEffect(Unit) {
        if (!launched) {
            launched = true
            if (startImport) importLauncher.launch(importTypes) else scan()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Capture") },
                navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = { if (s.session.isNotEmpty()) TextButton(done) { Text("Done") } }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val err by vm.error.collectAsStateWithLifecycle()
            err?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            if (scannerFailed) {
                Text(
                    "The scanner is not available on this phone. You can take a photo or import a file instead.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
            }
            if (s.session.isNotEmpty()) {
                Text("${s.session.size} scanned", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    items(s.session, key = { it.id }) { r ->
                        ReceiptImage(s.thumbs[r.id], Modifier.size(72.dp, 96.dp).clip(RoundedCornerShape(12.dp)))
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CaptureMode.entries.forEach { m -> FilterChip(mode == m, { mode = m }, { Text(m.label) }) }
            }
            Text(
                when (mode) {
                    CaptureMode.SINGLE -> "One receipt."
                    CaptureMode.BATCH -> "Many receipts in a row. Each page becomes its own receipt."
                    CaptureMode.LONG -> "One long receipt in pages. They are joined top to bottom."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Button(
                { if (scannerFailed) camera() else scan() },
                Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(if (scannerFailed) Icons.Rounded.CameraAlt else Icons.Rounded.DocumentScanner, null)
                Spacer(Modifier.size(8.dp))
                Text(if (s.session.isEmpty()) "Scan" else "Scan more")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton({ importLauncher.launch(importTypes) }, Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Rounded.FileOpen, null)
                Spacer(Modifier.size(8.dp))
                Text("Import from Files or Photos")
            }
        }
    }

    // After a single scan, the card fills in while the user sets track and payer.
    val last = s.focus
    if (last != null) {
        ModalBottomSheet(onDismissRequest = vm::clearFocus) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
                ReceiptCard(last, s.thumbs[last.id], s.categories[last.categoryId])
                Spacer(Modifier.height(16.dp))
                Text("Where is it headed?", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Track.entries.forEach { t ->
                        FilterChip(last.track == t, { vm.setTrack(last.id, t) }, { Text(t.label().substringBefore(' ')) })
                    }
                }
                if (s.payers.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Who pays you back?", style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(s.payers, key = { it.id }) { p ->
                            FilterChip(last.payerId == p.id, { vm.setPayer(last.id, p) }, { Text(p.name) })
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({
                        vm.clearFocus()
                        scan()
                    }, Modifier.weight(1f)) { Text("Scan another") }
                    Button({
                        vm.clearFocus()
                        done()
                    }, Modifier.weight(1f)) { Text("Done") }
                }
            }
        }
    }
}

private fun cameraFile(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val f = File(dir, "photo_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.files", f)
}
