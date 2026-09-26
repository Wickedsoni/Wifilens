package com.wickedcoder.wifilens.core.designsystem

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Deep red for error messages: white text on it is ~6:1, where the brand accent gives only ~4:1 either way. */
private val ErrorSnackbarContainer = Color(0xFFB3141B)

/**
 * Shows [message] as a Material 3 Snackbar (auto-dismissing, with a dismiss action) and calls
 * [onDismiss] once it goes away so the caller can clear its state. A new message replaces the current one.
 * Place it at the bottom of a `Box` (e.g. `Modifier.align(Alignment.BottomCenter)`).
 */
@Composable
fun NothingErrorSnackbar(
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
            containerColor = ErrorSnackbarContainer,
            contentColor = Color.White,
            dismissActionContentColor = Color.White,
        )
    }
}
