package jp.co.soramitsu.wallet.impl.domain.model

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.model.AssetKey
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.core.models.Asset as CoreAsset

data class Asset(
    val metaId: Long,
    val token: Token,
    val accountId: AccountId,
    val freeInPlanks: BigInteger?,
    val reservedInPlanks: BigInteger?,
    val miscFrozenInPlanks: BigInteger?,
    val feeFrozenInPlanks: BigInteger?,
    val bondedInPlanks: BigInteger?,
    val redeemableInPlanks: BigInteger?,
    val unbondingInPlanks: BigInteger?,
    val sortIndex: Int,
    val enabled: Boolean?,
    val minSupportedVersion: String?,
    val chainAccountName: String?,
    val markedNotNeed: Boolean
) {
    companion object {
        fun createEmpty(chainAccount: MetaAccount.ChainAccount) = chainAccount.chain?.let {
            it.utilityAsset?.let { utilityAsset ->
                Asset(
                    metaId = chainAccount.metaId,
                    token = Token(configuration = utilityAsset, fiatRate = null, fiatSymbol = null, recentRateChange = null),
                    accountId = chainAccount.accountId,
                    freeInPlanks = null,
                    reservedInPlanks = null,
                    miscFrozenInPlanks = null,
                    feeFrozenInPlanks = null,
                    bondedInPlanks = null,
                    redeemableInPlanks = null,
                    unbondingInPlanks = null,
                    sortIndex = Int.MAX_VALUE,
                    enabled = null,
                    minSupportedVersion = it.minSupportedVersion,
                    chainAccountName = chainAccount.accountName,
                    markedNotNeed = false
                )
            }
        }

        fun createEmpty(
            chainAsset: CoreAsset,
            metaId: Long,
            accountId: AccountId,
            chainAccountName: String? = null,
            minSupportedVersion: String?,
            enabled: Boolean = true
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
            enabled = enabled,
            minSupportedVersion = minSupportedVersion,
            chainAccountName = chainAccountName,
            markedNotNeed = false
        )
    }

    private val free = token.amountFromPlanks(freeInPlanks.orZero())
    val reserved = token.amountFromPlanks(reservedInPlanks.orZero())
    private val miscFrozen = token.amountFromPlanks(miscFrozenInPlanks.orZero())
    private val feeFrozen = token.amountFromPlanks(feeFrozenInPlanks.orZero())

    val locked: BigDecimal = miscFrozen.max(feeFrozen)
    val frozen = locked + reserved

    val total = calculateTotalBalance(freeInPlanks, reservedInPlanks)?.let { token.amountFromPlanks(it) }
    val availableForStaking = free - frozen

    val transferable = free - locked
    val transferableInPlanks = freeInPlanks?.let { it - miscFrozenInPlanks.orZero().max(feeFrozenInPlanks.orZero()) }.orZero()

    val bonded = token.amountFromPlanks(bondedInPlanks.orZero())
    val redeemable = token.amountFromPlanks(redeemableInPlanks.orZero())
    val unbonding = token.amountFromPlanks(unbondingInPlanks.orZero())

    val fiatAmount = total?.let { token.fiatRate?.multiply(total) }

    val uniqueKey = AssetKey(metaId, token.configuration.chainId, accountId, token.configuration.id)

    fun getAsFiatWithCurrency(value: BigDecimal?) =
        token.fiatRate?.let { value?.applyFiatRate(it).orZero().formatFiat(token.fiatSymbol) }
}

fun calculateTotalBalance(
    freeInPlanks: BigInteger?,
    reservedInPlanks: BigInteger?
) = freeInPlanks?.let { freeInPlanks + reservedInPlanks.orZero() }
