package com.wickedcoder.wifilens.feature.map.presentation

import com.wickedcoder.wifilens.core.designsystem.UiText
import com.wickedcoder.wifilens.core.model.CellType
import com.wickedcoder.wifilens.core.model.DevicePin
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.Measurement
import com.wickedcoder.wifilens.core.model.PlanCalibration
import com.wickedcoder.wifilens.core.model.Room
import com.wickedcoder.wifilens.core.model.Vec2
import com.wickedcoder.wifilens.core.model.WifiConnectionInfo
import com.wickedcoder.wifilens.core.model.WifiConnectionRepository
import com.wickedcoder.wifilens.core.rf.predictRssi
import com.wickedcoder.wifilens.core.testing.MainDispatcherRule
import com.wickedcoder.wifilens.feature.map.domain.MapRepository
import com.wickedcoder.wifilens.feature.map.domain.PlanSnapshot
import com.wickedcoder.wifilens.feature.map.domain.SurveyRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SurveyViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val plan = GridPlan(20, 20, List(400) { CellType.Floor(1) })
    private val maps = FakeSurveyMapRepository(plan)
    private val survey = FakeSurveyRepository()
    private val wifi = FakeConnection()

    private fun viewModel() = SurveyViewModel(maps, survey, wifi)

    private fun connected(rssi: Int, bssid: String? = "aa:bb") = WifiConnectionInfo.Connected("Home", rssi, 400, 5180, bssid)

    @Test
    fun `sampling skips disconnected moments and keeps the first visible BSSID`() = runTest {
        val readings = flowOf(WifiConnectionInfo.Disconnected, connected(-60, null), connected(-62), connected(-64, "cc:dd"))

        val sampled = sampleRssi(readings, count = 3, timeoutMs = 1_000)

        assertEquals(listOf(-60, -62, -64), sampled.rssi)
        assertEquals("aa:bb", sampled.bssid)
    }

    @Test
    fun `sampling stops at the timeout with what it has`() = runTest {
        val slow = flow {
            emit(connected(-50))
            delay(10_000)
            emit(connected(-70))
        }

        val sampled = sampleRssi(slow, count = 6, timeoutMs = 2_000)

        assertEquals(listOf(-50), sampled.rssi)
    }

    @Test
    fun `measuring a tile saves the averaged reading and confirms once`() = runTest(mainDispatcherRule.testDispatcher) {
        wifi.values = listOf(-60, -62, -64, -60, -62, -64)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onAction(SurveyAction.Measure(Vec2(4, 5)))
        advanceUntilIdle()

        assertEquals(listOf(Reading(Vec2(4, 5), "aa:bb", -62f, SAMPLES_PER_READING)), survey.readings)
        assertEquals(1, vm.state.value.completedReadings)
        assertNull(vm.state.value.measuringAt)
    }

    @Test
    fun `measuring without Wi-Fi reports it and saves nothing`() = runTest(mainDispatcherRule.testDispatcher) {
        wifi.values = emptyList()
        val vm = viewModel()
        advanceUntilIdle()

        vm.onAction(SurveyAction.Measure(Vec2(1, 1)))
        advanceUntilIdle()

        assertTrue(survey.readings.isEmpty())
        assertEquals(UiText.Resource(R.string.map_survey_error_no_wifi), vm.state.value.errorMessage)
    }

    @Test
    fun `calibrating needs the router pin`() = runTest(mainDispatcherRule.testDispatcher) {
        maps.router.value = null
        survey.stored.value = spreadReadings(-40f, 3f)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onAction(SurveyAction.Calibrate)

        assertEquals(UiText.Resource(R.string.map_survey_error_no_router), vm.state.value.errorMessage)
    }

    @Test
    fun `calibrating with too few readings says how many are needed`() = runTest(mainDispatcherRule.testDispatcher) {
        survey.stored.value = spreadReadings(-40f, 3f).take(2)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onAction(SurveyAction.Calibrate)

        val error = vm.state.value.errorMessage as UiText.Resource
        assertEquals(R.string.map_survey_error_too_few, error.id)
    }

    @Test
    fun `calibrating stores the fitted model for the plan`() = runTest(mainDispatcherRule.testDispatcher) {
        survey.stored.value = spreadReadings(-40f, 3f)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onAction(SurveyAction.Calibrate)
        advanceUntilIdle()

        val saved = requireNotNull(survey.calibration)
        assertEquals(3f, saved.pathLossExponent, 0.05f)
        assertEquals(-40f, saved.referenceRssiAt1m, 0.2f)
        assertEquals(R.string.map_survey_calibrated, (vm.state.value.infoMessage as UiText.Resource).id)
    }

    private fun spreadReadings(a: Float, n: Float) =
        listOf(Vec2(1, 0), Vec2(3, 0), Vec2(6, 2), Vec2(10, 5), Vec2(15, 10), Vec2(19, 19)).map { pos ->
            Measurement(pos, predictRssi(plan, ROUTER, pos, a, n), 6)
        }
}

private val ROUTER = Vec2(0, 0)

private data class Reading(val pos: Vec2, val bssid: String?, val rssi: Float, val count: Int)

private class FakeSurveyRepository : SurveyRepository {
    val readings = mutableListOf<Reading>()
    val stored = MutableStateFlow<List<Measurement>>(emptyList())
    var calibration: PlanCalibration? = null

    override fun observeMeasurements(planId: Long): Flow<List<Measurement>> = stored

    override suspend fun addReading(planId: Long, pos: Vec2, bssid: String?, rssi: Float, sampleCount: Int) {
        readings += Reading(pos, bssid, rssi, sampleCount)
    }

    override suspend fun clearMeasurements(planId: Long) {
        stored.value = emptyList()
        calibration = null
    }

    override suspend fun setCalibration(planId: Long, calibration: PlanCalibration?) {
        this.calibration = calibration
    }
}

/** Emits [values] as connected readings, then stays disconnected. */
private class FakeConnection : WifiConnectionRepository {
    var values: List<Int> = emptyList()

    override fun observe(): Flow<WifiConnectionInfo> = emptyFlow()

    override fun observeLive(periodMillis: Long): Flow<WifiConnectionInfo> = flow {
        values.forEach {
            emit(WifiConnectionInfo.Connected("Home", it, 400, 5180, "aa:bb"))
            delay(periodMillis)
        }
        while (true) {
            emit(WifiConnectionInfo.Disconnected)
            delay(periodMillis)
        }
    }
}

/** Only what the survey reads: the active plan and its router pin. */
private class FakeSurveyMapRepository(plan: GridPlan) : MapRepository {
    val router = MutableStateFlow<Vec2?>(ROUTER)
    private val snapshot = MutableStateFlow(PlanSnapshot(planId = 7, name = "Home", plan = plan, rooms = emptyList()))

    override fun getActivePlan(): Flow<GridPlan?> = snapshot.map { it.plan }

    override fun observePlan(): Flow<PlanSnapshot> = snapshot

    override fun getRooms(): Flow<List<Room>> = flowOf(emptyList())

    override fun getRouterPin(): Flow<Vec2?> = router

    override fun getDevicePins(): Flow<List<DevicePin>> = flowOf(emptyList())

    override suspend fun savePlan(planId: Long, plan: GridPlan, rooms: List<Room>) = Unit

    override suspend fun clearPlan() = Unit

    override suspend fun setRouterPin(pos: Vec2, band: String) = Unit

    override suspend fun addDevicePin(pos: Vec2, name: String) = Unit

    override suspend fun removeDevicePin(pos: Vec2) = Unit
}
