package jp.co.soramitsu.fearless_utils.runtime.definitions.registry

import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type

fun TypeRegistry.getOrThrow(
    definition: String
): Type<*> {
    return get(definition) ?: error("Type $definition was not found.")
}
