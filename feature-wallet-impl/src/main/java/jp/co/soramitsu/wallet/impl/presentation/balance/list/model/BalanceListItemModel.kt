package jp.co.soramitsu.wallet.impl.presentation.balance.list.model

import java.math.BigDecimal
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainEcosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.domain.model.Token

data class BalanceListItemModel(
    val asset: Asset,
    val chain: Chain?,
    val token: Token,
    val total: BigDecimal,
    val fiatAmount: BigDecimal?,
    val transferable: BigDecimal,
    val chainUrls: Map<ChainId, String>,
    val isHidden: Boolean
)
fun BalanceListItemModel.toAssetState(index: Int? = null) = AssetListItemViewState(
    index = index,
    assetIconUrl = asset.iconUrl,
    assetName = asset.name.orEmpty(),
    assetChainName = chain?.name.orEmpty(),
    assetSymbol = asset.symbol,
    assetTokenFiat = token.fiatRate?.formatFiat(token.fiatSymbol),
    assetTokenRate = token.recentRateChange?.formatAsChange(),
    assetTransferableBalance = transferable.formatCrypto(),
    assetTransferableBalanceFiat = token.fiatRate?.multiply(transferable)?.formatFiat(token.fiatSymbol),
    assetChainUrls = chainUrls,
    chainId = chain?.id.orEmpty(),
    chainAssetId = asset.id,
    isSupported = chain?.isSupported != false,
    isHidden = isHidden,
    isTestnet = chain?.isTestNet == true
)
