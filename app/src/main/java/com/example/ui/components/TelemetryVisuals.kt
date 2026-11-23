package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.ChannelCongestionInfo
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SlateOutline
import com.example.ui.theme.SlateSurfaceElevated
import com.example.ui.theme.StatusFault
import com.example.ui.theme.StatusHealthy
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LtrText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = TextPrimary,
    mono: Boolean = true,
    maxLines: Int = 1
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(
            text = text,
            modifier = modifier,
            style = if (mono) style.copy(fontFamily = JetBrainsMonoFontFamily) else style,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun HealthScoreArc(
    score: Int,
    isScanning: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 116.dp
) {
    val animatedSweep by animateFloatAsState(
        targetValue = (score.coerceIn(0, 100) / 100f) * 260f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "health_score_arc"
    )

    val scoreColor = when {
        score >= 80 -> StatusHealthy
        score >= 55 -> StatusWarning
        score > 0 -> StatusFault
        else -> CyberCyan
    }

    val statusWord = when {
        isScanning -> "در حال اسکن"
        score >= 85 -> "عالی"
        score >= 65 -> "قابل قبول"
        score >= 40 -> "نیاز به تعمیر"
        score > 0 -> "بحرانی"
        else -> "آماده"
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 10.dp.toPx()
            val inset = strokePx / 2f + 4.dp.toPx()
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            // Background track
            drawArc(
                color = SlateSurfaceElevated,
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Subtle outer glow
            if (animatedSweep > 1f) {
                drawArc(
                    color = scoreColor.copy(alpha = 0.22f),
                    startAngle = 140f,
                    sweepAngle = animatedSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx + 6.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(CyberCyan, scoreColor, SignalEmerald)
                    ),
                    startAngle = 140f,
                    sweepAngle = animatedSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LtrText(
                text = if (score > 0) "$score" else "--",
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 30.sp),
                color = TextPrimary,
                mono = true
            )
            Text(
                text = statusWord,
                style = MaterialTheme.typography.labelSmall,
                color = scoreColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun SpeedometerGauge(
    speedMbps: Double,
    phaseLabel: String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    // Non-linear scale: 0..100+ Mbps smoothly mapped onto 240 deg arc
    val normalized = when {
        speedMbps <= 0.0 -> 0f
        speedMbps <= 20.0 -> (speedMbps / 20.0).toFloat() * 0.45f
        speedMbps <= 100.0 -> 0.45f + ((speedMbps - 20.0) / 80.0).toFloat() * 0.40f
        else -> (0.85f + ((speedMbps - 100.0) / 200.0).toFloat() * 0.15f).coerceAtMost(1f)
    }

    val animatedFraction by animateFloatAsState(
        targetValue = normalized,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
        label = "speedometer_sweep"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(225.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(216.dp)) {
            val strokePx = 14.dp.toPx()
            val inset = 20.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            val center = Offset(size.width / 2f, size.height / 2f)
            val tickRadiusOuter = size.width / 2f - 4.dp.toPx()
            val tickRadiusInner = tickRadiusOuter - 7.dp.toPx()

            // Draw radial precision ticks
            val tickCount = 24
            for (i in 0..tickCount) {
                val angleDeg = 150.0 + (240.0 * i / tickCount)
                val rad = angleDeg * PI / 180.0
                val isLit = (i.toFloat() / tickCount) <= animatedFraction && animatedFraction > 0.01f
                val start = Offset(
                    x = center.x + (tickRadiusInner * cos(rad)).toFloat(),
                    y = center.y + (tickRadiusInner * sin(rad)).toFloat()
                )
                val end = Offset(
                    x = center.x + (tickRadiusOuter * cos(rad)).toFloat(),
                    y = center.y + (tickRadiusOuter * sin(rad)).toFloat()
                )
                drawLine(
                    color = if (isLit) SignalEmerald else SlateOutline.copy(alpha = 0.6f),
                    start = start,
                    end = end,
                    strokeWidth = if (i % 4 == 0) 2.5.dp.toPx() else 1.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Track arc
            drawArc(
                color = SlateSurfaceElevated,
                startAngle = 150f,
                sweepAngle = 240f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Active glow + sweep arc
            val sweep = animatedFraction * 240f
            if (sweep > 0.5f) {
                drawArc(
                    color = SignalEmerald.copy(alpha = 0.22f),
                    startAngle = 150f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx + 8.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    brush = Brush.horizontalGradient(listOf(CyberCyan, SignalEmerald)),
                    startAngle = 150f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            LtrText(
                text = "%.1f".format(speedMbps),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = if (isActive) SignalEmerald else TextPrimary,
                mono = true
            )
            LtrText(
                text = "Mbps",
                style = MaterialTheme.typography.labelMedium,
                color = CyberCyan,
                mono = true
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = phaseLabel,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun LiveThroughputChart(
    samples: List<Float>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(74.dp)
    ) {
        val w = size.width
        val h = size.height
        // Subtle horizontal guide lines
        for (i in 1..2) {
            val y = h * i / 3f
            drawLine(
                color = SlateOutline.copy(alpha = 0.35f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        if (samples.size < 2) return@Canvas

        val maxVal = (samples.maxOrNull() ?: 10f).coerceAtLeast(5f) * 1.15f
        val stepX = w / (samples.size - 1).coerceAtLeast(1)

        val linePath = Path()
        val fillPath = Path()

        samples.forEachIndexed { idx, value ->
            val x = idx * stepX
            val y = h - ((value / maxVal).coerceIn(0f, 1f) * (h - 8.dp.toPx())) - 4.dp.toPx()
            if (idx == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(w, h)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.32f), Color.Transparent)
            )
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
fun ChannelCongestionChart(
    channels: List<ChannelCongestionInfo>,
    modifier: Modifier = Modifier
) {
    if (channels.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(124.dp)
                .padding(horizontal = 4.dp)
        ) {
            val barCount = channels.size
            val gap = 6.dp.toPx()
            val barWidth = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(6f)
            val maxH = size.height - 10.dp.toPx()

            channels.forEachIndexed { idx, ch ->
                val x = idx * (barWidth + gap)
                // Always render a minimal base bar so empty channels are visibly clean
                val heightRatio = ((ch.interferenceScore.coerceAtLeast(8)) / 100f).coerceIn(0.08f, 1f)
                val barH = maxH * heightRatio
                val topY = size.height - barH

                val barColor = when {
                    ch.isRecommended -> SignalEmerald
                    ch.isCurrentChannel -> CyberCyan
                    ch.interferenceScore >= 65 -> StatusFault
                    ch.interferenceScore >= 35 -> StatusWarning
                    else -> SlateOutline
                }

                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, topY),
                    size = Size(barWidth, barH),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                channels.forEach { ch ->
                    val labelColor = when {
                        ch.isRecommended -> SignalEmerald
                        ch.isCurrentChannel -> CyberCyan
                        else -> TextMuted
                    }
                    Text(
                        text = "${ch.channel}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = if (ch.isRecommended || ch.isCurrentChannel) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = labelColor
                    )
                }
            }
        }
    }
}
