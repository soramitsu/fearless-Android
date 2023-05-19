package jp.co.soramitsu.wallet.impl.data.historySource

import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.getRuntime
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.shared_utils.runtime.definitions.types.composite.Alias
import jp.co.soramitsu.shared_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.shared_utils.runtime.definitions.types.useScaleWriter
import jp.co.soramitsu.wallet.impl.data.mappers.mapNodeToOperation
import jp.co.soramitsu.wallet.impl.data.network.model.request.SubqueryHistoryRequest
import jp.co.soramitsu.wallet.impl.data.network.subquery.OperationsHistoryApi
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation

class SubqueryHistorySource(
    private val walletOperationsApi: OperationsHistoryApi,
    private val chainRegistry: ChainRegistry,
    private val url: String
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
        val requestRewards = chainAsset.staking != Asset.StakingType.UNSUPPORTED
        val response = walletOperationsApi.getOperationsHistory(
            url = url,
            SubqueryHistoryRequest(
                accountAddress = accountAddress,
                pageSize,
                cursor,
                filters,
                requestRewards
            )
        ).data.query

        val encodedCurrencyId = if (!chainAsset.isUtility) {
            val runtime = chainRegistry.getRuntime(chain.id)
            val currencyIdKey = runtime.typeRegistry.types.keys.find { it.contains("CurrencyId") }
            val currencyIdType = runtime.typeRegistry.types[currencyIdKey]

            useScaleWriter {
                val currency = chainAsset.currency as? DictEnum.Entry<*> ?: return@useScaleWriter
                val alias = (currencyIdType?.value as Alias)
                val currencyIdEnum = alias.aliasedReference.requireValue() as DictEnum
                currencyIdEnum.encode(this, runtime, currency)
            }.toHexString(true)
        } else {
            null
        }

        val pageInfo = response.historyElements.pageInfo

        val filteredOperations = if (chainAsset.isUtility) {
            response.historyElements.nodes.filter {
                it.transfer?.assetId == null &&
                    it.extrinsic?.assetId == null &&
                    it.reward?.assetId == null
            }
        } else {
            response.historyElements.nodes.filter {
                it.transfer?.assetId == encodedCurrencyId ||
                    it.extrinsic?.assetId == encodedCurrencyId ||
                    it.reward?.assetId == encodedCurrencyId
            }
        }

        val operations = filteredOperations.map { mapNodeToOperation(it, chainAsset) }

        return CursorPage(pageInfo.endCursor, operations)
    }
}
