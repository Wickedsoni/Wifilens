package com.wickedcoder.wifilens.feature.more.presentation

import com.wickedcoder.wifilens.core.database.CellEntity
import com.wickedcoder.wifilens.core.database.GridPlanDao
import com.wickedcoder.wifilens.core.database.GridPlanEntity
import com.wickedcoder.wifilens.core.database.GridPlanSnapshot
import com.wickedcoder.wifilens.core.database.GridPlanWithCells
import com.wickedcoder.wifilens.core.model.AppSettings
import com.wickedcoder.wifilens.core.model.SettingsRepository
import com.wickedcoder.wifilens.core.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = RecordingSettingsRepository()
    private val planDao = FakeGridPlanDao()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newViewModel() = SettingsViewModel(repository, planDao)

    @Test
    fun `settings mirror the repository while collected`() = runTest(dispatcher) {
        val vm = newViewModel()
        backgroundScope.launch { vm.settings.collect {} }
        advanceUntilIdle()
        assertEquals(AppSettings(), vm.settings.value)

        repository.settings.value = AppSettings(theme = ThemeMode.Dark, hapticsEnabled = false)
        advanceUntilIdle()

        assertEquals(ThemeMode.Dark, vm.settings.value.theme)
        assertEquals(false, vm.settings.value.hapticsEnabled)
    }

    @Test
    fun `every setter forwards its value to the repository`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.setTheme(ThemeMode.Light)
        vm.setHapticsEnabled(false)
        vm.setHapticPaint(false)
        vm.setHapticConfirm(false)
        vm.setHapticError(false)
        vm.setAutoScanEnabled(false)
        vm.setPathLossExponent(3.5f)
        vm.setReferenceRssiAt1m(-45f)
        vm.resetPredictionModel()
        advanceUntilIdle()

        assertEquals(
            listOf(
                "theme=Light",
                "haptics=false",
                "paint=false",
                "confirm=false",
                "error=false",
                "autoScan=false",
                "exponent=3.5",
                "rssi1m=-45.0",
                "resetModel",
            ),
            repository.calls,
        )
    }

    @Test
    fun `deleting the floor plan removes the active plan`() = runTest(dispatcher) {
        val plan = GridPlanEntity(id = 7, name = "Home", width = 5, height = 5)
        planDao.plan.value = GridPlanWithCells(plan, emptyList())
        val vm = newViewModel()

        vm.deleteFloorPlan()
        advanceUntilIdle()

        assertEquals(listOf(plan), planDao.deleted)
    }

    @Test
    fun `deleting when there is no plan does nothing`() = runTest(dispatcher) {
        val vm = newViewModel()

        vm.deleteFloorPlan()
        advanceUntilIdle()

        assertTrue(planDao.deleted.isEmpty())
    }
}

private class RecordingSettingsRepository : SettingsRepository {
    override val settings = MutableStateFlow(AppSettings())
    val calls = mutableListOf<String>()

    override suspend fun setTheme(theme: ThemeMode) {
        calls += "theme=$theme"
    }

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        calls += "haptics=$enabled"
    }

    override suspend fun setHapticPaint(enabled: Boolean) {
        calls += "paint=$enabled"
    }

    override suspend fun setHapticConfirm(enabled: Boolean) {
        calls += "confirm=$enabled"
    }

    override suspend fun setHapticError(enabled: Boolean) {
        calls += "error=$enabled"
    }

    override suspend fun setAutoScanEnabled(enabled: Boolean) {
        calls += "autoScan=$enabled"
    }

    override suspend fun setPathLossExponent(value: Float) {
        calls += "exponent=$value"
    }

    override suspend fun setReferenceRssiAt1m(value: Float) {
        calls += "rssi1m=$value"
    }

    override suspend fun resetPredictionModel() {
        calls += "resetModel"
    }
}

private class FakeGridPlanDao : GridPlanDao {
    val plan = MutableStateFlow<GridPlanWithCells?>(null)
    val deleted = mutableListOf<GridPlanEntity>()

    override suspend fun insertPlan(plan: GridPlanEntity): Long = plan.id

    override suspend fun updatePlan(plan: GridPlanEntity) = Unit

    override suspend fun deletePlan(plan: GridPlanEntity) {
        deleted += plan
    }

    override suspend fun insertCells(cells: List<CellEntity>) = Unit

    override fun getActivePlan(): Flow<GridPlanWithCells?> = plan

    override fun observeSnapshot(): Flow<GridPlanSnapshot?> = plan.map { null }
}
