package jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import jp.co.soramitsu.fearless_utils.extensions.toHex
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.Primitive
import jp.co.soramitsu.fearless_utils.scale.dataType.byte
import jp.co.soramitsu.fearless_utils.scale.dataType.uint16
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow

sealed class Era {
    object Immortal : Era()

    class Mortal(val period: Int, val phase: Int) : Era()

    companion object {
        /**
         * @return first - period, second - phase
         */
        fun getPeriodPhaseFromBlockPeriod(
            currentBlockNumber: Int,
            periodInBlocks: Int
        ): Pair<Int, Int> {
            var callPeriod = 2.0.pow(ceil(log2(periodInBlocks.toDouble()))).toInt()
            callPeriod = callPeriod.coerceIn(4, 1 shl 16)
            val phase = currentBlockNumber % callPeriod
            val quantizeFactor = max(1, callPeriod shr 12)
            val quantizePhase = phase / quantizeFactor * quantizeFactor
            return callPeriod to quantizePhase
        }

        fun getEraFromBlockPeriod(currentBlockNumber: Int, periodInBlocks: Int): Mortal =
            getPeriodPhaseFromBlockPeriod(currentBlockNumber, periodInBlocks).let {
                Mortal(it.first, it.second)
            }
    }
}

object EraType : Primitive<Era>("Era") {

    override fun decode(scaleCodecReader: ScaleCodecReader, runtime: RuntimeSnapshot): Era {
        val firstByte = byte.read(scaleCodecReader).toHex()

        return if (firstByte == "00") {
            Era.Immortal
        } else {
            val secondByte = byte.read(scaleCodecReader).toHex()
            val encoded = (secondByte + firstByte).toInt(16)
            val period = 2 shl (encoded % 16)
            val quantizeFactor = max(1, period shr 12)
            val phase = (encoded shr 4) * quantizeFactor

            Era.Mortal(period, phase)
        }
    }

    override fun encode(scaleCodecWriter: ScaleCodecWriter, runtime: RuntimeSnapshot, value: Era) {
        when (value) {
            is Era.Immortal -> byte.write(scaleCodecWriter, 0)
            is Era.Mortal -> {
                val quantizeFactor = max(1, value.period shr 12)
                val trailingZeros = value.period.countTrailingZeroBits() - 1
                val encoded =
                    trailingZeros.coerceIn(1, 15) or ((value.phase / quantizeFactor) shl 4)

                uint16.write(scaleCodecWriter, encoded)
            }
        }
    }

    override fun isValidInstance(instance: Any?) = instance is Era
}
