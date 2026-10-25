package jp.co.soramitsu.fearless_utils.runtime.definitions.types

import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.extentsions.GenericsExtension
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.v13Preset
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.BooleanType
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u8
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Event
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.FunctionArgument
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.MetadataFunction
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Storage
import java.math.BigInteger

abstract class BaseTypeTest {

    protected val typeRegistry = TypeRegistry(
        v13Preset(),
        dynamicTypeResolver = DynamicTypeResolver(
            extensions = DynamicTypeResolver.DEFAULT_COMPOUND_EXTENSIONS + listOf(GenericsExtension)
        )
    )

    protected val runtime: RuntimeSnapshot = RuntimeSnapshot(
        typeRegistry = typeRegistry,
        metadata = RuntimeMetadata(
            runtimeVersion = BigInteger.ONE,
            modules = mapOf(
                "A" to Module(
                    name = "A",
                    storage = Storage("_A", emptyMap()),
                    calls = mapOf(
                        "B" to MetadataFunction(
                            name = "B",
                            arguments = listOf(
                                FunctionArgument(
                                    name = "arg1",
                                    type = BooleanType
                                ),
                                FunctionArgument(
                                    name = "arg2",
                                    type = u8
                                )
                            ),
                            documentation = emptyList(),
                            index = 1 to 0
                        )
                    ),
                    events = mapOf(
                        "A" to Event(
                            name = "A",
                            arguments = listOf(
                                BooleanType,
                                u8
                            ),
                            documentation = emptyList(),
                            index = 1 to 0
                        ),
                    ),
                    constants = emptyMap(),
                    errors = emptyMap(),
                    index = BigInteger.ONE
                )
            ),
            extrinsic = ExtrinsicMetadata(
                version = BigInteger.ONE,
                signedExtensions = emptyList()
            )
        )
    )

}