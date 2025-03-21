package jp.co.soramitsu.wallet.impl.presentation

import java.math.BigDecimal
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.sumByBigDecimal
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getWithToken
import jp.co.soramitsu.wallet.impl.domain.model.AssetWithStatus
import jp.co.soramitsu.wallet.impl.presentation.balance.list.model.BalanceListItemModel

object AssetListHelper {

    fun processAssets(
        assets: List<AssetWithStatus>,
        filteredChains: List<Chain>,
        selectedChainId: ChainId? = null,
    ): List<BalanceListItemModel> {
        val result = mutableListOf<BalanceListItemModel>()
        assets.groupBy { it.asset.token.configuration.symbol }
            .forEach { (symbol, symbolAssets) ->
                val chainsWithIssuesIds = symbolAssets.filter { it.hasAccount.not() }.map { it.asset.token.configuration.chainId }

                val tokenChains = filteredChains.asSequence()
                    .filter { chain ->
                        symbolAssets.any { it.asset.token.configuration.chainId == chain.id }
                    }
                    .filter { chain ->
                        chain.assets.any { it.symbol == symbol }
                    }.filter { chain ->
                        chain.id !in chainsWithIssuesIds
                    }.toList()

                if (tokenChains.isEmpty()) return@forEach

                val mainChain = tokenChains.sortedWith(
                    compareByDescending<Chain> { chain ->
                        chain.assets.firstOrNull { it.symbol == symbol }?.isUtility ?: false
                    }.thenByDescending { it.parentId == null }
                ).firstOrNull()

                val showChain = tokenChains.firstOrNull { it.id == selectedChainId } ?: mainChain
                val showChainAsset =
                    showChain?.assets?.firstOrNull { it.symbol == symbol } ?: return@forEach
                val assetIdsWithBalance = symbolAssets.filter {
                    it.asset.total.orZero() > BigDecimal.ZERO
                }.groupBy(
                    keySelector = { it.asset.token.configuration.chainId },
                    valueTransform = { it.asset.token.configuration.id }
                )

                val assetChainUrls = if (selectedChainId == null) {
                    filteredChains.getWithToken(symbol, assetIdsWithBalance)
                        .ifEmpty { listOf(showChain) }
                        .associate { it.id to it.icon }
                } else {
                    emptyMap()
                }

                val assetTransferable = symbolAssets.sumByBigDecimal { it.asset.transferable }
                val assetTotal = symbolAssets.sumByBigDecimal { it.asset.total.orZero() }
                val assetTotalFiat = symbolAssets.sumByBigDecimal { it.asset.fiatAmount.orZero() }

                val assetDisabledByUser = symbolAssets.any { it.asset.enabled == false }

                val token = symbolAssets.first().asset.token

                val model = BalanceListItemModel(
                    asset = showChainAsset,
                    chain = showChain,
                    token = token,
                    total = assetTotal,
                    fiatAmount = assetTotalFiat,
                    transferable = assetTransferable,
                    chainUrls = assetChainUrls,
                    isHidden = assetDisabledByUser
                )
                result.add(model)
            }
        return result
    }
}