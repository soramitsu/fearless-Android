package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import jp.co.soramitsu.fearless_utils.scale.dataType.DataType

sealed class Era {
    object Immortal : Era()

    class Mortal(val period: Byte, val phase: Byte) : Era()
}

object EraType : DataType<Era>() {
    override fun conformsType(value: Any?) = value is Era

    override fun read(reader: ScaleCodecReader): Era {
        val firstByte = reader.readByte()

        return if (firstByte == 0.toByte()) {
            Era.Immortal
        } else {
            val secondByte = reader.readByte()

            Era.Mortal(firstByte, secondByte)
        }
    }

    override fun write(writer: ScaleCodecWriter, value: Era) {
        when (value) {
            is Era.Immortal -> writer.writeByte(0)
            is Era.Mortal -> {
                writer.writeByte(value.period)
                writer.writeByte(value.phase)
            }
        }
    }
}