package jp.co.soramitsu.tonconnect.impl.data

import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.TonConnectStorageKeys
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageFailureKind
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.coredb.dao.TonConnectDao
import jp.co.soramitsu.coredb.model.ConnectionSource
import jp.co.soramitsu.coredb.model.TonConnectionLocal
import jp.co.soramitsu.coredb.model.TonConnectionReadProjection
import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.scale.toHexString
import jp.co.soramitsu.tonconnect.api.model.TonConnectionIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class TonConnectRepositoryDurabilityTest {

    @Test
    fun `codec round trip is canonical and rejects malformed noncanonical and unsupported journals`() {
        val codec = TonConnectMutationJournalCodec()
        val valid = saveJournal(connection())
        val encoded = codec.encode(valid)
        val previous = valid.connection.copy(
            clientId = "2".repeat(32),
            name = "previous row"
        )
        val withPrevious = valid.copy(previousConnection = previous)
        val encodedWithPrevious = codec.encode(withPrevious)

        assertEquals(valid, codec.decode(encoded))
        assertEquals(encoded, codec.encode(codec.decode(encoded)))
        assertEquals(withPrevious, codec.decode(encodedWithPrevious))
        assertEquals(
            encodedWithPrevious,
            codec.encode(codec.decode(encodedWithPrevious))
        )

        val adversarial = listOf(
            "",
            "{}",
            "$encoded ",
            encoded.replace("\"version\":1", "\"version\":2"),
            encoded.replace("\"SAVE\"", "\"UPSERT\""),
            encoded.replace(OPERATION_ID, OPERATION_ID.uppercase()),
            encoded.replace("\"QR\"", "\"BLUETOOTH\""),
            encoded.replaceFirst("{", "{\"unknown\":true,"),
            encoded.replaceFirst(
                "\"operationId\":\"$OPERATION_ID\"",
                "\"operationId\":\"$OPERATION_ID\",\"operationId\":\"$OPERATION_ID\""
            ),
            encodedWithPrevious.replace(
                ",\"previousSource\":\"QR\"",
                ""
            ),
            encoded.replaceFirst(
                "}",
                ",\"replacedClientId\":\"${"3".repeat(32)}\"}"
            ),
            encoded.dropLast(1),
            "x".repeat(32_769)
        )

        adversarial.forEachIndexed { index, candidate ->
            assertFails<TonConnectMutationJournalException>("case $index") {
                codec.decode(candidate)
            }
        }

        assertFails<TonConnectMutationJournalException>("wrong stage") {
            codec.encode(valid.copy(stageKey = "TON_CONNECT_MUTATION_STAGE_V1_deadbeef"))
        }
        assertFails<TonConnectMutationJournalException>("delete save state") {
            codec.encode(
                valid.copy(
                    operation = TonConnectMutationOperation.DELETE,
                    stageKey = valid.stageKey
                )
            )
        }
        assertFails<TonConnectMutationJournalException>("invalid URL") {
            codec.encode(valid.copy(connection = valid.connection.copy(url = " ")))
        }
        assertFails<TonConnectMutationJournalException>("mismatched previous identity") {
            codec.encode(
                withPrevious.copy(
                    previousConnection = previous.copy(
                        url = "https://different.example/connect"
                    )
                )
            )
        }
    }

    @Test
    fun `save stages commits and finalizes the exact repository path`() = runBlocking {
        val state = TonDurablePreferenceState()
        val preferences = TonFaultingEncryptedPreferences(state)
        val dao = FakeTonConnectDao()
        val target = connection()
        val keypair = keypair(1)

        repository(dao, preferences).saveConnection(target, keypair)

        assertEquals(target, dao.connection(TonConnectionIdentity(target)))
        assertEquals(
            encodedKeypair(keypair),
            state.values[activeKey(target)]
        )
        assertFalse(state.values.containsKey(TonConnectMutationJournalCodec.JOURNAL_KEY))
        assertTrue(state.values.keys.none { it.startsWith(STAGE_KEY_PREFIX) })
        assertEquals(1, dao.insertCalls)
    }

    @Test
    fun `stage commit then throw blocks same process and restart replays exact save`() {
        val state = TonDurablePreferenceState()
        val preferences = TonFaultingEncryptedPreferences(state)
        val dao = FakeTonConnectDao()
        val target = connection()
        preferences.nextReplaceFault = TonReplaceFault.COMMIT_THEN_THROW

        assertFails<IOException>("ambiguous stage") {
            runBlocking {
                repository(dao, preferences).saveConnection(target, keypair(2))
            }
        }

        assertNull(dao.connection(TonConnectionIdentity(target)))
        assertTrue(state.values.containsKey(TonConnectMutationJournalCodec.JOURNAL_KEY))
        assertFails<WalletSecureStorageUnavailableException>("same-process retry") {
            runBlocking { repository(dao, preferences).reconcilePendingMutation() }
        }
        assertEquals(0, dao.insertCalls)

        runBlocking {
            repository(
                dao,
                TonFaultingEncryptedPreferences(state)
            ).reconcilePendingMutation()
        }
        assertEquals(target, dao.connection(TonConnectionIdentity(target)))
        assertFalse(state.values.containsKey(TonConnectMutationJournalCodec.JOURNAL_KEY))
        assertEquals(1, dao.insertCalls)
    }

    @Test
    fun `stage throw before commit never reaches Room after restart`() {
        val state = TonDurablePreferenceState()
        val preferences = TonFaultingEncryptedPreferences(state)
        val dao = FakeTonConnectDao()
        preferences.nextReplaceFault = TonReplaceFault.THROW_BEFORE_COMMIT

        assertFails<IOException>("stage before commit") {
            runBlocking {
                repository(dao, preferences).saveConnection(connection(), keypair(3))
            }
        }

        assertTrue(state.values.isEmpty())
        runBlocking {
            repository(
                dao,
                TonFaultingEncryptedPreferences(state)
            ).reconcilePendingMutation()
        }
        assertEquals(0, dao.insertCalls)
        assertNull(dao.connection(TonConnectionIdentity(connection())))
    }

    @Test
    fun `staged journal is preflighted before journal read and Room mutation`() {
        val state = TonDurablePreferenceState()
        val preferences = TonFaultingEncryptedPreferences(state)
        val dao = FakeTonConnectDao()
        stageSave(state, connection(), keypair(4))
        preferences.markUnhealthy()

        assertFails<WalletSecureStorageUnavailableException>("preflight") {
            runBlocking { repository(dao, preferences).reconcilePendingMutation() }
        }

        assertEquals(0, dao.insertCalls)
        assertNull(dao.connection(TonConnectionIdentity(connection())))
        assertTrue(preferences.preflightCalls > 0)
    }

    @Test
    fun `journal-staged crash resumes before Room and finalizes once`() {
        val state = TonDurablePreferenceState()
        val preferences = TonFaultingEncryptedPreferences(state)
        val dao = FakeTonConnectDao()
        var crash = true
        val crashing = repository(dao, preferences) { boundary ->
            if (boundary == TonConnectMutationBoundary.JOURNAL_STAGED && crash) {
                crash = false
                throw SimulatedTonProcessDeath()
            }
        }

        assertFails<SimulatedTonProcessDeath>("staged crash") {
            runBlocking { crashing.saveConnection(connection(), keypair(5)) }
        }
        assertEquals(0, dao.insertCalls)
        assertTrue(state.values.containsKey(TonConnectMutationJournalCodec.JOURNAL_KEY))

        runBlocking {
            repository(dao, preferences).reconcilePendingMutation()
            repository(dao, preferences).reconcilePendingMutation()
        }
        assertEquals(1, dao.insertCalls)
        assertFalse(state.values.containsKey(TonConnectMutationJournalCodec.JOURNAL_KEY))
    }

    @Test
    fun `Room-committed save crash replays idempotently and finalizes`() {
        val state = TonDurablePreferenceState()
        val preferences = TonFaultingEncryptedPreferences(state)
        val dao = FakeTonConnectDao()
        var crash = true
        val crashing = repository(dao, preferences) { boundary ->
            if (boundary == TonConnectMutationBoundary.DATABASE_MUTATED && crash) {
                crash = false
                throw SimulatedTonProcessDeath()
            }
        }

        assertFails<SimulatedTonProcessDeath>("Room crash") {
            runBlocking { crashing.saveConnection(connection(), keypair(6)) }
        }
        assertNotNull(dao.connection(TonConnectionIdentity(connection())))
        assertTrue(state.values.containsKey(TonConnectMutationJournalCodec.JOURNAL_KEY))

        runBlocking {
            repository(dao, preferences).reconcilePendingMutation()
            repository(dao, preferences).reconcilePendingMutation()
        }
        assertEquals(connection(), dao.connection(TonConnectionIdentity(connection())))
        assertFalse(state.values.containsKey(TonConnectMutationJournalCodec.JOURNAL_KEY))
        assertEquals(1, dao.insertCalls)
    }

    @Test
    fun `malformed staged keypair fails closed before Room and keeps recovery evidence`() {
        val state = TonDurablePreferenceState()
        val preferences = TonFaultingEncryptedPreferences(state)
        val dao = FakeTonConnectDao()
        val journal = saveJournal(connection())
        state.values[TonConnectMutationJournalCodec.JOURNAL_KEY] =
            TonConnectMutationJournalCodec().encode(journal)
        state.values[requireNotNull(journal.stageKey)] = "0x00"

        assertFails<IllegalArgumentException>("malformed stage") {
            runBlocking { repository(dao, preferences).reconcilePendingMutation() }
        }

        assertEquals(0, dao.insertCalls)
        assertNull(dao.connection(TonConnectionIdentity(connection())))
        assertTrue(state.values.containsKey(TonConnectMutationJournalCodec.JOURNAL_KEY))
        assertTrue(state.values.containsKey(requireNotNull(journal.stageKey)))
    }

    @Test
    fun `save replay preserves a newer client transition and leaves journal pending`() {
        val state = TonDurablePreferenceState()
        val preferences = TonFaultingEncryptedPreferences(state)
        val dao = FakeTonConnectDao()
        val previous = connection(clientId = "a".repeat(32), name = "previous")
        val target = connection(clientId = "b".repeat(32), name = "staged")
        val newer = connection(clientId = "c".repeat(32), name = "newer")
        val journal = saveJournal(target).copy(previousConnection = previous)
        stageSave(state, journal, keypair(13))
        dao.put(newer)

        assertFails<TonConnectMutationJournalException>("newer save") {
            runBlocking { repository(dao, preferences).reconcilePendingMutation() }
        }

        assertEquals(newer, dao.connection(TonConnectionIdentity(newer)))
        assertEquals(0, dao.insertCalls)
        assertTrue(state.values.containsKey(TonConnectMutationJournalCodec.JOURNAL_KEY))
    }

    @Test
    fun `save replay preserves newer same-client metadata and scoped secret`() {
        val state = TonDurablePreferenceState()
        val preferences = TonFaultingEncryptedPreferences(state)
        val dao = FakeTonConnectDao()
        val previous = connection(clientId = "a".repeat(32), name = "previous")
        val target = connection(clientId = "b".repeat(32), name = "staged")
        val newer = previous.copy(name = "newer metadata")
        val journal = saveJournal(target).copy(previousConnection = previous)
        val newerSecret = encodedKeypair(keypair(15))
        stageSave(state, journal, keypair(16))
        state.values[activeKey(newer)] = newerSecret
        dao.put(newer)

        assertFails<TonConnectMutationJournalException>("same-client newer save") {
            runBlocking { repository(dao, preferences).reconcilePendingMutation() }
        }

        assertEquals(newer, dao.connection(TonConnectionIdentity(newer)))
        assertEquals(0, dao.insertCalls)
        assertEquals(newerSecret, state.values[activeKey(newer)])
        assertTrue(state.values.containsKey(TonConnectMutationJournalCodec.JOURNAL_KEY))
        assertTrue(state.values.containsKey(requireNotNull(journal.stageKey)))
    }

    @Test
    fun `delete replay preserves a newer client row and its scoped secret`() {
        val state = TonDurablePreferenceState()
        val preferences = TonFaultingEncryptedPreferences(state)
        val dao = FakeTonConnectDao()
        val deletedIntent = connection(clientId = "d".repeat(32), name = "old")
        val newer = connection(clientId = "e".repeat(32), name = "newer")
        val journal = TonConnectMutationJournal(
            operation = TonConnectMutationOperation.DELETE,
            operationId = OPERATION_ID,
            connection = deletedIntent,
            stageKey = null,
            previousConnection = null
        )
        state.values[TonConnectMutationJournalCodec.JOURNAL_KEY] =
            TonConnectMutationJournalCodec().encode(journal)
        state.values[activeKey(newer)] = encodedKeypair(keypair(14))
        dao.put(newer)

        assertFails<TonConnectMutationJournalException>("newer delete") {
            runBlocking { repository(dao, preferences).reconcilePendingMutation() }
        }

        assertEquals(newer, dao.connection(TonConnectionIdentity(newer)))
        assertEquals(0, dao.deleteCalls)
        assertTrue(state.values.containsKey(activeKey(newer)))
        assertTrue(state.values.containsKey(TonConnectMutationJournalCodec.JOURNAL_KEY))
    }

    @Test
    fun `delete replay preserves newer same-client metadata and scoped secret`() {
        val state = TonDurablePreferenceState()
        val preferences = TonFaultingEncryptedPreferences(state)
        val dao = FakeTonConnectDao()
        val deletedIntent = connection(clientId = "d".repeat(32), name = "old")
        val newer = deletedIntent.copy(name = "newer metadata")
        val journal = TonConnectMutationJournal(
            operation = TonConnectMutationOperation.DELETE,
            operationId = OPERATION_ID,
            connection = deletedIntent,
            stageKey = null,
            previousConnection = null
        )
        val newerSecret = encodedKeypair(keypair(17))
        state.values[TonConnectMutationJournalCodec.JOURNAL_KEY] =
            TonConnectMutationJournalCodec().encode(journal)
        state.values[activeKey(newer)] = newerSecret
        dao.put(newer)

        assertFails<TonConnectMutationJournalException>("same-client newer delete") {
            runBlocking { repository(dao, preferences).reconcilePendingMutation() }
        }

        assertEquals(newer, dao.connection(TonConnectionIdentity(newer)))
        assertEquals(0, dao.deleteCalls)
        assertEquals(newerSecret, state.values[activeKey(newer)])
        assertTrue(state.values.containsKey(TonConnectMutationJournalCodec.JOURNAL_KEY))
    }

    @Test
    fun `finalization throw before commit retains journal and restart repairs save`() {
        val state = TonDurablePreferenceState()
        val preferences = TonFaultingEncryptedPreferences(state)
        val dao = FakeTonConnectDao()
        val repo = repository(dao, preferences) { boundary ->
            if (boundary == TonConnectMutationBoundary.DATABASE_MUTATED) {
                preferences.nextReplaceFault = TonReplaceFault.THROW_BEFORE_COMMIT
            }
        }

        assertFails<IOException>("finalization before commit") {
            runBlocking { repo.saveConnection(connection(), keypair(7)) }
        }
        assertNotNull(dao.connection(TonConnectionIdentity(connection())))
        assertTrue(state.values.containsKey(TonConnectMutationJournalCodec.JOURNAL_KEY))
        assertFails<WalletSecureStorageUnavailableException>("latched retry") {
            runBlocking { repo.reconcilePendingMutation() }
        }

        runBlocking {
            repository(
                dao,
                TonFaultingEncryptedPreferences(state)
            ).reconcilePendingMutation()
        }
        assertFalse(state.values.containsKey(TonConnectMutationJournalCodec.JOURNAL_KEY))
        assertNotNull(state.values[activeKey(connection())])
    }

    @Test
    fun `finalization commit then throw does not duplicate or lose save after restart`() {
        val state = TonDurablePreferenceState()
        val preferences = TonFaultingEncryptedPreferences(state)
        val dao = FakeTonConnectDao()
        val repo = repository(dao, preferences) { boundary ->
            if (boundary == TonConnectMutationBoundary.DATABASE_MUTATED) {
                preferences.nextReplaceFault = TonReplaceFault.COMMIT_THEN_THROW
            }
        }

        assertFails<IOException>("finalization after commit") {
            runBlocking { repo.saveConnection(connection(), keypair(8)) }
        }
        assertFalse(state.values.containsKey(TonConnectMutationJournalCodec.JOURNAL_KEY))
        assertNotNull(state.values[activeKey(connection())])

        runBlocking {
            repository(
                dao,
                TonFaultingEncryptedPreferences(state)
            ).reconcilePendingMutation()
        }
        assertEquals(1, dao.insertCalls)
        assertEquals(connection(), dao.connection(TonConnectionIdentity(connection())))
    }

    @Test
    fun `Room-committed delete crash replays idempotently and removes exact secret`() {
        val state = TonDurablePreferenceState()
        val preferences = TonFaultingEncryptedPreferences(state)
        val dao = FakeTonConnectDao()
        val target = connection()
        dao.put(target)
        state.values[activeKey(target)] = encodedKeypair(keypair(9))
        var crash = true
        val repo = repository(dao, preferences) { boundary ->
            if (boundary == TonConnectMutationBoundary.DATABASE_MUTATED && crash) {
                crash = false
                throw SimulatedTonProcessDeath()
            }
        }

        assertFails<SimulatedTonProcessDeath>("delete Room crash") {
            runBlocking { repo.deleteConnection(TonConnectionIdentity(target)) }
        }
        assertNull(dao.connection(TonConnectionIdentity(target)))
        assertTrue(state.values.containsKey(TonConnectMutationJournalCodec.JOURNAL_KEY))
        assertTrue(state.values.containsKey(activeKey(target)))

        runBlocking {
            repository(dao, preferences).reconcilePendingMutation()
            repository(dao, preferences).reconcilePendingMutation()
        }
        assertNull(dao.connection(TonConnectionIdentity(target)))
        assertFalse(state.values.containsKey(activeKey(target)))
        assertFalse(state.values.containsKey(TonConnectMutationJournalCodec.JOURNAL_KEY))
    }

    @Test
    fun `post-commit journal read divergence cannot reach Room and disk replays on restart`() {
        val state = TonDurablePreferenceState()
        val preferences = TonFaultingEncryptedPreferences(state)
        val dao = FakeTonConnectDao()
        preferences.afterNextReplace = {
            preferences.readOverrides[TonConnectMutationJournalCodec.JOURNAL_KEY] = "{}"
        }

        assertFails<TonConnectMutationJournalException>("divergent read") {
            runBlocking {
                repository(dao, preferences).saveConnection(connection(), keypair(10))
            }
        }
        assertEquals(0, dao.insertCalls)
        assertTrue(state.values.containsKey(TonConnectMutationJournalCodec.JOURNAL_KEY))

        runBlocking {
            repository(
                dao,
                TonFaultingEncryptedPreferences(state)
            ).reconcilePendingMutation()
        }
        assertNotNull(dao.connection(TonConnectionIdentity(connection())))
        assertFalse(state.values.containsKey(TonConnectMutationJournalCodec.JOURNAL_KEY))
    }

    @Test
    fun `concurrent saves serialize journals and leave only the newest exact row and key`() = runBlocking {
        val state = TonDurablePreferenceState()
        val preferences = TonFaultingEncryptedPreferences(state)
        val dao = FakeTonConnectDao()
        val firstConnection = connection(clientId = "a".repeat(32), name = "first")
        val secondConnection = connection(clientId = "b".repeat(32), name = "second")
        val firstKeypair = keypair(11)
        val secondKeypair = keypair(12)
        val first = repository(dao, preferences)
        val second = repository(dao, preferences)

        listOf(
            async(Dispatchers.Default) {
                first.saveConnection(firstConnection, firstKeypair)
            },
            async(Dispatchers.Default) {
                second.saveConnection(secondConnection, secondKeypair)
            }
        ).awaitAll()

        val stored = dao.connection(TonConnectionIdentity(firstConnection))
        assertTrue(stored == firstConnection || stored == secondConnection)
        val expectedKeypair = if (stored == firstConnection) {
            firstKeypair
        } else {
            secondKeypair
        }
        assertEquals(encodedKeypair(expectedKeypair), state.values[activeKey(firstConnection)])
        assertFalse(state.values.containsKey(TonConnectMutationJournalCodec.JOURNAL_KEY))
        assertTrue(state.values.keys.none { it.startsWith(STAGE_KEY_PREFIX) })
        assertEquals(2, dao.insertCalls)
    }

    private fun repository(
        dao: FakeTonConnectDao,
        preferences: TonFaultingEncryptedPreferences,
        observer: (TonConnectMutationBoundary) -> Unit = {}
    ): TonConnectRepositoryImpl {
        return TonConnectRepositoryImpl(
            tonConnectDao = dao,
            encryptedPreferences = preferences,
            tonPublicKeyDeriver = ByteArray::clone,
            operationIdFactory = { OPERATION_ID },
            mutationBoundaryObserver = observer
        )
    }

    private companion object {
        const val OPERATION_ID = "0123456789abcdef0123456789abcdef"
        const val STAGE_KEY_PREFIX = "TON_CONNECT_MUTATION_STAGE_V1_"
    }
}

