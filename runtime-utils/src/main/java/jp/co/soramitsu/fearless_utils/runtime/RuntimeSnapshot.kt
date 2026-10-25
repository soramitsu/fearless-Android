package jp.co.soramitsu.fearless_utils.runtime

import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata

typealias OverriddenConstantsMap = Map<String, Map<String, String>>

class RuntimeSnapshot(
    val typeRegistry: TypeRegistry,
    val metadata: RuntimeMetadata,
    val overrides: OverriddenConstantsMap? = null
)
