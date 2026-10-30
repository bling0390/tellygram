package tv.telegram.ui.audio

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import tv.telegram.ui.home.HomeSpec

internal const val SpectrumBands = 32

internal const val SpectrumFftSize = 1024

internal const val SpectrumMinHz = 40f
internal const val SpectrumMaxHz = 16_000f

internal const val SpectrumFloorDb = -60f

internal const val SpectrumAttack = 0.55f
internal const val SpectrumRelease = 0.18f

internal const val SpectrumMinRadius = 0.45f

internal fun hannWindow(size: Int): FloatArray =
    FloatArray(size) { i -> (0.5 * (1.0 - cos(2.0 * PI * i / (size - 1)))).toFloat() }

internal fun fft(re: FloatArray, im: FloatArray) {
    val n = re.size
    if (n <= 1) return

    var j = 0
    for (i in 1 until n) {
        var bit = n shr 1
        while (j and bit != 0) {
            j = j xor bit
            bit = bit shr 1
        }
        j = j or bit
        if (i < j) {
            val tr = re[i]; re[i] = re[j]; re[j] = tr
            val ti = im[i]; im[i] = im[j]; im[j] = ti
        }
    }

    var len = 2
    while (len <= n) {
        val angle = -2.0 * PI / len
        val wr = cos(angle).toFloat()
        val wi = sin(angle).toFloat()
        var start = 0
        while (start < n) {
            var curR = 1f
            var curI = 0f
            for (k in 0 until len / 2) {
                val a = start + k
                val b = a + len / 2
                val xr = re[b] * curR - im[b] * curI
                val xi = re[b] * curI + im[b] * curR
                re[b] = re[a] - xr
                im[b] = im[a] - xi
                re[a] += xr
                im[a] += xi
                val nextR = curR * wr - curI * wi
                curI = curR * wi + curI * wr
                curR = nextR
            }
            start += len
        }
        len = len shl 1
    }
}

internal fun fftMagnitudes(samples: FloatArray, window: FloatArray = hannWindow(samples.size)): FloatArray {
    val n = samples.size
    val re = FloatArray(n)
    val im = FloatArray(n)
    for (i in 0 until n) re[i] = samples[i] * window[i]
    fft(re, im)

    val half = n / 2
    return FloatArray(half) { i -> sqrt(re[i] * re[i] + im[i] * im[i]) / half }
}

internal fun bandEdges(
    sampleRate: Int,
    fftSize: Int = SpectrumFftSize,
    bands: Int = SpectrumBands,
    minHz: Float = SpectrumMinHz,
    maxHz: Float = SpectrumMaxHz,
): IntArray {
    val binHz = sampleRate.toFloat() / fftSize
    val half = fftSize / 2
    val top = maxHz.coerceAtMost(sampleRate / 2f)
    val bottom = minHz.coerceAtMost(top)
    val logBottom = ln(bottom.toDouble())
    val logTop = ln(top.toDouble())

    return IntArray(bands + 1) { i ->
        val t = i.toDouble() / bands
        val hz = exp(logBottom + (logTop - logBottom) * t)
        (hz / binHz).toInt().coerceIn(0, half)
    }
}

internal fun bandLevels(magnitudes: FloatArray, edges: IntArray): FloatArray {
    val bands = edges.size - 1
    return FloatArray(bands) { b ->
        val from = edges[b]
        val to = edges[b + 1].coerceAtMost(magnitudes.size)
        if (to <= from) {
            0f
        } else {
            var sum = 0f
            for (i in from until to) sum += magnitudes[i]
            sum / (to - from)
        }
    }
}

internal fun normalizeDb(levels: FloatArray, floorDb: Float = SpectrumFloorDb): FloatArray =
    FloatArray(levels.size) { i ->
        val level = levels[i]
        if (level <= 1e-6f) {
            0f
        } else {
            ((20.0 * log10(level.toDouble())).toFloat() - floorDb) / -floorDb
        }.coerceIn(0f, 1f)
    }

internal fun smooth(
    current: FloatArray,
    target: FloatArray,
    attack: Float = SpectrumAttack,
    release: Float = SpectrumRelease,
): FloatArray = FloatArray(target.size) { i ->
    val from = current.getOrElse(i) { 0f }
    val to = target[i]
    val k = if (to > from) attack else release
    from + (to - from) * k
}

internal fun radiusFraction(level: Float, min: Float = SpectrumMinRadius, max: Float = 1f): Float =
    min + (max - min) * level.coerceIn(0f, 1f)

internal fun spectrumFrame(
    samples: FloatArray,
    edges: IntArray,
    previous: FloatArray,
    window: FloatArray = hannWindow(samples.size),
    attack: Float = SpectrumAttack,
    release: Float = SpectrumRelease,
): FloatArray = smooth(
    current = previous,
    target = normalizeDb(bandLevels(fftMagnitudes(samples, window), edges)),
    attack = attack,
    release = release,
)

internal fun mirroredLevels(levels: FloatArray): FloatArray {
    val n = levels.size
    if (n == 0) return levels
    return FloatArray(n) { i -> maxOf(levels[i], levels[(n - i) % n]) }
}

internal fun spectrumColor(level: Float): Color =
    lerp(HomeSpec.Primary, HomeSpec.Tertiary, level.coerceIn(0f, 1f))
