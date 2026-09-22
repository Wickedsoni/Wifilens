package com.wickedcoder.wifilens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.multiTouchSwipe
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wickedcoder.wifilens.core.designsystem.WifiLensTheme
import com.wickedcoder.wifilens.core.rf.CellType
import com.wickedcoder.wifilens.core.rf.GridPlan
import com.wickedcoder.wifilens.core.rf.Material
import com.wickedcoder.wifilens.feature.map.presentation.MapCanvas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device checks of MapCanvas's gesture handling. The map is 400dp square with a 20x20 grid, so
 * one cell is 20dp and the centre (200dp) sits on the boundary between cells 9 and 10. Zoom/pan
 * state is private to MapCanvas, so it's observed the honest way: through which cell a tap lands on.
 */
@RunWith(AndroidJUnit4::class)
class MapCanvasGestureTest {

    @get:Rule
    val rule = createComposeRule()

    private data class Touch(val x: Int, val y: Int, val isDrag: Boolean)

    private val touches = mutableListOf<Touch>()

    @Before
    fun setUp() {
        touches.clear()
        val plan = GridPlan(20, 20, List(400) { CellType.Empty(Material.Drywall) })
        rule.setContent {
            WifiLensTheme {
                Box(Modifier.size(400.dp).testTag("map")) {
                    MapCanvas(
                        plan = plan,
                        rooms = emptyList(),
                        routerPos = null,
                        devicePins = emptyList(),
                        onCellTouched = { x, y, isDrag -> touches += Touch(x, y, isDrag) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    /**
     * Taps 100dp right of centre and returns the cell it landed on. A tap made while zoomed is held
     * back for the double-tap window (200ms), so wait that out before looking.
     */
    private fun tapRightOfCentre(): Touch {
        touches.clear()
        rule.onNodeWithTag("map").performTouchInput {
            click(Offset(center.x + 100.dp.toPx(), center.y))
        }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(300)
        assertEquals("expected exactly one tap", 1, touches.size)
        return touches.single()
    }

    private fun pinchOutAboutCentre() {
        rule.onNodeWithTag("map").performTouchInput {
            pinch(
                start0 = Offset(center.x - 50.dp.toPx(), center.y),
                end0 = Offset(center.x - 150.dp.toPx(), center.y),
                start1 = Offset(center.x + 50.dp.toPx(), center.y),
                end1 = Offset(center.x + 150.dp.toPx(), center.y),
                durationMillis = 400,
            )
        }
        rule.waitForIdle()
    }

    @Test
    fun tapWithoutZoomLandsOnTheCellUnderTheFinger() {
        val tap = tapRightOfCentre()
        assertEquals(15, tap.x) // 300dp / 20dp
        assertEquals(10, tap.y)
        assertFalse(tap.isDrag)
    }

    @Test
    fun oneFingerDragPaintsAStrokeAndDoesNotZoom() {
        rule.onNodeWithTag("map").performTouchInput {
            swipe(
                start = Offset(center.x - 100.dp.toPx(), center.y),
                end = Offset(center.x + 100.dp.toPx(), center.y),
                durationMillis = 300,
            )
        }
        rule.waitForIdle()
        assertTrue("a drag should report several cells", touches.size >= 5)
        assertTrue("every cell of a drag is flagged as a drag", touches.all { it.isDrag })
        assertTrue("strokes go left to right", touches.map { it.x } == touches.map { it.x }.sorted())

        // ...and it did not zoom or pan: a tap still lands where it would have.
        assertEquals(15, tapRightOfCentre().x)
    }

    @Test
    fun pinchOutZoomsAboutTheCentroidAndTapsStillLandUnderTheFinger() {
        pinchOutAboutCentre()
        val zoomedTap = tapRightOfCentre()
        // ~3x zoom about the centre: 100dp from centre is ~33dp of plan, i.e. ~1.7 cells right of the
        // 9|10 boundary -> cell 11. Unzoomed it would be 15.
        assertTrue("expected a cell near the centre, got ${zoomedTap.x}", zoomedTap.x in 10..12)
        assertEquals(10, zoomedTap.y)
    }

    @Test
    fun pinchDoesNotPaintCells() {
        pinchOutAboutCentre()
        assertTrue("a pinch must not paint; got $touches", touches.isEmpty())
    }

    @Test
    fun twoFingerPanMovesTheViewWithoutPainting() {
        rule.onNodeWithTag("map").performTouchInput {
            val a: (Long) -> Offset = { t -> Offset(center.x - 40.dp.toPx() + 100.dp.toPx() * t / 300f, center.y) }
            val b: (Long) -> Offset = { t -> Offset(center.x + 40.dp.toPx() + 100.dp.toPx() * t / 300f, center.y) }
            multiTouchSwipe(listOf(a, b), durationMillis = 300)
        }
        rule.waitForIdle()
        assertTrue("a pan must not paint; got $touches", touches.isEmpty())

        // The plan moved 100dp (5 cells) right, so the centre now shows what used to be 5 cells left.
        touches.clear()
        rule.onNodeWithTag("map").performTouchInput { click(center) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(300) // panned, so the tap is held back for the double-tap window
        val tap = touches.single()
        assertTrue("expected about cell 5 after a 100dp pan, got ${tap.x}", tap.x in 4..6)
    }

    @Test
    fun doubleTapWhileZoomedResetsTheViewAndPaintsNothing() {
        pinchOutAboutCentre()
        touches.clear()
        rule.onNodeWithTag("map").performTouchInput {
            doubleClick(Offset(center.x + 60.dp.toPx(), center.y))
        }
        rule.waitForIdle()
        assertTrue("neither tap of a reset double-tap may paint; got $touches", touches.isEmpty())
        assertEquals("after the reset a tap lands unzoomed again", 15, tapRightOfCentre().x)
    }

    @Test
    fun tapWhileZoomedIsHeldBackForTheDoubleTapWindowThenPaints() {
        pinchOutAboutCentre()
        touches.clear()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("map").performTouchInput { click(Offset(center.x + 100.dp.toPx(), center.y)) }
        rule.mainClock.advanceTimeBy(100)
        assertTrue("held back inside the window; got $touches", touches.isEmpty())
        rule.mainClock.advanceTimeBy(300)
        assertEquals("painted once the window lapsed", 1, touches.size)
        assertFalse(touches.single().isDrag)
    }

    @Test
    fun tapAtNormalZoomIsImmediate() {
        touches.clear()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("map").performTouchInput { click(Offset(center.x + 100.dp.toPx(), center.y)) }
        rule.mainClock.advanceTimeByFrame()
        assertEquals("no hold-back at 1x", 1, touches.size)
    }

    @Test
    fun dragAfterAZoomedTapStillPaintsTheTapAndTheStroke() {
        pinchOutAboutCentre()
        touches.clear()
        rule.onNodeWithTag("map").performTouchInput { click(Offset(center.x + 100.dp.toPx(), center.y)) }
        rule.onNodeWithTag("map").performTouchInput {
            swipe(
                start = Offset(center.x - 60.dp.toPx(), center.y),
                end = Offset(center.x + 60.dp.toPx(), center.y),
                durationMillis = 300,
            )
        }
        rule.waitForIdle()
        assertTrue("the held-back tap must not be lost", touches.any { !it.isDrag })
        assertTrue("the stroke must paint", touches.count { it.isDrag } >= 3)
    }

    @Test
    fun doubleTapAtNormalZoomStillPaintsBothTaps() {
        // At 1x, rapid taps must not be swallowed as a "reset" gesture.
        touches.clear()
        rule.onNodeWithTag("map").performTouchInput {
            doubleClick(Offset(center.x + 60.dp.toPx(), center.y))
        }
        rule.waitForIdle()
        assertEquals(2, touches.size)
    }
}
