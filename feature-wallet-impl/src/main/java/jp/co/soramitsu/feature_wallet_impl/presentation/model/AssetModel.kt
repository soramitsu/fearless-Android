package jp.co.soramitsu.feature_wallet_impl.presentation.model

import jp.co.soramitsu.common.model.AssetKey
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.core_db.dao.emptyAccountIdValue
import java.math.BigDecimal

data class AssetModel(
    val metaId: Long,
    val token: TokenModel,
    val total: BigDecimal?,
    val fiatAmount: BigDecimal?,
    val locked: BigDecimal?,
    val bonded: BigDecimal?,
    val frozen: BigDecimal?,
    val reserved: BigDecimal?,
    val redeemable: BigDecimal?,
    val unbonding: BigDecimal?,
    val available: BigDecimal?,
    val sortIndex: Int,
    val enabled: Boolean,
    val chainAccountName: String?
) {
    val totalFiat = total?.applyFiatRate(token.fiatRate)
    val availableFiat = available?.applyFiatRate(token.fiatRate)
    val frozenFiat = frozen?.applyFiatRate(token.fiatRate)

    val primaryKey = AssetKey(
        metaId = metaId,
        tokenSymbol = token.configuration.symbol,
        accountId = emptyAccountIdValue,
        chainId = token.configuration.chainId
    )
}
