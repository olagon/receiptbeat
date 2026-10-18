package org.kealohalabs.receiptbeat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import org.kealohalabs.receiptbeat.core.designsystem.ReceiptBeatTheme
import org.kealohalabs.receiptbeat.ui.AppShell

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ReceiptBeatTheme { AppShell() }
        }
    }
}
