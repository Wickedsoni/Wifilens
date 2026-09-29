package com.wickedcoder.wifilens.core.designsystem

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Text produced outside Compose (ViewModels) without holding a Context: a string resource with format args, or
 * a plain string from data. Resolved at the edge with [asString], so it follows the current locale.
 */
sealed interface UiText {
    data class Resource(
        @StringRes val id: Int,
        val args: List<Any> = emptyList(),
    ) : UiText

    data class Dynamic(val value: String) : UiText
}

@Suppress("SpreadOperator") // getString/stringResource take varargs; args lists are tiny.
@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Resource -> stringResource(id, *args.toTypedArray())
    is UiText.Dynamic -> value
}

@Suppress("SpreadOperator")
fun UiText.asString(context: Context): String = when (this) {
    is UiText.Resource -> context.getString(id, *args.toTypedArray())
    is UiText.Dynamic -> value
}
