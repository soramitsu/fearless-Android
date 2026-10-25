package jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.errors.EncodeDecodeException
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.skipAliasesOrNull

open class DictEnum(
    name: String,
    val elements: Map<Int, Entry<TypeReference>>
) : Type<DictEnum.Entry<Any?>>(name) {

    constructor(
        name: String,
        elements: List<Entry<TypeReference>>
    ) : this(
        name = name,
        elements = elements
            .withIndex()
            .associateBy(
                keySelector = { it.index },
                valueTransform = { it.value }
            )
    )

    data class Entry<out T>(val name: String, val value: T)

    override fun decode(scaleCodecReader: ScaleCodecReader, runtime: RuntimeSnapshot): Entry<Any?> {
        val typeIndex = scaleCodecReader.readByte().toUByte().toInt()
        val entry = elements[typeIndex] ?: elementNotFound(typeIndex)

        val decoded = entry.value.requireValue().decode(scaleCodecReader, runtime)

        return Entry(entry.name, decoded)
    }

    override fun encode(
        scaleCodecWriter: ScaleCodecWriter,
        runtime: RuntimeSnapshot,
        value: Entry<Any?>
    ) {
        val entry = entryOf(value.name)

        val type = entry.value.value.requireValue()

        scaleCodecWriter.writeByte(entry.key)
        type.encodeUnsafe(scaleCodecWriter, runtime, value.value)
    }

    override fun isValidInstance(instance: Any?): Boolean {
        if (instance !is Entry<*>) return false

        val elementEntry = elements.values.find { it.name == instance.name } ?: return false

        return elementEntry.value.requireValue().isValidInstance(instance.value)
    }

    operator fun get(name: String): Type<*>? {
        return entryOf(name).value.value.skipAliasesOrNull()?.value
    }

    override val isFullyResolved: Boolean
        get() = elements.values.all { it.value.isResolved() }

    protected fun entryOf(name: String) = elements.entries.find { (_, value) -> value.name == name }
        ?: elementNotFound(name)

    private fun elementNotFound(name: String): Nothing {
        throw EncodeDecodeException("No $name in ${names()}")
    }

    protected fun elementNotFound(index: Int): Nothing {
        throw EncodeDecodeException("No index $index found in ${indices()}")
    }

    private fun names() = elements.map { (_, value) -> value.name }
    private fun indices() = elements.keys
}
