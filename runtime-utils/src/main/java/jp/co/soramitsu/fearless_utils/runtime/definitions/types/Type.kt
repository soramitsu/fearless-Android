package jp.co.soramitsu.fearless_utils.runtime.definitions.types

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.errors.EncodeDecodeException

class TypeReference(var value: Type<*>?) {
    private var resolutionInProgress: Boolean = false

    fun requireValue() = value ?: throw IllegalArgumentException("TypeReference is null")

    fun isResolved(): Boolean {
        if (isInRecursion()) {
            return true
        }

        resolutionInProgress = true

        val resolutionResult = resolveRecursive()

        resolutionInProgress = false

        return resolutionResult
    }

    private fun resolveRecursive() = value?.isFullyResolved ?: false

    private fun isInRecursion() = resolutionInProgress
}

abstract class Type<InstanceType>(val name: String) {

    interface InstanceConstructor<I> {

        fun constructInstance(typeRegistry: TypeRegistry, value: I): Any?
    }

    abstract val isFullyResolved: Boolean

    /**
     * @throws EncodeDecodeException
     */
    abstract fun decode(scaleCodecReader: ScaleCodecReader, runtime: RuntimeSnapshot): InstanceType

    /**
     * @throws EncodeDecodeException
     */
    abstract fun encode(
        scaleCodecWriter: ScaleCodecWriter,
        runtime: RuntimeSnapshot,
        value: InstanceType
    )

    abstract fun isValidInstance(instance: Any?): Boolean

    /**
     * @throws EncodeDecodeException
     */
    @Suppress("UNCHECKED_CAST")
    fun encodeUnsafe(scaleCodecWriter: ScaleCodecWriter, runtime: RuntimeSnapshot, value: Any?) {
        if (!isValidInstance(value)) {
            val valueTypeName = value?.let { it::class.java.simpleName }
            val message = """
                |$value ($valueTypeName) is not a valid instance of ${this.name}
                | (${this::class.java.simpleName})
            """.trimMargin()
            throw EncodeDecodeException(message)
        }

        encode(scaleCodecWriter, runtime, value as InstanceType)
    }
}
