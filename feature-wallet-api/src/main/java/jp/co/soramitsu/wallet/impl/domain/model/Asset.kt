package jp.co.soramitsu.wallet.impl.domain.model

import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.model.WalletAssetKey
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as CoreAsset

data class AssetBalance(
    val metaId: Long,
    val accountId: AccountId,
    val freeInPlanks: BigInteger?,
    val reservedInPlanks: BigInteger?,
    val miscFrozenInPlanks: BigInteger?,
    val feeFrozenInPlanks: BigInteger?,
    val bondedInPlanks: BigInteger?,
    val redeemableInPlanks: BigInteger?,
    val unbondingInPlanks: BigInteger?,
    val enabled: Boolean?,
    val status: String?
)

data class Asset(
    val metaId: Long,
    val token: Token,
    val accountId: AccountId,
    val freeInPlanksState: AssetAmountState<BigInteger>,
    val reservedInPlanksState: AssetAmountState<BigInteger>,
    val miscFrozenInPlanksState: AssetAmountState<BigInteger>,
    val feeFrozenInPlanksState: AssetAmountState<BigInteger>,
    val bondedInPlanksState: AssetAmountState<BigInteger>,
    val redeemableInPlanksState: AssetAmountState<BigInteger>,
    val unbondingInPlanksState: AssetAmountState<BigInteger>,
    val sortIndex: Int,
    val enabled: Boolean?,
    val minSupportedVersion: String?,
    val chainAccountName: String?,
    val markedNotNeed: Boolean,
    val status: String?
) {
    /** Compatibility construction accepts fresh nullable values; canonical copies use explicit states. */
    constructor(
        metaId: Long,
        token: Token,
        accountId: AccountId,
        freeInPlanks: BigInteger?,
        reservedInPlanks: BigInteger?,
        miscFrozenInPlanks: BigInteger?,
        feeFrozenInPlanks: BigInteger?,
        bondedInPlanks: BigInteger?,
        redeemableInPlanks: BigInteger?,
        unbondingInPlanks: BigInteger?,
        sortIndex: Int,
        enabled: Boolean?,
        minSupportedVersion: String?,
        chainAccountName: String?,
        markedNotNeed: Boolean,
        status: String?
    ) : this(
        metaId = metaId,
        token = token,
        accountId = accountId,
        freeInPlanksState = freeInPlanks.asAssetAmountState(),
        reservedInPlanksState = reservedInPlanks.asAssetAmountState(),
        miscFrozenInPlanksState = miscFrozenInPlanks.asAssetAmountState(),
        feeFrozenInPlanksState = feeFrozenInPlanks.asAssetAmountState(),
        bondedInPlanksState = bondedInPlanks.asAssetAmountState(),
        redeemableInPlanksState = redeemableInPlanks.asAssetAmountState(),
        unbondingInPlanksState = unbondingInPlanks.asAssetAmountState(),
        sortIndex = sortIndex,
        enabled = enabled,
        minSupportedVersion = minSupportedVersion,
        chainAccountName = chainAccountName,
        markedNotNeed = markedNotNeed,
        status = status
    )

    companion object {
        private const val STATUS_FROZEN = "Frozen"

        fun createEmpty(chainAccount: MetaAccount.ChainAccount) = chainAccount.chain?.let {
            it.utilityAsset?.let { utilityAsset ->
                createEmpty(
                    chainAsset = utilityAsset,
                    metaId = chainAccount.metaId,
                    accountId = chainAccount.accountId,
                    chainAccountName = chainAccount.accountName,
                    minSupportedVersion = it.minSupportedVersion
                )
            }
        }

        fun createEmpty(
            chainAsset: CoreAsset,
            metaId: Long,
            accountId: AccountId,
            chainAccountName: String? = null,
            minSupportedVersion: String?
        ) = Asset(
            metaId = metaId,
            Token(configuration = chainAsset, fiatRate = null, fiatSymbol = null, recentRateChange = null),
            accountId = accountId,
            freeInPlanks = null,
            reservedInPlanks = null,
            miscFrozenInPlanks = null,
            feeFrozenInPlanks = null,
            bondedInPlanks = null,
            redeemableInPlanks = null,
            unbondingInPlanks = null,
            sortIndex = Int.MAX_VALUE,
            enabled = null,
            minSupportedVersion = minSupportedVersion,
            chainAccountName = chainAccountName,
            markedNotNeed = false,
            status = null
        )
    }

    val amounts = AssetBalanceAmounts(
        freeInPlanksState, reservedInPlanksState, miscFrozenInPlanksState, feeFrozenInPlanksState,
        bondedInPlanksState, redeemableInPlanksState, unbondingInPlanksState,
        isFrozen = status == STATUS_FROZEN
    )

    val freeInPlanks = freeInPlanksState.knownOrNull()
    val reservedInPlanks = reservedInPlanksState.knownOrNull()
    val miscFrozenInPlanks = miscFrozenInPlanksState.knownOrNull()
    val feeFrozenInPlanks = feeFrozenInPlanksState.knownOrNull()
    val bondedInPlanks = bondedInPlanksState.knownOrNull()
    val redeemableInPlanks = redeemableInPlanksState.knownOrNull()
    val unbondingInPlanks = unbondingInPlanksState.knownOrNull()

    val isAssetFrozen = status == STATUS_FROZEN
    val reservedState = token.amountFromPlanksState(amounts.reservedAmount)
    val reserved = reservedState.knownOrNull()
    val lockedState = token.amountFromPlanksState(amounts.locked)
    val locked = lockedState.knownOrNull()
    val frozenState = token.amountFromPlanksState(amounts.frozen)
    val frozen = frozenState.knownOrNull()
    val totalState = token.amountFromPlanksState(amounts.total)
    val total = totalState.knownOrNull()
    val availableForStakingState = token.amountFromPlanksState(amounts.availableForStaking)
    val availableForStaking = availableForStakingState.knownOrNull()
    val transferableState = token.amountFromPlanksState(amounts.transferable)
    val transferable = transferableState.knownOrNull()
    val sendAvailableState = token.amountFromPlanksState(amounts.sendAvailable)
    val sendAvailable = sendAvailableState.knownOrNull()
    val bondedState = token.amountFromPlanksState(amounts.bondedAmount)
    val bonded = bondedState.knownOrNull()
    val redeemableState = token.amountFromPlanksState(amounts.redeemableAmount)
    val redeemable = redeemableState.knownOrNull()
    val unbondingState = token.amountFromPlanksState(amounts.unbondingAmount)
    val unbonding = unbondingState.knownOrNull()
    val transferableInPlanks = amounts.transferable.knownOrNull()
    val sendAvailableInPlanks = amounts.sendAvailable.knownOrNull()
    val fiatAmountState = token.fiatAmountState(totalState)
    val fiatAmount = fiatAmountState.knownOrNull()

    val uniqueKey = WalletAssetKey(metaId, token.configuration.chainId, accountId, token.configuration.id)

    fun getAsFiatWithCurrency(value: BigDecimal?) =
        value?.let(token::fiatAmount)?.formatFiat(token.fiatSymbol)

    val chainId = token.configuration.chainId
    val id = token.configuration.id
}

fun calculateTotalBalance(
    freeInPlanks: BigInteger?,
    reservedInPlanks: BigInteger?
): BigInteger? {
    return AssetBalanceAmounts(freeInPlanks.asAssetAmountState(), reservedInPlanks.asAssetAmountState()).total.knownOrNull()
}
