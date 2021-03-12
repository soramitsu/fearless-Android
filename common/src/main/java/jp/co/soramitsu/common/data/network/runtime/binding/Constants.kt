package jp.co.soramitsu.common.data.network.runtime.binding

import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromByteArrayOrNull
import jp.co.soramitsu.fearless_utils.runtime.metadata.Constant
import java.math.BigInteger

@HelperBinding
fun bindNumberConstant(
    constant: Constant,
    runtime: RuntimeSnapshot
): BigInteger {
    val decoded = constant.type?.fromByteArrayOrNull(runtime, constant.value) ?: incompatible()

    return decoded as? BigInteger ?: incompatible()
}
