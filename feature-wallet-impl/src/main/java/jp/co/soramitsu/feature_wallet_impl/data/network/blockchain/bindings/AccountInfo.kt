package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.bindings

import jp.co.soramitsu.common.data.network.runtime.binding.HelperBinding
import jp.co.soramitsu.common.data.network.runtime.binding.UseCaseBinding
import jp.co.soramitsu.common.data.network.runtime.binding.cast
import jp.co.soramitsu.common.data.network.runtime.binding.getTyped
import jp.co.soramitsu.common.data.network.runtime.binding.incompatible
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHexOrNull
import java.math.BigInteger

class AccountData(
    val free: BigInteger,
    val reserved: BigInteger,
    val miscFrozen: BigInteger,
    val feeFrozen: BigInteger,
)

class AccountInfo(
    val nonce: BigInteger,
    val data: AccountData,
) {

    companion object {
        fun empty() = AccountInfo(
            nonce = BigInteger.ZERO,
            data = AccountData(
                free = BigInteger.ZERO,
                reserved = BigInteger.ZERO,
                miscFrozen = BigInteger.ZERO,
                feeFrozen = BigInteger.ZERO,
            )
        )
    }
}

@HelperBinding
fun bindNumber(dynamicInstance: Any?): BigInteger {
    return dynamicInstance.cast()
}

@HelperBinding
fun bindAccountData(dynamicInstance: Struct.Instance) = AccountData(
    free = bindNumber(dynamicInstance["free"]),
    reserved = bindNumber(dynamicInstance["reserved"]),
    miscFrozen = bindNumber(dynamicInstance["miscFrozen"]),
    feeFrozen = bindNumber(dynamicInstance["feeFrozen"]),
)

@HelperBinding
fun bindNonce(dynamicInstance: Any?): BigInteger {
    return dynamicInstance.cast()
}

@UseCaseBinding
fun bindAccountInfo(scale: String, runtime: RuntimeSnapshot): AccountInfo {
    val type = runtime.typeRegistry["AccountInfo"] ?: incompatible()

    val dynamicInstance = type.fromHexOrNull(runtime, scale).cast<Struct.Instance>()

    return AccountInfo(
        nonce = bindNonce(dynamicInstance["nonce"]),
        data = bindAccountData(dynamicInstance.getTyped("data"))
    )
}
