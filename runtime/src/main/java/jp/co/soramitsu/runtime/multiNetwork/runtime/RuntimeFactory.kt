package jp.co.soramitsu.runtime.multiNetwork.runtime

import com.google.gson.Gson
import java.util.concurrent.Executors
import jp.co.soramitsu.common.utils.md5
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.fearless_utils.runtime.OverriddenConstantsMap
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionParser
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionsTree
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.extentsions.GenericsExtension
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypePreset
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.v14Preset
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.v14.TypesParserV14
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadataReader
import jp.co.soramitsu.fearless_utils.runtime.metadata.builder.VersionedRuntimeBuilder
import jp.co.soramitsu.fearless_utils.runtime.metadata.v14.RuntimeMetadataSchemaV14
import jp.co.soramitsu.runtime.multiNetwork.chain.model.TypesUsage
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

class ConstructedRuntime(
    val runtime: RuntimeSnapshot,
    val metadataHash: String,
    val ownTypesHash: String?,
    val runtimeVersion: Int,
    val typesUsage: TypesUsage
)

object ChainInfoNotInCacheException : Exception()
object NoRuntimeVersionException : Exception()

class RuntimeFactory(
    private val runtimeFilesCache: RuntimeFilesCache,
    private val chainDao: ChainDao,
    private val gson: Gson
) {

    // Acts as a operation queue due to be single threaded and guarantee of sequential execution
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    /**
     * @throws ChainInfoNotInCacheException
     */
    suspend fun constructRuntime(
        chainId: String,
        typesUsage: TypesUsage
    ): ConstructedRuntime? = withContext(dispatcher) {
        val runtimeVersion = chainDao.runtimeInfo(chainId)?.syncedVersion ?: return@withContext null
        val (types, ownHash) = when (typesUsage) {
            TypesUsage.ON_CHAIN -> constructOwnTypes(chainId, runtimeVersion)
            TypesUsage.UNSUPPORTED -> emptyMap<String, TypeReference>() to null
        }

        val metadataRaw = runCatching { runtimeFilesCache.getChainMetadata(chainId) }
            .getOrElse { throw ChainInfoNotInCacheException }

        val runtimeMetadataRaw = RuntimeMetadataReader.read(metadataRaw)
        val typeRegistry = if (runtimeMetadataRaw.metadataVersion < 14) {
            TypeRegistry(types, DynamicTypeResolver(DynamicTypeResolver.DEFAULT_COMPOUND_EXTENSIONS + GenericsExtension))
        } else {
            TypeRegistry(
                types,
                DynamicTypeResolver.defaultCompoundResolver()
            )
        }

        val runtimeMetadata = VersionedRuntimeBuilder.buildMetadata(runtimeMetadataRaw, typeRegistry)

        val overrides = createOverrides(chainId)

        ConstructedRuntime(
            runtime = RuntimeSnapshot(typeRegistry, runtimeMetadata, overrides),
            metadataHash = metadataRaw.md5(),
            ownTypesHash = ownHash,
            runtimeVersion = runtimeVersion,
            typesUsage = typesUsage
        )
    }

    private suspend fun createOverrides(
        chainId: String
    ): OverriddenConstantsMap? {
        val ownTypesRaw = runCatching { runtimeFilesCache.getChainTypes(chainId) }
            .getOrElse { throw ChainInfoNotInCacheException }

        val overrides = fromJson(ownTypesRaw).overrides
        val result = overrides?.mapNotNull {
            it.constants?.let { constants ->
                it.module to constants.associate { constant ->
                    constant.name to constant.value
                }
            }
        }?.toMap()

        return result
    }

    private suspend fun constructOwnTypes(
        chainId: String,
        runtimeVersion: Int
    ): Pair<TypePreset, String> {
        val ownTypesRaw = runCatching { runtimeFilesCache.getChainTypes(chainId) }
            .getOrElse { throw ChainInfoNotInCacheException }

        val metadataRaw = runCatching { runtimeFilesCache.getChainMetadata(chainId) }
            .getOrElse { throw ChainInfoNotInCacheException }
        val reader = RuntimeMetadataReader.read(metadataRaw)

        val parseResult = TypesParserV14.parse(
            reader.metadata[RuntimeMetadataSchemaV14.lookup],
            v14Preset()
        )

        val networkTypePreset = TypeDefinitionParser.parseNetworkVersioning(
            fromJson(ownTypesRaw),
            parseResult.typePreset,
            runtimeVersion
        ).typePreset

        return networkTypePreset to ownTypesRaw.md5()
    }

    private fun fromJson(types: String): TypeDefinitionsTree = gson.fromJson(types, TypeDefinitionsTree::class.java)
}
