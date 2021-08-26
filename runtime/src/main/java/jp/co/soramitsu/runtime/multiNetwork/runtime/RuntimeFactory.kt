package jp.co.soramitsu.runtime.multiNetwork.runtime

import com.google.gson.Gson
import jp.co.soramitsu.common.utils.md5
import jp.co.soramitsu.core_db.dao.ChainDao
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionParser.parseBaseDefinitions
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionParser.parseNetworkVersioning
import jp.co.soramitsu.fearless_utils.runtime.definitions.TypeDefinitionsTree
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.extentsions.GenericsExtension
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypePreset
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.substratePreParsePreset
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadataSchema
import jp.co.soramitsu.runtime.multiNetwork.chain.model.TypesUsage
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class ConstructedRuntime(
    val runtime: RuntimeSnapshot,
    val metadataHash: String,
    val baseTypesHash: String?,
    val ownTypesHash: String?,
    val runtimeVersion: Int,
    val typesUsage: TypesUsage,
)

object BaseTypesNotInCacheException : Exception()
object ChainInfoNotInCacheException : Exception()
object NoRuntimeVersionException : Exception()

class RuntimeFactory(
    private val runtimeFilesCache: RuntimeFilesCache,
    private val chainDao: ChainDao,
    private val gson: Gson,
) {

    // Acts as a operation queue due to be single threaded and guarantee of sequential execution
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    /**
     * @throws BaseTypesNotInCacheException
     * @throws ChainInfoNotInCacheException
     */
    suspend fun constructRuntime(
        chainId: String,
        typesUsage: TypesUsage,
    ): ConstructedRuntime? = withContext(dispatcher) {
        val runtimeVersion = chainDao.runtimeInfo(chainId)?.version ?: return@withContext null

        val (types, baseHash, ownHash) = when (typesUsage) {
            TypesUsage.BASE -> {
                val (types, baseHash) = constructBaseTypes()

                Triple(types, baseHash, null)
            }
            TypesUsage.BOTH -> constructBaseAndChainTypes(chainId, runtimeVersion)
            TypesUsage.OWN -> {
                val (types, ownHash) = constructOwnTypes(chainId, runtimeVersion)

                Triple(types, null, ownHash)
            }
        }

        val typeRegistry = TypeRegistry(types, DynamicTypeResolver(DynamicTypeResolver.DEFAULT_COMPOUND_EXTENSIONS + GenericsExtension))

        val runtimeMetadataRaw = runCatching { runtimeFilesCache.getChainMetadata(chainId) }
            .getOrElse { throw ChainInfoNotInCacheException }

        val runtimeMetadataStruct = RuntimeMetadataSchema.read(runtimeMetadataRaw)
        val runtimeMetadata = RuntimeMetadata(typeRegistry, runtimeMetadataStruct)

        ConstructedRuntime(
            runtime = RuntimeSnapshot(typeRegistry, runtimeMetadata),
            metadataHash = runtimeMetadataRaw.md5(),
            baseTypesHash = baseHash,
            ownTypesHash = ownHash,
            runtimeVersion = runtimeVersion,
            typesUsage = typesUsage
        )
    }

    private suspend fun constructBaseAndChainTypes(
        chainId: String,
        runtimeVersion: Int,
    ): Triple<TypePreset, String, String> {
        val (basePreset, baseHash) = constructBaseTypes()
        val (chainPreset, ownHash) = constructOwnTypes(chainId, runtimeVersion, basePreset)

        return Triple(chainPreset, baseHash, ownHash)
    }

    private suspend fun constructOwnTypes(
        chainId: String,
        runtimeVersion: Int,
        baseTypes: TypePreset = substratePreParsePreset(),
    ): Pair<TypePreset, String> {
        val ownTypesRaw = runCatching { runtimeFilesCache.getChainTypes(chainId) }
            .getOrElse { throw ChainInfoNotInCacheException }

        val ownTypesTree = fromJson(ownTypesRaw)

        val withoutVersioning = parseBaseDefinitions(ownTypesTree, baseTypes).typePreset

        val typePreset = parseNetworkVersioning(ownTypesTree, withoutVersioning, runtimeVersion).typePreset

        return typePreset to ownTypesRaw.md5()
    }

    private suspend fun constructBaseTypes(): Pair<TypePreset, String> {
        val baseTypesRaw = runCatching { runtimeFilesCache.getBaseTypes() }
            .getOrElse { throw BaseTypesNotInCacheException }

        val typePreset = parseBaseDefinitions(fromJson(baseTypesRaw), substratePreParsePreset()).typePreset

        return typePreset to baseTypesRaw.md5()
    }

    private fun fromJson(types: String): TypeDefinitionsTree = gson.fromJson(types, TypeDefinitionsTree::class.java)
}
