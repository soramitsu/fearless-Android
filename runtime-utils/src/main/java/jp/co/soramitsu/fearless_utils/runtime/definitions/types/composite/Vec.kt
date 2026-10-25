package jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.scale.dataType.compactInt

class Vec(name: String, typeReference: TypeReference) : WrapperType<List<*>>(name, typeReference) {

    override fun decode(scaleCodecReader: ScaleCodecReader, runtime: RuntimeSnapshot): List<*> {
        val type = typeReference.requireValue()
        val size = compactInt.read(scaleCodecReader)
        val result = mutableListOf<Any?>()

        repeat(size.toInt()) {
            val element = type.decode(scaleCodecReader, runtime)
            result.add(element)
        }

        return result
    }

    override fun encode(scaleCodecWriter: ScaleCodecWriter, runtime: RuntimeSnapshot, value: List<*>) {
        val type = typeReference.requireValue()
        val size = value.size.toBigInteger()
        compactInt.write(scaleCodecWriter, size)

        value.forEach {
            type.encodeUnsafe(scaleCodecWriter, runtime, it)
        }
    }

    override fun isValidInstance(instance: Any?): Boolean {
        return instance is List<*> && instance.all {
            typeReference.requireValue().isValidInstance(it)
        }
    }
}
