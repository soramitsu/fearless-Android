package jp.co.soramitsu.fearless_utils.runtime.definitions

import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypePreset
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypePresetBuilder
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.create
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.getOrCreate
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.newBuilder
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Alias
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.CollectionEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.SetType
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.skipAliasesOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigInteger

private const val TOKEN_SET = "set"
private const val TOKEN_STRUCT = "struct"
private const val TOKEN_ENUM = "enum"

object TypeDefinitionParserV2 {

    private class Params(
        val types: Map<String, JsonElement>,
        val dynamicTypeResolver: DynamicTypeResolver,
        val typesBuilder: TypePresetBuilder
    )

    fun parseBaseDefinitions(
        tree: TypeDefinitionsTreeV2,
        typePreset: TypePreset,
        dynamicTypeResolver: DynamicTypeResolver = DynamicTypeResolver.defaultCompoundResolver(),
        getUnknownTypes: Boolean = false
    ): ParseResult {
        val builder = typePreset.newBuilder()

        val params = Params(tree.types, dynamicTypeResolver, builder)

        parseTypes(params)

        val unknownTypes = if (getUnknownTypes) params.typesBuilder.entries
            .mapNotNull { (name, typeRef) -> if (!typeRef.isResolved()) name else null } else emptyList()

        return ParseResult(params.typesBuilder, unknownTypes)
    }

    fun parseNetworkVersioning(
        tree: TypeDefinitionsTreeV2,
        typePreset: TypePreset,
        currentRuntimeVersion: Int = tree.runtimeId!!,
        dynamicTypeResolver: DynamicTypeResolver = DynamicTypeResolver.defaultCompoundResolver(),
        getUnknownTypes: Boolean = false,
        upto14: Boolean = false
    ): ParseResult {
        val versioning = tree.versioning
        requireNotNull(versioning)

        val builder = typePreset.newBuilder()

        versioning.filter { it.isMatch(currentRuntimeVersion) }
            .sortedBy(TypeDefinitionsTreeV2.Versioning::from)
            .forEach {
                parseTypes(Params(it.types, dynamicTypeResolver, builder), !upto14)
            }

        val unknownTypes = if (getUnknownTypes) builder.entries
            .mapNotNull { (name, typeRef) -> if (!typeRef.isResolved()) name else null } else emptyList()

        return ParseResult(builder, unknownTypes)
    }

    private fun parseTypes(parsingParams: Params, doAliases: Boolean = false) {
        for (name in parsingParams.types.keys) {
            val type = parse(parsingParams, name, doAliases) ?: continue

            parsingParams.typesBuilder.type(type)
        }
    }

    private fun parse(parsingParams: Params, name: String, doAliases: Boolean): Type<*>? {
        val typeValue = parsingParams.types[name]

        return parseType(parsingParams, name, typeValue, doAliases)
    }

    private fun parseType(parsingParams: Params, name: String, typeValue: JsonElement?, doAliases: Boolean): Type<*>? {
        if (typeValue == null) return null

        val typesBuilder = parsingParams.typesBuilder

        return when {
            typeValue is JsonPrimitive && typeValue.jsonPrimitive.isString -> {
                val content = typeValue.jsonPrimitive.content
                val dynamicType = resolveDynamicType(parsingParams, name, content)

                when {
                    dynamicType != null -> dynamicType
                    content == name -> parsingParams.typesBuilder[name]?.value
                    else -> {
                        val fromType = typesBuilder[name]
                        if (doAliases && fromType != null && fromType.value is Alias) {
                            val toTypeValue = typesBuilder[content]?.skipAliasesOrNull()
                            val fromTypeValue = (fromType.value as Alias).aliasedReference
                            if (fromTypeValue.value != null) {
                                val aliasSkipped = fromTypeValue.skipAliasesOrNull()
                                if (toTypeValue != null && toTypeValue.value?.name != aliasSkipped?.value?.name) {
                                    typesBuilder.type(Alias(fromTypeValue.value!!.name, typesBuilder.getOrCreate(content)))
                                }
                            }
                        }
                        Alias(name, typesBuilder.getOrCreate(content))
                    }
                }
            }

            typeValue is JsonObject -> {
                val typeValueCasted = typeValue.jsonObject

                val typePrimitive = (typeValueCasted["type"] as? JsonPrimitive)
                when (typePrimitive?.contentOrNull) {
                    TOKEN_STRUCT -> {
                        val typeMapping = typeValueCasted["type_mapping"] as? JsonArray
                        val children = parseTypeMapping(parsingParams, typeMapping)

                        Struct(name, children)
                    }

                    TOKEN_ENUM -> {
                        val valueList = (typeValueCasted["value_list"] as? JsonArray)?.mapNotNull {
                            (it as? JsonPrimitive)?.content
                        }
                        val typeMapping = typeValueCasted["type_mapping"] as? JsonArray

                        when {
                            valueList != null -> CollectionEnum(name, valueList)

                            typeMapping != null -> {
                                val children = parseTypeMapping(parsingParams, typeMapping)
                                    .map { (name, typeRef) -> DictEnum.Entry(name, typeRef) }

                                DictEnum(name, children)
                            }
                            else -> null
                        }
                    }

                    TOKEN_SET -> {
                        val valueTypeName = (typeValueCasted["value_type"] as JsonPrimitive).content
                        val valueListRaw = (typeValueCasted["value_list"] as? JsonObject)
                            ?.mapValues { entry -> entry.value.jsonPrimitive.content.toDouble() }
                            .orEmpty()

                        val valueTypeRef = resolveTypeAllWaysOrCreate(parsingParams, valueTypeName)

                        val valueList = valueListRaw.mapValues { (_, value) ->
                            BigInteger(value.toInt().toString())
                        }

                        SetType(name, valueTypeRef, LinkedHashMap(valueList))
                    }

                    else -> null
                }
            }

            else -> null
        }
    }

    private fun parseTypeMapping(
        parsingParams: Params,
        typeMapping: JsonArray?
    ): LinkedHashMap<String, TypeReference> {
        val children = LinkedHashMap<String, TypeReference>()

        val typeMappingList = typeMapping?.mapNotNull { types ->
            (types as? JsonArray)?.toList()
                ?.mapNotNull { field -> (field as? JsonPrimitive)?.content }
                ?.takeIf { fieldData -> fieldData.size == 2 }
        }.orEmpty()

        for ((fieldName, fieldType) in typeMappingList) {
            children[fieldName] = resolveTypeAllWaysOrCreate(parsingParams, fieldType)
        }

        return children
    }

    private fun resolveDynamicType(
        parsingParams: Params,
        name: String,
        typeDef: String
    ): Type<*>? {
        return parsingParams.dynamicTypeResolver.createDynamicType(name, typeDef) {
            resolveTypeAllWaysOrCreate(parsingParams, it)
        }
    }

    private fun resolveTypeAllWaysOrCreate(
        parsingParams: Params,
        typeDef: String,
        name: String = typeDef
    ): TypeReference {
        return parsingParams.typesBuilder[name]
            ?: resolveDynamicType(parsingParams, name, typeDef)?.let(::TypeReference)
            ?: parsingParams.typesBuilder.create(name)
    }
}