private class FakeTonConnectDao : TonConnectDao() {

    private val rows = linkedMapOf<TonIdentityKey, TonConnectionLocal>()
    private val observed = MutableStateFlow<List<TonConnectionReadProjection>>(emptyList())

    var insertCalls = 0
        private set
    var deleteCalls = 0
        private set

    override suspend fun insertTonConnection(connection: TonConnectionLocal) {
        insertCalls += 1
        put(connection)
    }

    override fun observeTonConnections(
        metaId: Long,
        source: ConnectionSource
    ): Flow<List<TonConnectionReadProjection>> = observed

    override suspend fun getTonConnections(metaId: Long, source: ConnectionSource): List<TonConnectionReadProjection> {
        return rows.values
            .filter { it.metaId == metaId && it.source == source }
            .map(TonConnectionLocal::toProjection)
    }

    override suspend fun getTonConnection(
        metaId: Long,
        url: String,
        source: ConnectionSource
    ): TonConnectionReadProjection? {
        return rows[TonIdentityKey(metaId, url, source)]?.toProjection()
    }

    override suspend fun getTonConnectionsByClientId(clientId: String): List<TonConnectionReadProjection> {
        return rows.values.filter { it.clientId == clientId }
            .map(TonConnectionLocal::toProjection)
    }

