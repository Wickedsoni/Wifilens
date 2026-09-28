package com.wickedcoder.wifilens.core.common

import kotlinx.coroutines.flow.SharingStarted

/** Loading/success/error wrapper that a ViewModel maps a repository flow into before building its UI state. */
sealed interface Async<out T> {
    data object Loading : Async<Nothing>

    data class Success<out T>(val data: T) : Async<T>

    data class Error(val throwable: Throwable) : Async<Nothing>
}

private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * `stateIn` policy for UI state: upstream stays active for 5 s after the last collector leaves, so a
 * configuration change doesn't restart scans or queries, while leaving the app does stop them.
 */
val WhileUiSubscribed: SharingStarted = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS)
