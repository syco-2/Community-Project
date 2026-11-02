package com.sixmonths.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Hand-drawn 24 dp vectors for the two tabs Material's core icon set does not ship:
 * a simple energy-drink can for Monsters and clean headphones for Music. Both are single
 * tinted paths, so they inherit the navigation item colour on selection.
 */
object AppIcons {

    /** Slim can with a label window and a pull tab cut out (even-odd fill). */
    val EnergyCan: ImageVector by lazy {
        ImageVector.Builder(
            name = "EnergyCan",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd,
            ) {
                // Body: rounded shoulders, slightly rounder base.
                moveTo(9.4f, 2.6f)
                lineTo(14.6f, 2.6f)
                curveTo(16.0f, 2.6f, 17.2f, 3.8f, 17.2f, 5.2f)
                lineTo(17.2f, 17.4f)
                curveTo(17.2f, 19.6f, 15.4f, 21.4f, 13.2f, 21.4f)
                lineTo(10.8f, 21.4f)
                curveTo(8.6f, 21.4f, 6.8f, 19.6f, 6.8f, 17.4f)
                lineTo(6.8f, 5.2f)
                curveTo(6.8f, 3.8f, 8.0f, 2.6f, 9.4f, 2.6f)
                close()

                // Label window.
                moveTo(9.6f, 9.2f)
                lineTo(14.4f, 9.2f)
                lineTo(14.4f, 14.4f)
                lineTo(9.6f, 14.4f)
                close()

                // Pull tab.
                moveTo(12f, 4.5f)
                curveTo(12.7f, 4.5f, 13.2f, 5.0f, 13.2f, 5.7f)
                curveTo(13.2f, 6.4f, 12.7f, 6.9f, 12f, 6.9f)
                curveTo(11.3f, 6.9f, 10.8f, 6.4f, 10.8f, 5.7f)
                curveTo(10.8f, 5.0f, 11.3f, 4.5f, 12f, 4.5f)
                close()
            }
        }.build()
    }

    /** Filled triangle, used for the playlist transport. */
    val Play: ImageVector by lazy {
        ImageVector.Builder(
            name = "Play",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(8f, 5.4f)
                lineTo(19f, 12f)
                lineTo(8f, 18.6f)
                close()
            }
        }.build()
    }

    val Pause: ImageVector by lazy {
        ImageVector.Builder(
            name = "Pause",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(7f, 5f)
                lineTo(10.2f, 5f)
                lineTo(10.2f, 19f)
                lineTo(7f, 19f)
                close()
                moveTo(13.8f, 5f)
                lineTo(17f, 5f)
                lineTo(17f, 19f)
                lineTo(13.8f, 19f)
                close()
            }
        }.build()
    }

    val Next: ImageVector by lazy {
        ImageVector.Builder(
            name = "Next",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 5.6f)
                lineTo(14.4f, 12f)
                lineTo(6f, 18.4f)
                close()
                moveTo(15.4f, 5.6f)
                lineTo(18.4f, 5.6f)
                lineTo(18.4f, 18.4f)
                lineTo(15.4f, 18.4f)
                close()
            }
        }.build()
    }

    val Previous: ImageVector by lazy {
        ImageVector.Builder(
            name = "Previous",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(18f, 5.6f)
                lineTo(9.6f, 12f)
                lineTo(18f, 18.4f)
                close()
                moveTo(5.6f, 5.6f)
                lineTo(8.6f, 5.6f)
                lineTo(8.6f, 18.4f)
                lineTo(5.6f, 18.4f)
                close()
            }
        }.build()
    }

    /** Two crossing arrows: shuffle the whole playlist instead of playing it in order. */
    val Shuffle: ImageVector by lazy {
        ImageVector.Builder(
            name = "Shuffle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(3.5f, 7f)
                lineTo(7.5f, 7f)
                lineTo(16.5f, 17f)
                lineTo(18.4f, 17f)

                moveTo(3.5f, 17f)
                lineTo(7.5f, 17f)
                lineTo(16.5f, 7f)
                lineTo(18.4f, 7f)
            }
            path(fill = SolidColor(Color.Black)) {
                // Arrow head, top right.
                moveTo(17.6f, 4.4f)
                lineTo(21f, 7f)
                lineTo(17.6f, 9.6f)
                close()

                // Arrow head, bottom right.
                moveTo(17.6f, 14.4f)
                lineTo(21f, 17f)
                lineTo(17.6f, 19.6f)
                close()
            }
        }.build()
    }

    /** Over-ear headphones: a rounded band plus two soft ear cups. */
    val Headphones: ImageVector by lazy {
        ImageVector.Builder(
            name = "Headphones",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(4.4f, 13.2f)
                lineTo(4.4f, 12f)
                curveTo(4.4f, 7.8f, 7.8f, 4.4f, 12f, 4.4f)
                curveTo(16.2f, 4.4f, 19.6f, 7.8f, 19.6f, 12f)
                lineTo(19.6f, 13.2f)
            }
            path(fill = SolidColor(Color.Black)) {
                // Left cup.
                moveTo(4.5f, 12.4f)
                lineTo(5.9f, 12.4f)
                curveTo(6.6f, 12.4f, 7.1f, 12.9f, 7.1f, 13.6f)
                lineTo(7.1f, 18.4f)
                curveTo(7.1f, 19.1f, 6.6f, 19.6f, 5.9f, 19.6f)
                lineTo(4.5f, 19.6f)
                curveTo(3.7f, 19.6f, 3.1f, 19.0f, 3.1f, 18.2f)
                lineTo(3.1f, 13.8f)
                curveTo(3.1f, 13.0f, 3.7f, 12.4f, 4.5f, 12.4f)
                close()

                // Right cup.
                moveTo(18.1f, 12.4f)
                lineTo(19.5f, 12.4f)
                curveTo(20.3f, 12.4f, 20.9f, 13.0f, 20.9f, 13.8f)
                lineTo(20.9f, 18.2f)
                curveTo(20.9f, 19.0f, 20.3f, 19.6f, 19.5f, 19.6f)
                lineTo(18.1f, 19.6f)
                curveTo(17.4f, 19.6f, 16.9f, 19.1f, 16.9f, 18.4f)
                lineTo(16.9f, 13.6f)
                curveTo(16.9f, 12.9f, 17.4f, 12.4f, 18.1f, 12.4f)
                close()
            }
        }.build()
    }
}
