@file:Suppress("EXPERIMENTAL_API_USAGE")

package jp.co.soramitsu.wallet.impl.data.network.blockchain

import java.math.BigInteger
import jp.co.soramitsu.account.api.extrinsic.ExtrinsicService
import jp.co.soramitsu.common.data.network.runtime.binding.AccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.EventRecord
import jp.co.soramitsu.common.data.network.runtime.binding.ExtrinsicStatusEvent
import jp.co.soramitsu.common.data.network.runtime.binding.OrmlTokensAccountData
import jp.co.soramitsu.common.data.network.runtime.binding.Phase
import jp.co.soramitsu.common.data.network.runtime.binding.bindExtrinsicStatusEventRecords
import jp.co.soramitsu.common.data.network.runtime.binding.bindOrNull
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.common.utils.tokens
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.FixedByteArray
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainAssetType
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.network.rpc.RpcCalls
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.runtime.storage.source.queryNonNull
import jp.co.soramitsu.wallet.api.data.cache.bindAccountInfoOrDefault
import jp.co.soramitsu.wallet.api.data.cache.bindOrmlTokensAccountDataOrDefault
import jp.co.soramitsu.wallet.impl.data.network.blockchain.bindings.bindTransferExtrinsic
import jp.co.soramitsu.wallet.impl.data.repository.totalBalance
import jp.co.soramitsu.wallet.impl.domain.model.Transfer

