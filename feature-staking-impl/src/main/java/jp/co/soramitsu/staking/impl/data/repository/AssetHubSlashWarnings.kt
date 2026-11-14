package jp.co.soramitsu.staking.impl.data.repository

import jp.co.soramitsu.common.utils.numberConstant
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.core.runtime.storage.returnType
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.metadata.moduleOrNull
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindActiveEra
import kotlinx.coroutines.withTimeout
import java.math.BigInteger

/** Recent and still-pending async slashes both keep a validator warning visible. */
internal class AssetHubSlashWarnings(private val registry: ChainRegistry) {
    suspend fun get(chainId: String, accounts: List<String>): Map<String, Boolean> =
        withTimeout(STAKING_READ_TIMEOUT_MILLIS) {
        val snapshot = AssetHubStorageSnapshot.capture(registry, chainId)
        val runtime = snapshot.runtime
        require(runtime.metadata.moduleOrNull("StakingRcClient") != null)
        val staking = runtime.metadata.staking()
        val activeKey = staking.storage("ActiveEra").storageKey()
        val active = bindActiveEra(requireNotNull(snapshot.query(listOf(activeKey))[activeKey]), runtime)
        val first = (active - staking.numberConstant("SlashDeferDuration", runtime) + BigInteger.ONE).max(BigInteger.ZERO)
        val slashed = recent(snapshot, first, active) + pending(snapshot)
        accounts.associateWith { it.fromHex().toHexString() in slashed }
    }

    private suspend fun recent(
        snapshot: AssetHubStorageSnapshot,
        first: BigInteger,
        active: BigInteger
    ): Set<String> {
        val runtime = snapshot.runtime
        val storage = runtime.metadata.staking().storage("ValidatorSlashInEra")
        val keys = snapshot.discoverKeys(storage.storageKey())
        val values = snapshot.query(keys)
        return keys.mapNotNull { key ->
            val bytes = key.fromHex()
            require(bytes.size == RECENT_KEY_BYTES) { "Unsupported validator slash key" }
            val era = BigInteger(1, bytes.copyOfRange(ERA_OFFSET, ERA_END).reversedArray())
            val account = bytes.copyOfRange(VALIDATOR_OFFSET, VALIDATOR_END)
            require(storage.storageKey(runtime, era, account) == key)
            val amounts = storage.returnType().fromHex(runtime, requireNotNull(values[key])) as? List<*>
            require(amounts?.size == 2 && amounts.all { it is BigInteger && it.signum() >= 0 }) { "Malformed validator slash" }
            if (era in first..active && amounts.any { (it as BigInteger).signum() > 0 }) account.toHexString() else null
        }.toSet()
    }

    private suspend fun pending(snapshot: AssetHubStorageSnapshot): Set<String> {
        val runtime = snapshot.runtime
        val storage = runtime.metadata.staking().storage("UnappliedSlashes")
        val keys = snapshot.discoverKeys(storage.storageKey())
        val values = snapshot.query(keys)
        return keys.map { key ->
            val bytes = key.fromHex()
            require(bytes.size == PENDING_KEY_BYTES) { "Unsupported pending slash key" }
            val era = BigInteger(1, bytes.copyOfRange(ERA_OFFSET, ERA_END).reversedArray())
            val account = bytes.copyOfRange(VALIDATOR_OFFSET, VALIDATOR_END)
            val fraction = BigInteger(1, bytes.copyOfRange(VALIDATOR_END, FRACTION_END).reversedArray())
            val page = BigInteger(1, bytes.copyOfRange(FRACTION_END, PENDING_KEY_BYTES).reversedArray())
            require(storage.storageKey(runtime, era, listOf(account, fraction, page)) == key)
            val slash = storage.returnType().fromHex(runtime, requireNotNull(values[key])) as? Struct.Instance
            val validator = requireNotNull(slash?.get<ByteArray>("validator"))
            require(validator.contentEquals(account)) { "Pending slash validator identity mismatch" }
            validator.toHexString()
        }.toSet()
    }

    private companion object {
        const val STAKING_READ_TIMEOUT_MILLIS = 180_000L
        const val ERA_OFFSET = 40
        const val ERA_END = 44
        const val VALIDATOR_OFFSET = 52
        const val VALIDATOR_END = 84
        const val FRACTION_END = 88
        const val RECENT_KEY_BYTES = 84
        const val PENDING_KEY_BYTES = 92
    }
}
