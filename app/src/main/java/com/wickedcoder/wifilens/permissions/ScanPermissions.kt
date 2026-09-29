package com.wickedcoder.wifilens.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** What the user has allowed for Wi-Fi scanning, independent of Android version. */
enum class ScanPermission {
    Granted,
    NotGranted,

    /** Android 12+: the user chose "Approximate". Scan results need precise location, so it's not enough. */
    ApproximateOnly,
}

/**
 * The platform rule for the scan permission, kept in one place: Wi-Fi scan results require **precise location**
 * (`ACCESS_FINE_LOCATION`) on every Android version. Android 13's `NEARBY_WIFI_DEVICES` covers Wi-Fi Direct,
 * Aware and hotspot APIs but not scanning; on-device check (Sprint 4): with only that permission the platform
 * rejects `startScan` with "UID has no location permission". FINE is requested together with COARSE because
 * Android 12+ ignores a lone FINE request.
 */
object ScanPermissions {
    /** Permissions to pass to the system request dialog. */
    val required: Array<String> =
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun current(context: Context): ScanPermission = when {
        context.isGranted(Manifest.permission.ACCESS_FINE_LOCATION) -> ScanPermission.Granted
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.isGranted(Manifest.permission.ACCESS_COARSE_LOCATION) -> ScanPermission.ApproximateOnly
        else -> ScanPermission.NotGranted
    }

    /**
     * False right after a denial means Android will no longer show the dialog ("Don't ask again", or a second
     * denial on Android 11+), so the only way forward is the app's system settings page.
     */
    fun canAskAgain(activity: Activity): Boolean = required.any { activity.shouldShowRequestPermissionRationale(it) }

    private fun Context.isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
