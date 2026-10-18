package org.kealohalabs.receiptbeat

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import org.kealohalabs.receiptbeat.core.designsystem.Brand
import org.kealohalabs.receiptbeat.ui.onboarding.Heartbeat

/** Fingerprint, face or the phone's PIN. The lock comes back after the chosen time away. */
object AppLock {
    private var unlockedAt = 0L
    private var leftAt = 0L

    fun left() {
        leftAt = System.currentTimeMillis()
    }

    fun needsUnlock(timeoutSec: Int): Boolean =
        unlockedAt == 0L || (leftAt > unlockedAt && System.currentTimeMillis() - leftAt > timeoutSec * 1000L)

    fun prompt(activity: FragmentActivity, done: () -> Unit) {
        // No fingerprint, face or screen lock set up on the phone: there is nothing to check against, so never lock the user out.
        if (BiometricManager.from(activity).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) != BiometricManager.BIOMETRIC_SUCCESS) {
            unlockedAt = System.currentTimeMillis()
            done()
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlockedAt = System.currentTimeMillis()
                    done()
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Receipt Beat")
                .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                .build(),
        )
    }
}

@Composable
fun LockScreen(unlock: () -> Unit) {
    LaunchedEffect(Unit) { unlock() }
    Column(
        Modifier.fillMaxSize().background(Brand.Teal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Heartbeat(Modifier.size(120.dp))
        Spacer(Modifier.height(24.dp))
        Text("Receipt Beat is locked", color = Brand.Paper, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        Button(unlock, colors = ButtonDefaults.buttonColors(containerColor = Brand.Coral, contentColor = Brand.Teal)) { Text("Unlock") }
    }
}
