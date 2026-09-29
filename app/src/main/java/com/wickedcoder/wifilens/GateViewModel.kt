package com.wickedcoder.wifilens

import android.content.Context
import android.net.wifi.WifiManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wickedcoder.wifilens.core.wifi.isLocationServicesEnabled
import com.wickedcoder.wifilens.core.wifi.wifiEnabledFlow
import com.wickedcoder.wifilens.permissions.ScanPermission
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Why the gate is shown instead of the app. */
enum class GateReason { NeedsPermission, ApproximateOnly, PermanentlyDenied, WifiOff }

sealed interface GateUiState {
    data object ShowApp : GateUiState

    /** Blocked, with the real device state for the gate's status rows. */
    data class Blocked(
        val reason: GateReason,
        val permission: ScanPermission,
        val wifiOn: Boolean,
        val locationServicesOn: Boolean,
    ) : GateUiState
}

/** Pure decision table for the gate, unit-tested in `GateResolverTest`. */
internal fun resolveGate(
    permission: ScanPermission,
    permanentlyDenied: Boolean,
    wifiOn: Boolean,
    locationServicesOn: Boolean,
    scanningSkipped: Boolean,
): GateUiState {
    fun blocked(reason: GateReason) = GateUiState.Blocked(reason, permission, wifiOn, locationServicesOn)
    return when {
        scanningSkipped -> GateUiState.ShowApp
        permission == ScanPermission.Granted && wifiOn -> GateUiState.ShowApp
        permission == ScanPermission.Granted -> blocked(GateReason.WifiOff)
        permission == ScanPermission.ApproximateOnly -> blocked(GateReason.ApproximateOnly)
        permanentlyDenied -> blocked(GateReason.PermanentlyDenied)
        else -> blocked(GateReason.NeedsPermission)
    }
}

/**
 * Owns the scan-permission gate that decides whether the app or [com.wickedcoder.wifilens.ui.PermissionGateScreen]
 * is shown. The Activity only feeds it facts it alone can read: the current permission ([refresh], on every
 * resume, which catches changes made in system Settings) and each request result ([onPermissionResult]). The
 * system dialog is never shown on launch; the gate screen explains first and requests on the user's tap.
 *
 * "Continue without scanning" and "Android won't ask again" can't be re-derived from the OS, so they live in
 * [SavedStateHandle] and survive rotation and process death.
 */
@HiltViewModel
class GateViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val permission = MutableStateFlow(ScanPermission.NotGranted)
        private val locationServicesOn = MutableStateFlow(isLocationServicesEnabled(context))
        private val permanentlyDenied = savedStateHandle.getStateFlow(KEY_PERMANENTLY_DENIED, false)
        private val scanningSkipped = savedStateHandle.getStateFlow(KEY_SCANNING_SKIPPED, false)
        private val wifiOn = wifiEnabledFlow(context)
            .stateIn(viewModelScope, SharingStarted.Eagerly, context.getSystemService(WifiManager::class.java).isWifiEnabled)

        val uiState: StateFlow<GateUiState> =
            combine(permission, permanentlyDenied, wifiOn, locationServicesOn, scanningSkipped, ::resolveGate)
                .stateIn(viewModelScope, SharingStarted.Eagerly, snapshot())

        /** Re-reads what the OS knows; call on every resume. */
        fun refresh(current: ScanPermission) {
            permission.value = current
            locationServicesOn.value = isLocationServicesEnabled(context)
            if (current == ScanPermission.Granted) savedStateHandle[KEY_PERMANENTLY_DENIED] = false
        }

        /** Result of the system dialog; [canAskAgain] false after a denial means only Settings can grant it now. */
        fun onPermissionResult(result: ScanPermission, canAskAgain: Boolean) {
            permission.value = result
            savedStateHandle[KEY_PERMANENTLY_DENIED] = result == ScanPermission.NotGranted && !canAskAgain
        }

        fun skipScanning() {
            savedStateHandle[KEY_SCANNING_SKIPPED] = true
        }

        /** Takes back "Continue without scanning" (Analyze's "Allow access"), so the gate explains and asks again. */
        fun resumeScanning() {
            savedStateHandle[KEY_SCANNING_SKIPPED] = false
        }

        private fun snapshot() = resolveGate(
            permission.value,
            permanentlyDenied.value,
            wifiOn.value,
            locationServicesOn.value,
            scanningSkipped.value,
        )

        private companion object {
            const val KEY_SCANNING_SKIPPED = "scanning_skipped"
            const val KEY_PERMANENTLY_DENIED = "permanently_denied"
        }
    }
