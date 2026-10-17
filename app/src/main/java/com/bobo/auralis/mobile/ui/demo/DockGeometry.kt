package com.bobo.auralis.mobile.ui.demo

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal fun shouldExpandCollapsedPlayer(deltaX: Float, deltaY: Float, thresholdPx: Float): Boolean =
    deltaY <= -thresholdPx && -deltaY > abs(deltaX)

internal fun dampedDragOffsetPx(
    dragDistancePx: Float,
    maximumDownOffsetPx: Float,
    maximumUpOffsetPx: Float,
): Float {
    if (dragDistancePx == 0f) return 0f
    val isDownward = dragDistancePx > 0f
    val maximumOffset = if (isDownward) maximumDownOffsetPx else maximumUpOffsetPx
    if (maximumOffset <= 0f) return 0f
    val resistance = if (isDownward) 1f else 2.5f
    val distance = kotlin.math.abs(dragDistancePx)
    val offset = maximumOffset * (1f - 1f / (1f + distance / (maximumOffset * resistance)))
    return if (isDownward) offset else -offset
}

internal fun isPointInsideBounds(
    x: Float,
    y: Float,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
): Boolean = x in left..right && y in top..bottom

internal fun playbackProgress(positionMs: Long, durationMs: Long?): Float {
    val duration = durationMs?.takeIf { it > 0L } ?: return 0f
    return (positionMs.coerceAtLeast(0L).toDouble() / duration.toDouble()).toFloat().coerceIn(0f, 1f)
}

internal fun waveOutwardOffsetFactor(seed: Int, index: Int, pointCount: Int): Float {
    if (pointCount <= 0) return 0f
    val angle = index.toDouble() / pointCount.toDouble() * PI * 2.0
    val phaseA = Math.floorMod(seed, 360) / 360.0 * PI * 2.0
    val phaseB = Math.floorMod(seed / 31, 360) / 360.0 * PI * 2.0
    return (0.55 + sin(angle * 18.0 + phaseA) * 0.30 + sin(angle * 29.0 + phaseB) * 0.15)
        .toFloat()
        .coerceIn(0f, 1f)
}

internal fun waveAccentPeakCount(seed: Int): Int = 1 + Math.floorMod(seed, 2)

internal fun waveAccentFactor(seed: Int, index: Int, pointCount: Int): Float {
    if (pointCount <= 0) return 0f
    val normalizedIndex = Math.floorMod(index, pointCount).toFloat() / pointCount
    val primaryCenter = Math.floorMod(seed.toLong() * 1_103_515_245L + 12_345L, 10_000L) / 10_000f
    val secondaryOffset = 0.38f +
        Math.floorMod(seed.toLong() * 2_654_435_761L + 97L, 2_000L) / 10_000f
    val centers = if (waveAccentPeakCount(seed) == 1) {
        listOf(primaryCenter)
    } else {
        listOf(primaryCenter, (primaryCenter + secondaryOffset) % 1f)
    }
    val halfWidth = 0.04f
    return centers.maxOf { center ->
        val directDistance = abs(normalizedIndex - center)
        val circularDistance = min(directDistance, 1f - directDistance)
        if (circularDistance >= halfWidth) {
            0f
        } else {
            ((1.0 + cos(PI * circularDistance / halfWidth)) * 0.5).toFloat()
        }
    }
}

internal data class WaveTrackPoint(val x: Float, val y: Float)

internal fun roundedWaveTrackPoint(
    seed: Int,
    index: Int,
    pointCount: Int,
    diameter: Float,
): WaveTrackPoint {
    if (pointCount <= 0 || diameter <= 0f) return WaveTrackPoint(0f, 0f)
    val normalized = (index.toFloat() / pointCount).coerceIn(0f, 1f)
    val center = diameter / 2f
    val extent = diameter * (19f / 46f)
    val cornerRadius = diameter * (11f / 46f)
    val straightLength = (extent - cornerRadius) * 2f
    val halfStraightLength = straightLength / 2f
    val cornerLength = (PI.toFloat() / 2f) * cornerRadius
    val perimeter = straightLength * 4f + cornerLength * 4f
    var distance = normalized * perimeter

    fun arcPoint(angle: Float): Pair<WaveTrackPoint, Offset> {
        val cornerCenterX = when {
            angle < 0f -> center + extent - cornerRadius
            angle <= PI.toFloat() / 2f -> center + extent - cornerRadius
            angle <= PI.toFloat() -> center - extent + cornerRadius
            else -> center - extent + cornerRadius
        }
        val cornerCenterY = when {
            angle < 0f -> center - extent + cornerRadius
            angle <= PI.toFloat() / 2f -> center + extent - cornerRadius
            angle <= PI.toFloat() -> center + extent - cornerRadius
            else -> center - extent + cornerRadius
        }
        val normal = Offset(cos(angle.toDouble()).toFloat(), sin(angle.toDouble()).toFloat())
        return WaveTrackPoint(
            x = cornerCenterX + normal.x * cornerRadius,
            y = cornerCenterY + normal.y * cornerRadius,
        ) to normal
    }

    val baseAndNormal: Pair<WaveTrackPoint, Offset> = when {
        distance <= halfStraightLength -> {
            WaveTrackPoint(center + distance, center - extent) to Offset(0f, -1f)
        }
        else -> {
            distance -= halfStraightLength
            when {
                distance <= cornerLength -> arcPoint(-PI.toFloat() / 2f + distance / cornerRadius)
                else -> {
                    distance -= cornerLength
                    when {
                        distance <= straightLength -> WaveTrackPoint(
                            center + extent,
                            center - extent + cornerRadius + distance,
                        ) to Offset(1f, 0f)
                        else -> {
                            distance -= straightLength
                            when {
                                distance <= cornerLength -> arcPoint(distance / cornerRadius)
                                else -> {
                                    distance -= cornerLength
                                    when {
                                        distance <= straightLength -> WaveTrackPoint(
                                            center + extent - cornerRadius - distance,
                                            center + extent,
                                        ) to Offset(0f, 1f)
                                        else -> {
                                            distance -= straightLength
                                            when {
                                                distance <= cornerLength -> arcPoint(PI.toFloat() / 2f + distance / cornerRadius)
                                                else -> {
                                                    distance -= cornerLength
                                                    when {
                                                        distance <= straightLength -> WaveTrackPoint(
                                                            center - extent,
                                                            center + extent - cornerRadius - distance,
                                                        ) to Offset(-1f, 0f)
                                                        else -> {
                                                            distance -= straightLength
                                                            when {
                                                                distance <= cornerLength -> arcPoint(PI.toFloat() + distance / cornerRadius)
                                                                else -> {
                                                                    distance -= cornerLength
                                                                    WaveTrackPoint(
                                                                        center - extent + cornerRadius + distance,
                                                                        center - extent,
                                                                    ) to Offset(0f, -1f)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    val outward = diameter * (1.5f / 46f) * waveOutwardOffsetFactor(seed, index, pointCount) +
        diameter * (1.35f / 46f) * waveAccentFactor(seed, index, pointCount)
    return WaveTrackPoint(
        x = baseAndNormal.first.x + baseAndNormal.second.x * outward,
        y = baseAndNormal.first.y + baseAndNormal.second.y * outward,
    )
}
