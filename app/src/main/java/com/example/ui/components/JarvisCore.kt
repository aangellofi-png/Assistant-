package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisBlue
import com.example.ui.theme.JarvisMagenta
import com.example.utils.JarvisVoiceManager
import kotlin.math.sin

@Composable
fun JarvisCore(
    state: JarvisVoiceManager.SpeechState,
    soundLevel: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "JarvisCoreTransition")

    // Core Rotation Angle
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    // Counter-Rotation Angle
    val counterRotationAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "CounterRotation"
    )

    // Pulse scale (breathing effect)
    val basePulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    // Select color accents based on current state
    val coreColor = when (state) {
        JarvisVoiceManager.SpeechState.LISTENING -> JarvisMagenta
        JarvisVoiceManager.SpeechState.PROCESSING -> JarvisCyan
        JarvisVoiceManager.SpeechState.SPEAKING -> JarvisBlue
        JarvisVoiceManager.SpeechState.ERROR -> Color.Red
        else -> JarvisCyan
    }

    val glowScale = if (state == JarvisVoiceManager.SpeechState.LISTENING) {
        basePulseScale + (soundLevel * 0.4f)
    } else if (state == JarvisVoiceManager.SpeechState.SPEAKING) {
        basePulseScale + 0.15f
    } else {
        basePulseScale
    }

    Box(
        modifier = modifier.size(240.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val baseRadius = size.width.coerceAtMost(size.height) / 2

            // Background glow aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        coreColor.copy(alpha = 0.25f * glowScale),
                        coreColor.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 0.9f
                ),
                radius = baseRadius * 0.9f,
                center = center
            )

            // Inner Pulsing Core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        coreColor.copy(alpha = 0.8f),
                        coreColor.copy(alpha = 0.2f)
                    ),
                    center = center,
                    radius = baseRadius * 0.35f * glowScale
                ),
                radius = baseRadius * 0.35f * glowScale,
                center = center
            )

            // Dynamic Voice Waves Orbits (when speaking/listening)
            if (state == JarvisVoiceManager.SpeechState.LISTENING || state == JarvisVoiceManager.SpeechState.SPEAKING) {
                val waveCount = 5
                for (i in 0 until waveCount) {
                    val phase = (rotationAngle * (i + 1) * 0.02f)
                    val waveRadius = baseRadius * (0.4f + i * 0.08f) * (1f + sin(phase) * 0.05f * soundLevel)
                    drawCircle(
                        color = coreColor.copy(alpha = 0.15f + (0.1f * soundLevel)),
                        radius = waveRadius,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }

            // Concentric Outer Ring 1 (Rotating Clockwise, Dashed)
            rotate(rotationAngle, pivot = center) {
                drawCircle(
                    color = coreColor.copy(alpha = 0.6f),
                    radius = baseRadius * 0.65f,
                    center = center,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(40f, 20f), 0f)
                    )
                )
            }

            // Concentric Outer Ring 2 (Counter-Rotating, Fine Dashes)
            rotate(counterRotationAngle, pivot = center) {
                drawCircle(
                    color = JarvisBlue.copy(alpha = 0.4f),
                    radius = baseRadius * 0.78f,
                    center = center,
                    style = Stroke(
                        width = 1.5f.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                    )
                )
            }

            // Heavy Tech Brackets (Drawn as arcs)
            rotate(rotationAngle * 0.5f, pivot = center) {
                drawArc(
                    color = coreColor.copy(alpha = 0.8f),
                    startAngle = 0f,
                    sweepAngle = 45f,
                    useCenter = false,
                    topLeft = Offset(center.x - baseRadius * 0.88f, center.y - baseRadius * 0.88f),
                    size = size * 0.88f,
                    style = Stroke(width = 4.dp.toPx())
                )
                drawArc(
                    color = coreColor.copy(alpha = 0.8f),
                    startAngle = 180f,
                    sweepAngle = 45f,
                    useCenter = false,
                    topLeft = Offset(center.x - baseRadius * 0.88f, center.y - baseRadius * 0.88f),
                    size = size * 0.88f,
                    style = Stroke(width = 4.dp.toPx())
                )
            }

            // Outer Perimeter Radar Ring
            drawCircle(
                color = coreColor.copy(alpha = 0.15f),
                radius = baseRadius * 0.95f,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}
