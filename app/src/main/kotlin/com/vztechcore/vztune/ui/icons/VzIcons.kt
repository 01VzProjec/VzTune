package com.vztechcore.vztune.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

object VzIcons {
    private val cache = HashMap<String, ImageVector>()

    fun icon(name: String, pathData: String): ImageVector {
        return cache.getOrPut(name) {
            ImageVector.Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                addPath(
                    pathData = PathParser().parsePathString(pathData).toNodes(),
                    fill = SolidColor(Color.Black)
                )
            }.build()
        }
    }
}