    override suspend fun hasTonConnectionsByClientId(clientId: String): Boolean {
        return rows.values.any { it.clientId == clientId }
    }

    override suspend fun deleteTonConnection(
        metaId: Long,
        url: String,
        source: ConnectionSource
    ) {
        deleteCalls += 1
        rows.remove(TonIdentityKey(metaId, url, source))
        publish()
    }

    fun put(connection: TonConnectionLocal) {
        rows[TonIdentityKey(connection.metaId, connection.url, connection.source)] =
            connection.copy()
        publish()
    }

    fun connection(identity: TonConnectionIdentity): TonConnectionLocal? {
        return rows[
            TonIdentityKey(identity.metaId, identity.url, identity.source)
        ]?.copy()
    }

    private fun publish() {
        observed.value = rows.values.map(TonConnectionLocal::toProjection)
    }
}

private data class TonIdentityKey(
    val metaId: Long,
    val url: String,
    val source: ConnectionSource
)

private data class TonDurablePreferenceState(
    val values: LinkedHashMap<String, String> = linkedMapOf()
)

private enum class TonReplaceFault {
    THROW_BEFORE_COMMIT,
    COMMIT_THEN_THROW
}

private class TonFaultingEncryptedPreferences(
    private val state: TonDurablePreferenceState
) : EncryptedPreferences {

    var nextReplaceFault: TonReplaceFault? = null
    var afterNextReplace: (() -> Unit)? = null
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
        return state.values.keys.filterTo(linkedSetOf()) { key ->
            prefixes.any(key::startsWith)
        }
    }

    override fun removeKey(field: String) {
        state.values.remove(field)
    }

    @Synchronized
    override fun replaceEncryptedStringsDurably(valuesToPut: Map<String, String>, keysToRemove: Set<String>) {
        requireDurableStorageHealthy()
        val fault = nextReplaceFault.also { nextReplaceFault = null }
        if (fault == TonReplaceFault.THROW_BEFORE_COMMIT) {
            healthy = false
            throw IOException("simulated failure before durable commit")
        }
        state.values.putAll(valuesToPut)
        keysToRemove.forEach(state.values::remove)
        afterNextReplace?.also { afterNextReplace = null }?.invoke()
        if (fault == TonReplaceFault.COMMIT_THEN_THROW) {
            healthy = false
            throw IOException("simulated failure after durable commit")
        }
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

private class SimulatedTonProcessDeath : RuntimeException()

private fun connection(clientId: String = "1".repeat(32), name: String = "Fearless dapp"): TonConnectionLocal {
    return TonConnectionLocal(
        metaId = 77L,
        clientId = clientId,
        name = name,
        icon = "https://dapp.example/icon.png",
        url = "https://dapp.example/connect",
        source = ConnectionSource.QR
    )
}

private fun saveJournal(connection: TonConnectionLocal): TonConnectMutationJournal {
    val codec = TonConnectMutationJournalCodec()
    val operationId = "0123456789abcdef0123456789abcdef"
    return TonConnectMutationJournal(
        operation = TonConnectMutationOperation.SAVE,
        operationId = operationId,
        connection = connection,
        stageKey = codec.stageKey(operationId),
        previousConnection = null
    )
}

private fun stageSave(
    state: TonDurablePreferenceState,
    connection: TonConnectionLocal,
    keypair: Keypair
) {
    stageSave(state, saveJournal(connection), keypair)
}

private fun stageSave(
    state: TonDurablePreferenceState,
    journal: TonConnectMutationJournal,
    keypair: Keypair
) {
    state.values[TonConnectMutationJournalCodec.JOURNAL_KEY] =
        TonConnectMutationJournalCodec().encode(journal)
    state.values[requireNotNull(journal.stageKey)] = encodedKeypair(keypair)
}

private fun keypair(marker: Int): Keypair {
    val key = ByteArray(32) { marker.toByte() }
    return BaseKeypair(privateKey = key, publicKey = key.clone())
}

private fun encodedKeypair(keypair: Keypair): String {
    return KeyPairSchema { schema ->
        schema[PublicKey] = keypair.publicKey
        schema[PrivateKey] = keypair.privateKey
        schema[Nonce] = null
    }.toHexString()
}

private fun activeKey(connection: TonConnectionLocal): String {
    return TonConnectStorageKeys.scoped(
        metaId = connection.metaId,
        url = connection.url,
        source = connection.source.name
    )
}

private fun TonConnectionLocal.toProjection(): TonConnectionReadProjection {
    return TonConnectionReadProjection(
        metaId = metaId,
        clientId = clientId,
        name = name,
        icon = icon,
        url = url,
        source = source.name,
        clientIdUtf8Bytes = clientId.toByteArray().size.toLong(),
        nameUtf8Bytes = name.toByteArray().size.toLong(),
        iconUtf8Bytes = icon.toByteArray().size.toLong(),
        urlUtf8Bytes = url.toByteArray().size.toLong(),
        sourceUtf8Bytes = source.name.toByteArray().size.toLong(),
        rowWithinBounds = true
    )
}

private inline fun <reified T : Throwable> assertFails(label: String, block: () -> Unit): T {
    try {
        block()
        throw AssertionError("$label: expected ${T::class.java.simpleName}")
    } catch (failure: Throwable) {
        if (failure is T) return failure
        throw AssertionError(
            "$label: expected ${T::class.java.simpleName}, got ${failure::class.java.simpleName}",
            failure
        )
    }
}
