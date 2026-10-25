package jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot

open class FixedByteArray(name: String, val length: Int) : Primitive<ByteArray>(name) {

    override fun decode(scaleCodecReader: ScaleCodecReader, runtime: RuntimeSnapshot): ByteArray {
        return scaleCodecReader.readByteArray(length)
    }

    override fun encode(scaleCodecWriter: ScaleCodecWriter, runtime: RuntimeSnapshot, value: ByteArray) {
        return scaleCodecWriter.directWrite(value, 0, length)
    }

    override fun isValidInstance(instance: Any?): Boolean {
        return instance is ByteArray && instance.size == length
    }
}
