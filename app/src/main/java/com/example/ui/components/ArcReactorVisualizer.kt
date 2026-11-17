package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ArcBlue
import com.example.ui.theme.CoreWhiteCyan
import com.example.ui.theme.HoloBorder
import com.example.ui.theme.HoloPanel
import com.example.ui.theme.HudAmber
import com.example.ui.theme.NeonCyan
import kotlin.math.cos
import kotlin.math.sin

enum class VisualizerMode {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

@Composable
fun ArcReactorVisualizer(
    mode: VisualizerMode,
    rmsLevel: Float = 0f,
    size: Dp = 172.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "arc_reactor")

    val slowRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "slow_rotation"
    )

    val fastRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (mode == VisualizerMode.THINKING) 1800 else 7500,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "fast_rotation"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mode) {
                    VisualizerMode.IDLE -> 2400
                    VisualizerMode.LISTENING -> 650
                    VisualizerMode.THINKING -> 800
                    VisualizerMode.SPEAKING -> 500
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_pulse"
    )

    val animatedRms by animateFloatAsState(
        targetValue = if (mode == VisualizerMode.LISTENING) rmsLevel.coerceIn(0f, 1f) else 0f,
        animationSpec = spring(stiffness = 500f),
        label = "rms_smooth"
    )

    val primaryGlow = when (mode) {
        VisualizerMode.IDLE -> NeonCyan
        VisualizerMode.LISTENING -> Color(0xFF00F5D4)
        VisualizerMode.THINKING -> HudAmber
        VisualizerMode.SPEAKING -> NeonCyan
    }

    val secondaryGlow = when (mode) {
        VisualizerMode.THINKING -> NeonCyan
        else -> ArcBlue
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val center = Offset(w / 2f, h / 2f)
            val maxR = minOf(w, h) / 2f

            // Ambient radial energy aura
            val auraScale = 0.85f + (animatedRms * 0.25f) + ((pulse - 0.9f) * 0.35f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryGlow.copy(alpha = if (mode == VisualizerMode.IDLE) 0.20f else 0.36f),
                        secondaryGlow.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxR * auraScale
                ),
                center = center,
                radius = maxR * auraScale
            )

            // Outer tick / dashed HUD ring
            rotate(degrees = slowRotation, pivot = center) {
                val rOuter = maxR * 0.90f
                drawCircle(
                    color = primaryGlow.copy(alpha = 0.45f),
                    radius = rOuter,
                    center = center,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                    )
                )

                // Cardinal reticle ticks
                for (i in 0 until 12) {
                    val angleRad = Math.toRadians(i * 30.0)
                    val startR = maxR * 0.93f
                    val endR = maxR * if (i % 3 == 0) 0.99f else 0.96f
                    drawLine(
                        color = primaryGlow.copy(alpha = if (i % 3 == 0) 0.85f else 0.35f),
                        start = Offset(
                            center.x + (startR * cos(angleRad)).toFloat(),
                            center.y + (startR * sin(angleRad)).toFloat()
                        ),
                        end = Offset(
                            center.x + (endR * cos(angleRad)).toFloat(),
                            center.y + (endR * sin(angleRad)).toFloat()
                        ),
                        strokeWidth = if (i % 3 == 0) 2.dp.toPx() else 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // Counter-rotating segmented arc reactor ring
            rotate(degrees = fastRotation, pivot = center) {
                val rMid = maxR * 0.72f
                val arcRectSize = Size(rMid * 2f, rMid * 2f)
                val arcTopLeft = Offset(center.x - rMid, center.y - rMid)
                val sweep = if (mode == VisualizerMode.THINKING) 85f else 62f
                for (k in 0 until 3) {
                    drawArc(
                        color = if (k == 0) primaryGlow else secondaryGlow,
                        startAngle = k * 120f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcRectSize,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // Audio-reactive / speaking waveform ring
            val waveRadius = maxR * (0.52f + animatedRms * 0.14f + if (mode == VisualizerMode.SPEAKING) (pulse - 0.95f) * 0.25f else 0f)
            drawCircle(
                color = primaryGlow.copy(alpha = 0.7f),
                radius = waveRadius,
                center = center,
                style = Stroke(width = 2.5.dp.toPx())
            )

            // Inner glowing core
            val coreRadius = maxR * (0.28f * pulse + animatedRms * 0.08f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        CoreWhiteCyan,
                        primaryGlow.copy(alpha = 0.92f),
                        secondaryGlow.copy(alpha = 0.45f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = coreRadius * 1.45f
                ),
                center = center,
                radius = coreRadius * 1.45f
            )
            drawCircle(
                color = CoreWhiteCyan.copy(alpha = 0.95f),
                center = center,
                radius = coreRadius * 0.55f
            )
        }
    }
}

@Composable
fun HudPanelCard(
    modifier: Modifier = Modifier,
    borderColor: Color = HoloBorder,
    backgroundColor: Color = HoloPanel.copy(alpha = 0.88f),
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 4.dp
    ) {
        Box(content = content)
    }
}