class WssSubstrateSource(
    private val rpcCalls: RpcCalls,
    private val remoteStorageSource: StorageDataSource,
    private val extrinsicService: ExtrinsicService
) : SubstrateRemoteSource {

    override suspend fun getAccountFreeBalance(chainAsset: Chain.Asset, accountId: AccountId): BigInteger {
        return when (val info = getAccountInfo(chainAsset, accountId)) {
            is OrmlTokensAccountData -> info.free
            is AccountInfo -> info.data.free
            else -> BigInteger.ZERO
        }
    }

    override suspend fun getTotalBalance(chainAsset: Chain.Asset, accountId: AccountId): BigInteger {
        return when (val info = getAccountInfo(chainAsset, accountId)) {
            is OrmlTokensAccountData -> info.totalBalance
            is AccountInfo -> info.totalBalance
            else -> BigInteger.ZERO
        }
    }

    @Suppress("IMPLICIT_CAST_TO_ANY")
    private suspend fun getAccountInfo(chainAsset: Chain.Asset, accountId: AccountId) = when (chainAsset.type) {
        null, ChainAssetType.Normal,
        ChainAssetType.SoraUtilityAsset -> {
            getDefaultAccountInfo(chainAsset.chainId, accountId)
        }
        ChainAssetType.OrmlChain,
        ChainAssetType.OrmlAsset,
        ChainAssetType.ForeignAsset,
        ChainAssetType.StableAssetPoolToken,
        ChainAssetType.LiquidCrowdloan,
        ChainAssetType.VToken,
        ChainAssetType.VSToken,
        ChainAssetType.SoraAsset,
        ChainAssetType.Stable -> {
            getOrmlTokensAccountData(chainAsset, accountId)
        }
        ChainAssetType.Equilibrium -> {
            getEquilibriumAccountInfo(chainAsset, accountId)
        }
        ChainAssetType.Unknown -> null
    }

    private suspend fun getDefaultAccountInfo(
        chainId: ChainId,
        accountId: AccountId
    ): AccountInfo {
        return remoteStorageSource.query(
            chainId = chainId,
            keyBuilder = {
                it.metadata.system().storage("Account").storageKey(it, accountId)
            },
            binding = { scale, runtime ->
                bindAccountInfoOrDefault(scale, runtime)
            }
        )
    }

    private suspend fun getEquilibriumAccountInfo(
        asset: Chain.Asset,
        accountId: AccountId
    ): AccountInfo {
        return remoteStorageSource.query(
            chainId = asset.chainId,
            keyBuilder = {
                it.metadata.module(Modules.EQBALANCES).storage("Account").storageKey(it, accountId, asset.currency)
            },
            binding = { scale, runtime ->
                bindAccountInfoOrDefault(scale, runtime)
            }
        )
    }

    private suspend fun getOrmlTokensAccountData(asset: Chain.Asset, accountId: AccountId): OrmlTokensAccountData {
        return remoteStorageSource.query(
            chainId = asset.chainId,
            keyBuilder = {
                it.metadata.tokens().storage("Accounts").storageKey(it, accountId, asset.currency)
            },
            binding = { scale, runtime ->
                bindOrmlTokensAccountDataOrDefault(scale, runtime)
            }
        )
    }

    override suspend fun getTransferFee(
        chain: Chain,
        transfer: Transfer,
        additional: (suspend ExtrinsicBuilder.() -> Unit)?,
        batchAll: Boolean
    ): BigInteger {
        return extrinsicService.estimateFee(
            chain = chain,
            useBatchAll = batchAll,
            formExtrinsic = {
                transfer(chain, transfer, this.runtime.typeRegistry)
                additional?.invoke(this)
            }
        )
    }

    override suspend fun performTransfer(
        accountId: AccountId,
        chain: Chain,
        transfer: Transfer,
        tip: BigInteger?,
        additional: (suspend ExtrinsicBuilder.() -> Unit)?,
        batchAll: Boolean
    ): String {
        return extrinsicService.submitExtrinsic(
            chain = chain,
            accountId = accountId,
            useBatchAll = batchAll,
            tip = tip,
            formExtrinsic = {
                transfer(chain, transfer, this.runtime.typeRegistry)
                additional?.invoke(this)
            }
        ).getOrThrow()
    }

    override suspend fun fetchAccountTransfersInBlock(
        chainId: ChainId,
        blockHash: String,
        accountId: ByteArray
    ): Result<List<TransferExtrinsicWithStatus>> = runCatching {
        val block = rpcCalls.getBlock(chainId, blockHash)

        val extrinsics = remoteStorageSource.queryNonNull(
            chainId = chainId,
            keyBuilder = { it.metadata.system().storage("Events").storageKey() },
            binding = { scale, runtime ->
                val statuses = bindExtrinsicStatusEventRecords(scale, runtime)
                    .filter { it.phase is Phase.ApplyExtrinsic }
                    .associateBy { (it.phase as Phase.ApplyExtrinsic).extrinsicId.toInt() }

                buildExtrinsics(runtime, statuses, block.block.extrinsics)
            },
            at = blockHash
        )

        extrinsics.filter { transferWithStatus ->
            val extrinsic = transferWithStatus.extrinsic

            extrinsic.senderId.contentEquals(accountId) || extrinsic.recipientId.contentEquals(accountId)
        }
    }

    private fun buildExtrinsics(
        runtime: RuntimeSnapshot,
        statuses: Map<Int, EventRecord<ExtrinsicStatusEvent>>,
        extrinsicsRaw: List<String>
    ): List<TransferExtrinsicWithStatus> {
        return extrinsicsRaw.mapIndexed { index, extrinsicScale ->
            val transferExtrinsic = bindOrNull { bindTransferExtrinsic(extrinsicScale, runtime) }

            transferExtrinsic?.let {
                val status = statuses[index]?.event

                TransferExtrinsicWithStatus(transferExtrinsic, status)
            }
        }.filterNotNull()
    }

    private fun ExtrinsicBuilder.transfer(chain: Chain, transfer: Transfer, typeRegistry: TypeRegistry): ExtrinsicBuilder {
        val accountId = chain.accountIdOf(transfer.recipient)
        return if (transfer.chainAsset.currency == null) {
            defaultTransfer(accountId, transfer, typeRegistry)
        } else {
            when (transfer.chainAsset.typeExtra) {
                null, ChainAssetType.Normal -> defaultTransfer(accountId, transfer, typeRegistry)
                ChainAssetType.OrmlChain -> ormlChainTransfer(accountId, transfer)

                ChainAssetType.SoraAsset,
                ChainAssetType.SoraUtilityAsset -> soraAssetTransfer(accountId, transfer)

                ChainAssetType.OrmlAsset,
                ChainAssetType.ForeignAsset,
                ChainAssetType.StableAssetPoolToken,
                ChainAssetType.LiquidCrowdloan,
                ChainAssetType.VToken,
                ChainAssetType.VSToken,
                ChainAssetType.Stable -> ormlAssetTransfer(accountId, transfer)

                ChainAssetType.Equilibrium -> equilibriumAssetTransfer(accountId, transfer)
                ChainAssetType.Unknown -> error("Token ${transfer.chainAsset.symbolToShow} not supported, chain ${chain.name}")
            }
        }
    }

    private fun ExtrinsicBuilder.equilibriumAssetTransfer(
        accountId: AccountId,
        transfer: Transfer
    ): ExtrinsicBuilder = call(
        moduleName = Modules.EQBALANCES,
        callName = "transfer",
        arguments = mapOf(
            "asset" to transfer.chainAsset.currency,
            "to" to accountId,
            "value" to transfer.amountInPlanks
        )
    )

    private fun ExtrinsicBuilder.ormlAssetTransfer(
        accountId: AccountId,
        transfer: Transfer
    ) = call(
        moduleName = Modules.CURRENCIES,
        callName = "transfer",
        arguments = mapOf(
            "dest" to DictEnum.Entry("Id", accountId),
            "currency_id" to transfer.chainAsset.currency,
            "amount" to transfer.amountInPlanks
        )
    )

    private fun ExtrinsicBuilder.soraAssetTransfer(
        accountId: AccountId,
        transfer: Transfer
    ) = call(
        moduleName = Modules.ASSETS,
        callName = "transfer",
        arguments = mapOf(
            "asset_id" to transfer.chainAsset.currency,
            "to" to accountId,
            "amount" to transfer.amountInPlanks
        )
    )

    private fun ExtrinsicBuilder.ormlChainTransfer(
        accountId: AccountId,
        transfer: Transfer
    ) = call(
        moduleName = Modules.TOKENS,
        callName = "transfer",
        arguments = mapOf(
            "dest" to accountId,
            "currency_id" to transfer.chainAsset.currency,
            "amount" to transfer.amountInPlanks
        )
    )

    private fun ExtrinsicBuilder.defaultTransfer(
        accountId: AccountId,
        transfer: Transfer,
        typeRegistry: TypeRegistry
    ): ExtrinsicBuilder {
        @Suppress("IMPLICIT_CAST_TO_ANY")
        val dest = when (typeRegistry["Address"]) { // this logic was added to support Moonbeam/Moonriver chains; todo separate assets in json like orml
            is FixedByteArray -> accountId
            else -> DictEnum.Entry(
                name = "Id",
                value = accountId
            )
        }
        return call(
            moduleName = Modules.BALANCES,
            callName = "transfer",
            arguments = mapOf(
                "dest" to dest,
                "value" to transfer.amountInPlanks
            )
        )
    }
}
