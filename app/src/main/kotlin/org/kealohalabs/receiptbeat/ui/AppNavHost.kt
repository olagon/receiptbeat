package org.kealohalabs.receiptbeat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import org.kealohalabs.receiptbeat.ui.capture.CaptureScreen
import org.kealohalabs.receiptbeat.ui.detail.DetailScreen
import org.kealohalabs.receiptbeat.ui.home.HomeScreen
import org.kealohalabs.receiptbeat.ui.receipts.ReceiptsScreen

/** Actions that need the Activity (share sheet, pickers). */
data class AppActions(val shareReceipt: (String) -> Unit)

@Composable
fun AppNavHost(nav: NavHostController, modifier: Modifier, actions: AppActions) {
    val openReceipt: (String) -> Unit = { nav.navigate(ReceiptRoute(it)) }
    NavHost(nav, startDestination = HomeRoute, modifier = modifier) {
        composable<HomeRoute> {
            HomeScreen(
                openList = { nav.navigate(ReceiptsRoute(it)) },
                openReceipt = openReceipt,
                openSettings = { nav.navigate(SettingsRoute) },
                openReview = { nav.navigate(ReviewRoute) },
                openMoney = { nav.navigate(MoneyRoute()) }
            )
        }
        composable<ReceiptsRoute> { ReceiptsScreen(open = openReceipt) }
        composable<ReceiptRoute> { DetailScreen(back = { nav.popBackStack() }, share = actions.shareReceipt) }
        composable<CaptureRoute> {
            val r = it.toRoute<CaptureRoute>()
            CaptureScreen(
                startMode = r.mode,
                startImport = r.mode == "import",
                back = { nav.popBackStack() },
                done = {
                    nav.popBackStack()
                    nav.navigate(ReceiptsRoute("inbox"))
                }
            )
        }
        composable<ReviewRoute> { Placeholder("Review") }
        composable<MoneyRoute> { Placeholder("Money") }
        composable<SettingsRoute> { Placeholder("Settings") }
    }
}

@Composable
private fun Placeholder(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(name) }
}
