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
import org.kealohalabs.receiptbeat.ui.onboarding.OnboardingScreen
import org.kealohalabs.receiptbeat.ui.receipts.ReceiptsScreen
import org.kealohalabs.receiptbeat.ui.settings.SettingsLinks
import org.kealohalabs.receiptbeat.ui.settings.SettingsScreen

/** Actions that need the Activity (share sheet, pickers). */
data class AppActions(val shareReceipt: (String) -> Unit)

@Composable
fun AppNavHost(nav: NavHostController, modifier: Modifier, actions: AppActions, start: Any = HomeRoute) {
    val openReceipt: (String) -> Unit = { nav.navigate(ReceiptRoute(it)) }
    NavHost(nav, startDestination = start, modifier = modifier) {
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
        composable<SettingsRoute> {
            SettingsScreen(
                back = { nav.popBackStack() },
                links = SettingsLinks(
                    mailAccounts = { nav.navigate(MailAccountsRoute) },
                    backup = { nav.navigate(BackupRoute) },
                    exportAll = { nav.navigate(BackupRoute) },
                    restore = { nav.navigate(BackupRoute) },
                ),
            )
        }
        composable<OnboardingRoute> {
            val finish: (Any?) -> Unit = { next ->
                nav.navigate(HomeRoute) { popUpTo(OnboardingRoute) { inclusive = true } }
                if (next != null) nav.navigate(next)
            }
            OnboardingScreen(addEmail = { finish(EmailWizardRoute()) }, done = { finish(null) })
        }
        composable<MailAccountsRoute> { Placeholder("Mail accounts") }
        composable<EmailWizardRoute> { Placeholder("Add email receipts") }
        composable<BackupRoute> { Placeholder("Backup") }
    }
}

@Composable
private fun Placeholder(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(name) }
}
