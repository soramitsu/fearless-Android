package jp.co.soramitsu.fearless_utils.runtime.metadata

import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import java.math.BigInteger

interface WithName {
    val name: String
}

fun <T : WithName> List<T>.groupByName() = associateBy(WithName::name).toMap()

class RuntimeMetadata(
    val runtimeVersion: BigInteger,
    val modules: Map<String, Module>,
    val extrinsic: ExtrinsicMetadata
)

class ExtrinsicMetadata(
    val version: BigInteger,
    val signedExtensions: List<String>,
    val signedExtensionTypes: Map<String, SignedExtensionTypes> = emptyMap()
)

/** Portable metadata retains both halves of every signed extension. */
class SignedExtensionTypes(val extra: Type<*>?, val additional: Type<*>?)
