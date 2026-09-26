package com.wickedcoder.wifilens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wickedcoder.wifilens.core.designsystem.NothingErrorSnackbar
import com.wickedcoder.wifilens.core.designsystem.WifiLensTheme
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The shared error snackbar used by Map and Diagnose. */
@RunWith(AndroidJUnit4::class)
class SnackbarUiTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun showsTheMessageAndClearsItWhenDismissed() {
        var message by mutableStateOf<String?>("Could not save floor plan")
        rule.setContent {
            WifiLensTheme {
                Box(Modifier.fillMaxSize()) {
                    NothingErrorSnackbar(
                        message = message,
                        onDismiss = { message = null },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }

        rule.onNodeWithText("Could not save floor plan").assertExists()

        rule.onNodeWithContentDescription("Dismiss").performClick()
        rule.waitUntil(timeoutMillis = 5_000) { message == null }

        assertNull(message)
    }
}
