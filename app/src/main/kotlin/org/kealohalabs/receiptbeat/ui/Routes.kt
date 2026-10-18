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
