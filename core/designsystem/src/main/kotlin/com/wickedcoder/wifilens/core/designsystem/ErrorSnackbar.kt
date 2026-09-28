package com.wickedcoder.wifilens.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Shows [message] as an error-styled M3 Snackbar (auto-dismissing, with a dismiss action) and calls [onDismiss]
 * once it goes away so the caller can clear its state. A new message replaces the current one.
 * Place it at the bottom of a `Box` (e.g. `Modifier.align(Alignment.BottomCenter)`).
 */
@Composable
fun WifiLensErrorSnackbar(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        if (message != null) {
            hostState.showSnackbar(message = message, withDismissAction = true, duration = SnackbarDuration.Short)
            onDismiss()
        }
    }
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            dismissActionContentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
