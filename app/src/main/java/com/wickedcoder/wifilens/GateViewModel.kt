package com.wickedcoder.wifilens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * Holds the one permission-gate fact that can't be re-derived from the system: that the user chose
 * "Continue without scanning". Permission, Wi-Fi and Location are re-read from the OS on every
 * (re)creation, so they don't need to live here.
 *
 * It was a plain field on MainActivity, which is rebuilt on a theme change or rotation — so the gate
 * came back. Backed by [SavedStateHandle] it survives configuration changes *and* process death.
 * Lives outside AnalyzeViewModel on purpose: the gate is decided before the Analyze screen (and so
 * its ViewModel) is ever created.
 */
class GateViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    val scanningSkipped: StateFlow<Boolean> = savedStateHandle.getStateFlow(KEY_SCANNING_SKIPPED, false)

    fun skipScanning() {
        savedStateHandle[KEY_SCANNING_SKIPPED] = true
    }

    private companion object {
        const val KEY_SCANNING_SKIPPED = "scanning_skipped"
    }
}
