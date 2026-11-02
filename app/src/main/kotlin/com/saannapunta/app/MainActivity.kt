package com.saannapunta.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import android.os.SystemClock
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.saannapunta.app.data.DataRepository
import com.saannapunta.app.security.BiometricLockManager
import com.saannapunta.app.ui.feedback.SoundFx
import com.saannapunta.app.ui.screens.AppLockScreen
import com.saannapunta.app.ui.screens.SplashScreen
import com.saannapunta.app.ui.theme.SaanNaPuntaTheme

class MainActivity : FragmentActivity() {

    companion object {
        const val EXTRA_PAY_BILL_ID = "extra_pay_bill_id"

        /** Short trips out (file picker, system settings, category screen) don't relock the app. */
        private const val RELOCK_AFTER_BACKGROUND_MS = 30_000L

        // Process-scoped on purpose: survives rotation, but a killed process always relocks
        // (rememberSaveable would restore "unlocked" from saved state after process death).
        private var isUnlocked by mutableStateOf(false)
        private var backgroundedAtElapsed = 0L
    }

    private var pendingPayBillIdState by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app only has a light theme, so keep dark status and navigation bar icons even in system dark mode
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        extractPayBillExtra(intent)

        setContent {
            val repository = remember { DataRepository.getInstance(applicationContext) }
            val biometricEnabled by repository.biometricLockEnabled.collectAsState()
            val onboardingCompleted by repository.onboardingCompleted.collectAsState()
            val uiSoundsEnabled by repository.uiSoundsEnabled.collectAsState()
            LaunchedEffect(uiSoundsEnabled) {
                SoundFx.init(applicationContext)
                SoundFx.enabled = uiSoundsEnabled
            }

            // When app is closed and reopened, isSplashFinished starts false, showing the splash screen.
            var isSplashFinished by remember { mutableStateOf(false) }
            val isLocked = biometricEnabled && !isUnlocked
            // Keeps MainNavigation's saved state (current tab, back stack) across a relock.
            val mainStateHolder = rememberSaveableStateHolder()

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, biometricEnabled, isSplashFinished) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        repository.recomputeOverview()
                    }
                    if (event == Lifecycle.Event.ON_START && biometricEnabled) {
                        val awayMs = SystemClock.elapsedRealtime() - backgroundedAtElapsed
                        if (backgroundedAtElapsed > 0L && awayMs > RELOCK_AFTER_BACKGROUND_MS) {
                            isUnlocked = false
                        }
                        backgroundedAtElapsed = 0L
                        if (!isUnlocked && isSplashFinished) {
                            BiometricLockManager.authenticate(
                                activity = this@MainActivity,
                                onSuccess = { isUnlocked = true },
                                onError = { /* Stay on lock screen */ }
                            )
                        }
                    } else if (event == Lifecycle.Event.ON_STOP && biometricEnabled && !isChangingConfigurations) {
                        backgroundedAtElapsed = SystemClock.elapsedRealtime()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            SaanNaPuntaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AnimatedContent(
                        targetState = when {
                            !isSplashFinished -> 0
                            isLocked -> 1
                            else -> 2
                        },
                        transitionSpec = {
                            fadeIn(animationSpec = tween(350)) togetherWith fadeOut(animationSpec = tween(250))
                        },
                        label = "AppScreenTransition"
                    ) { targetState ->
                        when (targetState) {
                            0 -> {
                                SplashScreen(
                                    isDataLoaded = onboardingCompleted != null,
                                    onSplashComplete = {
                                        isSplashFinished = true
                                        if (biometricEnabled && !isUnlocked) {
                                            BiometricLockManager.authenticate(
                                                activity = this@MainActivity,
                                                onSuccess = { isUnlocked = true },
                                                onError = { /* Stay on lock screen */ }
                                            )
                                        }
                                    }
                                )
                            }
                            1 -> {
                                AppLockScreen(
                                    onUnlockClick = {
                                        BiometricLockManager.authenticate(
                                            activity = this@MainActivity,
                                            onSuccess = { isUnlocked = true },
                                            onError = { /* Stay on lock screen */ }
                                        )
                                    }
                                )
                            }
                            else -> {
                                mainStateHolder.SaveableStateProvider("main") {
                                    MainNavigation(
                                        pendingPayBillId = pendingPayBillIdState,
                                        onConsumedPayBillId = { pendingPayBillIdState = null }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractPayBillExtra(intent)
    }

    private fun extractPayBillExtra(intent: Intent?) {
        val billId = intent?.getLongExtra(EXTRA_PAY_BILL_ID, -1L) ?: -1L
        if (billId > 0L) {
            pendingPayBillIdState = billId
            intent?.removeExtra(EXTRA_PAY_BILL_ID)
        }
    }
}
