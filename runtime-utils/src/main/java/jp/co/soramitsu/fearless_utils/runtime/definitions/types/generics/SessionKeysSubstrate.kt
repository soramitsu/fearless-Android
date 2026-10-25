package jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics

import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypePresetBuilder
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.getOrCreate
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct

@Suppress("FunctionName")
fun SessionKeysSubstrate(typePresetBuilder: TypePresetBuilder) = Struct(
    name = "SessionKeysSubstrate",
    mapping = linkedMapOf(
        "grandpa" to typePresetBuilder.getOrCreate("AccountId"),
        "babe" to typePresetBuilder.getOrCreate("AccountId"),
        "im_online" to typePresetBuilder.getOrCreate("AccountId")
    )
)
