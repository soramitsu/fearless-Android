package jp.co.soramitsu.common.shikiban

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.common.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Shikiban (brand kit v4, SPEC "Shikiban: depth, instruments and sacred geometry"): every number of
 * the launch flight, the sync dial, the seal, the loader and the lattice, read from the kit's
 * `tokens/shikiban.json` (copied byte for byte to `raw/fw_shikiban.json` by
 * `scripts/generate-brand-assets.py`), and the kit's formulas (`tools/shikiban.mjs`) as plain
 * functions. Nothing here is retyped from the kit: the numbers come from the file.
 *
 * Figures: outer radius 1, origin at the centre, y down; turns run clockwise from 12 o'clock.
 * Depths and the perspective are points (dp). Times are milliseconds.
 */
class Shikiban(
    val perspective: Float,
    val durationsMs: List<Int>,
    val settle: CubicBezier,
    val depart: CubicBezier,
    val glide: CubicBezier,
    val colors: Colors,
    val mirror: Mirror,
    val gates: List<Gate>,
    val flight: Flight,
    val dial: Dial,
    val seal: Seal,
    val loader: Loader,
    val asanoha: Asanoha
) {
    /** `#RRGGBB` colours as opaque ARGB ints. */
    data class Colors(val core: Int, val glow: Int, val star: Int, val flight: Int, val failure: Int, val rest: Int)

    data class RimArc(val cx: Float, val cy: Float, val r: Float, val from: Float, val to: Float)

    data class Mirror(val lobes: Int, val lobeRadius: Float, val lobeCentre: Float, val cusp: Float, val rim: List<RimArc>)

    /** A gate of the launch flight: a plate (circle and inscribed square, [square] its corners) or the mirror. */
    data class Gate(val mirror: Boolean, val diamond: Boolean, val depth: Float, val square: List<Pair<Float, Float>>)

    data class Flight(
        val durationMs: Int,
        val irisAtMs: Int,
        val skipMs: Int,
        val landingMs: Int,
        val baseRadius: Float,
        val passScale: Float,
        val rest: Float,
        val tierLag: Float,
        val tierStaggerMs: Int,
        val landingDepth: Float,
        val landingLag: Float,
        val gateFadeIn: Float,
        val irisInset: Float,
        val irisOpenMs: Int,
        val coronaMs: Int,
        /** (stop, ARGB) pairs of the corona's radial gradient. */
        val corona: List<Pair<Float, Int>>,
        val tierFadeMs: Int,
        val travel: Float,
        val samples: Samples,
        val landingFrom: List<Float>
    )

    /** The kit's keyframes at u = 0, 1/24 … 1 (for checking the formulas, and for apps that prefer keyframes). */
    data class Samples(
        val camera: List<Float>,
        val iris: List<Float>,
        val gates: List<List<Float>>,
        val gateAlpha: List<List<Float>>,
        val tiers: List<List<Float>>
    )

    data class Dial(val segments: Int, val gapTurn: Float, val cometTurn: Float, val cometSeconds: Float, val sealMs: Int)

    data class Seal(
        val stages: List<String>,
        val progress: Map<String, Float>,
        val stageMs: Int,
        val igniteMs: Int,
        val restMs: Int,
        val restStrength: Float,
        val breakMs: Int,
        val cometSeconds: Float,
        /** Stage → strokes of the solar geometry as (group, index within the group). */
        val strokes: Map<String, List<Pair<String, Int>>>,
        /** Stage → the index of the solar geometry star that lands when its side closes. */
        val stars: Map<String, Int>
    )

    data class Loader(val comets: Int, val tailTurn: Float, val secondsPerTurn: Float, val restAlpha: Float)

    /** One asanoha tile: segments (x1, y1, x2, y2) in a cell [width] wide and [height] tall. */
    data class Asanoha(val width: Float, val height: Float, val lines: List<FloatArray>)

    // -----------------------------------------------------------------------------------------------------------------
    // Rule 2: depth is one law.
    // -----------------------------------------------------------------------------------------------------------------

    /** The scale of a plane at depth [z] points: `p / (p + z)`, about the focus point. */
    fun depthScale(z: Float): Float = perspective / (perspective + z)

    /** Camera position (points) at flight progress [u] 0..1. */
    fun cameraAt(u: Float): Float = flight.travel * smootherstep(u)

    /** Scale of gate [i] at flight progress [u], or 0 once it has passed the viewer. */
    fun gateScale(i: Int, u: Float): Float {
        val d = perspective + gates[i].depth - cameraAt(u)
        if (d <= perspective / flight.passScale) return 0f
        return perspective / d
    }

    /** How strongly gate [i] shows at flight progress [u]: far gates sit in haze, a gate fades as it sweeps past. */
    fun gateAlpha(i: Int, u: Float): Float {
        val s = gateScale(i, u)
        if (s == 0f) return 0f
        val haze = clamp01((s - 0.1f) / 0.5f)
        val pass = clamp01((flight.passScale - s) / (flight.passScale * 0.7f))
        return (0.25f + 0.75f * haze) * pass
    }

    /** The mirror's scale without the pass cut-off, so the opening keeps growing until it has cleared the view. */
    fun irisScale(u: Float): Float {
        val d = perspective + gates.last().depth - cameraAt(u)
        return perspective / max(d, perspective / 400f)
    }

    /**
     * The opening's radius in pixels at flight [elapsedMs]: the mirror's radius × cusp × inset, opening from a point
     * over `irisOpenMs` from `irisAtMs` (`1 − (1 − o)³`). 0 before the mirror opens.
     */
    fun irisRadius(elapsedMs: Float, side: Float): Float {
        val open = clamp01((elapsedMs - flight.irisAtMs) / flight.irisOpenMs)
        if (open <= 0f) return 0f
        val u = clamp01(elapsedMs / flight.durationMs)
        val o = 1f - open
        return flight.baseRadius * side * irisScale(u) * mirror.cusp * flight.irisInset * (1f - o * o * o)
    }

    /** Strength of the corona at flight [elapsedMs] (0 before the mirror opens, 1 after `coronaMs`). */
    fun coronaStrength(elapsedMs: Float): Float = clamp01((elapsedMs - flight.irisAtMs) / flight.coronaMs)

    /** Strength every gate is multiplied by: they fade in over the first `gateFadeIn` of the flight. */
    fun gatesFade(u: Float): Float = clamp01(u / flight.gateFadeIn)

    /** Scale of the first screen's tier [k] at flight progress [u]; exactly 1 at u = 1. */
    fun tierScale(k: Int, u: Float): Float {
        if (u >= 1f) return 1f
        val remaining = flight.travel - cameraAt(u)
        return depthScale(remaining * (1f + k * flight.tierLag))
    }

    /** Opacity of tier [k] at flight [elapsedMs]: it fades in over `tierFadeMs` from `irisAtMs + k × tierStaggerMs`. */
    fun tierAlpha(k: Int, elapsedMs: Float): Float =
        if (elapsedMs >= flight.durationMs) 1f
        else clamp01((elapsedMs - flight.irisAtMs - k * flight.tierStaggerMs) / flight.tierFadeMs)

    /** Scale of tier [k] at landing progress [v] (0..1, already eased with [settle]). */
    fun landingScale(k: Int, v: Float): Float = depthScale(flight.landingDepth * (1f + k * flight.landingLag) * (1f - v))

    /** Total length of a later arrival (an unlock): the last tier starts `tiers × 45` ms later. */
    fun landingTotalMs(tiers: Int): Int = flight.landingMs + (tiers - 1).coerceAtLeast(0) * flight.tierStaggerMs

    /** Tier [k] of a later arrival at [elapsedMs]: (scale, alpha). It fades in over the first 45 % of its time. */
    fun landingAt(k: Int, elapsedMs: Float): Pair<Float, Float> {
        val local = clamp01((elapsedMs - k * flight.tierStaggerMs) / flight.landingMs)
        if (local >= 1f) return 1f to 1f
        return landingScale(k, settle(local)) to clamp01(local / LANDING_FADE_SHARE)
    }

    // -----------------------------------------------------------------------------------------------------------------
    // The sync dial
    // -----------------------------------------------------------------------------------------------------------------

    enum class DialPhase { Idle, Syncing, Live, Partial }

    /** What the dial shows: lit [lit] (from 12 o'clock), ember from [failedFrom] to the end, and its phase. */
    data class DialState(val phase: DialPhase, val lit: Float, val failedFrom: Float) {
        val settled: Boolean get() = phase == DialPhase.Live || phase == DialPhase.Partial
    }

    fun dialState(done: Int, failed: Int, total: Int): DialState {
        if (total <= 0) return DialState(DialPhase.Idle, 0f, 1f)
        val d = done.coerceIn(0, total)
        val f = failed.coerceIn(0, total - d)
        val settled = d + f >= total
        return DialState(
            phase = if (settled) (if (f > 0) DialPhase.Partial else DialPhase.Live) else DialPhase.Syncing,
            lit = d.toFloat() / total,
            failedFrom = 1f - f.toFloat() / total
        )
    }

    /** The part of segment [s] covered by [from]..[to] (turns), or null. Gaps between segments stay dark. */
    fun dialSegment(s: Int, from: Float, to: Float): Pair<Float, Float>? {
        val a = s.toFloat() / dial.segments + dial.gapTurn / 2f
        val b = (s + 1).toFloat() / dial.segments - dial.gapTurn / 2f
        val lo = max(a, from)
        val hi = min(b, to)
        return if (hi - lo > 1e-6f) lo to hi else null
    }

    // -----------------------------------------------------------------------------------------------------------------
    // The seal
    // -----------------------------------------------------------------------------------------------------------------

    /** Ignite progress a stage draws to (`seal.progress`). */
    fun sealProgress(stage: String): Float = seal.progress[stage] ?: 0f

    // -----------------------------------------------------------------------------------------------------------------
    // The loader
    // -----------------------------------------------------------------------------------------------------------------

    /** Where the loader's comets are at [seconds]: (head, tail) turns each, clockwise from 12 o'clock. */
    fun loaderComets(seconds: Float): List<Pair<Float, Float>> {
        val head = (seconds / loader.secondsPerTurn).mod(1f)
        return List(loader.comets) { i ->
            val h = (head + i.toFloat() / loader.comets).mod(1f)
            h to h - loader.tailTurn
        }
    }

    /** A point on the mirror's rim at [turn] (clockwise from 12 o'clock), in figure units. */
    fun rimPoint(turn: Float): Pair<Float, Float> {
        val t = turn.mod(1f)
        val shifted = (t + 1f / 16f).mod(1f)
        val k = floor(shifted * 8f).toInt().coerceIn(0, 7)
        val within = shifted * 8f - k
        val cx = sin(k / 8f * TAU) * mirror.lobeCentre
        val cy = -cos(k / 8f * TAU) * mirror.lobeCentre
        val out = -PI.toFloat() / 2f + k / 8f * TAU
        val a = out - PI.toFloat() / 4f + within * (PI.toFloat() / 2f)
        return (cx + cos(a) * mirror.lobeRadius) to (cy + sin(a) * mirror.lobeRadius)
    }

    companion object {
        const val TAU = (2 * PI).toFloat()

        /** A later arrival's tier fades in over this share of its time (SPEC part 1, "Later arrivals"). */
        const val LANDING_FADE_SHARE = 0.45f

        fun clamp01(v: Float): Float = v.coerceIn(0f, 1f)

        fun smootherstep(x: Float): Float {
            val k = clamp01(x)
            return k * k * k * (k * (6f * k - 15f) + 10f)
        }

        /** Parses the kit file. Throws on anything missing or malformed: nothing of shikiban is then drawn. */
        fun parse(json: String): Shikiban {
            val root = JsonParser.parseString(json).asJsonObject
            require(root.get("formatVersion").asInt == 1) { "Unknown shikiban format" }
            val easing = root.getAsJsonObject("easing")
            val colors = root.getAsJsonObject("colors")
            val mirror = root.getAsJsonObject("mirror")
            val flight = root.getAsJsonObject("flight")
            val samples = flight.getAsJsonObject("samples")
            val dial = root.getAsJsonObject("dial")
            val seal = root.getAsJsonObject("seal")
            val loader = root.getAsJsonObject("loader")
            val asanoha = root.getAsJsonObject("asanoha")
            val durations = root.getAsJsonArray("durationsMs").map { it.asInt }
            val result = Shikiban(
                perspective = root.getAsJsonObject("depth").f("perspective"),
                durationsMs = durations,
                settle = CubicBezier.of(easing.getAsJsonArray("settle")),
                depart = CubicBezier.of(easing.getAsJsonArray("depart")),
                glide = CubicBezier.of(easing.getAsJsonArray("glide")),
                colors = Colors(
                    core = hex(colors.s("core")),
                    glow = hex(colors.s("glow")),
                    star = hex(colors.s("star")),
                    flight = hex(colors.s("flight")),
                    failure = hex(colors.s("failure")),
                    rest = hex(colors.s("rest"))
                ),
                mirror = Mirror(
                    lobes = mirror.get("lobes").asInt,
                    lobeRadius = mirror.f("lobeRadius"),
                    lobeCentre = mirror.f("lobeCentre"),
                    cusp = mirror.f("cusp"),
                    rim = mirror.getAsJsonArray("rim").map {
                        val a = it.asJsonObject
                        RimArc(a.f("cx"), a.f("cy"), a.f("r"), a.f("from"), a.f("to"))
                    }
                ),
                gates = root.getAsJsonArray("gates").map {
                    val g = it.asJsonObject
                    val isMirror = g.s("figure") == "mirror"
                    Gate(
                        mirror = isMirror,
                        diamond = g.get("diamond")?.asBoolean ?: false,
                        depth = g.f("depth"),
                        square = if (isMirror) emptyList() else g.getAsJsonArray("square").map { p ->
                            p.asJsonArray[0].asFloat to p.asJsonArray[1].asFloat
                        }
                    )
                },
                flight = Flight(
                    durationMs = flight.get("durationMs").asInt,
                    irisAtMs = flight.get("irisAtMs").asInt,
                    skipMs = flight.get("skipMs").asInt,
                    landingMs = flight.get("landingMs").asInt,
                    baseRadius = flight.f("baseRadius"),
                    passScale = flight.f("passScale"),
                    rest = flight.f("rest"),
                    tierLag = flight.f("tierLag"),
                    tierStaggerMs = flight.get("tierStaggerMs").asInt,
                    landingDepth = flight.f("landingDepth"),
                    landingLag = flight.f("landingLag"),
                    gateFadeIn = flight.f("gateFadeIn"),
                    irisInset = flight.f("irisInset"),
                    irisOpenMs = flight.get("irisOpenMs").asInt,
                    coronaMs = flight.get("coronaMs").asInt,
                    corona = flight.getAsJsonArray("corona").map { it.asJsonArray[0].asFloat to hex(it.asJsonArray[1].asString) },
                    tierFadeMs = flight.get("tierFadeMs").asInt,
                    travel = flight.f("travel"),
                    samples = Samples(
                        camera = samples.getAsJsonArray("camera").floats(),
                        iris = samples.getAsJsonArray("iris").floats(),
                        gates = samples.getAsJsonArray("gates").map { it.asJsonArray.floats() },
                        gateAlpha = samples.getAsJsonArray("gateAlpha").map { it.asJsonArray.floats() },
                        tiers = samples.getAsJsonArray("tiers").map { it.asJsonArray.floats() }
                    ),
                    landingFrom = flight.getAsJsonArray("landingFrom").floats()
                ),
                dial = Dial(
                    segments = dial.get("segments").asInt,
                    gapTurn = dial.f("gapTurn"),
                    cometTurn = dial.f("cometTurn"),
                    cometSeconds = dial.f("cometSeconds"),
                    sealMs = dial.get("sealMs").asInt
                ),
                seal = Seal(
                    stages = seal.getAsJsonArray("stages").map { it.asString },
                    progress = seal.getAsJsonObject("progress").entrySet().associate { it.key to it.value.asFloat },
                    stageMs = seal.get("stageMs").asInt,
                    igniteMs = seal.get("igniteMs").asInt,
                    restMs = seal.get("restMs").asInt,
                    restStrength = seal.f("restStrength"),
                    breakMs = seal.get("breakMs").asInt,
                    cometSeconds = seal.f("cometSeconds"),
                    strokes = seal.getAsJsonObject("strokes").entrySet().associate { entry ->
                        entry.key to entry.value.asJsonArray.map { it.asJsonArray[0].asString to it.asJsonArray[1].asInt }
                    },
                    stars = seal.getAsJsonObject("stars").entrySet().associate { it.key to it.value.asInt }
                ),
                loader = Loader(
                    comets = loader.get("comets").asInt,
                    tailTurn = loader.f("tailTurn"),
                    secondsPerTurn = loader.f("secondsPerTurn"),
                    restAlpha = loader.f("restAlpha")
                ),
                asanoha = Asanoha(
                    width = asanoha.f("width"),
                    height = asanoha.f("height"),
                    lines = asanoha.getAsJsonArray("lines").map { l -> FloatArray(4) { l.asJsonArray[it].asFloat } }
                )
            )
            require(result.gates.isNotEmpty() && result.gates.last().mirror) { "The last gate must be the mirror" }
            require(result.flight.samples.tiers.isNotEmpty() && result.flight.landingFrom.size == result.flight.samples.tiers.size) {
                "Tier samples and landing scales disagree"
            }
            require(durations.zipWithNext().all { (a, b) -> b > a }) { "Durations are not a ladder" }
            return result
        }

        @Volatile
        private var cached: Shikiban? = null

        /** The bundled tokens, read once per process; null if the file cannot be read (nothing is then drawn). */
        fun load(context: Context): Shikiban? = cached ?: runCatching {
            context.resources.openRawResource(R.raw.fw_shikiban).bufferedReader().use { parse(it.readText()) }
        }.getOrNull()?.also { cached = it }

        private fun JsonObject.f(name: String): Float = get(name).asFloat
        private fun JsonObject.s(name: String): String = get(name).asString
        private fun JsonArray.floats(): List<Float> = map { it.asFloat }

        /** `#RRGGBB` → opaque ARGB. */
        fun hex(value: String): Int {
            require(Regex("#[0-9A-Fa-f]{6}").matches(value)) { "Colour is not #RRGGBB: $value" }
            return (0xFF000000 or value.substring(1).toLong(16)).toInt()
        }
    }
}

/** A cubic bezier easing (the curve CSS, Core Animation and Android's PathInterpolator use), as in `shikiban.mjs`. */
class CubicBezier(val x1: Float, val y1: Float, val x2: Float, val y2: Float) {
    private val cx = 3f * x1
    private val bx = 3f * (x2 - x1) - cx
    private val ax = 1f - cx - bx
    private val cy = 3f * y1
    private val by = 3f * (y2 - y1) - cy
    private val ay = 1f - cy - by

    private fun sx(t: Float) = ((ax * t + bx) * t + cx) * t
    private fun sy(t: Float) = ((ay * t + by) * t + cy) * t

    operator fun invoke(x: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        var lo = 0f
        var hi = 1f
        var t = x
        repeat(BISECTIONS) {
            val v = sx(t)
            if (abs(v - x) < 1e-6f) return sy(t)
            if (v < x) lo = t else hi = t
            t = (lo + hi) / 2f
        }
        return sy(t)
    }

    companion object {
        private const val BISECTIONS = 24
        fun of(points: JsonArray): CubicBezier {
            require(points.size() == 4) { "A cubic bezier has four numbers" }
            return CubicBezier(points[0].asFloat, points[1].asFloat, points[2].asFloat, points[3].asFloat)
        }
    }
}
