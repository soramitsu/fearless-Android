package jp.co.soramitsu.wallet.impl.data.repository.tranfser

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.core.extrinsic.keypair_provider.KeypairProvider
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.wallet.impl.data.network.blockchain.EthereumRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.SubstrateTransferParams
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal

class TransferServiceProvider(
    private val substrateSource: SubstrateRemoteSource,
    private val ethereumRemoteSource: EthereumRemoteSource,
    private val keyPairRepository: KeypairProvider,
    private val accountRepository: AccountRepository,
    private val tonRemoteSource: TonRemoteSource,
    private val assetDao: AssetDao
) {
    fun provide(chain: Chain): TransferService {
        return when (chain.ecosystem) {
            Ecosystem.EthereumBased, Ecosystem.Substrate -> SubstrateTransferService(
                chain,
                substrateSource
            )
            Ecosystem.Ethereum -> EthereumTransferService(
                chain,
                ethereumRemoteSource,
                keyPairRepository
            )
            Ecosystem.Ton -> TonTransferService(chain, keyPairRepository, accountRepository, tonRemoteSource, assetDao)
            else -> throw IllegalStateException("Unsupported chain for transfers: ${chain.name}, ecosystem: ${chain.ecosystem.name}")
        }
    }
}

interface TransferService {
    suspend fun getTransferFee(transfer: Transfer): BigDecimal
    fun observeTransferFee(transfer: Transfer): Flow<BigDecimal>
    suspend fun transfer(transfer: Transfer): String
}

class SubstrateTransferService(
    private val chain: Chain,
    private val substrateSource: SubstrateRemoteSource
) : TransferService {
    override suspend fun getTransferFee(transfer: Transfer): BigDecimal {
        val transferFee = substrateSource.getTransferFee(chain, transfer, null, false)
        return chain.utilityAsset?.amountFromPlanks(transferFee)
            ?: throw IllegalStateException("cannot calculate substrate fee ${chain.name}, fee: $transferFee")
    }

    override fun observeTransferFee(transfer: Transfer): Flow<BigDecimal> {
        return flow { emit(getTransferFee(transfer)) }
    }

    override suspend fun transfer(transfer: Transfer): String {
        require(transfer.additionalParams is SubstrateTransferParams) { "Wrong additional params type for substrate transfer" }

        val accountId = transfer.sender.toAccountId()
        return substrateSource.performTransfer(
            accountId,
            chain,
            transfer,
            null,
            false
        )
    }
}

class EthereumTransferService(
    private val chain: Chain,
    private val ethereumRemoteSource: EthereumRemoteSource,
    private val keyPairRepository: KeypairProvider,
) : TransferService {
    override suspend fun getTransferFee(transfer: Transfer): BigDecimal {
        return observeTransferFee(transfer).first()
    }

    override fun observeTransferFee(transfer: Transfer): Flow<BigDecimal> {
        return ethereumRemoteSource.listenGas(transfer, chain).map {
            chain.utilityAsset?.amountFromPlanks(it)
                ?: throw IllegalStateException("cannot calculate ethereum fee ${chain.name}, fee: $it")
        }
    }

    override suspend fun transfer(transfer: Transfer): String {
        val accountId = transfer.sender.fromHex()
        val keypair = keyPairRepository.getKeypairFor(chain, accountId)
        val privateKey = keypair.privateKey

        return ethereumRemoteSource.performTransfer(chain, transfer, privateKey.toHexString(true))
            .requireValue() // handle error
    }
}