package com.financetracker.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.financetracker.app.ui.nav.FinanceNavHost
import com.financetracker.app.ui.security.AppLock
import com.financetracker.app.ui.security.LockedScreen
import com.financetracker.app.ui.theme.FinanceTheme

/**
 * A [FragmentActivity] rather than a plain ComponentActivity because BiometricPrompt attaches to
 * the fragment manager. FragmentActivity extends ComponentActivity, so Compose is unaffected.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as FinanceApplication

        setContent {
            FinanceTheme {
                val lockEnabled by app.settingsRepository.appLockEnabled.collectAsState(initial = false)
                var unlocked by remember { mutableStateOf(false) }
                var promptShowing by remember { mutableStateOf(false) }

                // Re-lock when the app leaves the foreground, so returning to it from the recents
                // list asks again rather than handing over an already-open ledger.
                val lifecycleOwner = LocalLifecycleOwner.current
                LaunchedEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_STOP) {
                            unlocked = false
                            promptShowing = false
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                }

                val requestUnlock: () -> Unit = {
                    if (!promptShowing) {
                        promptShowing = true
                        AppLock.prompt(
                            activity = this@MainActivity,
                            onSuccess = { unlocked = true; promptShowing = false },
                            onFailure = { promptShowing = false }
                        )
                    }
                }

                // Auto-present the prompt when the app opens locked, so the usual case is one tap
                // on a fingerprint rather than a button press followed by a fingerprint.
                LaunchedEffect(lockEnabled, unlocked) {
                    if (lockEnabled && !unlocked) requestUnlock()
                }

                if (lockEnabled && !unlocked) {
                    LockedScreen(onUnlock = requestUnlock)
                } else {
                    FinanceNavHost(application = app)
                }
            }
        }
    }
}
