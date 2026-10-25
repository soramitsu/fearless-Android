package jp.co.soramitsu.wallet.impl.domain.model

import java.math.BigInteger

/** Read availability is distinct from a monetary zero; diagnostics contain no stored payload. */
sealed interface AssetAmountState<out T> {
    data object Missing : AssetAmountState<Nothing>
    data class Known<T>(val value: T) : AssetAmountState<T>
    data class Unavailable(val reason: String) : AssetAmountState<Nothing>
}

/**
 * Exact integer arithmetic over independently read fields. Optional nulls retain the released
 * writer's absent-component semantics, while a missing free balance still means loading.
 * Unavailable fields never take that optional default. The original input states remain intact.
 */
data class AssetBalanceAmounts(
    val free: AssetAmountState<BigInteger>,
    val reserved: AssetAmountState<BigInteger> = AssetAmountState.Missing,
    val miscFrozen: AssetAmountState<BigInteger> = AssetAmountState.Missing,
    val feeFrozen: AssetAmountState<BigInteger> = AssetAmountState.Missing,
    val bonded: AssetAmountState<BigInteger> = AssetAmountState.Missing,
    val redeemable: AssetAmountState<BigInteger> = AssetAmountState.Missing,
    val unbonding: AssetAmountState<BigInteger> = AssetAmountState.Missing,
    val isFrozen: Boolean = false
) {
    private val usableFree = nonNegative(free, "free")
    val reservedAmount = optionalComponent(reserved, "reserved")
    val locked = combine(optionalComponent(miscFrozen, "miscFrozen"), optionalComponent(feeFrozen, "feeFrozen"), BigInteger::max)
    val frozen = combine(locked, reservedAmount, BigInteger::add)
    val total = combine(usableFree, reservedAmount, BigInteger::add)
    val transferable = combine(usableFree, locked, BigInteger::subtract)
    val availableForStaking = combine(usableFree, frozen) { available, held -> (available - held).max(BigInteger.ZERO) }
    val sendAvailable = when (transferable) {
        is AssetAmountState.Known -> AssetAmountState.Known(if (isFrozen) BigInteger.ZERO else transferable.value)
        else -> transferable
    }
    val bondedAmount = nonNegative(bonded, "bonded")
    val redeemableAmount = optionalComponent(redeemable, "redeemable")
    val unbondingAmount = optionalComponent(unbonding, "unbonding")

    private fun nonNegative(value: AssetAmountState<BigInteger>, field: String): AssetAmountState<BigInteger> =
        if (value is AssetAmountState.Known && value.value.signum() < 0) {
            AssetAmountState.Unavailable(if (field == "free" && value.value == BigInteger.valueOf(-1)) "balance-load-failed" else "negative-$field")
        } else value

    private fun optionalComponent(value: AssetAmountState<BigInteger>, field: String): AssetAmountState<BigInteger> =
        if (value == AssetAmountState.Missing) AssetAmountState.Known(BigInteger.ZERO) else nonNegative(value, field)

    private fun combine(
        first: AssetAmountState<BigInteger>,
        second: AssetAmountState<BigInteger>,
        operation: (BigInteger, BigInteger) -> BigInteger
    ): AssetAmountState<BigInteger> = when {
        first is AssetAmountState.Unavailable -> first
        second is AssetAmountState.Unavailable -> second
        first is AssetAmountState.Missing || second is AssetAmountState.Missing -> AssetAmountState.Missing
        first is AssetAmountState.Known && second is AssetAmountState.Known -> AssetAmountState.Known(operation(first.value, second.value))
        else -> error("Unrecognized amount state")
    }
}
