package jp.co.soramitsu.common.shikiban

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import kotlin.math.max
import kotlin.math.min

/**
 * Draws the launch flight's layers on an Android canvas (a port of the kit's `tools/shikiban-draw.mjs`
 * `drawFlight` and the preview's corona and opening, `output/shikiban-20261115/preview/proto.mjs`).
 * Sizes are pixels; [density] converts the kit's points (dp) for stroke widths. One instance per
 * overlay: paints and paths are reused, nothing is allocated per frame except the corona's shader.
 */
class ShikibanFlightPainter(private val tokens: Shikiban, private val density: Float) {

    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = tokens.colors.glow
        additive()
    }
    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = tokens.colors.core
        additive()
    }
    private val star = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = tokens.colors.star
        additive()
    }
    private val corona = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val punch = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
    private val path = Path()
    private val oval = RectF()
    private val coronaColors = tokens.flight.corona.map { it.second }.toIntArray()
    private val coronaStops = tokens.flight.corona.map { it.first }.toFloatArray()

    /** Strokes [path] twice: a wide soft glow, then the bright core. Additive, so crossings brighten. */
    private fun glowStroke(canvas: Canvas, widthPx: Float, alpha: Float, glowScale: Float = GLOW_SCALE) {
        if (alpha <= 0f) return
        glow.strokeWidth = widthPx * glowScale
        glow.alpha = (GLOW_ALPHA * alpha * 255f).toInt().coerceIn(0, 255)
        canvas.drawPath(path, glow)
        core.strokeWidth = widthPx
        core.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
        canvas.drawPath(path, core)
    }

    private fun circlePath(cx: Float, cy: Float, r: Float) {
        path.reset()
        path.addCircle(cx, cy, r, Path.Direction.CW)
    }

    /** The mirror's eight-lobed rim about (cx, cy), radius [r]. */
    fun rimPath(target: Path, cx: Float, cy: Float, r: Float) {
        target.reset()
        tokens.mirror.rim.forEachIndexed { index, arc ->
            val ar = arc.r * r
            oval.set(cx + arc.cx * r - ar, cy + arc.cy * r - ar, cx + arc.cx * r + ar, cy + arc.cy * r + ar)
            val start = Math.toDegrees(arc.from.toDouble()).toFloat()
            val sweep = Math.toDegrees((arc.to - arc.from).toDouble()).toFloat()
            if (index == 0) target.arcTo(oval, start, sweep, true) else target.arcTo(oval, start, sweep, false)
        }
        target.close()
    }

    /** One gate, radius [r] pixels, at depth scale [scale] (for its stroke width) and strength [alpha]. */
    fun drawGate(canvas: Canvas, gate: Shikiban.Gate, cx: Float, cy: Float, r: Float, scale: Float, alpha: Float) {
        if (alpha <= 0f || r <= 0f) return
        val width = min(MAX_CORE_DP, 1f + 0.9f * scale) * density
        if (gate.mirror) {
            rimPath(path, cx, cy, r)
            glowStroke(canvas, width * 1.25f, alpha)
            circlePath(cx, cy, r * tokens.mirror.cusp * MIRROR_INNER)
            glowStroke(canvas, width * 0.7f, alpha * 0.8f)
            return
        }
        circlePath(cx, cy, r)
        glowStroke(canvas, width, alpha)
        path.reset()
        gate.square.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(cx + x * r, cy + y * r) else path.lineTo(cx + x * r, cy + y * r) }
        path.close()
        glowStroke(canvas, width * 0.75f, alpha * 0.85f)
        // The four corners where earth touches heaven.
        star.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
        for ((x, y) in gate.square) canvas.drawCircle(cx + x * r, cy + y * r, width * 1.5f, star)
    }

    /** All gates at flight progress [u], far ones first, about ([cx], [cy]); [side] is the view's shorter side. */
    fun drawGates(canvas: Canvas, u: Float, cx: Float, cy: Float, side: Float) {
        val fade = tokens.gatesFade(u)
        if (fade <= 0f) return
        for (i in tokens.gates.indices.reversed()) {
            val scale = tokens.gateScale(i, u)
            if (scale == 0f) continue
            drawGate(canvas, tokens.gates[i], cx, cy, tokens.flight.baseRadius * side * scale, scale, tokens.gateAlpha(i, u) * fade)
        }
    }

    /**
     * The corona over everything drawn so far: the radial gradient `flight.corona` about the focus, its first stop at
     * the opening's edge ([radius]), its last at max(2.4 × radius, radius + side / 2), at [strength].
     */
    fun drawCorona(canvas: Canvas, cx: Float, cy: Float, radius: Float, side: Float, strength: Float, width: Int, height: Int) {
        if (strength <= 0f || radius <= 0f) return
        val outer = max(radius * 2.4f, radius + side * 0.5f)
        // Android's RadialGradient starts at the centre: map the kit's stops (0 at the opening's edge) onto 0..outer.
        val inner = radius / outer
        val stops = FloatArray(coronaStops.size + 1)
        val colors = IntArray(coronaColors.size + 1)
        stops[0] = 0f
        colors[0] = coronaColors[0]
        for (i in coronaStops.indices) {
            stops[i + 1] = inner + (1f - inner) * coronaStops[i]
            colors[i + 1] = coronaColors[i]
        }
        corona.shader = RadialGradient(cx, cy, outer, colors, stops, Shader.TileMode.CLAMP)
        corona.alpha = (strength * 255f).toInt().coerceIn(0, 255)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), corona)
    }

    /** Cuts the round opening through everything drawn so far in the current layer (a soft 1.5 dp edge). */
    fun punchOpening(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        if (radius <= 0f) return
        canvas.drawCircle(cx, cy, radius, punch)
    }

    private fun Paint.additive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) blendMode = BlendMode.PLUS
        else xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
    }

    private companion object {
        const val GLOW_SCALE = 5f
        const val GLOW_ALPHA = 0.3f
        const val MAX_CORE_DP = 4.5f

        /** The mirror's inner circle: `mirror.cusp × 0.94` of its radius (SPEC part 1, "The gates"). */
        const val MIRROR_INNER = 0.94f
    }
}
