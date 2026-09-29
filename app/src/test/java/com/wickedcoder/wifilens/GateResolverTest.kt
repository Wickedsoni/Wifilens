package com.wickedcoder.wifilens

import com.wickedcoder.wifilens.permissions.ScanPermission
import org.junit.Assert.assertEquals
import org.junit.Test

class GateResolverTest {
    private fun gate(
        permission: ScanPermission = ScanPermission.NotGranted,
        permanentlyDenied: Boolean = false,
        wifiOn: Boolean = true,
        locationServicesOn: Boolean = true,
        skipped: Boolean = false,
    ) = resolveGate(permission, permanentlyDenied, wifiOn, locationServicesOn, skipped)

    private fun reasonOf(state: GateUiState) = (state as GateUiState.Blocked).reason

    @Test
    fun `granted with wifi on shows the app`() = assertEquals(GateUiState.ShowApp, gate(permission = ScanPermission.Granted))

    @Test
    fun `granted with wifi off asks to turn wifi on`() =
        assertEquals(GateReason.WifiOff, reasonOf(gate(permission = ScanPermission.Granted, wifiOn = false)))

    @Test
    fun `first launch explains before asking`() = assertEquals(GateReason.NeedsPermission, reasonOf(gate()))

    @Test
    fun `denied with dont ask again sends the user to settings`() =
        assertEquals(GateReason.PermanentlyDenied, reasonOf(gate(permanentlyDenied = true)))

    @Test
    fun `approximate location only asks for precise location`() =
        assertEquals(GateReason.ApproximateOnly, reasonOf(gate(permission = ScanPermission.ApproximateOnly)))

    @Test
    fun `approximate wins over a stale permanently denied flag`() =
        assertEquals(
            GateReason.ApproximateOnly,
            reasonOf(gate(permission = ScanPermission.ApproximateOnly, permanentlyDenied = true)),
        )

    @Test
    fun `continue without scanning always shows the app`() {
        assertEquals(GateUiState.ShowApp, gate(skipped = true))
        assertEquals(GateUiState.ShowApp, gate(skipped = true, permanentlyDenied = true, wifiOn = false))
    }

    @Test
    fun `blocked state carries the real device status for the rows`() {
        val state = gate(wifiOn = false, locationServicesOn = false) as GateUiState.Blocked
        assertEquals(false, state.wifiOn)
        assertEquals(false, state.locationServicesOn)
        assertEquals(ScanPermission.NotGranted, state.permission)
    }
}
