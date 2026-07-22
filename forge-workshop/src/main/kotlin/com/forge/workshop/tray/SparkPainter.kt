package com.forge.workshop.tray

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter

/** Tray icon: the ember "spark", drawn programmatically so no image resource is needed. */
class SparkPainter(
    private val hot: Color = Color(0xFFFFB347),
    private val ember: Color = Color(0xFFFF7A34),
) : Painter() {

    override val intrinsicSize: Size = Size(32f, 32f)

    override fun DrawScope.onDraw() {
        drawRoundRect(
            brush = Brush.linearGradient(listOf(hot, ember)),
            cornerRadius = CornerRadius(size.minDimension * 0.28f),
        )
    }
}
