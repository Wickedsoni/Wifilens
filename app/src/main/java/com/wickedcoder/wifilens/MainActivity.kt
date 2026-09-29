package com.wickedcoder.wifilens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.net.wifi.WifiManager
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wickedcoder.wifilens.core.designsystem.WifiLensTheme
import com.wickedcoder.wifilens.core.model.AppSettings
import com.wickedcoder.wifilens.core.model.SettingsRepository
import com.wickedcoder.wifilens.core.model.ThemeMode
import com.wickedcoder.wifilens.core.wifi.wifiEnabledFlow
import com.wickedcoder.wifilens.ui.PermissionGateScreen
import com.wickedcoder.wifilens.ui.PermissionGateState
import com.wickedcoder.wifilens.ui.WifiLensApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The app's single Activity. Owns the permission/Wi-Fi/Location gate that decides whether
 * [WifiLensApp] (the real app) or [PermissionGateScreen] gets composed — that decision, and the
 * state it depends on, has to live above the gate itself, so it's here rather than in a ViewModel
 * scoped below the gate. See [GateViewModel] for why "scanning skipped" specifically is the one
 * piece of that decision kept in a ViewModel instead of a local field.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    private var hasLocationPermission by mutableStateOf(false)
    private var permissionPermanentlyDenied by mutableStateOf(false)

    // Survives recreation (theme change, rotation) and process death; a plain field here reset the gate.
    private val gate: GateViewModel by viewModels()

    // FINE and COARSE are requested together: on API 31+ the platform requires ACCESS_COARSE_LOCATION
    // in the same request as ACCESS_FINE_LOCATION (a lone FINE request is not reliably honoured).
    // Scan results need FINE, so an "approximate only" grant still counts as not granted here.
    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants: Map<String, Boolean> ->
        hasLocationPermission = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (!hasLocationPermission) {
            permissionPermanentlyDenied = !shouldShowRequestPermissionRationale(
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        }
    }

    private fun launchLocationPermissionRequest() {
        requestLocationPermission.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        )
    }

    private fun checkFineLocationGranted(): Boolean = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    private fun isWifiEnabled(): Boolean {
        val wifiManager = getSystemService(WIFI_SERVICE) as WifiManager
        return wifiManager.isWifiEnabled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate: swaps Theme.Wifilens.Starting (animated splash) for Theme.Wifilens.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        hasLocationPermission = checkFineLocationGranted()

        if (!hasLocationPermission) {
            launchLocationPermissionRequest()
        }

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            val darkTheme = when (settings.theme) {
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
                ThemeMode.System -> isSystemInDarkTheme()
            }
            // Observed, not read once: isWifiEnabled() inside `when` was evaluated only when something
            // else happened to recompose, so turning Wi-Fi on left the gate screen up until a restart.
            val scanningSkipped by gate.scanningSkipped.collectAsStateWithLifecycle()
            val wifiEnabled by remember { wifiEnabledFlow(this@MainActivity) }.collectAsStateWithLifecycle(initialValue = isWifiEnabled())
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
                when {
                    (hasLocationPermission && wifiEnabled) || scanningSkipped -> {
                        WifiLensApp()
                    }

                    permissionPermanentlyDenied -> {
                        PermissionGateScreen(
                            state = PermissionGateState.PermanentlyDenied,
                            onGrantAccess = { openAppSettings() },
                            onContinueWithoutScanning = gate::skipScanning,
                        )
                    }

                    !hasLocationPermission -> {
                        PermissionGateScreen(
                            state = PermissionGateState.AllMissing,
                            onGrantAccess = { launchLocationPermissionRequest() },
                            onContinueWithoutScanning = gate::skipScanning,
                        )
                    }

                    else -> {
                        PermissionGateScreen(
                            state = PermissionGateState.WifiOff,
                            onGrantAccess = {
                                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                            },
                            onContinueWithoutScanning = gate::skipScanning,
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have granted the permission from system Settings (the "Open settings" path)
        // and come back; without this the gate stays up until the process restarts.
        hasLocationPermission = checkFineLocationGranted()
        if (hasLocationPermission) permissionPermanentlyDenied = false
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
        )
    }
}
