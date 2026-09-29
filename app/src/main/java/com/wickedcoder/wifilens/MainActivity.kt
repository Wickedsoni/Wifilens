package com.wickedcoder.wifilens

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wickedcoder.wifilens.core.designsystem.WifiLensTheme
import com.wickedcoder.wifilens.core.model.AppSettings
import com.wickedcoder.wifilens.core.model.SettingsRepository
import com.wickedcoder.wifilens.core.model.ThemeMode
import com.wickedcoder.wifilens.permissions.ScanPermissions
import com.wickedcoder.wifilens.ui.PermissionGateScreen
import com.wickedcoder.wifilens.ui.WifiLensApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The single Activity. It hosts either [WifiLensApp] or the [PermissionGateScreen], as decided by
 * [GateViewModel]. Its only permission duties are the ones that need an Activity: launching the system dialog
 * and reading `shouldShowRequestPermissionRationale`. The dialog is launched only from the gate's "Allow"
 * button, never on startup.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val gate: GateViewModel by viewModels()

    private val requestScanPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        gate.onPermissionResult(ScanPermissions.current(this), canAskAgain = ScanPermissions.canAskAgain(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate: swaps Theme.Wifilens.Starting (animated splash) for Theme.Wifilens.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        gate.refresh(ScanPermissions.current(this))

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            val gateState by gate.uiState.collectAsStateWithLifecycle()
            val darkTheme = when (settings.theme) {
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
                ThemeMode.System -> isSystemInDarkTheme()
            }
            // System bar icons follow the *app* theme, not the system one; otherwise Light-in-a-dark-system
            // leaves white status-bar icons on a light background (B-34).
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                )
                onDispose {}
            }
            WifiLensTheme(darkTheme = darkTheme, dynamicColor = settings.dynamicColor) {
                when (val state = gateState) {
                    GateUiState.ShowApp -> WifiLensApp()
                    is GateUiState.Blocked -> PermissionGateScreen(
                        state = state,
                        onPrimaryAction = { onGateAction(state.reason) },
                        onContinueWithoutScanning = gate::skipScanning,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Catches changes made outside the app: permission granted or revoked in Settings, Location toggled.
        gate.refresh(ScanPermissions.current(this))
    }

    private fun onGateAction(reason: GateReason) {
        when (reason) {
            GateReason.NeedsPermission, GateReason.ApproximateOnly -> requestScanPermission.launch(ScanPermissions.required)
            GateReason.PermanentlyDenied -> startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
            )
            // Android 10+: an in-app Wi-Fi panel instead of leaving for the Settings app.
            GateReason.WifiOff -> startActivity(
                Intent(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS),
            )
        }
    }
}
