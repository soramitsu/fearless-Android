package jp.co.soramitsu.account.impl.domain

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.model.AssetLocal
import jp.co.soramitsu.coredb.model.AssetWithToken
import jp.co.soramitsu.coredb.model.TokenPriceLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import org.junit.Assert.assertEquals
import org.junit.Test

class TotalBalanceUseCaseImplTest {

    @Test
    fun `missing catalog row skips only that balance`() {
        val verified = chainAsset("verified", priceId = "verified-price")
        val chain = chain(listOf(verified))
        val balances = listOf(
            walletAsset("verified", "verified-price", amount = 2, rate = "3"),
            walletAsset("missing", "missing-price", amount = 999, rate = "100")
        )

        val total = calculateTrustedTotalBalance(balances, mapOf(CHAIN_ID to chain))

        assertEquals(BigDecimal("6"), total.balance)
    }

    @Test
    fun `same symbol different contract never inherits a curated price`() {
        val pricedContract = chainAsset(PRICED_CONTRACT, symbol = "DUP", priceId = "priced-contract")
        val unpricedContract = chainAsset(UNPRICED_CONTRACT, symbol = "DUP", priceId = null)
        val chain = chain(listOf(pricedContract, unpricedContract))
        val balances = listOf(
            walletAsset(PRICED_CONTRACT, "priced-contract", amount = 4, rate = "2"),
            // Simulates a symbol-keyed price accidentally attached to a different contract.
            walletAsset(UNPRICED_CONTRACT, "priced-contract", amount = 100, rate = "2")
        )

        val total = calculateTrustedTotalBalance(balances, mapOf(CHAIN_ID to chain))

        assertEquals(BigDecimal("8"), total.balance)
    }

    @Test
    fun `hidden verified asset remains in net worth`() {
        val asset = chainAsset("hidden", priceId = "hidden-price")
        val chain = chain(listOf(asset))

        val total = calculateTrustedTotalBalance(
            assets = listOf(walletAsset("hidden", "hidden-price", amount = 5, rate = "7", enabled = false)),
            chainsById = mapOf(CHAIN_ID to chain)
        )

        assertEquals(BigDecimal("35"), total.balance)
    }

    private fun walletAsset(
        assetId: String,
        priceId: String,
        amount: Long,
        rate: String,
        enabled: Boolean? = null
    ): AssetWithToken {
        return AssetWithToken(
            asset = AssetLocal(
                id = assetId,
                chainId = CHAIN_ID,
                accountId = byteArrayOf(1),
                metaId = 1,
                tokenPriceId = priceId,
                freeInPlanks = BigInteger.valueOf(amount),
                enabled = enabled
            ),
            token = TokenPriceLocal(
                priceId = priceId,
                fiatSymbol = "$",
                fiatRate = BigDecimal(rate),
                recentRateChange = BigDecimal.ZERO
            )
        )
    }

    private fun chainAsset(
        id: String,
        symbol: String = id,
        priceId: String?
    ): Asset {
        return Asset(
            id = id,
            name = symbol,
            symbol = symbol,
            iconUrl = "",
            chainId = CHAIN_ID,
            chainName = "Ethereum",
            chainIcon = null,
            isTestNet = false,
            priceId = priceId,
            precision = 0,
            staking = Asset.StakingType.UNSUPPORTED,
            purchaseProviders = null,
            supportStakingPool = false,
            isUtility = false,
            type = ChainAssetType.ERC20,
            currencyId = id,
            existentialDeposit = null,
            color = null,
            isNative = false
        )
    }

    private fun chain(assets: List<Asset>): Chain {
        return Chain(
            id = CHAIN_ID,
            paraId = null,
            rank = null,
            name = "Ethereum",
            minSupportedVersion = null,
            assets = assets,
            nodes = emptyList(),
            explorers = emptyList(),
            externalApi = null,
            icon = "",
            addressPrefix = 0,
            isEthereumBased = true,
            isTestNet = false,
            hasCrowdloans = false,
            parentId = null,
            supportStakingPool = false,
            isEthereumChain = true,
            chainlinkProvider = false,
            supportNft = false,
            isUsesAppId = false,
            identityChain = null,
            ecosystem = Ecosystem.Ethereum,
            remoteAssetsSource = null
        )
    }

    private companion object {
        const val CHAIN_ID = "1"
        const val PRICED_CONTRACT = "0xA0b86991c6218b36C1d19D4a2e9Eb0cE3606eB48"
        const val UNPRICED_CONTRACT = "0x1111111111111111111111111111111111111111"
    }
}
