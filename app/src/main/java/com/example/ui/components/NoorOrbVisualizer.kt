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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ai.NoorEmotion
import com.example.ui.theme.NoorCyan
import com.example.ui.theme.NoorEmerald
import com.example.ui.theme.NoorGold
import com.example.ui.theme.NoorIndigo
import com.example.ui.theme.NoorRose
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun NoorOrbVisualizer(
    emotion: NoorEmotion,
    isListening: Boolean,
    isSpeaking: Boolean,
    isThinking: Boolean,
    rmsLevel: Float,
    modifier: Modifier = Modifier,
    size: Dp = 156.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "noor_orb_transition")

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isThinking) 2600 else 11000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_rotation"
    )

    val breathingPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_breathe"
    )

    val reactiveBoost by animateFloatAsState(
        targetValue = when {
            isListening -> 0.25f + (rmsLevel * 0.75f)
            isSpeaking -> 0.45f + (0.25f * sin(breathingPhase * 2f))
            isThinking -> 0.35f
            else -> 0.08f
        },
        animationSpec = spring(stiffness = 400f),
        label = "reactive_boost"
    )

    val (primaryGlow, secondaryGlow) = when (emotion) {
        NoorEmotion.HAPPY -> NoorCyan to NoorGold
        NoorEmotion.EXCITED -> NoorGold to NoorEmerald
        NoorEmotion.EMPATHETIC -> NoorGold to NoorCyan
        NoorEmotion.CONCERNED -> NoorRose to NoorIndigo
        NoorEmotion.PLAYFUL -> NoorIndigo to NoorCyan
        NoorEmotion.CALM -> NoorCyan to NoorIndigo
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = this.size.minDimension * 0.30f
            val pulseRadius = baseRadius * (1f + 0.10f * sin(breathingPhase) + 0.14f * reactiveBoost)

            // Outer ambient nebula glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryGlow.copy(alpha = 0.30f + 0.22f * reactiveBoost),
                        secondaryGlow.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = this.size.minDimension * 0.50f
                ),
                center = center,
                radius = this.size.minDimension * 0.50f
            )

            // Outer rotating orbital arc
            rotate(degrees = rotationAngle, pivot = center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color.Transparent,
                            secondaryGlow.copy(alpha = 0.85f),
                            primaryGlow.copy(alpha = 0.9f),
                            Color.Transparent
                        )
                    ),
                    startAngle = 0f,
                    sweepAngle = 220f,
                    useCenter = false,
                    topLeft = Offset(center.x - baseRadius * 1.48f, center.y - baseRadius * 1.48f),
                    size = Size(baseRadius * 2.96f, baseRadius * 2.96f),
                    style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Counter-rotating inner orbital ring
            rotate(degrees = -rotationAngle * 1.35f, pivot = center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color.Transparent,
                            primaryGlow.copy(alpha = 0.75f),
                            Color.Transparent
                        )
                    ),
                    startAngle = 40f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(center.x - baseRadius * 1.24f, center.y - baseRadius * 1.24f),
                    size = Size(baseRadius * 2.48f, baseRadius * 2.48f),
                    style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Harmonic fluid waveform ring
            val wavePath = Path()
            val segments = 64
            val waveAmp = baseRadius * (0.04f + 0.15f * reactiveBoost)
            for (i in 0..segments) {
                val theta = (i.toFloat() / segments) * (2f * PI.toFloat())
                val ripple = sin(theta * 5f + breathingPhase * 2f) * waveAmp +
                    cos(theta * 3f - breathingPhase) * (waveAmp * 0.5f)
                val r = pulseRadius + ripple
                val x = center.x + r * cos(theta)
                val y = center.y + r * sin(theta)
                if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
            }
            wavePath.close()

            drawPath(
                path = wavePath,
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.92f),
                        primaryGlow.copy(alpha = 0.85f),
                        secondaryGlow.copy(alpha = 0.65f)
                    ),
                    center = center,
                    radius = pulseRadius * 1.2f
                )
            )

            drawPath(
                path = wavePath,
                color = primaryGlow.copy(alpha = 0.9f),
                style = Stroke(width = 1.6.dp.toPx())
            )
        }
    }
}

@Composable
fun VoiceWaveformStrip(
    isActive: Boolean,
    rmsLevel: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_strip")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Row(
        modifier = modifier.height(26.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val barCount = 15
        for (i in 0 until barCount) {
            val factor = if (isActive) {
                val wave = (sin(phase + i * 0.65f) + 1f) * 0.5f
                (0.22f + (0.35f + rmsLevel * 0.65f) * wave).coerceIn(0.18f, 1f)
            } else {
                0.18f
            }
            Canvas(
                modifier = Modifier
                    .width(3.dp)
                    .height(24.dp)
            ) {
                val barHeight = size.height * factor
                val top = (size.height - barHeight) / 2f
                drawRoundRect(
                    color = accentColor.copy(alpha = if (isActive) 0.92f else 0.3f),
                    topLeft = Offset(0f, top),
                    size = Size(size.width, barHeight),
                    cornerRadius = CornerRadius(4f, 4f)
                )
            }
        }
    }
}
