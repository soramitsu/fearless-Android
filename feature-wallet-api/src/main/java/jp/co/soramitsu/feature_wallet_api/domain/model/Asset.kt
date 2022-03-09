package jp.co.soramitsu.feature_wallet_api.domain.model

import jp.co.soramitsu.core_db.dao.emptyAccountIdValue
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.feature_account_api.domain.model.MetaAccount
import jp.co.soramitsu.runtime.ext.utilityAsset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import java.math.BigInteger

class Asset(
    val metaId: Long?,
    val token: Token,
    val accountId: AccountId,
    val freeInPlanks: BigInteger,
    val reservedInPlanks: BigInteger,
    val miscFrozenInPlanks: BigInteger,
    val feeFrozenInPlanks: BigInteger,
    val bondedInPlanks: BigInteger,
    val redeemableInPlanks: BigInteger,
    val unbondingInPlanks: BigInteger,
    val sortIndex: Int,
    val enabled: Boolean,
    val chainAccountName: String?
) {
    companion object {
        fun createEmpty(chainAccount: MetaAccount.ChainAccount) = chainAccount.chain?.let {
            Asset(
                metaId = chainAccount.metaId,
                token = Token(configuration = it.utilityAsset, fiatRate = null, fiatSymbol = null, recentRateChange = null),
                accountId = chainAccount.accountId,
                freeInPlanks = BigInteger.ZERO,
                reservedInPlanks = BigInteger.ZERO,
                miscFrozenInPlanks = BigInteger.ZERO,
                feeFrozenInPlanks = BigInteger.ZERO,
                bondedInPlanks = BigInteger.ZERO,
                redeemableInPlanks = BigInteger.ZERO,
                unbondingInPlanks = BigInteger.ZERO,
                sortIndex = Int.MAX_VALUE,
                enabled = true,
                chainAccountName = chainAccount.accountName
            )
        }

        fun createEmpty(chainAsset: Chain.Asset, metaId: Long? = null, chainAccountName: String? = null) = Asset(
            metaId = metaId,
            Token(configuration = chainAsset, fiatRate = null, fiatSymbol = null, recentRateChange = null),
            accountId = emptyAccountIdValue,
            freeInPlanks = BigInteger.ZERO,
            reservedInPlanks = BigInteger.ZERO,
            miscFrozenInPlanks = BigInteger.ZERO,
            feeFrozenInPlanks = BigInteger.ZERO,
            bondedInPlanks = BigInteger.ZERO,
            redeemableInPlanks = BigInteger.ZERO,
            unbondingInPlanks = BigInteger.ZERO,
            sortIndex = Int.MAX_VALUE,
            enabled = true,
            chainAccountName = chainAccountName
        )
    }

    val free = token.amountFromPlanks(freeInPlanks)
    val reserved = token.amountFromPlanks(reservedInPlanks)
    val miscFrozen = token.amountFromPlanks(miscFrozenInPlanks)
    val feeFrozen = token.amountFromPlanks(feeFrozenInPlanks)

    val locked = miscFrozen.max(feeFrozen)
    val frozen = locked + reserved

    val total = token.amountFromPlanks(calculateTotalBalance(freeInPlanks, reservedInPlanks))

    val transferable = free - locked

    val bonded = token.amountFromPlanks(bondedInPlanks)
    val redeemable = token.amountFromPlanks(redeemableInPlanks)
    val unbonding = token.amountFromPlanks(unbondingInPlanks)

    val fiatAmount = token.fiatRate?.multiply(total)
}

fun calculateTotalBalance(
    freeInPlanks: BigInteger,
    reservedInPlanks: BigInteger
) = freeInPlanks + reservedInPlanks
