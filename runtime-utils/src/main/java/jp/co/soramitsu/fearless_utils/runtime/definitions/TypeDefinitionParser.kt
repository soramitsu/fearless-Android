package jp.co.soramitsu.fearless_utils.runtime.definitions

import com.google.gson.annotations.SerializedName
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
import java.math.BigInteger

class TypeDefinitionsTree(
    @SerializedName("runtime_id")
    val runtimeId: Int?,
    val types: Map<String, Any>,
    val versioning: List<Versioning>?,
    val overrides: List<OverriddenItem>?
) {

    class OverriddenItem(
        val module: String,
        val constants: List<OverriddenConstant>?
    )

    class OverriddenConstant(
        val name: String,
        val value: String
    )

    class Versioning(
        @SerializedName("runtime_range") val range: List<Int?>,
        val types: Map<String, Any>
    ) {
        val from: Int
            get() = range.first()!!
        fun isMatch(v: Int): Boolean = (v >= from && range.size == 2) && ((range[1] == null) || (range[1] != null && range[1]!! >= v))
    }
}

class ParseResult(
    val typePreset: TypePreset,
    val unknownTypes: List<String>
)

private const val TOKEN_SET = "set"
private const val TOKEN_STRUCT = "struct"
private const val TOKEN_ENUM = "enum"

object TypeDefinitionParser {

    private class Params(
        val types: Map<String, Any>,
        val dynamicTypeResolver: DynamicTypeResolver,
        val typesBuilder: TypePresetBuilder
    )

    fun parseBaseDefinitions(
        tree: TypeDefinitionsTree,
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
        tree: TypeDefinitionsTree,
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
            .sortedBy(TypeDefinitionsTree.Versioning::from)
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

    private fun parseType(parsingParams: Params, name: String, typeValue: Any?, doAliases: Boolean): Type<*>? {
        val typesBuilder = parsingParams.typesBuilder

        return when (typeValue) {
            is String -> {
                val dynamicType = resolveDynamicType(parsingParams, name, typeValue)

                when {
                    dynamicType != null -> dynamicType
                    typeValue == name -> parsingParams.typesBuilder[name]?.value
                    else -> {
                        val fromType = typesBuilder[name]
                        if (doAliases && fromType != null && fromType.value is Alias) {
                            val toTypeValue = typesBuilder[typeValue]?.skipAliasesOrNull()
                            val fromTypeValue = (fromType.value as Alias).aliasedReference
                            if (fromTypeValue.value != null) {
                                val aliasSkipped = fromTypeValue.skipAliasesOrNull()
                                if (toTypeValue != null && toTypeValue.value?.name != aliasSkipped?.value?.name) {
                                    typesBuilder.type(Alias(fromTypeValue.value!!.name, typesBuilder.getOrCreate(typeValue)))
                                }
                            }
                        }
                        Alias(name, typesBuilder.getOrCreate(typeValue))
                    }
                }
            }

            is Map<*, *> -> {
                val typeValueCasted = typeValue as Map<String, Any?>

                when (typeValueCasted["type"]) {
                    TOKEN_STRUCT -> {
                        val typeMapping = typeValueCasted["type_mapping"] as List<List<String>>
                        val children = parseTypeMapping(parsingParams, typeMapping)

                        Struct(name, children)
                    }

                    TOKEN_ENUM -> {
                        val valueList = typeValueCasted["value_list"] as? List<String>
                        val typeMapping = typeValueCasted["type_mapping"] as? List<List<String>>

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
                        val valueTypeName = typeValueCasted["value_type"] as String
                        val valueListRaw = typeValueCasted["value_list"] as Map<String, Double>

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
        typeMapping: List<List<String>>
    ): LinkedHashMap<String, TypeReference> {
        val children = LinkedHashMap<String, TypeReference>()

        for ((fieldName, fieldType) in typeMapping) {
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
