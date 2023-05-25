package jp.co.soramitsu.wallet.api.data.cache

import java.math.BigInteger
import jp.co.soramitsu.common.data.network.runtime.binding.AccountData
import jp.co.soramitsu.common.data.network.runtime.binding.AccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.EqAccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.OrmlTokensAccountData
import jp.co.soramitsu.common.data.network.runtime.binding.bindAccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.bindEquilibriumAccountInfo
import jp.co.soramitsu.common.data.network.runtime.binding.bindNonce
import jp.co.soramitsu.common.data.network.runtime.binding.bindOrmlTokensAccountData
import jp.co.soramitsu.common.data.network.runtime.binding.cast
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.rpc.storage.returnType
import jp.co.soramitsu.coredb.model.AssetLocal
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.shared_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.shared_utils.runtime.definitions.types.fromHexOrNull
import jp.co.soramitsu.shared_utils.runtime.metadata.storage

suspend fun AssetCache.updateAsset(
    metaId: Long,
    accountId: AccountId,
    chainAsset: Asset,
    accountInfo: AccountInfo
) = updateAsset(metaId, accountId, chainAsset, accountInfoUpdater(accountInfo))

suspend fun AssetCache.updateAsset(
    accountId: AccountId,
    chainAsset: Asset,
    accountInfo: AccountInfo
) = updateAsset(accountId, chainAsset, accountInfoUpdater(accountInfo))

private fun accountInfoUpdater(accountInfo: AccountInfo) = { asset: AssetLocal ->
    val data = accountInfo.data

    asset.copy(
        freeInPlanks = data.free,
        reservedInPlanks = data.reserved,
        miscFrozenInPlanks = data.miscFrozen,
        feeFrozenInPlanks = data.feeFrozen
    )
}

fun bind9420AccountInfo(hex: String?, runtime: RuntimeSnapshot): AccountInfo {
    hex ?: return AccountInfo.empty()
    val type = runtime.metadata.system().storage("Account").returnType()

    val dynamicInstance = type.fromHexOrNull(runtime, hex).cast<Struct.Instance>()
    val dataInstance: Struct.Instance? = dynamicInstance["data"]
    val data = AccountData(
        free = (dataInstance?.get("free") as? BigInteger).orZero(),
        reserved = (dataInstance?.get("reserved") as? BigInteger).orZero(),
        miscFrozen = (dataInstance?.get("frozen") as? BigInteger).orZero(),
        feeFrozen = (dataInstance?.get("feeFrozen") as? BigInteger).orZero()
    )
    return AccountInfo(
        nonce = bindNonce(dynamicInstance["nonce"]),
        data = data
    )
}

fun bindAccountInfoOrDefault(hex: String?, runtime: RuntimeSnapshot): AccountInfo {
    return hex?.let { bindAccountInfo(it, runtime) } ?: AccountInfo.empty()
}

fun bindOrmlTokensAccountDataOrDefault(hex: String?, runtime: RuntimeSnapshot): OrmlTokensAccountData {
    return hex?.let { bindOrmlTokensAccountData(it, runtime) } ?: OrmlTokensAccountData.empty()
}

fun bindEquilibriumAccountData(hex: String?, runtime: RuntimeSnapshot): EqAccountInfo? {
    return hex?.let { bindEquilibriumAccountInfo(it, runtime) }
}
