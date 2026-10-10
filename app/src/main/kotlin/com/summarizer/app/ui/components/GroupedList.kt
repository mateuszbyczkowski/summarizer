package com.summarizer.app.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * Lists drawn as a group of tiles: tiles 2 dp apart, the group's outer corners rounded more
 * than the inner ones, tiles a tone lighter than the screen behind them.
 */
object GroupedListDefaults {
    val ItemSpacing = 2.dp
    private val OuterCorner = 20.dp
    private val InnerCorner = 4.dp

    /** Shape of the tile at [index] of [count]. */
    fun shape(index: Int, count: Int): Shape {
        val top = if (index == 0) OuterCorner else InnerCorner
        val bottom = if (index == count - 1) OuterCorner else InnerCorner
        return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    }

    /** Behind the tiles. */
    val screenColor: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.let { if (it.isDark) it.surface else it.surfaceContainerLow }

    val itemColor: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.let { if (it.isDark) it.surfaceContainer else it.surfaceContainerLowest }

    /** Lowest is the lightest container in light schemes but the darkest in dark ones. */
    private val androidx.compose.material3.ColorScheme.isDark get() = surface.luminance() < 0.5f
}
