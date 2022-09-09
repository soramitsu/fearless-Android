package jp.co.soramitsu.wallet.impl.presentation.model

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.AppVersion
import jp.co.soramitsu.common.model.AssetKey
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.coredb.dao.emptyAccountIdValue
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount

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
    val minSupportedVersion: String?,
    val chainAccountName: String?,
    val isHidden: Boolean?
) {
    val totalFiat = total?.applyFiatRate(token.fiatRate)
    val availableFiat = available?.applyFiatRate(token.fiatRate)
    val frozenFiat = frozen?.applyFiatRate(token.fiatRate)

    val isSupported: Boolean = when (minSupportedVersion) {
        null -> true
        else -> AppVersion.isSupported(minSupportedVersion)
    }

    val primaryKey = AssetKey(
        metaId = metaId,
        assetId = token.configuration.id,
        accountId = emptyAccountIdValue,
        chainId = token.configuration.chainId
    )

    fun formatTokenAmount(value: BigDecimal?) =
        value.orZero().formatTokenAmount(token.configuration.symbol)

    fun getAsFiatWithCurrency(value: BigDecimal?) =
        token.fiatRate?.let { value?.applyFiatRate(it).orZero().formatAsCurrency(token.fiatSymbol) }
}
