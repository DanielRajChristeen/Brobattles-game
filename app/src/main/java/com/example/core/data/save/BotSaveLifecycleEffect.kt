package com.example.core.data.save

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Triggers [onPersistNow] whenever the composable leaves composition OR the Android
 * lifecycle enters ON_PAUSE / ON_STOP, ensuring Vs Bot progress is saved before
 * navigation away, app backgrounding, or process termination.
 */
@Composable
fun BotSaveLifecycleEffect(
    enabled: Boolean,
    onPersistNow: () -> Unit
) {
    if (!enabled) return

    val latestPersist by rememberUpdatedState(onPersistNow)
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, enabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                latestPersist()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            latestPersist()
        }
    }
}
