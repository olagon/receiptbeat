package org.kealohalabs.receiptbeat.core.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("settings")

enum class AiProvider { NONE, GEMINI, CLAUDE, OPENAI }

data class Settings(
    val onboarded: Boolean = false,
    val userName: String = "",
    val theme: String = "SYSTEM",
    val dynamicColor: Boolean = false,
    val homeCurrency: String = "USD",
    val appLock: Boolean = false,
    val lockTimeoutSec: Int = 60,
    val secureScreen: Boolean = false,
    val hideAmounts: Boolean = false,
    val aiProvider: AiProvider = AiProvider.NONE,
    val aiSendImages: Boolean = false,
    val remindInboxDays: Int = 3,
    val remindUnpaidDays: Int = 30,
    val remindSubmitBeforeDays: Int = 3,
    val remindTaxJanuary: Boolean = true,
    val remindersOn: Boolean = true,
    val retentionYears: Int = 7,
    val useWork: Boolean = true,
    val useTaxes: Boolean = false,
    val useHousehold: Boolean = false
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val userName = stringPreferencesKey("userName")
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamicColor")
        val homeCurrency = stringPreferencesKey("homeCurrency")
        val appLock = booleanPreferencesKey("appLock")
        val lockTimeoutSec = intPreferencesKey("lockTimeoutSec")
        val secureScreen = booleanPreferencesKey("secureScreen")
        val hideAmounts = booleanPreferencesKey("hideAmounts")
        val aiProvider = stringPreferencesKey("aiProvider")
        val aiSendImages = booleanPreferencesKey("aiSendImages")
        val remindInboxDays = intPreferencesKey("remindInboxDays")
        val remindUnpaidDays = intPreferencesKey("remindUnpaidDays")
        val remindSubmitBeforeDays = intPreferencesKey("remindSubmitBeforeDays")
        val remindTaxJanuary = booleanPreferencesKey("remindTaxJanuary")
        val remindersOn = booleanPreferencesKey("remindersOn")
        val retentionYears = intPreferencesKey("retentionYears")
        val useWork = booleanPreferencesKey("useWork")
        val useTaxes = booleanPreferencesKey("useTaxes")
        val useHousehold = booleanPreferencesKey("useHousehold")
    }

    val settings: Flow<Settings> = context.store.data.map { p -> p.toSettings() }

    suspend fun current(): Settings = settings.first()

    suspend fun update(change: (Settings) -> Settings) {
        context.store.edit { p ->
            val s = change(p.toSettings())
            p[K.onboarded] = s.onboarded
            p[K.userName] = s.userName
            p[K.theme] = s.theme
            p[K.dynamicColor] = s.dynamicColor
            p[K.homeCurrency] = s.homeCurrency
            p[K.appLock] = s.appLock
            p[K.lockTimeoutSec] = s.lockTimeoutSec
            p[K.secureScreen] = s.secureScreen
            p[K.hideAmounts] = s.hideAmounts
            p[K.aiProvider] = s.aiProvider.name
            p[K.aiSendImages] = s.aiSendImages
            p[K.remindInboxDays] = s.remindInboxDays
            p[K.remindUnpaidDays] = s.remindUnpaidDays
            p[K.remindSubmitBeforeDays] = s.remindSubmitBeforeDays
            p[K.remindTaxJanuary] = s.remindTaxJanuary
            p[K.remindersOn] = s.remindersOn
            p[K.retentionYears] = s.retentionYears
            p[K.useWork] = s.useWork
            p[K.useTaxes] = s.useTaxes
            p[K.useHousehold] = s.useHousehold
        }
    }

    private fun Preferences.toSettings(): Settings {
        val d = Settings()
        return Settings(
            onboarded = this[K.onboarded] ?: d.onboarded,
            userName = this[K.userName] ?: d.userName,
            theme = this[K.theme] ?: d.theme,
            dynamicColor = this[K.dynamicColor] ?: d.dynamicColor,
            homeCurrency = this[K.homeCurrency] ?: d.homeCurrency,
            appLock = this[K.appLock] ?: d.appLock,
            lockTimeoutSec = this[K.lockTimeoutSec] ?: d.lockTimeoutSec,
            secureScreen = this[K.secureScreen] ?: d.secureScreen,
            hideAmounts = this[K.hideAmounts] ?: d.hideAmounts,
            aiProvider = this[K.aiProvider]?.let { runCatching { AiProvider.valueOf(it) }.getOrNull() } ?: d.aiProvider,
            aiSendImages = this[K.aiSendImages] ?: d.aiSendImages,
            remindInboxDays = this[K.remindInboxDays] ?: d.remindInboxDays,
            remindUnpaidDays = this[K.remindUnpaidDays] ?: d.remindUnpaidDays,
            remindSubmitBeforeDays = this[K.remindSubmitBeforeDays] ?: d.remindSubmitBeforeDays,
            remindTaxJanuary = this[K.remindTaxJanuary] ?: d.remindTaxJanuary,
            remindersOn = this[K.remindersOn] ?: d.remindersOn,
            retentionYears = this[K.retentionYears] ?: d.retentionYears,
            useWork = this[K.useWork] ?: d.useWork,
            useTaxes = this[K.useTaxes] ?: d.useTaxes,
            useHousehold = this[K.useHousehold] ?: d.useHousehold
        )
    }
}
