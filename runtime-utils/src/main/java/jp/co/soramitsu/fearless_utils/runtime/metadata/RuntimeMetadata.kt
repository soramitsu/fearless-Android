package jp.co.soramitsu.fearless_utils.runtime.metadata

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
    val signedExtensions: List<String>
)
