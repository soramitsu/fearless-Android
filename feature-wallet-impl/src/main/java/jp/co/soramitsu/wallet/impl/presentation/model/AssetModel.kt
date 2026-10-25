package jp.co.soramitsu.wallet.impl.presentation.model

import jp.co.soramitsu.common.domain.AppVersion
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.knownOrNull
import java.math.BigDecimal

/**
 * Presentation retains the canonical quantity/quote states and captured currency. Copy the
 * canonical asset to change the model; a detached numeric value cannot relabel its quote.
 * This is a captured value, not a claim that its originating selection is still current.
 */
data class AssetModel(val asset: Asset) {
    val metaId = asset.metaId
    val token = TokenModel(asset.token)
    val fiatContext = asset.token.fiatContext
    val totalState = asset.totalState
    val lockedState = asset.lockedState
    val bondedState = asset.bondedState
    val frozenState = asset.frozenState
    val reservedState = asset.reservedState
    val redeemableState = asset.redeemableState
    val unbondingState = asset.unbondingState
    val availableState = asset.transferableState
    val fiatAmountState = asset.fiatAmountState

    val total = asset.total
    val fiatAmount = asset.fiatAmount
    val locked = asset.locked
    val bonded = asset.bonded
    val frozen = asset.frozen
    val reserved = asset.reserved
    val redeemable = asset.redeemable
    val unbonding = asset.unbonding
    val available = asset.transferable
    val sortIndex = asset.sortIndex
    val minSupportedVersion = asset.minSupportedVersion
    val chainAccountName = asset.chainAccountName
    val isHidden: Boolean? = asset.enabled == false

    val totalFiatState = asset.fiatAmountState
    val availableFiatState = asset.token.fiatAmountState(availableState)
    val frozenFiatState = asset.token.fiatAmountState(frozenState)
    val totalFiat = totalFiatState.knownOrNull()
    val availableFiat = availableFiatState.knownOrNull()
    val frozenFiat = frozenFiatState.knownOrNull()

    val isSupported: Boolean = when (minSupportedVersion) {
        null -> true
        else -> AppVersion.isSupported(minSupportedVersion)
    }

    /** Preserve the actual selected account; an empty-account key would erase ownership. */
    val primaryKey = asset.uniqueKey

    fun getAsFiatWithCurrency(value: BigDecimal?): String? {
        val context = fiatContext?.takeIf { it.isValid } ?: return null
        return value?.let(asset.token::fiatAmount)?.formatFiat(context.displayUnit)
    }
}
