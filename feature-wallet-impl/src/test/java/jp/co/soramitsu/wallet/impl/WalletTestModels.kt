package jp.co.soramitsu.wallet.impl

import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

/** Real chain and asset values for tests that map, format or route them. */
internal object WalletTestModels {

    fun fundedAsset(
        configuration: Asset = asset(priceId = "dot"),
        balance: String = "100",
        locked: String = "0"
    ): jp.co.soramitsu.wallet.impl.domain.model.Asset {
        val zero = jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState.Known(java.math.BigInteger.ZERO)
        val token = jp.co.soramitsu.wallet.impl.domain.model.Token(
            configuration,
            jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState.Known(java.math.BigDecimal("6")),
            jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState.Known(java.math.BigDecimal.ONE),
            jp.co.soramitsu.wallet.impl.domain.model.FiatValuationContext("usd"),
            "usd",
            configuration.priceId
        )
        return jp.co.soramitsu.wallet.impl.domain.model.Asset.createEmpty(configuration, 1L, substrateAccountId, minSupportedVersion = null).copy(
            token = token,
            freeInPlanksState = jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState.Known(
                java.math.BigDecimal(balance).movePointRight(configuration.precision).toBigIntegerExact()
            ),
            reservedInPlanksState = zero,
            miscFrozenInPlanksState = jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState.Known(
                java.math.BigDecimal(locked).movePointRight(configuration.precision).toBigIntegerExact()
            ),
            feeFrozenInPlanksState = zero,
            bondedInPlanksState = zero,
            redeemableInPlanksState = zero,
            unbondingInPlanksState = zero
        )
    }

    const val ALICE = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY"
    const val BOB = "5FHneW46xGXgs5mUiveU4sbTyGBzmstUspZC92UhjJM694ty"
    const val ETH_A = "0x1111111111111111111111111111111111111111"
    const val ETH_B = "0x2222222222222222222222222222222222222222"
    const val TON_RAW = "0:83dfd552e63729b472fcbcc8c45ebcc6691702558b68ec7527e1ba403a0f31a8"

    fun asset(
        id: String = "dot",
        symbol: String = id.uppercase(),
        chainId: String = "polkadot",
        precision: Int = 10,
        currencyId: String? = null,
        isUtility: Boolean = true,
        type: ChainAssetType? = ChainAssetType.Normal,
        priceId: String? = null,
        existentialDeposit: String? = null,
        isTestNet: Boolean = false,
        staking: Asset.StakingType = Asset.StakingType.UNSUPPORTED,
        color: String? = "#E6007A"
    ) = Asset(
        id = id,
        name = symbol,
        symbol = symbol,
        iconUrl = "https://icons/$id.svg",
        chainId = chainId,
        chainName = chainId.replaceFirstChar { it.uppercase() },
        chainIcon = null,
        isTestNet = isTestNet,
        priceId = priceId,
        precision = precision,
        staking = staking,
        purchaseProviders = null,
        supportStakingPool = false,
        isUtility = isUtility,
        type = type,
        currencyId = currencyId,
        existentialDeposit = existentialDeposit,
        color = color,
        isNative = isUtility
    )

    fun chain(
        id: String = "polkadot",
        assets: List<Asset> = listOf(asset(chainId = id)),
        ecosystem: Ecosystem = Ecosystem.Substrate,
        explorers: List<Chain.Explorer> = emptyList(),
        externalApi: Chain.ExternalApi? = null,
        isTestNet: Boolean = false,
        name: String = id.replaceFirstChar { it.uppercase() },
        addressPrefix: Int = 0,
        isEthereumChain: Boolean = ecosystem == Ecosystem.Ethereum,
        parentId: String? = null,
        supportNft: Boolean = false
    ) = Chain(
        id = id, paraId = null, rank = null, name = name, minSupportedVersion = null,
        assets = assets, nodes = emptyList(), explorers = explorers,
        externalApi = externalApi,
        icon = "https://icons/$id-chain.svg", addressPrefix = addressPrefix,
        isEthereumBased = ecosystem == Ecosystem.Ethereum || ecosystem == Ecosystem.EthereumBased,
        isTestNet = isTestNet, hasCrowdloans = false, parentId = parentId, supportStakingPool = false,
        isEthereumChain = isEthereumChain, chainlinkProvider = false, supportNft = supportNft,
        isUsesAppId = false, identityChain = null, ecosystem = ecosystem, remoteAssetsSource = null
    )

    val substrateAccountId = ByteArray(32) { 1 }
    val ethereumAccountId = ByteArray(20) { 2 }

    fun metaAccount(
        id: Long = 1L,
        name: String = "Main",
        substrate: ByteArray? = substrateAccountId,
        ethereum: ByteArray? = ethereumAccountId,
        ton: ByteArray? = null,
        isBackedUp: Boolean = true
    ) = MetaAccount(
        id = id,
        chainAccounts = emptyMap(),
        favoriteChains = emptyMap(),
        substratePublicKey = substrate,
        substrateCryptoType = null,
        substrateAccountId = substrate,
        ethereumAddress = ethereum,
        ethereumPublicKey = ethereum,
        tonPublicKey = ton,
        isSelected = true,
        isBackedUp = isBackedUp,
        googleBackupAddress = null,
        name = name,
        initialized = true
    )
}
