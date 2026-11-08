package com.vztechcore.vztune.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform

private const val ANIMATION_DURATION = 3200

/**
 * VzTechCore loading animation: the condensed Vz logo drawing itself.
 * The logo only fills ~30% of the square canvas, so give it a generous size (e.g. 96-160.dp).
 */
@Composable
fun VzLoading(
    modifier: Modifier = Modifier
) {
    val easing = remember {
        CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
    }

    val transition = rememberInfiniteTransition(
        label = "VzLoading"
    )

    // "V" letter animation
    val vProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = ANIMATION_DURATION

                0f at 0
                0f at 160
                1f at 896 using easing
                1f at 2400
                0f at 3040 using easing
                0f at 3200
            }
        ),
        label = "VProgress"
    )

    // "Z" letter animation
    val zProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = ANIMATION_DURATION

                0f at 0
                0f at 800
                1f at 1600 using easing
                1f at 2400
                0f at 3040 using easing
                0f at 3200
            }
        ),
        label = "ZProgress"
    )

    // Original paths of the Vz identity (512x512 design space)
    val vPath = remember {
        Path().apply {
            moveTo(180f, 200f)
            lineTo(240f, 330f)
            lineTo(270f, 250f)
        }
    }

    val zPath = remember {
        Path().apply {
            moveTo(250f, 210f)
            lineTo(330f, 210f)
            lineTo(270f, 320f)
            lineTo(340f, 320f)
        }
    }

    val vMeasure = remember { PathMeasure() }
    val zMeasure = remember { PathMeasure() }

    val vSegment = remember { Path() }
    val zSegment = remember { Path() }

    val blueGradient = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF2196F3),
                Color(0xFF00E5FF)
            ),
            start = Offset(180f, 200f),
            end = Offset(340f, 330f)
        )
    }

    Canvas(modifier = modifier) {

        val canvasSize = minOf(size.width, size.height)
        val scale = canvasSize / 512f

        val offsetX = (size.width - canvasSize) / 2f
        val offsetY = (size.height - canvasSize) / 2f

        vMeasure.setPath(vPath, false)
        zMeasure.setPath(zPath, false)

        vSegment.reset()
        zSegment.reset()

        if (vProgress > 0f) {
            vMeasure.getSegment(
                startDistance = 0f,
                stopDistance = vMeasure.length * vProgress,
                destination = vSegment,
                startWithMoveTo = true
            )
        }

        if (zProgress > 0f) {
            zMeasure.getSegment(
                startDistance = 0f,
                stopDistance = zMeasure.length * zProgress,
                destination = zSegment,
                startWithMoveTo = true
            )
        }

        withTransform({
            translate(offsetX, offsetY)
            scale(
                scaleX = scale,
                scaleY = scale,
                pivot = Offset.Zero
            )
        }) {

            if (vProgress > 0f) {
                drawPath(
                    path = vSegment,
                    brush = blueGradient,
                    style = Stroke(
                        width = 34f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            if (zProgress > 0f) {
                drawPath(
                    path = zSegment,
                    color = Color.White,
                    style = Stroke(
                        width = 34f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

/**
 * Static version of the VzTechCore logo — no animation.
 * Used as a placeholder (e.g. an empty playlist without a cover).
 *
 * It draws the exact same V + Z as [VzLoading], so the brand stays consistent
 * between the loading screens and the empty cards.
 */
@Composable
fun VzLogoStatic(modifier: Modifier = Modifier) {
    val vPath = remember {
        Path().apply {
            moveTo(180f, 200f)
            lineTo(240f, 330f)
            lineTo(270f, 250f)
        }
    }
    val zPath = remember {
        Path().apply {
            moveTo(250f, 210f)
            lineTo(330f, 210f)
            lineTo(270f, 320f)
            lineTo(340f, 320f)
        }
    }
    val blueGradient = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF2196F3),
                Color(0xFF00E5FF)
            ),
            start = Offset(180f, 200f),
            end = Offset(340f, 330f)
        )
    }

    Canvas(modifier = modifier) {
        val canvasSize = minOf(size.width, size.height)
        val scale = canvasSize / 512f
        val offsetX = (size.width - canvasSize) / 2f
        val offsetY = (size.height - canvasSize) / 2f

        withTransform({
            translate(offsetX, offsetY)
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
        }) {
            drawPath(
                path = vPath,
                brush = blueGradient,
                style = Stroke(
                    width = 34f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            drawPath(
                path = zPath,
                color = Color.White,
                style = Stroke(
                    width = 34f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}