package jp.co.soramitsu.account.impl.data.repository

import java.io.IOException
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshotMove
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore.Journal
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore.Operation
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore.PublicAfterImage
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageFailureKind
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.scale.toHexString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletSecretMutationCoordinatorDurabilityTest {

    @Test
    fun `ambiguous stage commit blocks same-process Room and restart replays exact delete`() {
        val durableState = DurablePreferenceState()
        val preferences = FaultingEncryptedPreferences(durableState)
        val database = DurabilityWalletMutationDatabase(wallet(metaId = 7L, selected = true))
        val coordinator = coordinator(database, preferences)
        preferences.nextReplaceFault = ReplaceFault.COMMIT_THEN_THROW

        val firstFailure = assertFails<WalletSecretMutationCoordinatorException> {
            runBlocking { coordinator.delete(7L) }
        }

        assertEquals(WalletMutationFailureReason.DURABILITY_UNKNOWN, firstFailure.reason)
        assertNotNull(database.account(7L))
        assertEquals(0, database.deleteCalls)
        assertTrue(durableState.values.containsKey(WalletSecretMutationJournalStore.JOURNAL_KEY))

        assertFails<WalletSecureStorageUnavailableException> {
            runBlocking { coordinator.reconcilePendingMutation() }
        }
        assertEquals(0, database.deleteCalls)

        val restartedPreferences = FaultingEncryptedPreferences(durableState)
        runBlocking {
            coordinator(database, restartedPreferences).reconcilePendingMutation()
        }

        assertNull(database.account(7L))
        assertFalse(durableState.values.containsKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
        assertEquals(1, database.deleteCalls)
    }

    @Test
    fun `ambiguous stage without commit preserves Room across process restart`() {
        val durableState = DurablePreferenceState()
        val preferences = FaultingEncryptedPreferences(durableState)
        val database = DurabilityWalletMutationDatabase(wallet(metaId = 8L, selected = true))
        val coordinator = coordinator(database, preferences)
        preferences.nextReplaceFault = ReplaceFault.THROW_BEFORE_COMMIT

        assertFails<WalletSecretMutationCoordinatorException> {
            runBlocking { coordinator.delete(8L) }
        }
        assertNotNull(database.account(8L))
        assertFalse(durableState.values.containsKey(WalletSecretMutationJournalStore.JOURNAL_KEY))

        runBlocking {
            coordinator(
                database,
                FaultingEncryptedPreferences(durableState)
            ).reconcilePendingMutation()
        }

        assertNotNull(database.account(8L))
        assertEquals(0, database.deleteCalls)
    }

    @Test
    fun `durability preflight rejects a staged journal before any Room mutation`() {
        val durableState = DurablePreferenceState()
        val preferences = FaultingEncryptedPreferences(durableState)
        val database = DurabilityWalletMutationDatabase(wallet(metaId = 9L, selected = true))
        stageDelete(database.account(9L)!!, preferences)
        preferences.markUnhealthy()

        assertFails<WalletSecureStorageUnavailableException> {
            runBlocking {
                coordinator(database, preferences).reconcilePendingMutation()
            }
        }

        assertNotNull(database.account(9L))
        assertEquals(0, database.deleteCalls)
        assertTrue(preferences.preflightCalls > 0)
    }

    @Test
    fun `Room-committed delete crash replays idempotently and finalizes secrets`() {
        val durableState = DurablePreferenceState()
        val preferences = FaultingEncryptedPreferences(durableState)
        val database = DurabilityWalletMutationDatabase(wallet(metaId = 10L, selected = true))
        database.throwAfterDeleteCommit = true

        assertFails<SimulatedProcessDeath> {
            runBlocking { coordinator(database, preferences).delete(10L) }
        }

        assertNull(database.account(10L))
        assertTrue(durableState.values.containsKey(WalletSecretMutationJournalStore.JOURNAL_KEY))

        runBlocking {
            coordinator(database, preferences).reconcilePendingMutation()
            coordinator(database, preferences).reconcilePendingMutation()
        }

        assertNull(database.account(10L))
        assertFalse(durableState.values.containsKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
        assertEquals(1, database.deleteCalls)
    }

    @Test
    fun `finalization crash before commit retains journal for restart replay`() {
        val durableState = DurablePreferenceState()
        val preferences = FaultingEncryptedPreferences(durableState)
        val database = DurabilityWalletMutationDatabase(wallet(metaId = 11L, selected = true))
        database.afterDeleteCommit = {
            preferences.nextReplaceFault = ReplaceFault.THROW_BEFORE_COMMIT
        }

        assertFails<WalletSecretMutationCoordinatorException> {
            runBlocking { coordinator(database, preferences).delete(11L) }
        }

        assertNull(database.account(11L))
        assertTrue(durableState.values.containsKey(WalletSecretMutationJournalStore.JOURNAL_KEY))

        runBlocking {
            coordinator(
                database,
                FaultingEncryptedPreferences(durableState)
            ).reconcilePendingMutation()
        }

        assertFalse(durableState.values.containsKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
        assertEquals(1, database.deleteCalls)
    }

    @Test
    fun `finalization commit then throw requires restart without resurrecting wallet`() {
        val durableState = DurablePreferenceState()
        val preferences = FaultingEncryptedPreferences(durableState)
        val database = DurabilityWalletMutationDatabase(wallet(metaId = 12L, selected = true))
        database.afterDeleteCommit = {
            preferences.nextReplaceFault = ReplaceFault.COMMIT_THEN_THROW
        }

        assertFails<WalletSecretMutationCoordinatorException> {
            runBlocking { coordinator(database, preferences).delete(12L) }
        }

        assertNull(database.account(12L))
        assertFalse(durableState.values.containsKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
        assertFails<WalletSecureStorageUnavailableException> {
            runBlocking { coordinator(database, preferences).reconcilePendingMutation() }
        }

        runBlocking {
            coordinator(
                database,
                FaultingEncryptedPreferences(durableState)
            ).reconcilePendingMutation()
        }
        assertNull(database.account(12L))
        assertEquals(1, database.deleteCalls)
    }

    @Test
    fun `CAS conflict after Room commit replays while preserving newer metadata and selection`() {
        val durableState = DurablePreferenceState()
        val preferences = FaultingEncryptedPreferences(durableState)
        val target = wallet(metaId = 20L, selected = true, name = "original")
        val other = wallet(metaId = 21L, selected = false, name = "other", position = 1)
        val database = DurabilityWalletMutationDatabase(target, other)
        val material = ethereumMaterial()
        val after = target.copyAccount(
            ethereumPublicKey = material.publicKey,
            ethereumAddress = material.address
        )
        database.afterUpdateCommit = {
            preferences.failNextCompareAndSwap = true
        }

        val failure = assertFails<WalletSecretMutationCoordinatorException> {
            runBlocking {
                coordinator(database, preferences).addEvm(
                    existing = target,
                    after = after,
                    ethereumSecretPlaintext = material.encodedSecret
                )
            }
        }

        assertEquals(WalletMutationFailureReason.STATE_CONFLICT, failure.reason)
        assertTrue(durableState.values.containsKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
        database.replace(
            database.account(20L)!!.copyAccount(
                name = "newer metadata",
                selected = false
            )
        )
        database.replace(
            database.account(21L)!!.copyAccount(selected = true)
        )

        runBlocking {
            coordinator(database, preferences).reconcilePendingMutation()
            coordinator(database, preferences).reconcilePendingMutation()
        }

        val reconciled = database.account(20L)!!
        assertEquals("newer metadata", reconciled.name)
        assertFalse(reconciled.isSelected)
        assertTrue(database.account(21L)!!.isSelected)
        assertTrue(reconciled.ethereumPublicKey!!.contentEquals(material.publicKey))
        assertFalse(durableState.values.containsKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
    }

    @Test
    fun `post-commit read divergence cannot mutate Room and durable journal replays after restart`() {
        val durableState = DurablePreferenceState()
        val preferences = FaultingEncryptedPreferences(durableState)
        val database = DurabilityWalletMutationDatabase(wallet(metaId = 30L, selected = true))
        preferences.afterNextReplace = {
            preferences.readOverrides[WalletSecretMutationJournalStore.JOURNAL_KEY] = "{}"
        }

        assertFails<WalletSecretMutationCoordinatorException> {
            runBlocking { coordinator(database, preferences).delete(30L) }
        }

        assertNotNull(database.account(30L))
        assertEquals(0, database.deleteCalls)
        assertTrue(durableState.values.containsKey(WalletSecretMutationJournalStore.JOURNAL_KEY))

        runBlocking {
            coordinator(
                database,
                FaultingEncryptedPreferences(durableState)
            ).reconcilePendingMutation()
        }
        assertNull(database.account(30L))
    }

    @Test
    fun `malformed stored journal fails closed before Room`() {
        val durableState = DurablePreferenceState(
            linkedMapOf(WalletSecretMutationJournalStore.JOURNAL_KEY to """{"version":1}""")
        )
        val preferences = FaultingEncryptedPreferences(durableState)
        val database = DurabilityWalletMutationDatabase(wallet(metaId = 31L, selected = true))

        val failure = assertFails<WalletSecretMutationCoordinatorException> {
            runBlocking {
                coordinator(database, preferences).reconcilePendingMutation()
            }
        }

        assertEquals(WalletMutationFailureReason.RECOVERY_REQUIRED, failure.reason)
        assertNotNull(database.account(31L))
        assertEquals(0, database.deleteCalls)
    }

    @Test
    fun `concurrent delete requests serialize to one durable mutation`() = runBlocking {
        val durableState = DurablePreferenceState()
        val preferences = FaultingEncryptedPreferences(durableState)
        val database = DurabilityWalletMutationDatabase(wallet(metaId = 40L, selected = true))
        val first = coordinator(database, preferences)
        val second = coordinator(database, preferences)

        val outcomes = listOf(first, second).map { instance ->
            async(Dispatchers.Default) {
                runCatching { instance.delete(40L) }
            }
        }.awaitAll()

        assertEquals(1, outcomes.count { it.isSuccess })
        assertEquals(1, outcomes.count { it.isFailure })
        assertEquals(1, database.deleteCalls)
        assertNull(database.account(40L))
        assertFalse(durableState.values.containsKey(WalletSecretMutationJournalStore.JOURNAL_KEY))
    }

    private fun coordinator(
        database: DurabilityWalletMutationDatabase,
        preferences: FaultingEncryptedPreferences
    ): WalletSecretMutationCoordinator {
        return WalletSecretMutationCoordinator(
            database = database,
            journalStore = WalletSecretMutationJournalStore(preferences),
            encryptedPreferences = preferences,
            identifierSource = FixedIdentifierSource,
            testOnly = Unit
        )
    }

    private fun stageDelete(
        account: MetaAccountLocal,
        preferences: FaultingEncryptedPreferences
    ) {
        val store = WalletSecretMutationJournalStore(preferences)
        val beforeImage = account.toJournalImage()
        val keys = store.deletionSecretKeys(
            metaId = account.id,
            beforeImage = beforeImage,
            chainAccountIdsHex = emptySet()
        )
        store.stage(
            Journal(
                operationId = OPERATION_ID,
                operation = Operation.DELETE,
                metaId = account.id,
                beforeImage = beforeImage,
                afterImage = null,
                selectedMetaIdAfterDelete = null,
                chainAccountIdsHex = emptySet(),
                secretKeysToPut = emptySet(),
                secretKeysToRemove = keys
            ),
            emptyMap()
        )
    }

    private fun ethereumMaterial(): EthereumMaterial {
        val privateKey = ByteArray(32).also { it[it.lastIndex] = 1 }
        val generated = EthereumKeypairFactory.createWithPrivateKey(privateKey)
        val publicKey = generated.publicKey
        val encoded = EthereumSecrets(
            ethereumKeypair = BaseKeypair(
                privateKey = privateKey,
                publicKey = publicKey
            )
        ).toHexString()
        return EthereumMaterial(
            publicKey = publicKey,
            address = publicKey.ethereumAddressFromPublicKey(),
            encodedSecret = encoded
        )
    }

    private data class EthereumMaterial(
        val publicKey: ByteArray,
        val address: ByteArray,
        val encodedSecret: String
    )

    private companion object {
        const val OPERATION_ID = "00000000-0000-4000-8000-000000000001"

        val FixedIdentifierSource = object : WalletMutationIdentifierSource {
            override fun nextMetaIdCandidate(): Long = 100L
            override fun nextOperationId(): String = OPERATION_ID
        }
    }
}

private class DurabilityWalletMutationDatabase(
    vararg initialAccounts: MetaAccountLocal
) : WalletMutationDatabase {

    private val accounts = linkedMapOf<Long, MetaAccountLocal>()

    var deleteCalls = 0
        private set
    var updateCalls = 0
        private set
    var throwAfterDeleteCommit = false
    var afterDeleteCommit: (() -> Unit)? = null
    var afterUpdateCommit: (() -> Unit)? = null

    init {
        initialAccounts.forEach(::replace)
    }

    override suspend fun <T> inTransaction(
        block: suspend WalletMutationDatabase.() -> T
    ): T = block(this)

    override suspend fun getMetaAccounts(): List<MetaAccountLocal> {
        return accounts.values.map(MetaAccountLocal::copyAccount)
    }

    override suspend fun getMetaAccount(metaId: Long): MetaAccountLocal? {
        return account(metaId)
    }

    override suspend fun metaAccountExists(metaId: Long): Boolean {
        return metaId in accounts
    }

    override suspend fun hasIdentityConflict(
        metaId: Long,
        substrateAccountId: ByteArray?,
        ethereumAddress: ByteArray?,
        tonPublicKey: ByteArray?
    ): Boolean {
        return accounts.values.any { account ->
            account.id != metaId &&
                (
                    account.substrateAccountId.contentEqualsNullable(substrateAccountId) ||
                        account.ethereumAddress.contentEqualsNullable(ethereumAddress) ||
                        account.tonPublicKey.contentEqualsNullable(tonPublicKey)
                    )
        }
    }

    override suspend fun getChainAccountIds(metaId: Long): List<ByteArray> = emptyList()

    override suspend fun getTonConnectionSecretOwners(
        metaId: Long
    ): List<TonConnectionSecretOwner> = emptyList()

    override suspend fun hasOtherTonConnectionOwner(
        metaId: Long,
        clientId: String
    ): Boolean = false

    override suspend fun hasAssets(metaId: Long): Boolean = false

    override suspend fun getNextPosition(): Int {
        return (accounts.values.maxOfOrNull(MetaAccountLocal::position) ?: -1) + 1
    }

    override suspend fun insertMetaAccount(metaAccount: MetaAccountLocal): Long {
        check(metaAccount.id !in accounts)
        replace(metaAccount)
        return metaAccount.id
    }

    override suspend fun updateMetaAccount(metaAccount: MetaAccountLocal) {
        check(metaAccount.id in accounts)
        updateCalls += 1
        replace(metaAccount)
        afterUpdateCommit?.also { afterUpdateCommit = null }?.invoke()
    }

    override suspend fun selectMetaAccount(metaId: Long) {
        check(metaId in accounts)
        accounts.keys.toList().forEach { id ->
            replace(accounts.getValue(id).copyAccount(selected = id == metaId))
        }
    }

    override suspend fun deleteMetaAccountAndSelectSuccessor(metaId: Long): Boolean {
        val target = accounts.remove(metaId) ?: return false
        deleteCalls += 1
        if (target.isSelected && accounts.isNotEmpty()) {
            val successor = accounts.values.minWith(
                compareBy<MetaAccountLocal>({ it.position }, { it.id })
            )
            accounts.keys.toList().forEach { id ->
                replace(accounts.getValue(id).copyAccount(selected = id == successor.id))
            }
        }
        afterDeleteCommit?.also { afterDeleteCommit = null }?.invoke()
        if (throwAfterDeleteCommit) {
            throwAfterDeleteCommit = false
            throw SimulatedProcessDeath()
        }
        return true
    }

    fun account(metaId: Long): MetaAccountLocal? = accounts[metaId]?.copyAccount()

    fun replace(account: MetaAccountLocal) {
        accounts[account.id] = account.copyAccount()
    }
}

private data class DurablePreferenceState(
    val values: LinkedHashMap<String, String> = linkedMapOf()
)

private enum class ReplaceFault {
    THROW_BEFORE_COMMIT,
    COMMIT_THEN_THROW
}

private class FaultingEncryptedPreferences(
    private val state: DurablePreferenceState
) : EncryptedPreferences {

    var nextReplaceFault: ReplaceFault? = null
    var afterNextReplace: (() -> Unit)? = null
    var failNextCompareAndSwap = false
    val readOverrides = mutableMapOf<String, String?>()
    var preflightCalls = 0
        private set
    private var healthy = true

    override fun putEncryptedString(field: String, value: String) {
        state.values[field] = value
    }

    override fun getDecryptedString(field: String): String? {
        return if (readOverrides.containsKey(field)) {
            readOverrides[field]
        } else {
            state.values[field]
        }
    }

    override fun hasKey(field: String): Boolean {
        return if (readOverrides.containsKey(field)) {
            readOverrides[field] != null
        } else {
            field in state.values
        }
    }

    override fun hasKeyWithPrefix(prefix: String): Boolean {
        return state.values.keys.any { it.startsWith(prefix) }
    }

    override fun keysWithPrefixes(
        prefixes: Set<String>,
        maxResultCount: Int,
        maxKeyBytes: Int,
        maxTotalKeyBytes: Int,
        failOnOversizedMatch: Boolean
    ): Set<String> {
        var totalBytes = 0
        return buildSet {
            state.values.keys.sorted().forEach { key ->
                if (prefixes.none(key::startsWith)) return@forEach
                val bytes = key.toByteArray().size
                if (bytes > maxKeyBytes) {
                    check(!failOnOversizedMatch)
                    return@forEach
                }
                check(size < maxResultCount)
                totalBytes += bytes
                check(totalBytes <= maxTotalKeyBytes)
                add(key)
            }
        }
    }

    override fun removeKey(field: String) {
        state.values.remove(field)
    }

    override fun replaceEncryptedStringsDurably(
        valuesToPut: Map<String, String>,
        keysToRemove: Set<String>
    ) {
        requireDurableStorageHealthy()
        val fault = nextReplaceFault.also { nextReplaceFault = null }
        if (fault == ReplaceFault.THROW_BEFORE_COMMIT) {
            healthy = false
            throw IOException("simulated failure before durable commit")
        }
        state.values.putAll(valuesToPut)
        keysToRemove.forEach(state.values::remove)
        afterNextReplace?.also { afterNextReplace = null }?.invoke()
        if (fault == ReplaceFault.COMMIT_THEN_THROW) {
            healthy = false
            throw IOException("simulated failure after durable commit")
        }
    }

    override fun replaceEncryptedStringsDurablyIfStatesMatch(
        expectedStates: Map<String, EncryptedPreferenceSnapshot?>,
        valuesToPut: Map<String, String>,
        keysToRemove: Set<String>,
        snapshotMoves: List<EncryptedPreferenceSnapshotMove>
    ): Boolean {
        if (failNextCompareAndSwap) {
            failNextCompareAndSwap = false
            return false
        }
        return super<EncryptedPreferences>
            .replaceEncryptedStringsDurablyIfStatesMatch(
                expectedStates,
                valuesToPut,
                keysToRemove,
                snapshotMoves
            )
    }

    override fun quarantineEncryptedStringDurably(
        sourceKey: String,
        quarantineKey: String,
        expectedSnapshot: EncryptedPreferenceSnapshot
    ): Boolean {
        val current = state.values[sourceKey] ?: return false
        if (!expectedSnapshot.matchesUnencryptedStorageValue(current)) return false
        state.values[quarantineKey] = current
        state.values.remove(sourceKey)
        return true
    }

    override fun requireDurableStorageHealthy() {
        preflightCalls += 1
        if (!healthy) {
            throw WalletSecureStorageUnavailableException(
                message = "simulated restart-required durability latch",
                kind = WalletSecureStorageFailureKind.PROCESS_RESTART_REQUIRED
            )
        }
    }

    fun markUnhealthy() {
        healthy = false
    }
}

private class SimulatedProcessDeath : RuntimeException()

private fun wallet(
    metaId: Long,
    selected: Boolean,
    name: String = "wallet-$metaId",
    position: Int = 0
): MetaAccountLocal {
    return MetaAccountLocal(
        substratePublicKey = null,
        substrateCryptoType = null,
        substrateAccountId = null,
        ethereumPublicKey = null,
        ethereumAddress = null,
        tonPublicKey = ByteArray(32) { metaId.toByte() },
        name = name,
        isSelected = selected,
        position = position,
        isBackedUp = false,
        googleBackupAddress = null,
        initialized = true
    ).apply {
        id = metaId
    }
}

private fun MetaAccountLocal.copyAccount(
    name: String = this.name,
    selected: Boolean = isSelected,
    ethereumPublicKey: ByteArray? = this.ethereumPublicKey,
    ethereumAddress: ByteArray? = this.ethereumAddress
): MetaAccountLocal {
    return MetaAccountLocal(
        substratePublicKey = substratePublicKey?.clone(),
        substrateCryptoType = substrateCryptoType,
        substrateAccountId = substrateAccountId?.clone(),
        ethereumPublicKey = ethereumPublicKey?.clone(),
        ethereumAddress = ethereumAddress?.clone(),
        tonPublicKey = tonPublicKey?.clone(),
        name = name,
        isSelected = selected,
        position = position,
        isBackedUp = isBackedUp,
        googleBackupAddress = googleBackupAddress,
        initialized = initialized
    ).apply {
        id = this@copyAccount.id
    }
}

private fun MetaAccountLocal.toJournalImage(): PublicAfterImage {
    return PublicAfterImage(
        name = name,
        substratePublicKeyHex = null,
        substrateAccountIdHex = null,
        substrateCryptoType = null,
        ethereumPublicKeyHex = ethereumPublicKey?.let(Hex::toHexString),
        ethereumAddressHex = ethereumAddress?.let(Hex::toHexString),
        tonPublicKeyHex = tonPublicKey?.let(Hex::toHexString),
        isSelected = isSelected,
        position = position,
        isBackedUp = isBackedUp,
        googleBackupAddress = googleBackupAddress,
        initialized = initialized
    )
}

private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean {
    if (this == null || other == null) return false
    return contentEquals(other)
}

private inline fun <reified T : Throwable> assertFails(block: () -> Unit): T {
    try {
        block()
        throw AssertionError("Expected ${T::class.java.simpleName}")
    } catch (failure: Throwable) {
        if (failure is T) return failure
        throw failure
    }
}
