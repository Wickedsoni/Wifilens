package com.wickedcoder.wifilens.feature.map.data

import com.wickedcoder.wifilens.core.model.CellType
import com.wickedcoder.wifilens.core.model.DevicePin
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.Material
import com.wickedcoder.wifilens.core.model.Room
import com.wickedcoder.wifilens.core.model.Vec2
import com.wickedcoder.wifilens.feature.map.domain.PlanArchive
import com.wickedcoder.wifilens.feature.map.domain.PlanArchiveRules
import com.wickedcoder.wifilens.feature.map.domain.PlanImportException
import com.wickedcoder.wifilens.feature.map.domain.PlanImportProblem
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val FORMAT_ID = "wifilens-plan"
private const val FORMAT_VERSION = 1

// Stable on-disk codes, never enum names: R8 may rename enum constants in release builds.
private val MATERIAL_CODES = mapOf(
    Material.Drywall to "drywall",
    Material.Wood to "wood",
    Material.Glass to "glass",
    Material.Brick to "brick",
    Material.Concrete to "concrete",
    Material.Metal to "metal",
)
private val MATERIAL_BY_CODE = MATERIAL_CODES.entries.associate { (material, code) -> code to material }

/**
 * The versioned JSON plan file (format "wifilens-plan", v1). Cells are compact codes, row-major:
 * `f<roomId>` floor, `w<material>` wall, `d` door. Decoding checks the structure, then [PlanArchiveRules].
 */
object PlanArchiveCodec {
    private val json = Json { prettyPrint = true }

    fun encode(archive: PlanArchive, exportedAt: Long): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("format", FORMAT_ID)
            put("formatVersion", FORMAT_VERSION)
            put("exportedAt", exportedAt)
            put("name", archive.name)
            put("width", archive.plan.width)
            put("height", archive.plan.height)
            put("cells", buildJsonArray { archive.plan.cells.forEach { add(JsonPrimitive(it.toCode())) } })
            put(
                "rooms",
                buildJsonArray {
                    archive.rooms.forEach { room ->
                        add(
                            buildJsonObject {
                                put("id", room.id)
                                put("name", room.name)
                            },
                        )
                    }
                },
            )
            put(
                "router",
                archive.router?.let { pos ->
                    buildJsonObject {
                        put("x", pos.x)
                        put("y", pos.y)
                    }
                } ?: JsonNull,
            )
            put(
                "devices",
                buildJsonArray {
                    archive.devices.forEach { device ->
                        add(
                            buildJsonObject {
                                put("x", device.pos.x)
                                put("y", device.pos.y)
                                put("name", device.name)
                            },
                        )
                    }
                },
            )
        },
    )

    /** @throws PlanImportException for anything that isn't a valid WifiLens plan of this version or older. */
    fun decode(text: String): PlanArchive {
        val root = parseRoot(text)
        requireSupportedHeader(root)
        val archive = parseArchive(root)
        val problem = if (archive.name.isBlank()) PlanImportProblem.InvalidContent else PlanArchiveRules.problem(archive)
        return if (problem == null) archive else throw PlanImportException(problem)
    }

    /** Structure to model; a missing key, wrong type or bad cell code means the content is invalid. */
    private fun parseArchive(root: JsonObject): PlanArchive = try {
        root.toArchive()
    } catch (e: IllegalArgumentException) {
        // incl. NumberFormatException
        throw PlanImportException(PlanImportProblem.InvalidContent, e)
    } catch (e: NoSuchElementException) {
        throw PlanImportException(PlanImportProblem.InvalidContent, e)
    }

    /** Right format id, and a version this app understands (older or equal). */
    private fun requireSupportedHeader(root: JsonObject) {
        val version = root["formatVersion"]?.jsonPrimitive?.content?.toIntOrNull()
        val problem = when {
            root["format"]?.jsonPrimitive?.content != FORMAT_ID || version == null -> PlanImportProblem.NotAPlanFile
            version > FORMAT_VERSION -> PlanImportProblem.NewerVersion
            else -> null
        }
        if (problem != null) throw PlanImportException(problem)
    }

    private fun parseRoot(text: String): JsonObject = try {
        json.parseToJsonElement(text).jsonObject
    } catch (e: SerializationException) {
        throw PlanImportException(PlanImportProblem.NotAPlanFile, e)
    } catch (e: IllegalArgumentException) {
        throw PlanImportException(PlanImportProblem.NotAPlanFile, e)
    }
}

private fun JsonObject.toArchive(): PlanArchive {
    val width = getValue("width").jsonPrimitive.int
    val height = getValue("height").jsonPrimitive.int
    return PlanArchive(
        name = getValue("name").jsonPrimitive.content.trim(),
        plan = GridPlan(width, height, getValue("cells").jsonArray.map { it.jsonPrimitive.content.toCellType() }),
        rooms = getValue("rooms").jsonArray.map {
            val room = it.jsonObject
            Room(id = room.getValue("id").jsonPrimitive.int, name = room.getValue("name").jsonPrimitive.content)
        },
        router = (this["router"] as? JsonObject)?.let { Vec2(it.getValue("x").jsonPrimitive.int, it.getValue("y").jsonPrimitive.int) },
        devices = (this["devices"] as? JsonArray).orEmpty().map {
            val device = it.jsonObject
            DevicePin(
                pos = Vec2(device.getValue("x").jsonPrimitive.int, device.getValue("y").jsonPrimitive.int),
                name = device.getValue("name").jsonPrimitive.content,
            )
        },
    )
}

private fun CellType.toCode(): String = when (this) {
    is CellType.Floor -> "f$roomId"
    is CellType.Empty -> "w" + MATERIAL_CODES.getValue(material)
    CellType.Door -> "d"
}

private fun String.toCellType(): CellType = when {
    this == "d" -> CellType.Door
    startsWith("f") -> CellType.Floor(substring(1).toInt())
    startsWith("w") -> CellType.Empty(requireNotNull(MATERIAL_BY_CODE[substring(1)]) { "wall material $this" })
    else -> throw IllegalArgumentException("cell code $this")
}
