package jp.co.soramitsu.fearless_utils.runtime.metadata

import jp.co.soramitsu.fearless_utils.common.assertInstance
import jp.co.soramitsu.fearless_utils.getFileContentFromResources
import jp.co.soramitsu.fearless_utils.runtime.RealRuntimeProvider
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.stub.FakeType
import jp.co.soramitsu.fearless_utils.runtime.metadata.builder.VersionedRuntimeBuilder
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.StorageEntryType
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner

data class Holder(val module: String, val type: String)

@RunWith(MockitoJUnitRunner::class)
class MetadataTest {

    @Mock
    private lateinit var typeRegistry: TypeRegistry

    @Before
    fun startUp() {
        Mockito.`when`(typeRegistry[Mockito.anyString()])
            .thenAnswer { FakeType(it.arguments[0] as String) }
    }

    @Test
    fun `should decode metadata`() {
        val inHex = getFileContentFromResources("westend_metadata")

        val metadataReader = RuntimeMetadataReader.read(inHex)
        val metadata = VersionedRuntimeBuilder.buildMetadata(metadataReader, typeRegistry)

        assertInstance<StorageEntryType.Plain>(metadata.module("System").storage("Events").type)
        assertEquals(4 to 2, metadata.module("Balances").event("Transfer").index)
        assertEquals(4 to 0, metadata.module("Balances").call("transfer").index)
    }

    @Test
    fun `should decode metadata with NMap`() {
        val inHex = getFileContentFromResources("statemine_metadata")

        val metadataReader = RuntimeMetadataReader.read(inHex)
        val metadata = VersionedRuntimeBuilder.buildMetadata(metadataReader, typeRegistry)

        assertInstance<StorageEntryType.NMap>(metadata.module("Assets").storage("Approvals").type)
    }

    @Test
    fun `connect metadata with real type registry`() {
        val metadataReader = RealRuntimeProvider.buildRawMetadata()
        val kusamaTypeRegistry = RealRuntimeProvider.buildRegistry("kusama")

        VersionedRuntimeBuilder.buildMetadata(metadataReader, kusamaTypeRegistry)
    }

    @Test
    fun `find unknown types in metadata`() {
        val metadataReader = RealRuntimeProvider.buildRawMetadata()
        val kusamaTypeRegistry = RealRuntimeProvider.buildRegistry("kusama")

        val toResolve = mutableSetOf<Holder>()

        for (module in metadataReader.metadata[RuntimeMetadataSchema.modules]) {
            val toResolveInModule = mutableSetOf<String>()

            val storage = module[ModuleMetadataSchema.storage]

            storage?.let {
                for (storageItem in storage[StorageMetadataSchema.entries]) {
                    val toResolveStorage =
                        when (val item = storageItem[StorageEntryMetadataSchema.type]) {
                            is String -> listOf(item)
                            is EncodableStruct<*> -> when (item.schema) {
                                MapSchema -> listOf(item[MapSchema.key], item[MapSchema.value])
                                DoubleMapSchema -> listOf(
                                    item[DoubleMapSchema.key1],
                                    item[DoubleMapSchema.key2],
                                    item[DoubleMapSchema.value]
                                )
                                else -> listOf()
                            }
                            else -> listOf()
                        }

                    toResolveInModule += toResolveStorage
                }
            }

            val calls = module[ModuleMetadataSchema.calls]

            calls?.let {
                for (call in calls) {
                    for (argument in call[FunctionMetadataSchema.arguments]) {
                        toResolveInModule += argument[FunctionArgumentMetadataSchema.type]
                    }
                }
            }

            val events = module[ModuleMetadataSchema.events]

            events?.let {
                for (event in events) {
                    toResolveInModule += event[EventMetadataSchema.arguments]
                }
            }

            for (const in module[ModuleMetadataSchema.constants]) {
                toResolveInModule += const[ModuleConstantMetadataSchema.type]
            }

            toResolve += toResolveInModule.map { Holder(module[ModuleMetadataSchema.name], it) }
        }

        val notResolvable =
            toResolve.filter { kusamaTypeRegistry[it.type]?.isFullyResolved?.not() ?: true }

        println("To resolve: ${toResolve.size}, Resolved: ${(toResolve - notResolvable).size}, Not resolved: ${notResolvable.size}")

        notResolvable.groupBy { it.module }
            .forEach { (module, types) ->
                println("$module: ${types.map(Holder::type)}\n")
            }

        assertEquals(2, notResolvable.size)
    }
}
