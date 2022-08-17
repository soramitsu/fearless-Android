package jp.co.soramitsu.featurewalletapi.domain.model

import java.math.BigInteger
import jp.co.soramitsu.common.model.AssetKey
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.featureaccountapi.domain.model.MetaAccount
import jp.co.soramitsu.runtime.ext.utilityAsset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

class Asset(
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
    val enabled: Boolean,
    val minSupportedVersion: String?,
    val chainAccountName: String?,
    val markedNotNeed: Boolean
) {
    companion object {
        fun createEmpty(chainAccount: MetaAccount.ChainAccount) = chainAccount.chain?.let {
            Asset(
                metaId = chainAccount.metaId,
                token = Token(configuration = it.utilityAsset, fiatRate = null, fiatSymbol = null, recentRateChange = null),
                accountId = chainAccount.accountId,
                freeInPlanks = null,
                reservedInPlanks = null,
                miscFrozenInPlanks = null,
                feeFrozenInPlanks = null,
                bondedInPlanks = null,
                redeemableInPlanks = null,
                unbondingInPlanks = null,
                sortIndex = Int.MAX_VALUE,
                enabled = true,
                minSupportedVersion = it.minSupportedVersion,
                chainAccountName = chainAccount.accountName,
                markedNotNeed = false
            )
        }

        fun createEmpty(
            chainAsset: Chain.Asset,
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
            enabled = true,
            minSupportedVersion = minSupportedVersion,
            chainAccountName = chainAccountName,
            markedNotNeed = false
        )
    }

    val free = token.amountFromPlanks(freeInPlanks.orZero())
    val reserved = token.amountFromPlanks(reservedInPlanks.orZero())
    val miscFrozen = token.amountFromPlanks(miscFrozenInPlanks.orZero())
    val feeFrozen = token.amountFromPlanks(feeFrozenInPlanks.orZero())

    val locked = miscFrozen.max(feeFrozen)
    val frozen = locked + reserved

    val total = calculateTotalBalance(freeInPlanks, reservedInPlanks)?.let { token.amountFromPlanks(it) }

    val transferable = free - locked

    val bonded = token.amountFromPlanks(bondedInPlanks.orZero())
    val redeemable = token.amountFromPlanks(redeemableInPlanks.orZero())
    val unbonding = token.amountFromPlanks(unbondingInPlanks.orZero())

    val fiatAmount = total?.let { token.fiatRate?.multiply(total) }

    val uniqueKey = AssetKey(metaId, token.configuration.chainId, accountId, token.configuration.symbol)
}

fun calculateTotalBalance(
    freeInPlanks: BigInteger?,
    reservedInPlanks: BigInteger?
) = freeInPlanks?.let { freeInPlanks + reservedInPlanks.orZero() }
