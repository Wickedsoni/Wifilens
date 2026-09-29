package com.wickedcoder.wifilens.feature.map.data

import com.wickedcoder.wifilens.core.model.CellType
import com.wickedcoder.wifilens.core.model.DevicePin
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.Material
import com.wickedcoder.wifilens.core.model.Room
import com.wickedcoder.wifilens.core.model.Vec2
import com.wickedcoder.wifilens.feature.map.domain.PlanArchive
import com.wickedcoder.wifilens.feature.map.domain.PlanImportException
import com.wickedcoder.wifilens.feature.map.domain.PlanImportProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class PlanArchiveCodecTest {
    /** 5x5: a wall column of mixed materials at x = 2, a door at (2, 2), rooms 1 and 2 either side. */
    private val archive = PlanArchive(
        name = "Home",
        plan = GridPlan(
            5,
            5,
            List(25) { i ->
                val x = i % 5
                val y = i / 5
                when {
                    x == 2 && y == 2 -> CellType.Door
                    x == 2 -> CellType.Empty(if (y % 2 == 0) Material.Concrete else Material.Glass)
                    x < 2 -> CellType.Floor(1)
                    else -> CellType.Floor(2)
                }
            },
        ),
        rooms = listOf(Room(1, "Living room"), Room(2, "Kitchen")),
        router = Vec2(0, 0),
        devices = listOf(DevicePin(Vec2(4, 4), "TV"), DevicePin(Vec2(3, 1), "Laptop")),
    )

    private fun expectProblem(problem: PlanImportProblem, text: String) {
        try {
            PlanArchiveCodec.decode(text)
            fail("expected $problem")
        } catch (e: PlanImportException) {
            assertEquals(problem, e.problem)
        }
    }

    private fun validJson() = PlanArchiveCodec.encode(archive, exportedAt = 1_700_000_000_000)

    @Test
    fun `a plan survives export and import unchanged`() {
        assertEquals(archive, PlanArchiveCodec.decode(validJson()))
    }

    @Test
    fun `text that isn't JSON is not a plan file`() = expectProblem(PlanImportProblem.NotAPlanFile, "hello, not json")

    @Test
    fun `JSON from another app is not a plan file`() = expectProblem(PlanImportProblem.NotAPlanFile, """{"name":"x","cells":[]}""")

    @Test
    fun `a file from a newer app version is refused`() =
        expectProblem(PlanImportProblem.NewerVersion, validJson().replace("\"formatVersion\": 1", "\"formatVersion\": 2"))

    @Test
    fun `a cell count that doesn't match the size is invalid`() =
        expectProblem(PlanImportProblem.InvalidContent, validJson().replace("\"width\": 5", "\"width\": 6"))

    @Test
    fun `a pin placed on a wall is invalid`() =
        expectProblem(PlanImportProblem.InvalidContent, PlanArchiveCodec.encode(archive.copy(router = Vec2(2, 0)), 0))

    @Test
    fun `an unknown wall material is invalid`() =
        expectProblem(PlanImportProblem.InvalidContent, validJson().replace("\"wconcrete\"", "\"wlava\""))

    @Test
    fun `a missing field is invalid`() =
        expectProblem(PlanImportProblem.InvalidContent, validJson().replace("\"rooms\"", "\"rumz\""))
}
