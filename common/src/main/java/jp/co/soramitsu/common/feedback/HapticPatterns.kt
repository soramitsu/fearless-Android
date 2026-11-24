package jp.co.soramitsu.common.feedback

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * One VibrationEffect primitive of a pattern (brand kit `tokens/haptics.json`, `android.primitives`).
 * [id] is the `VibrationEffect.Composition.PRIMITIVE_*` name without its prefix.
 */
data class HapticPrimitive(val id: String, val scale: Float, val delayMs: Int)

/** The plain waveform for devices without primitives: timings in ms (starting with a pause), amplitudes 0-255. */
class HapticWaveform(val timingsMs: LongArray, val amplitudes: IntArray) {

    /**
     * The same rhythm for a vibrator without amplitude control: alternating off and on durations,
     * starting with off, where any segment with an amplitude above zero counts as on.
     */
    fun onOffTimings(): LongArray {
        val result = ArrayList<Long>()
        var on = false
        var run = 0L
        for (index in timingsMs.indices) {
            val segmentOn = amplitudes[index] > 0
            if (segmentOn != on) {
                result += run
                run = 0L
                on = segmentOn
            }
            run += timingsMs[index]
        }
        result += run
        return result.toLongArray()
    }

    override fun equals(other: Any?): Boolean =
        other is HapticWaveform && timingsMs.contentEquals(other.timingsMs) && amplitudes.contentEquals(other.amplitudes)

    override fun hashCode(): Int = 31 * timingsMs.contentHashCode() + amplitudes.contentHashCode()
}

/** A named pattern. [platformOnly] patterns (failure) use the platform's own effect instead of these. */
data class HapticPattern(
    val name: String,
    val primitives: List<HapticPrimitive>,
    val waveform: HapticWaveform?,
    val platformOnly: Boolean
)

/** The haptic patterns of the brand kit, timed to the transfer sounds. */
class HapticPatterns(val patterns: Map<String, HapticPattern>) {

    operator fun get(name: String): HapticPattern? = patterns[name]

    companion object {
        /** Primitive names this app knows how to play (VibrationEffect.Composition, API 30/31). */
        val KNOWN_PRIMITIVES = setOf("CLICK", "THUD", "SPIN", "QUICK_RISE", "SLOW_RISE", "QUICK_FALL", "TICK", "LOW_TICK")

        /** Parses the kit file. Anything malformed throws: no pattern is then played. */
        fun parse(json: String): HapticPatterns {
            val root = JsonParser.parseString(json).asJsonObject
            require(root.get("formatVersion").asInt == 1) { "Unknown haptics format" }
            val patterns = root.getAsJsonObject("patterns").entrySet().associate { (name, value) ->
                name to pattern(name, value.asJsonObject)
            }
            return HapticPatterns(patterns)
        }

        private fun pattern(name: String, value: JsonObject): HapticPattern {
            val android = value.getAsJsonObject("android")
                ?: return HapticPattern(name, emptyList(), null, platformOnly = true)
            val primitives = android.getAsJsonArray("primitives")?.map { element ->
                val primitive = element.asJsonObject
                HapticPrimitive(
                    id = primitive.get("id").asString,
                    scale = primitive.get("scale").asFloat,
                    delayMs = primitive.get("delayMs").asInt
                ).also {
                    require(it.id in KNOWN_PRIMITIVES) { "Unknown primitive ${it.id} in $name" }
                    require(it.scale in 0f..1f && it.delayMs >= 0) { "Primitive out of range in $name" }
                }
            }.orEmpty()
            val waveform = android.getAsJsonObject("waveform")?.let { wave ->
                val timings = wave.getAsJsonArray("timingsMs").map { it.asLong }.toLongArray()
                val amplitudes = wave.getAsJsonArray("amplitudes").map { it.asInt }.toIntArray()
                require(timings.size == amplitudes.size && timings.isNotEmpty()) { "Waveform lengths differ in $name" }
                require(timings.all { it >= 0 } && amplitudes.all { it in 0..MAX_AMPLITUDE }) { "Waveform out of range in $name" }
                HapticWaveform(timings, amplitudes)
            }
            return HapticPattern(name, primitives, waveform, platformOnly = false)
        }

        private const val MAX_AMPLITUDE = 255
    }
}
