package jp.co.soramitsu.liquiditypools.impl.util

import jp.co.soramitsu.androidfoundation.format.Big100
import jp.co.soramitsu.androidfoundation.format.divideBy
import jp.co.soramitsu.androidfoundation.format.equalTo
import jp.co.soramitsu.androidfoundation.format.safeDivide
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import java.math.BigDecimal

object PolkaswapFormulas {

    fun calculatePooledValue(
        reserves: BigDecimal,
        poolProvidersBalance: BigDecimal,
        totalIssuance: BigDecimal,
        precision: Int? = 18 // OptionsProvider.defaultScale,
    ): BigDecimal =
        reserves.multiply(poolProvidersBalance).divideBy(totalIssuance, precision)

    private fun calculateShareOfPool(poolProvidersBalance: BigDecimal, totalIssuance: BigDecimal): BigDecimal =
        poolProvidersBalance.divideBy(totalIssuance).multiply(Big100)

    fun calculateShareOfPoolFromAmount(amount: BigDecimal, amountPooled: BigDecimal): Double =
        if (amount.equalTo(amountPooled)) {
            100.0
        } else {
            calculateShareOfPool(amount, amountPooled).toDouble()
        }

    fun calculateAddLiquidityAmount(
        baseAmount: BigDecimal,
        reservesFirst: BigDecimal,
        reservesSecond: BigDecimal,
        precisionFirst: Int,
        precisionSecond: Int,
        desired: WithDesired,
    ): BigDecimal {
        return if (desired == WithDesired.INPUT) {
            baseAmount.multiply(reservesSecond).safeDivide(reservesFirst, precisionSecond)
        } else {
            baseAmount.multiply(reservesFirst).safeDivide(reservesSecond, precisionFirst)
        }
    }

    fun estimateAddingShareOfPool(
        amount: BigDecimal,
        pooled: BigDecimal,
        reserves: BigDecimal
    ): BigDecimal {
        return pooled
            .plus(amount)
            .multiply(Big100)
            .safeDivide(amount.plus(reserves))
    }

    fun estimateRemovingShareOfPool(
        amount: BigDecimal,
        pooled: BigDecimal,
        reserves: BigDecimal
    ): BigDecimal = pooled
        .minus(amount)
        .multiply(Big100)
        .safeDivide(reserves.minus(amount))

    fun calculateMinAmount(amount: BigDecimal, slippageTolerance: Double): BigDecimal {
        return amount.minus(amount.multiply(BigDecimal.valueOf(slippageTolerance / 100)))
    }

    fun calculateTokenPerTokenRate(amount1: BigDecimal, amount2: BigDecimal): BigDecimal {
        return amount1.safeDivide(amount2)
    }

    fun calculateMarkerAssetDesired(
        fromAmount: BigDecimal,
        firstReserves: BigDecimal,
        totalIssuance: BigDecimal,
    ): BigDecimal = fromAmount.safeDivide(firstReserves).multiply(totalIssuance)

    fun calculateStrategicBonusAPY(strategicBonusApy: Double?): Double? {
        return strategicBonusApy?.times(100)
    }

    fun calculateAmountByPercentage(
        amount: BigDecimal,
        percentage: Double,
        precision: Int,
    ): BigDecimal = if (percentage == 100.0) {
        amount
    } else {
        amount.multiply(percentage.toBigDecimal())
            .safeDivide(Big100, precision)
    }

    fun calculateOneAmountFromAnother(
        amount: BigDecimal,
        amountPooled: BigDecimal,
        otherPooled: BigDecimal,
        precision: Int? = 18 // OptionsProvider.defaultScale,
    ): BigDecimal = amount.multiply(otherPooled).safeDivide(amountPooled, precision)
}
