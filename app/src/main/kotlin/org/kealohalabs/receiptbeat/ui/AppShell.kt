package org.kealohalabs.receiptbeat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.kealohalabs.receiptbeat.core.designsystem.LocalExtraColors

data class Tab(val route: Any, val label: String, val icon: ImageVector)

val Tabs = listOf(
    Tab(HomeRoute, "Home", Icons.Rounded.Home),
    Tab(ReceiptsRoute(), "Receipts", Icons.Rounded.ReceiptLong),
    Tab(ReviewRoute, "Review", Icons.Rounded.MarkEmailUnread),
    Tab(MoneyRoute(), "Money", Icons.Rounded.AccountBalanceWallet)
)

@Composable
fun AppShell(actions: AppActions, nav: NavHostController = rememberNavController(), reviewBadge: Int = 0, start: Any = HomeRoute) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    val showBars = Tabs.any { t -> dest?.hasRoute(t.route::class) == true }
    val select: (Tab) -> Unit = { t ->
        nav.navigate(t.route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val capture = { nav.navigate(CaptureRoute()) }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            if (showBars) {
                NavigationRail(
                    header = { CaptureButton(onClick = capture, modifier = Modifier.padding(vertical = 12.dp)) }
                ) {
                    Tabs.forEach { t ->
                        NavigationRailItem(
                            selected = dest?.hasRoute(t.route::class) == true,
                            onClick = { select(t) },
                            icon = { TabIcon(t, if (t.route == ReviewRoute) reviewBadge else 0) },
                            label = { Text(t.label) }
                        )
                    }
                }
            }
            AppNavHost(nav, Modifier.weight(1f), actions, start)
        }
    } else {
        Scaffold(
            bottomBar = {
                if (showBars) BottomBar(dest = { t -> dest?.hasRoute(t.route::class) == true }, select, capture, reviewBadge)
            },
            contentWindowInsets = WindowInsets(0)
        ) { pad ->
            AppNavHost(nav, Modifier.padding(pad), actions, start)
        }
    }
}

@Composable
private fun TabIcon(t: Tab, badge: Int) {
    if (badge > 0) {
        BadgedBox(badge = { Badge { Text("$badge") } }) { Icon(t.icon, null) }
    } else {
        Icon(t.icon, null)
    }
}

@Composable
private fun BottomBar(dest: (Tab) -> Boolean, select: (Tab) -> Unit, capture: () -> Unit, reviewBadge: Int) {
    Box {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Tabs.take(2).forEach { t ->
                NavigationBarItem(dest(t), { select(t) }, { TabIcon(t, 0) }, label = { Text(t.label) })
            }
            Spacer(Modifier.weight(1f))
            Tabs.drop(2).forEach { t ->
                NavigationBarItem(
                    dest(t),
                    { select(t) },
                    { TabIcon(t, if (t.route == ReviewRoute) reviewBadge else 0) },
                    label = { Text(t.label) }
                )
            }
        }
        // The raised Capture button sits over the middle of the bar, always within thumb reach.
        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets(0)).offset(y = (-18).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            CaptureButton(onClick = capture)
        }
        Spacer(Modifier.height(0.dp).windowInsetsPadding(WindowInsets.navigationBars))
    }
}

@Composable
fun CaptureButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val extra = LocalExtraColors.current
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(64.dp).semantics { contentDescription = "Capture a receipt" },
        containerColor = extra.capture,
        contentColor = extra.onCapture,
        shape = MaterialTheme.shapes.large,
        elevation = FloatingActionButtonDefaults.elevation(6.dp)
    ) {
        Icon(Icons.Rounded.DocumentScanner, null, Modifier.size(30.dp))
    }
}
