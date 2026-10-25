package jp.co.soramitsu.core.utils

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.fearless_utils.extensions.fromHex

val IChain.utilityAsset: Asset?
    get() = assets.firstOrNull { it.isUtility } ?: assets.firstOrNull { it.isNative == true }

fun Asset.amountFromPlanks(amountInPlanks: BigInteger): BigDecimal {
    return amountInPlanks.toBigDecimal(scale = precision)
}

fun Asset.planksFromAmount(amount: BigDecimal): BigInteger {
    return amount.scaleByPowerOfTen(precision).toBigInteger()
}

fun Asset.isSoraUtilityAsset(): Boolean {
    return typeExtra == ChainAssetType.SoraUtilityAsset
}

/**
 * The `code` of a SORA asset id (`[u8; 32]`). 4.2.0 passed signed bytes (0xf2 as -14) and its SCALE writer kept
 * the low byte; fearless-utils refuses negative u8 values, so pass 0..255, which encode to the same bytes.
 */
fun soraAssetIdCode(assetIdHex: String?): List<BigInteger> {
    return assetIdHex?.fromHex()?.let { soraAssetIdCode(it) }.orEmpty()
}

fun soraAssetIdCode(assetId: ByteArray): List<BigInteger> {
    return assetId.map { (it.toInt() and 0xFF).toBigInteger() }
}
