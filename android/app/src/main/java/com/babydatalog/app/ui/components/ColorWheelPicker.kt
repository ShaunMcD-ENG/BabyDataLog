package com.babydatalog.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Simple HSV colour wheel: hue = angle around the circle, saturation = distance from
// center, plus a separate brightness slider underneath. No third-party colour library —
// conversion goes through android.graphics.Color's HSV helpers, which ship with the platform.
@Composable
fun ColorWheelPicker(
    initialColor: Color,
    onColorChanged: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val initialHsv = remember(initialColor) {
        val out = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), out)
        out
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember { mutableFloatStateOf(initialHsv[2].coerceAtLeast(0.35f)) }

    val currentColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)))

    LaunchedEffect(currentColor) { onColorChanged(currentColor) }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        updateHueSaturation(offset, size) { h, s -> hue = h; saturation = s }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        updateHueSaturation(change.position, size) { h, s -> hue = h; saturation = s }
                    }
                }
        ) {
            val radius = min(size.width, size.height) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // Hue ring at full saturation/value
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = (0..360 step 15).map { deg ->
                        Color(android.graphics.Color.HSVToColor(floatArrayOf(deg.toFloat() % 360, 1f, 1f)))
                    },
                    center = center
                ),
                radius = radius,
                center = center
            )
            // Desaturate toward the center (white core fading out to the hue ring)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color.White.copy(alpha = 0f)),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
            // Darken the whole wheel by the current brightness
            if (brightness < 1f) {
                drawCircle(
                    color = Color.Black.copy(alpha = 1f - brightness),
                    radius = radius,
                    center = center
                )
            }

            // Selection thumb
            val angleRad = Math.toRadians(hue.toDouble())
            val dist = saturation * radius
            val thumb = Offset(
                x = center.x + (dist * cos(angleRad)).toFloat(),
                y = center.y + (dist * sin(angleRad)).toFloat()
            )
            drawCircle(color = Color.White, radius = 12f, center = thumb)
            drawCircle(color = Color.Black, radius = 12f, center = thumb, style = Stroke(width = 3f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Brightness", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = brightness,
                onValueChange = { brightness = it },
                valueRange = 0.15f..1f,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(currentColor)
            )
            Text(
                text = "#%06X".format(currentColor.toArgb() and 0xFFFFFF),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun updateHueSaturation(offset: Offset, size: IntSize, onUpdate: (hue: Float, saturation: Float) -> Unit) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = min(size.width, size.height) / 2f
    val dx = offset.x - center.x
    val dy = offset.y - center.y
    val dist = sqrt(dx * dx + dy * dy)
    val angleDeg = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 360.0) % 360.0).toFloat()
    val saturation = (dist / radius).coerceIn(0f, 1f)
    onUpdate(angleDeg, saturation)
}
