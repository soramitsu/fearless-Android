package jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.extentsions

import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.DynamicTypeExtension
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.TypeProvider
import jp.co.soramitsu.fearless_utils.runtime.definitions.splitTuple
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.FixedArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Option
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Tuple
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Vec
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.ResultType
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.Compact
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.DynamicByteArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.FixedByteArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u8

object VectorExtension : WrapperExtension() {
    override val wrapperName = "Vec"

    override fun createWrapper(name: String, innerTypeRef: TypeReference): Type<*> {
        return if (innerTypeRef.value == u8) {
            DynamicByteArray(name)
        } else {
            Vec(name, innerTypeRef)
        }
    }
}

object CompactExtension : WrapperExtension() {
    override val wrapperName = "Compact"

    override fun createWrapper(name: String, innerTypeRef: TypeReference) = Compact(name)
}

object OptionExtension : WrapperExtension() {
    override val wrapperName = "Option"

    override fun createWrapper(name: String, innerTypeRef: TypeReference) = Option(name, innerTypeRef)
}

object BoxExtension : WrapperExtension() {
    override val wrapperName: String
        get() = "Box"

    override fun createWrapper(name: String, innerTypeRef: TypeReference): Type<*>? {
        return innerTypeRef.value
    }
}

object TupleExtension : DynamicTypeExtension {
    override fun createType(name: String, typeDef: String, typeProvider: TypeProvider): Type<*>? {
        if (!typeDef.startsWith("(")) return null

        val innerTypeRefDefinitions = typeDef.splitTuple()

        val innerTypeRefs = innerTypeRefDefinitions.map(typeProvider)

        return Tuple(name, innerTypeRefs)
    }
}

object FixedArrayExtension : DynamicTypeExtension {

    override fun createType(name: String, typeDef: String, typeProvider: TypeProvider): Type<*>? {
        if (!typeDef.startsWith("[")) return null

        val withoutBrackets = typeDef.removeSurrounding("[", "]").replace(" ", "")
        val (typeName, lengthRaw) = withoutBrackets.split(";")

        val length = lengthRaw.toInt()

        val typeRef = typeProvider(typeName)

        return if (typeRef.value == u8) {
            FixedByteArray(name, length)
        } else {
            FixedArray(name, length, typeRef)
        }
    }
}

object HashMapExtension : DynamicTypeExtension {

    override fun createType(name: String, typeDef: String, typeProvider: TypeProvider): Type<*>? {
        if (!typeDef.startsWith("HashMap")) return null
        val withoutBrackets = typeDef.removePrefix("HashMap").removeSurrounding("<", ">").replace(" ", "")
        if (withoutBrackets.split(",").size != 2) return null
        val tuple = "($withoutBrackets)"
        val typeRef = typeProvider(tuple)
        return Vec("Vec<$tuple>", typeRef)
    }
}

object ResultTypeExtension : DynamicTypeExtension {

    override fun createType(name: String, typeDef: String, typeProvider: TypeProvider): Type<*>? {
        if (!typeDef.startsWith("Result")) return null
        val withoutBrackets =
            typeDef.removePrefix("Result").removeSurrounding("<", ">").replace(" ", "")
        val types = withoutBrackets.split(",")
        if (types.size != 2) return null
        return ResultType(typeProvider(types[0]), typeProvider(types[1]))
    }
}
