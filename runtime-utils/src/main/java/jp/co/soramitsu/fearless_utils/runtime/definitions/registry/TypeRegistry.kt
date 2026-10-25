package jp.co.soramitsu.fearless_utils.runtime.definitions.registry

import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.preprocessors.RemoveGenericNoisePreprocessor
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.resolvedOrNull
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.skipAliases

interface RequestPreprocessor {

    fun process(definition: String): String
}

class TypeRegistry(
    val types: Map<String, TypeReference> = mapOf(),
    val dynamicTypeResolver: DynamicTypeResolver
) {

    operator fun get(
        definition: String
    ): Type<*>? {
        val typeRef = getTypeReference(definition)

        return typeRef?.value
    }

    inline operator fun <reified R> get(key: String): R? {
        return get(key)?.let { it as? R }
    }

    operator fun plus(other: TypeRegistry): TypeRegistry {
        return TypeRegistry(
            types = types + other.types,
            dynamicTypeResolver = DynamicTypeResolver(dynamicTypeResolver.extensions + other.dynamicTypeResolver.extensions)
        )
    }

    private fun getTypeReference(definition: String): TypeReference? {
        val preprocessed = applyPreprocessor(definition)

        val result = types[definition]
            ?: types[preprocessed]
            ?: resolveDynamicType(preprocessed)?.resolvedOrNull()
            ?: resolveDynamicType(definition)

        return result?.skipAliases()
    }

    private fun resolveDynamicType(definition: String): TypeReference? {
        val type = dynamicTypeResolver.createDynamicType(definition, definition) {
            getTypeReference(it) ?: TypeReference(null)
        }

        return type?.let { TypeReference(type) }
    }

    private fun applyPreprocessor(requestDef: String): String {
        return RemoveGenericNoisePreprocessor.process(requestDef)
    }
}
