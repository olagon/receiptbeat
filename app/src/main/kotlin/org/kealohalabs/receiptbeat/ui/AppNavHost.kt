package org.kealohalabs.receiptbeat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import org.kealohalabs.receiptbeat.ui.backup.BackupScreen
import org.kealohalabs.receiptbeat.ui.capture.CaptureScreen
import org.kealohalabs.receiptbeat.ui.detail.DetailScreen
import org.kealohalabs.receiptbeat.ui.home.HomeScreen
import org.kealohalabs.receiptbeat.ui.money.MoneyScreen
import org.kealohalabs.receiptbeat.ui.onboarding.OnboardingScreen
import org.kealohalabs.receiptbeat.ui.receipts.ReceiptsScreen
import org.kealohalabs.receiptbeat.ui.review.EmailWizard
import org.kealohalabs.receiptbeat.ui.review.MailAccountsScreen
import org.kealohalabs.receiptbeat.ui.review.ReviewScreen
import org.kealohalabs.receiptbeat.ui.settings.SettingsLinks
import org.kealohalabs.receiptbeat.ui.settings.SettingsScreen

/** Actions that need the Activity (share sheet, pickers). */
data class AppActions(val shareReceipt: (String) -> Unit, val shareFiles: (List<java.io.File>, String) -> Unit)

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
        composable<ReceiptsRoute> {
            if (LocalConfiguration.current.screenWidthDp >= TWO_PANE_DP) {
                // Foldable inner screen and tablets: list on the left, receipt on the right.
                var selected by rememberSaveable { mutableStateOf<String?>(null) }
                Row {
                    Box(Modifier.weight(1f)) { ReceiptsScreen(open = { selected = it }) }
                    Box(Modifier.weight(1.2f)) {
                        val id = selected
                        if (id == null) {
                            Text(
                                "Pick a receipt to see it here.",
                                Modifier.align(Alignment.Center),
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            DetailScreen(id, back = { selected = null }, share = actions.shareReceipt, showBack = false)
                        }
                    }
                }
            } else {
                ReceiptsScreen(open = openReceipt)
            }
        }
        composable<ReceiptRoute> {
            DetailScreen(it.toRoute<ReceiptRoute>().id, back = { nav.popBackStack() }, share = actions.shareReceipt)
        }
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
        composable<ReviewRoute> {
            ReviewScreen(addEmail = { nav.navigate(EmailWizardRoute()) }, accounts = { nav.navigate(MailAccountsRoute) })
        }
        composable<MoneyRoute> {
            MoneyScreen(openReceipt = openReceipt, shareFiles = actions.shareFiles, startOnTaxes = it.toRoute<MoneyRoute>().taxes)
        }
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
                nav.popBackStack(OnboardingRoute, inclusive = true)
                if (next != null) nav.navigate(next)
            }
            OnboardingScreen(addEmail = { finish(EmailWizardRoute()) }, done = { finish(null) })
        }
        composable<MailAccountsRoute> { MailAccountsScreen(back = { nav.popBackStack() }, add = { nav.navigate(EmailWizardRoute()) }) }
        composable<EmailWizardRoute> {
            EmailWizard(
                start = it.toRoute<EmailWizardRoute>().provider,
                back = { nav.popBackStack() },
                done = {
                    nav.popBackStack()
                    nav.navigate(ReviewRoute) { launchSingleTop = true }
                },
            )
        }
        composable<BackupRoute> { BackupScreen(back = { nav.popBackStack() }, share = actions.shareFiles) }
    }
}

private const val TWO_PANE_DP = 700
