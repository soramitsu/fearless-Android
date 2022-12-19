package jp.co.soramitsu.common.data.network.runtime.binding

import java.math.BigInteger
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHexOrNull
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage

class AccountData(
    val free: BigInteger,
    val reserved: BigInteger,
    val miscFrozen: BigInteger,
    val feeFrozen: BigInteger
)

class OrmlTokensAccountData(
    val free: BigInteger,
    val reserved: BigInteger,
    val frozen: BigInteger
) {
    companion object {
        fun empty() = OrmlTokensAccountData(
            free = BigInteger.ZERO,
            reserved = BigInteger.ZERO,
            frozen = BigInteger.ZERO
        )
    }
}

class AccountInfo(
    val nonce: BigInteger,
    val data: AccountData
) {

    companion object {
        fun empty() = AccountInfo(
            nonce = BigInteger.ZERO,
            data = AccountData(
                free = BigInteger.ZERO,
                reserved = BigInteger.ZERO,
                miscFrozen = BigInteger.ZERO,
                feeFrozen = BigInteger.ZERO
            )
        )
    }
}

@HelperBinding
fun bindAccountData(dynamicInstance: Struct.Instance?) = AccountData(
    free = (dynamicInstance?.get("free") as? BigInteger).orZero(),
    reserved = (dynamicInstance?.get("reserved") as? BigInteger).orZero(),
    miscFrozen = (dynamicInstance?.get("miscFrozen") as? BigInteger).orZero(),
    feeFrozen = (dynamicInstance?.get("feeFrozen") as? BigInteger).orZero()
)

@HelperBinding
fun bindNonce(dynamicInstance: Any?): BigInteger {
    return bindNumber(dynamicInstance)
}

@UseCaseBinding
fun bindAccountInfo(scale: String, runtime: RuntimeSnapshot): AccountInfo {
    val type = runtime.metadata.system().storage("Account").returnType()

    val dynamicInstance = type.fromHexOrNull(runtime, scale).cast<Struct.Instance>()

    return AccountInfo(
        nonce = bindNonce(dynamicInstance["nonce"]),
        data = bindAccountData(dynamicInstance["data"])
    )
}

@UseCaseBinding
fun bindOrmlTokensAccountData(scale: String, runtime: RuntimeSnapshot): OrmlTokensAccountData {
    val type = runtime.metadata.module(Modules.TOKENS).storage("Accounts").returnType()

    val dynamicInstance = type.fromHexOrNull(runtime, scale).cast<Struct.Instance>()

    return OrmlTokensAccountData(
        free = bindNumber(dynamicInstance["free"]),
        reserved = bindNumber(dynamicInstance["reserved"]),
        frozen = bindNumber(dynamicInstance["frozen"])
    )
}
