package jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic

import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference

typealias TypeProvider = (typeDef: String) -> TypeReference

interface DynamicTypeExtension {

    fun createType(name: String, typeDef: String, typeProvider: TypeProvider): Type<*>?
}
