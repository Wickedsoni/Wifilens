package com.wickedcoder.wifilens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wickedcoder.wifilens.core.designsystem.WifiLensChip
import com.wickedcoder.wifilens.core.designsystem.WifiLensPrimaryButton
import com.wickedcoder.wifilens.core.designsystem.WifiLensTheme
import com.wickedcoder.wifilens.core.designsystem.brandDarkScheme
import com.wickedcoder.wifilens.core.designsystem.brandLightScheme
import com.wickedcoder.wifilens.core.designsystem.danger
import com.wickedcoder.wifilens.core.designsystem.success
import com.wickedcoder.wifilens.core.designsystem.warning
import com.wickedcoder.wifilens.feature.map.presentation.MapTool
import com.wickedcoder.wifilens.feature.map.presentation.ToolDock
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Automated accessibility checks (Accessibility Test Framework, the engine behind TalkBack audits):
 * touch-target size, missing labels and colour contrast measured on the rendered pixels.
 * This complements, not replaces, a hands-on TalkBack pass.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityTest {
    @get:Rule
    val rule = createComposeRule()

    private fun audit(content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.enableAccessibilityChecks()
        rule.setContent { content() }
        rule.waitForIdle()
        rule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun toolDockPassesTheAudit() = audit {
        WifiLensTheme { ToolDock(activeTool = MapTool.Room, onToolSelected = {}) }
    }

    @Test
    fun primaryButtonPassesTheAudit() = audit {
        WifiLensTheme { Column(Modifier.padding(16.dp)) { WifiLensPrimaryButton(text = "Create", onClick = {}) } }
    }

    @Test
    fun chipsPassTheAudit() = audit {
        WifiLensTheme {
            Column(Modifier.padding(16.dp)) {
                WifiLensChip(text = "Kitchen", selected = true, onClick = {})
                WifiLensChip(text = "+ New room", selected = false, onClick = {})
            }
        }
    }

    @Test
    fun textTonesPassOnDarkTheme() = audit { TextSamples(brandDarkScheme) }

    @Test
    fun textTonesPassOnLightTheme() = audit { TextSamples(brandLightScheme) }

    @androidx.compose.runtime.Composable
    private fun TextSamples(colors: ColorScheme) {
        Column(Modifier.background(colors.surface).padding(16.dp)) {
            Text("Primary text sample", style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            Text("Secondary text sample", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            Text("Caption text sample", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Text("Warning text sample", style = MaterialTheme.typography.bodyLarge, color = colors.warning)
            Text("Success text sample", style = MaterialTheme.typography.bodyLarge, color = colors.success)
            Text("Error text sample", style = MaterialTheme.typography.bodyLarge, color = colors.danger)
        }
    }
}
