package jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.scale.dataType.byteArray

class DynamicByteArray(name: String) : Primitive<ByteArray>(name) {

    override fun decode(scaleCodecReader: ScaleCodecReader, runtime: RuntimeSnapshot): ByteArray {
        return byteArray.read(scaleCodecReader)
    }

    override fun encode(scaleCodecWriter: ScaleCodecWriter, runtime: RuntimeSnapshot, value: ByteArray) {
        return byteArray.write(scaleCodecWriter, value)
    }

    override fun isValidInstance(instance: Any?): Boolean {
        return instance is ByteArray
    }
}
