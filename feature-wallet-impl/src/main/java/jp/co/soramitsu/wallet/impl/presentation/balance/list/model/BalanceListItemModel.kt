package jp.co.soramitsu.wallet.impl.presentation.balance.list.model

import java.math.BigDecimal
import jp.co.soramitsu.common.compose.viewstate.AssetListItemViewState
import jp.co.soramitsu.common.model.AssetMetadataSource
import jp.co.soramitsu.common.model.AssetMetadataTrust
import jp.co.soramitsu.common.model.AssetPreference
import jp.co.soramitsu.common.model.CanonicalAssetIdentity
import jp.co.soramitsu.common.model.PriceTrust
import jp.co.soramitsu.common.utils.formatAsChange
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.wallet.impl.domain.model.Token

data class BalanceListItemModel(
    val asset: Asset,
    val chain: Chain?,
    val token: Token,
    val total: BigDecimal?,
    val fiatAmount: BigDecimal?,
    val transferable: BigDecimal?,
    val chainUrls: Map<ChainId, String>,
    val chainAccountName: String?,
    val isHidden: Boolean,
    val preference: AssetPreference,
    val canonicalIdentity: CanonicalAssetIdentity,
    val metadataTrust: AssetMetadataTrust,
    val metadataSource: AssetMetadataSource,
    val priceTrust: PriceTrust,
    /** The shared testnet rule: the registry option or the bundled testnet list. */
    val isTestnet: Boolean = chain?.isTestNet == true
)
fun BalanceListItemModel.toAssetState(
    index: Int? = null,
    networkFiatSubtotal: String? = null,
    networkAssetCount: Int = 0,
    unavailableText: String = "—"
) = AssetListItemViewState(
    index = index,
    assetIconUrl = asset.iconUrl,
    assetName = asset.name.orEmpty(),
    assetChainName = chain?.name.orEmpty(),
    assetSymbol = asset.symbol,
    assetTokenFiat = token.fiatRate?.formatFiat(token.fiatSymbol),
    assetTokenRate = token.recentRateChange?.formatAsChange(),
    assetTransferableBalance = transferable?.formatCrypto() ?: unavailableText,
    assetTransferableBalanceFiat = transferable?.let(token::fiatAmount)?.formatFiat(token.fiatSymbol),
    assetTotalBalance = total?.formatCrypto() ?: unavailableText,
    assetTotalBalanceFiat = fiatAmount?.formatFiat(token.fiatSymbol),
    showTransferableBalance = total != null && (transferable == null || transferable < total),
    assetChainUrls = chainUrls,
    chainId = chain?.id.orEmpty(),
    chainAssetId = asset.id,
    isSupported = chain?.isSupported != false,
    isHidden = isHidden,
    assetPreference = preference.name.lowercase(),
    isTestnet = isTestnet,
    ecosystemId = canonicalIdentity.ecosystem,
    canonicalAssetKey = canonicalIdentity.serialized,
    metadataTrust = metadataTrust.name.lowercase(),
    metadataSource = metadataSource.name.lowercase(),
    priceTrust = priceTrust.name.lowercase(),
    networkFiatSubtotal = networkFiatSubtotal,
    networkAssetCount = networkAssetCount,
    networkAccountLabel = chainAccountName
)
