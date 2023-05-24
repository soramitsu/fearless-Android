package jp.co.soramitsu.wallet.impl.presentation.balance.list.model

import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainEcosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.domain.model.Token
import java.math.BigDecimal

data class BalanceListItemModel(
    val asset: Asset,
    val chain: Chain?,
    val token: Token,
    val total: BigDecimal,
    val fiatAmount: BigDecimal?,
    val transferable: BigDecimal,
    val chainUrls: Map<ChainId, String>,
    val isHidden: Boolean,
    val hasChainWithoutAccount: Boolean,
    val hasNetworkIssue: Boolean,
    val ecosystem: ChainEcosystem
)

fun BalanceListItemModel.toAssetState() = AssetListItemViewState(
    assetIconUrl = asset.iconUrl,
    assetName = asset.name.orEmpty(),
    assetChainName = chain?.name.orEmpty(),
    assetSymbol = asset.symbol,
    displayName = asset.symbol,
    assetTokenFiat = token.fiatRate?.formatFiat(token.fiatSymbol),
    assetTokenRate = token.recentRateChange?.formatAsChange(),
    assetTransferableBalance = transferable.formatCrypto(),
    assetTransferableBalanceFiat = token.fiatRate?.multiply(transferable)?.formatFiat(token.fiatSymbol),
    assetChainUrls = chainUrls,
    chainId = chain?.id.orEmpty(),
    chainAssetId = asset.id,
    isSupported = chain?.isSupported != false,
    isHidden = isHidden,
    hasAccount = !hasChainWithoutAccount,
    priceId = asset.priceId,
    hasNetworkIssue = hasNetworkIssue,
    ecosystem = ecosystem.name,
    isTestnet = chain?.isTestNet == true
)
