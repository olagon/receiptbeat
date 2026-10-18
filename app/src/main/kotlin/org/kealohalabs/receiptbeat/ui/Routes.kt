package org.kealohalabs.receiptbeat.ui

import kotlinx.serialization.Serializable

@Serializable object HomeRoute

@Serializable data class ReceiptsRoute(val filter: String? = null)

@Serializable object ReviewRoute

@Serializable data class MoneyRoute(val taxes: Boolean = false)

@Serializable data class CaptureRoute(val mode: String = "single")

@Serializable data class ReceiptRoute(val id: String)

@Serializable object SettingsRoute

@Serializable object OnboardingRoute

@Serializable object MailAccountsRoute

@Serializable data class EmailWizardRoute(val provider: String? = null)

@Serializable object BackupRoute

@Serializable object TripsRoute

/**
 * The one way to move around the app. Home is the root. Going to a tab (Receipts, Review, Money)
 * always starts again from Home, so a tab never comes back showing some other screen.
 * Anything else (a receipt, settings, capture) opens on top of where you are.
 */
fun androidx.navigation.NavController.go(route: Any) {
    when (route) {
        HomeRoute -> popBackStack(HomeRoute, inclusive = false)
        is ReceiptsRoute, ReviewRoute, is MoneyRoute -> navigate(route) {
            popUpTo<HomeRoute>()
            launchSingleTop = true
        }
        else -> navigate(route)
    }
}
