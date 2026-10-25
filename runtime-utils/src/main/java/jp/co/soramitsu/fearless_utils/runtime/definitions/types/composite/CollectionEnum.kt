package jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.scale.dataType.CollectionEnumType

class CollectionEnum(
    name: String,
    val elements: List<String>
) : Type<String>(name) {

    override fun decode(scaleCodecReader: ScaleCodecReader, runtime: RuntimeSnapshot): String {
        return CollectionEnumType(elements).read(scaleCodecReader)
    }

    override fun encode(scaleCodecWriter: ScaleCodecWriter, runtime: RuntimeSnapshot, value: String) {
        CollectionEnumType(elements).write(scaleCodecWriter, value)
    }

    override fun isValidInstance(instance: Any?): Boolean {
        return instance in elements
    }

    operator fun get(key: Int): String = elements[key]

    override val isFullyResolved = true
}
