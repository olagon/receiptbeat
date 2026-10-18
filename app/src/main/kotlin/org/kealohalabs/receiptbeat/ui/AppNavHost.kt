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

@Composable
fun AppNavHost(nav: NavHostController, modifier: Modifier) {
    NavHost(nav, startDestination = HomeRoute, modifier = modifier) {
        composable<HomeRoute> { Placeholder("Home") }
        composable<ReceiptsRoute> { Placeholder("Receipts") }
        composable<ReviewRoute> { Placeholder("Review") }
        composable<MoneyRoute> { Placeholder("Money") }
        composable<CaptureRoute> { Placeholder("Capture") }
        composable<SettingsRoute> { Placeholder("Settings") }
    }
}

@Composable
private fun Placeholder(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(name) }
}
