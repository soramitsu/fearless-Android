package jp.co.soramitsu.common.data.network.runtime.binding

import java.math.BigInteger
import jp.co.soramitsu.common.utils.orZero

@HelperBinding
fun bindNumber(dynamicInstance: Any?): BigInteger = runCatching { dynamicInstance.cast<BigInteger>() }.getOrNull().orZero()

@HelperBinding
fun bindString(dynamicInstance: Any?): String = dynamicInstance.cast<ByteArray>().decodeToString()
