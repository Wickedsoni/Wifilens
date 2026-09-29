package com.wickedcoder.wifilens.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Brand seed colour, the launcher icon background. Fixed: the logo never follows dynamic colour. */
private val LogoBackground = Color(0xFF2F6FDE)

/** The WifiLens app icon (coverage-grid mark on the brand circle), identical to the launcher icon. */
@Composable
fun WifiLensLogo(modifier: Modifier = Modifier, size: Dp = 72.dp) {
    Box(modifier = modifier.size(size).clip(CircleShape).background(LogoBackground)) {
        Icon(
            painter = painterResource(R.drawable.wifilens_mark),
            contentDescription = stringResource(R.string.ds_logo_description),
            tint = Color.White,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
