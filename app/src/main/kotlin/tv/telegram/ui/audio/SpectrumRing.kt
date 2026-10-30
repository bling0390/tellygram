package tv.telegram.ui.audio

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import tv.telegram.ui.home.HomeSpec
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
internal fun SpectrumRing(levels: FloatArray, modifier: Modifier = Modifier) {
    val banded = mirroredLevels(levels)
    Canvas(modifier = modifier) {
        if (banded.isEmpty()) return@Canvas
        val outer = min(size.width, size.height) / 2f
        val inner = outer * SpectrumMinRadius
        val centre = Offset(size.width / 2f, size.height / 2f)
        val slot = (2.0 * PI / banded.size).toFloat()

        drawCircle(color = HomeSpec.SurfaceContainer, radius = inner, style = Stroke(width = 4f))

        val thickness = inner * slot * 0.65f
        for (i in banded.indices) {
            val level = banded[i]
            if (level <= 0f) continue
            val angle = -PI.toFloat() / 2f + i * slot
            val dx = cos(angle)
            val dy = sin(angle)
            val reach = outer * radiusFraction(level)
            drawLine(
                color = spectrumColor(level),
                start = Offset(centre.x + dx * inner, centre.y + dy * inner),
                end = Offset(centre.x + dx * reach, centre.y + dy * reach),
                strokeWidth = thickness,
                cap = StrokeCap.Round,
            )
        }
    }
}
