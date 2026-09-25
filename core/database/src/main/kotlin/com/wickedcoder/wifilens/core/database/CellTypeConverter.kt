package com.wickedcoder.wifilens.core.database

import androidx.room.TypeConverter
import com.wickedcoder.wifilens.core.model.CellType
import com.wickedcoder.wifilens.core.model.Material
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private val json = Json { ignoreUnknownKeys = true }

/**
 * CellType <-> JSON. Kept as manual encode/decode (rather than @Serializable on the CellType
 * hierarchy) so :core:rf stays free of a serialization dependency/plugin.
 */
class CellTypeConverter {
    @TypeConverter
    fun fromCellType(cellType: CellType): String = json.encodeToString(JsonObject.serializer(), cellType.toJson())

    @TypeConverter
    fun toCellType(value: String): CellType = json.parseToJsonElement(value).jsonObject.toCellType()
}

private fun CellType.toJson(): JsonObject = when (this) {
    is CellType.Floor -> buildJsonObject {
        put("type", "floor")
        put("roomId", roomId)
    }

    is CellType.Empty -> buildJsonObject {
        put("type", "empty")
        put("material", material.toWireName())
    }

    CellType.Door -> buildJsonObject {
        put("type", "door")
    }
}

private fun JsonObject.toCellType(): CellType = when (val type = this["type"]?.jsonPrimitive?.content) {
    "floor" -> CellType.Floor(roomId = getValue("roomId").jsonPrimitive.int)
    "empty" -> CellType.Empty(material = getValue("material").jsonPrimitive.content.toMaterial())
    "door" -> CellType.Door
    else -> error("Unknown CellType JSON \"type\": $type")
}

private fun Material.toWireName(): String = when (this) {
    Material.Drywall -> "drywall"
    Material.Wood -> "wood"
    Material.Glass -> "glass"
    Material.Brick -> "brick"
    Material.Concrete -> "concrete"
    Material.Metal -> "metal"
}

private fun String.toMaterial(): Material = when (this) {
    "drywall" -> Material.Drywall
    "wood" -> Material.Wood
    "glass" -> Material.Glass
    "brick" -> Material.Brick
    "concrete" -> Material.Concrete
    "metal" -> Material.Metal
    else -> error("Unknown material wire name: $this")
}
