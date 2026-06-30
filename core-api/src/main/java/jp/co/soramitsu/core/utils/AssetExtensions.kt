package jp.co.soramitsu.core.utils

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.IChain

val IChain.utilityAsset: Asset?
    get() = assets.firstOrNull { it.isUtility } ?: assets.firstOrNull { it.isNative == true }

fun Asset.amountFromPlanks(amountInPlanks: BigInteger): BigDecimal {
    return amountInPlanks.toBigDecimal(scale = precision)
}

fun Asset.planksFromAmount(amount: BigDecimal): BigInteger {
    return amount.scaleByPowerOfTen(precision).toBigInteger()
}

fun Asset.isSoraUtilityAsset(): Boolean {
    return type == ChainAssetType.SoraUtilityAsset
}
