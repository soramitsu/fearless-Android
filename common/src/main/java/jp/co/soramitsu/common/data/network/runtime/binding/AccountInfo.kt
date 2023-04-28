package jp.co.soramitsu.common.data.network.runtime.binding

import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.core.rpc.storage.returnType
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.shared_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.shared_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.shared_utils.runtime.definitions.types.fromHexOrNull
import jp.co.soramitsu.shared_utils.runtime.metadata.module
import jp.co.soramitsu.shared_utils.runtime.metadata.storage
import java.math.BigInteger

class AccountData(
    val free: BigInteger,
    val reserved: BigInteger,
    val miscFrozen: BigInteger,
    val feeFrozen: BigInteger
)

class EqAccountData(
    val lock: BigInteger,
    val balances: Map<BigInteger, BigInteger>
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

class EqAccountInfo(
    val nonce: BigInteger,
    val data: EqAccountData
)

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

class EqOraclePricePoint(
    val blockNumber: BigInteger,
    val timestamp: BigInteger,
    val lastFinRecalcTimestamp: BigInteger,
    val price: BigInteger,
    val dataPoints: List<DataPoint>
)

class DataPoint(
    val price: BigInteger,
    val accountId: AccountId,
    val blockNumber: BigInteger,
    val timestamp: BigInteger
)

@HelperBinding
fun bindAccountData(dynamicInstance: Struct.Instance?) = AccountData(
    free = (dynamicInstance?.get("free") as? BigInteger).orZero(),
    reserved = (dynamicInstance?.get("reserved") as? BigInteger).orZero(),
    miscFrozen = (dynamicInstance?.get("miscFrozen") as? BigInteger).orZero(),
    feeFrozen = (dynamicInstance?.get("feeFrozen") as? BigInteger).orZero()
)

@UseCaseBinding
fun bindEquilibriumAccountInfo(scale: String, runtime: RuntimeSnapshot): EqAccountInfo {
    val type = runtime.metadata.system().storage("Account").returnType()

    val dynamicInstance = type.fromHexOrNull(runtime, scale).cast<Struct.Instance>()
    val data: DictEnum.Entry<Struct.Instance>? = dynamicInstance["data"]

    return EqAccountInfo(
        nonce = bindNonce(dynamicInstance["nonce"]),
        data = bindEquilibriumAccountData(data?.value)
    )
}

@UseCaseBinding
fun bindEquilibriumAssetRates(scale: String?, runtime: RuntimeSnapshot): EqOraclePricePoint? {
    scale ?: return null

    val type = runtime.metadata.module(Modules.ORACLE).storage("PricePoints").returnType()
    val dynamicInstance = type.fromHexOrNull(runtime, scale).cast<Struct.Instance>()

    val dataPoints = dynamicInstance.getList("dataPoints").filterIsInstance<Struct.Instance>().map { dataPointStruct ->
        DataPoint(
            price = bindNumber(dataPointStruct["price"]),
            accountId = bindAccountId(dataPointStruct["accountId"]),
            blockNumber = bindNumber(dataPointStruct["blockNumber"]),
            timestamp = bindNumber(dataPointStruct["timestamp"])
        )
    }
    return EqOraclePricePoint(
        blockNumber = bindNumber(dynamicInstance["blockNumber"]),
        timestamp = bindNumber(dynamicInstance["timestamp"]),
        lastFinRecalcTimestamp = bindNumber(dynamicInstance["lastFinRecalcTimestamp"]),
        price = bindNumber(dynamicInstance["price"]),
        dataPoints = dataPoints
    )
}

fun bindEquilibriumAccountData(dynamicInstance: Struct.Instance?): EqAccountData {
    val balanceList: List<List<Any>>? = dynamicInstance?.getList("balance")?.cast()
    val balances = balanceList?.mapNotNull {
        (it.getOrNull(0) as? BigInteger)?.let { eqAssetId ->
            val balanceEnum: DictEnum.Entry<BigInteger>? = it.getOrNull(1).cast()
            val balanceValue = if (balanceEnum?.name == "Positive") balanceEnum.value else BigInteger.ZERO
            eqAssetId to balanceValue
        }
    }?.toMap().orEmpty()

    return EqAccountData(
        lock = bindNumber(dynamicInstance?.get("lock")).orZero(),
        balances = balances
    )
}

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
