package jp.co.soramitsu.crowdloan.impl.data.repository

import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.crowdloan
import jp.co.soramitsu.common.utils.hasModule
import jp.co.soramitsu.common.utils.u32ArgumentFromStorageKey
import jp.co.soramitsu.crowdloan.api.data.network.blockhain.binding.Contribution
import jp.co.soramitsu.crowdloan.api.data.network.blockhain.binding.FundInfo
import jp.co.soramitsu.crowdloan.api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.crowdloan.api.data.network.blockhain.binding.bindContribution
import jp.co.soramitsu.crowdloan.api.data.network.blockhain.binding.bindFundInfo
import jp.co.soramitsu.crowdloan.api.data.repository.CrowdloanRepository
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.bytes
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u32
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toByteArray
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import java.math.BigInteger

private const val CONTRIBUTIONS_CHILD_SUFFIX = "crowdloan"

class CrowdloanRepositoryImpl(
    private val remoteStorage: StorageDataSource,
    private val chainRegistry: ChainRegistry
) : CrowdloanRepository {

    override suspend fun isCrowdloansAvailable(chainId: ChainId): Boolean {
        return runtimeFor(chainId).metadata.hasModule(Modules.CROWDLOAN)
    }

    override suspend fun allFundInfos(chainId: ChainId): Map<ParaId, FundInfo> {
        return remoteStorage.queryByPrefix(
            prefixKeyBuilder = { it.metadata.crowdloan().storage("Funds").storageKey() },
            keyExtractor = { it.u32ArgumentFromStorageKey() },
            chainId = chainId
        ) { scale, runtime, paraId -> bindFundInfo(scale!!, runtime, paraId) }
    }

    override suspend fun getContribution(
        chainId: ChainId,
        accountId: AccountId,
        paraId: ParaId,
        fundIndex: BigInteger
    ): Contribution? {
        return remoteStorage.queryChildState(
            storageKeyBuilder = { it.typeRegistry["0"]!!.bytes(it, accountId).toHexString(withPrefix = true) },
            childKeyBuilder = {
                val suffix = (CONTRIBUTIONS_CHILD_SUFFIX.encodeToByteArray() + u32.toByteArray(it, fundIndex))
                    .blake2b256()

                write(suffix)
            },
            binder = { scale, runtime -> scale?.let { bindContribution(it, runtime) } },
            chainId = chainId
        )
    }

    private suspend fun runtimeFor(chainId: String) = chainRegistry.getRuntime(chainId)
}
