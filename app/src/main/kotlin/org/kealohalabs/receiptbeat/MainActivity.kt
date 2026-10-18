package org.kealohalabs.receiptbeat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.kealohalabs.receiptbeat.capture.ReceiptPipeline
import org.kealohalabs.receiptbeat.core.data.SettingsRepository
import org.kealohalabs.receiptbeat.core.designsystem.ReceiptBeatTheme
import org.kealohalabs.receiptbeat.core.designsystem.ThemeMode
import org.kealohalabs.receiptbeat.core.model.Source
import org.kealohalabs.receiptbeat.ui.AppActions
import org.kealohalabs.receiptbeat.ui.AppShell
import org.kealohalabs.receiptbeat.ui.CaptureRoute
import org.kealohalabs.receiptbeat.ui.HomeRoute
import org.kealohalabs.receiptbeat.ui.MoneyRoute
import org.kealohalabs.receiptbeat.ui.OnboardingRoute
import org.kealohalabs.receiptbeat.ui.ReceiptsRoute
import org.kealohalabs.receiptbeat.ui.ReviewRoute
import org.kealohalabs.receiptbeat.ui.common.ImageLoader
import org.kealohalabs.receiptbeat.ui.common.LocalImageLoader

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var images: ImageLoader

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var pipeline: ReceiptPipeline

    @Inject lateinit var sharing: Sharing

    /** Routes asked for by shortcuts, notifications and the share sheet. */
    private val routes = Channel<Any>(Channel.BUFFERED)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        lifecycleScope.launch {
            settings.settings.collect { s ->
                if (s.secureScreen) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
        // Read once so the first frame knows whether to show onboarding.
        val first = runBlocking { settings.current() }
        setContent {
            val s by settings.settings.collectAsStateWithLifecycle(first)
            val nav = rememberNavController()
            LaunchedEffect(Unit) { routes.receiveAsFlow().collect { nav.navigate(it) } }
            ReceiptBeatTheme(
                mode = runCatching { ThemeMode.valueOf(s.theme) }.getOrDefault(ThemeMode.SYSTEM),
                dynamicColor = s.dynamicColor
            ) {
                CompositionLocalProvider(LocalImageLoader provides images) {
                    AppShell(
                        actions = AppActions(shareReceipt = { id -> lifecycleScope.launch { sharing.receipt(this@MainActivity, id) } }),
                        nav = nav,
                        start = if (first.onboarded) HomeRoute else OnboardingRoute
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        // Receipts read in the background used rules only. Let the on device AI finish them now.
        lifecycleScope.launch { pipeline.finishWithAi() }
    }

    private fun handle(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                val uris = sharedUris(intent)
                if (uris.isEmpty()) return
                lifecycleScope.launch {
                    pipeline.fromUris(uris.filter { contentResolver.getType(it) != "message/rfc822" }, Source.SHARE)
                    routes.trySend(ReceiptsRoute("inbox"))
                }
            }
        }
        when (intent.getStringExtra("route")) {
            "capture" -> routes.trySend(CaptureRoute())
            "import" -> routes.trySend(CaptureRoute("import"))
            "review" -> routes.trySend(ReviewRoute)
            "inbox" -> routes.trySend(ReceiptsRoute("inbox"))
            "ready" -> routes.trySend(ReceiptsRoute("ready"))
            "owed" -> routes.trySend(ReceiptsRoute("owed"))
            "taxes" -> routes.trySend(MoneyRoute(taxes = true))
        }
    }

    @Suppress("DEPRECATION")
    private fun sharedUris(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM))
        else -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
    }
}
