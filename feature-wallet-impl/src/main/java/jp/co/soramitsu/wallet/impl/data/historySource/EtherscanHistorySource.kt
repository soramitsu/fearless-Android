package jp.co.soramitsu.wallet.impl.data.historySource

import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.utils.isNotZero
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.BSCChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.BSCTestnetChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.optimismChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ethereumChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.goerliChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polygonChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polygonTestnetChainId
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.wallet.impl.data.network.subquery.OperationsHistoryApi
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private val etherscanApiKeys = mapOf(
    ethereumChainId to BuildConfig.ETHERSCAN_API_KEY,
    goerliChainId to BuildConfig.ETHERSCAN_API_KEY,
    BSCChainId to BuildConfig.BSCSCAN_API_KEY,
    BSCTestnetChainId to BuildConfig.BSCSCAN_API_KEY,
    polygonChainId to BuildConfig.POLYGONSCAN_API_KEY,
    polygonTestnetChainId to BuildConfig.POLYGONSCAN_API_KEY,
    optimismChainId to BuildConfig.OPMAINNET_API_KEY
)

class EtherscanHistorySource(
    private val walletOperationsApi: OperationsHistoryApi,
    private val historyUrl: String
) : HistorySource {
    override suspend fun getOperations(
        pageSize: Int,
        cursor: String?,
        filters: Set<TransactionFilter>,
        accountId: AccountId,
        chain: Chain,
        chainAsset: Asset,
        accountAddress: String
    ): CursorPage<Operation> {
        return kotlin.runCatching {
            val response = when (chainAsset.ethereumType) {
                Asset.EthereumType.NORMAL -> {
                    walletOperationsApi.getEtherscanOperationsHistory(
                        url = historyUrl,
                        address = accountId.toHexString(true),
                        apiKey = etherscanApiKeys[chain.id]
                    )
                        .let { response -> response.copy(result = response.result.filter { it.contractAddress.isEmpty() && it.value.isNotZero() }) }
                }
                Asset.EthereumType.BEP20,
                Asset.EthereumType.ERC20 -> {
                    walletOperationsApi.getEtherscanOperationsHistory(
                        url = historyUrl,
                        action = "tokentx",
                        contractAddress = chainAsset.id,
                        address = accountId.toHexString(true),
                        apiKey = etherscanApiKeys[chain.id]
                    )
                        .let { response -> response.copy(result = response.result.filter { it.contractAddress.lowercase() == chainAsset.id.lowercase() && it.value.isNotZero() }) }
                }

                else -> throw IllegalArgumentException()
            }
            if (response.status != 1) {
                throw RuntimeException("Etherscan exception: code: ${response.status}, message: ${response.message}")
            }
            response
        }.fold(onSuccess = {
            val operations = it.result.map { element ->
                val status = if (element.isError == 0) {
                    Operation.Status.COMPLETED
                } else {
                    Operation.Status.FAILED
                }
                val fee = element.gasUsed.multiply(element.gasPrice)
                Operation(
                    id = element.hash,
                    address = accountAddress,
                    time = element.timeStamp.toDuration(DurationUnit.SECONDS).inWholeMilliseconds,
                    chainAsset = chainAsset,
                    type = Operation.Type.Transfer(
                        hash = element.hash,
                        myAddress = accountAddress,
                        amount = element.value,
                        receiver = element.to,
                        sender = element.from,
                        status = status,
                        fee = fee
                    )
                )
            }
            CursorPage(null, operations)
        }, onFailure = {
            CursorPage(null, emptyList())
        })
    }
}