package com.sixmonths.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.sixmonths.app.R

/**
 * The supplied logo, with its white background removed (see tools/make_brand_assets.py).
 *
 * One asset feeds the launcher icon, the cold-start splash, the welcome screen and the
 * dashboard header, so the mark never has to be redrawn or re-exported per screen.
 */
@Composable
fun SixMonthsLogo(modifier: Modifier = Modifier.size(120.dp)) {
    Image(
        painter = painterResource(R.drawable.logo),
        contentDescription = "Six Months logo",
        modifier = modifier,
    )
}
