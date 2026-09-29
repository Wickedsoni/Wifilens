package com.wickedcoder.wifilens.feature.map.presentation

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.wickedcoder.wifilens.core.designsystem.UiText
import com.wickedcoder.wifilens.core.model.Material
import com.wickedcoder.wifilens.feature.map.domain.RoomNameProblem

/** How a rejected room name is explained; shared by the room sheets and the ViewModel. */
internal fun RoomNameProblem.toUiText(): UiText = when (this) {
    RoomNameProblem.Blank -> UiText.Resource(R.string.map_room_name_blank)
    is RoomNameProblem.TooLong -> UiText.Resource(R.string.map_room_name_too_long, listOf(maxLength))
    is RoomNameProblem.Duplicate -> UiText.Resource(R.string.map_room_name_duplicate, listOf(name))
}

@StringRes
internal fun Material.labelRes(): Int = when (this) {
    Material.Drywall -> R.string.map_material_drywall
    Material.Wood -> R.string.map_material_wood
    Material.Glass -> R.string.map_material_glass
    Material.Brick -> R.string.map_material_brick
    Material.Concrete -> R.string.map_material_concrete
    Material.Metal -> R.string.map_material_metal
}

/** Explicit resources: `::class.simpleName` is renamed by R8 in release builds. */
@Composable
internal fun Material.displayName(): String = stringResource(labelRes())
