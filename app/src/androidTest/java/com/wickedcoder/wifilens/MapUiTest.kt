package com.wickedcoder.wifilens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wickedcoder.wifilens.core.designsystem.WifiLensTheme
import com.wickedcoder.wifilens.core.rf.CellType
import com.wickedcoder.wifilens.core.rf.GridPlan
import com.wickedcoder.wifilens.core.rf.Material
import com.wickedcoder.wifilens.feature.map.presentation.CreatePlanSheet
import com.wickedcoder.wifilens.feature.map.presentation.IsoCanvas
import com.wickedcoder.wifilens.feature.map.presentation.MapCanvas
import com.wickedcoder.wifilens.feature.map.presentation.MapTool
import com.wickedcoder.wifilens.feature.map.presentation.NewRoomSheet
import com.wickedcoder.wifilens.feature.map.presentation.ToolDock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Compose component tests for the Map tab's controls, plus render-time budgets for big plans. */
@RunWith(AndroidJUnit4::class)
class MapUiTest {

    private companion object {
        // Motorola Edge 40: 2D ~60ms. Pixel 8a emulator: 200ms-2s depending on host load (ISO 0.7-2.1s). The old ISO renderer took ~5s even when idle.
        const val BUDGET_MS = 2_500
    }

    @get:Rule
    val rule = createComposeRule()

    /** Sheet content can sit below the fold of a small screen, where injected touches fail; invoke the click action directly. */
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.click() {
        performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
    }

    // ---- tool dock (B-11, V-6) ----------------------------------------------------------------

    @Test
    fun dockShowsEveryToolAndReportsSelection() {
        var selected = MapTool.Room
        rule.setContent {
            WifiLensTheme { ToolDock(activeTool = selected, onToolSelected = { selected = it }) }
        }

        MapTool.entries.forEach { rule.onNodeWithText(it.name.uppercase()).assertExists() }
        rule.onNodeWithText("ROOM").assertIsSelected()
        rule.onNodeWithText("DEVICE").performClick()
        assertEquals(MapTool.Device, selected)
    }

    @Test
    fun dockButtonsMeetTheMinimumTouchTarget() {
        rule.setContent { WifiLensTheme { ToolDock(activeTool = MapTool.Room, onToolSelected = {}) } }

        MapTool.entries.forEach {
            rule.onNodeWithText(it.name.uppercase()).assertHasClickAction().assertHeightIsAtLeast(48.dp)
        }
    }

    // ---- create plan sheet (B-16) -------------------------------------------------------------

    @Test
    fun createPlanRejectsOutOfRangeSizesWithAMessage() {
        var created: Pair<Int, Int>? = null
        rule.setContent {
            WifiLensTheme { CreatePlanSheet(onDismiss = {}, onCreate = { w, h -> created = w to h }) }
        }
        rule.waitForIdle()

        rule.onNodeWithText("Width (tiles)").performTextClearance()
        rule.onNodeWithText("Width (tiles)").performTextInput("1")
        rule.onNodeWithText("Create", ignoreCase = true).click()

        rule.onNodeWithText("Width and height must each be between 5 and 200 tiles.").assertExists()
        assertEquals(null, created)
    }

    @Test
    fun createPlanAcceptsValidSizes() {
        var created: Pair<Int, Int>? = null
        rule.setContent {
            WifiLensTheme { CreatePlanSheet(onDismiss = {}, onCreate = { w, h -> created = w to h }) }
        }
        rule.waitForIdle()

        rule.onNodeWithText("Create", ignoreCase = true).click()

        assertEquals(20 to 20, created)
    }

    // ---- new room sheet (B-17) ----------------------------------------------------------------

    @Test
    fun newRoomExplainsBlankAndDuplicateNames() {
        var created: String? = null
        rule.setContent {
            WifiLensTheme {
                NewRoomSheet(onDismiss = {}, existingNames = listOf("Kitchen"), onCreate = { created = it })
            }
        }
        rule.waitForIdle()

        rule.onNodeWithText("Create", ignoreCase = true).click()
        rule.onNodeWithText("Enter a room name").assertExists()

        rule.onNodeWithText("Room name").performTextInput("kitchen")
        rule.onNodeWithText("Create", ignoreCase = true).click()
        rule.onNodeWithText("A room called \"kitchen\" already exists").assertExists()
        assertEquals(null, created)

        rule.onNodeWithText("Room name").performTextClearance()
        rule.onNodeWithText("Room name").performTextInput("Study")
        rule.onNodeWithText("Create", ignoreCase = true).click()
        assertEquals("Study", created)
    }

    // ---- render budgets for large plans (B-13) ------------------------------------------------

    private fun bigPlan(): GridPlan {
        // Mostly walls with a painted room in the middle: the realistic worst case for both renderers.
        val cells = List(200 * 200) { i ->
            val x = i % 200
            val y = i / 200
            if (x in 40..160 && y in 40..160) CellType.Floor(1) else CellType.Empty(Material.Drywall)
        }
        return GridPlan(200, 200, cells)
    }

    private fun renderMillis(content: @androidx.compose.runtime.Composable () -> Unit): Long {
        // API 26-29 images here are software-rendered x86 emulators: a 200x200 frame exceeds Compose's idle
        // timeout regardless of the code, so the budget only means something on hardware / newer images.
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 30)
        rule.setContent { WifiLensTheme { Box(Modifier.size(360.dp, 640.dp).testTag("canvas")) { content() } } }
        rule.waitForIdle()
        val start = System.nanoTime()
        rule.onNodeWithTag("canvas").captureToImage() // forces a real draw
        return (System.nanoTime() - start) / 1_000_000
    }

    @Test
    fun twoDimensionalEditorDrawsA200x200PlanQuickly() {
        val plan = bigPlan()
        val ms = renderMillis {
            MapCanvas(plan, emptyList(), null, emptyList(), onCellTouched = { _, _, _ -> }, modifier = Modifier.fillMaxSize())
        }
        println("MapCanvas 200x200 first draw: ${ms}ms")
        assertTrue("2D draw took ${ms}ms", ms < BUDGET_MS)
    }

    @Test
    fun isometricViewDrawsA200x200PlanQuickly() {
        val plan = bigPlan()
        val ms = renderMillis {
            IsoCanvas(plan, emptyList(), null, emptyList(), wallRiseProgress = 1f, rotationAngle = 0.6f)
        }
        println("IsoCanvas 200x200 first draw: ${ms}ms")
        assertTrue("ISO draw took ${ms}ms", ms < BUDGET_MS)
    }
}
