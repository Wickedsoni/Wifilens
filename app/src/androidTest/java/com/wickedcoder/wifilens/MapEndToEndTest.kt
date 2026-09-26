package com.wickedcoder.wifilens

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wickedcoder.wifilens.core.database.TransactionRunner
import com.wickedcoder.wifilens.core.database.WifiLensDatabase
import com.wickedcoder.wifilens.core.designsystem.WifiLensTheme
import com.wickedcoder.wifilens.core.model.AppSettings
import com.wickedcoder.wifilens.core.model.SettingsRepository
import com.wickedcoder.wifilens.core.model.ThemeMode
import com.wickedcoder.wifilens.feature.map.data.MapRepositoryImpl
import com.wickedcoder.wifilens.feature.map.presentation.MapScreen
import com.wickedcoder.wifilens.feature.map.presentation.MapViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.room.Room as RoomDb

/**
 * The whole Map tab driven through its UI: real Compose screen, real ViewModel, real Room (in memory).
 * Covers the flows that were missing from the UI: create plan, add / rename / delete a room, reset.
 */
@RunWith(AndroidJUnit4::class)
class MapEndToEndTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var db: WifiLensDatabase
    private lateinit var viewModel: MapViewModel
    private val stores = mutableListOf<ViewModelStore>()

    /** ViewModels live in a store so tearDown can clear them (cancelling their DB collectors) before the DB closes. */
    private fun newViewModel(repo: MapRepositoryImpl): MapViewModel {
        val store = ViewModelStore().also { stores += it }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                MapViewModel(repo, SavedStateHandle(), NoSettings()) as T
        }
        return rule.runOnUiThread { ViewModelProvider(store, factory)[MapViewModel::class.java] }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = RoomDb.inMemoryDatabaseBuilder(context, WifiLensDatabase::class.java).build()
        val repo = MapRepositoryImpl(db.gridPlanDao(), db.roomDao(), db.pinDao(), TransactionRunner(db))
        viewModel = newViewModel(repo)
        rule.setContent { WifiLensTheme { MapScreen(viewModel = viewModel) } }
    }

    @After
    fun tearDown() {
        rule.runOnUiThread { stores.forEach { it.clear() } }
        Thread.sleep(300) // let in-flight Room queries on the IO threads finish before the connection closes (slow emulators)
        db.close()
    }

    /** Sheet content can sit below the fold on a small screen; invoke the action instead of injecting a touch. */
    private fun SemanticsNodeInteraction.click() {
        performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun waitFor(message: String, condition: () -> Boolean) {
        rule.waitUntil(timeoutMillis = 10_000) { condition() }
        rule.waitForIdle()
        assert(condition()) { message }
    }

    private fun createDefaultPlan() {
        rule.onNodeWithText("Create plan", ignoreCase = true).click()
        rule.waitForIdle()
        rule.onNodeWithText("Create", ignoreCase = true).click()
        waitFor("plan should exist") { viewModel.state.value.plan != null }
    }

    private fun addRoom(name: String) {
        rule.onNodeWithText("+ New room", ignoreCase = true).click()
        rule.waitForIdle()
        rule.onNodeWithText("Room name").performTextInput(name)
        rule.onNodeWithText("Create", ignoreCase = true).click()
        waitFor("room '$name' should exist") {
            viewModel.state.value.rooms
                .any { it.name == name }
        }
    }

    @Test
    fun emptyStateHidesEditingControlsUntilAPlanExists() {
        rule.onNodeWithText("No floor plan yet", ignoreCase = true).assertExistsCompat()
        rule.onNodeWithContentDescription("Undo").assertDoesNotExistCompat()
        rule.onNodeWithText("RESET").assertDoesNotExistCompat()
    }

    @Test
    fun createPlanThenAddRenameAndDeleteARoom() {
        createDefaultPlan()
        assertEquals(
            20,
            viewModel.state.value.plan!!
                .width,
        )
        rule.onNodeWithContentDescription("Undo").assertExistsCompat()

        addRoom("Kitchen")
        assertEquals(
            listOf("Kitchen"),
            viewModel.state.value.rooms
                .map { it.name },
        )

        rule.onNodeWithText("Edit room", ignoreCase = true).click()
        rule.waitForIdle()
        rule.onNodeWithText("Room name").performTextClearance()
        rule.onNodeWithText("Room name").performTextInput("Galley")
        rule.onNodeWithText("Save", ignoreCase = true).click()
        waitFor("room should be renamed") {
            viewModel.state.value.rooms
                .singleOrNull()
                ?.name == "Galley"
        }

        rule.onNodeWithText("Edit room", ignoreCase = true).click()
        rule.waitForIdle()
        rule.onNodeWithText("Delete room", ignoreCase = true).click()
        waitFor("room should be deleted") {
            viewModel.state.value.rooms
                .isEmpty()
        }
    }

    @Test
    fun resetPlanClearsEverythingAfterConfirmation() {
        createDefaultPlan()
        addRoom("Study")

        rule.onNodeWithText("RESET").click()
        rule.waitForIdle()
        rule.onNodeWithText("Reset plan", ignoreCase = true).click()

        waitFor("plan should be cleared") { viewModel.state.value.plan == null }
        assertNull(viewModel.state.value.plan)
        assertEquals(emptyList<Any>(), viewModel.state.value.rooms)
        rule.onNodeWithText("No floor plan yet", ignoreCase = true).assertExistsCompat()
    }

    @Test
    fun persistedRoomsSurviveRecreatingTheViewModel() {
        createDefaultPlan()
        addRoom("Bedroom")
        rule.runOnUiThread { viewModel.persistIfDirty() }
        Thread.sleep(500) // let the async write commit

        val repo = MapRepositoryImpl(db.gridPlanDao(), db.roomDao(), db.pinDao(), TransactionRunner(db))
        val reloaded = newViewModel(repo)
        waitFor("reloaded VM should see the saved room") {
            reloaded.state.value.rooms
                .any { it.name == "Bedroom" }
        }
        assertNotNull(reloaded.state.value.plan)
    }
}

private fun SemanticsNodeInteraction.assertExistsCompat() = also { assertExists() }

private fun SemanticsNodeInteraction.assertDoesNotExistCompat() = also { assertDoesNotExist() }

private class NoSettings : SettingsRepository {
    override val settings = MutableStateFlow(AppSettings())

    override suspend fun setTheme(theme: ThemeMode) = Unit

    override suspend fun setDynamicColor(enabled: Boolean) = Unit

    override suspend fun setHapticsEnabled(enabled: Boolean) = Unit

    override suspend fun setHapticPaint(enabled: Boolean) = Unit

    override suspend fun setHapticConfirm(enabled: Boolean) = Unit

    override suspend fun setHapticError(enabled: Boolean) = Unit

    override suspend fun setAutoScanEnabled(enabled: Boolean) = Unit

    override suspend fun setPathLossExponent(value: Float) = Unit

    override suspend fun setReferenceRssiAt1m(value: Float) = Unit

    override suspend fun resetPredictionModel() = Unit
}
