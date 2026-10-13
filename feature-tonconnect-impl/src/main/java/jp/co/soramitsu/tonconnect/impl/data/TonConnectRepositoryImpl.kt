package jp.co.soramitsu.tonconnect.impl.data

import jp.co.soramitsu.common.data.secrets.WalletSecretScaleCorruptionException
import jp.co.soramitsu.common.data.secrets.WalletSecretScalePreflight
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.TonConnectStorageKeys
import jp.co.soramitsu.common.data.storage.encrypt.WalletCrossStoreMutationMutex
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.common.data.storage.encrypt.quarantineEncryptedStringSnapshotDurably
import jp.co.soramitsu.common.utils.invoke
import jp.co.soramitsu.coredb.dao.TonConnectDao
import jp.co.soramitsu.coredb.model.ConnectionSource
import jp.co.soramitsu.coredb.model.TonConnectionLocal
import jp.co.soramitsu.coredb.model.TonConnectionReadProjection
import jp.co.soramitsu.fearless_utils.encrypt.xsalsa20poly1305.Keys
import jp.co.soramitsu.fearless_utils.scale.toHexString
import jp.co.soramitsu.tonconnect.api.domain.TonConnectRepository
import jp.co.soramitsu.tonconnect.api.model.TonConnectionIdentity
import jp.co.soramitsu.tonconnect.api.model.TonDappConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.UUID
import jp.co.soramitsu.common.data.Keypair as createKeypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair as FearlessKeypair

@Suppress("LargeClass")
internal class TonConnectRepositoryImpl(
    private val tonConnectDao: TonConnectDao,
    private val encryptedPreferences: EncryptedPreferences,
    private val tonPublicKeyDeriver: (ByteArray) -> ByteArray = {
        Keys.generatePublicKey(it)
    },
    private val operationIdFactory: () -> String = {
        UUID.randomUUID().toString().replace("-", "")
    },
    private val mutationBoundaryObserver: (TonConnectMutationBoundary) -> Unit = {}
) : TonConnectRepository {

    private val mutex = WalletCrossStoreMutationMutex.instance
    private val journalCodec = TonConnectMutationJournalCodec()

    override suspend fun reconcilePendingMutation() = mutex.withLock {
        replayPendingMutationLocked()
    }

    override suspend fun saveConnection(connection: TonConnectionLocal, keypair: FearlessKeypair) = mutex.withLock {
        replayPendingMutationLocked()
        journalCodec.validateConnection(connection)
        requireValidKeypair(
            privateKey = keypair.privateKey,
            publicKey = keypair.publicKey,
            nonce = null
        )
        val encodedKeypair = encodeKeypair(keypair)
        val identity = TonConnectionIdentity(connection)
        identity.scopedStorageKey()

        val previous = getExactConnectionLocked(identity)

        val operationId = operationIdFactory()
        val stageKey = journalCodec.stageKey(operationId)
        val journal = TonConnectMutationJournal(
            operation = TonConnectMutationOperation.SAVE,
            operationId = operationId,
            connection = connection,
            stageKey = stageKey,
            previousConnection = previous
        )
        val encodedJournal = journalCodec.encode(journal)
        requireDurableStorageHealthy()
        check(!encryptedPreferences.hasKey(TonConnectMutationJournalCodec.JOURNAL_KEY)) {
            "A TON Connect mutation journal is already active"
        }
        check(!encryptedPreferences.hasKey(stageKey)) {
            "A TON Connect mutation stage already exists"
        }

        encryptedPreferences.requireDurableStorageHealthy()
        encryptedPreferences.replaceEncryptedStringsDurably(
            valuesToPut = mapOf(
                TonConnectMutationJournalCodec.JOURNAL_KEY to encodedJournal,
                stageKey to encodedKeypair
            ),
            keysToRemove = emptySet()
        )
        mutationBoundaryObserver(TonConnectMutationBoundary.JOURNAL_STAGED)

        val committedJournal = loadPendingMutationLocked()
            ?: throw TonConnectMutationJournalException(
                "The committed TON Connect save journal is missing"
            )
        if (committedJournal != journal) {
            throw TonConnectMutationJournalException(
                "The committed TON Connect save journal differs from its intent"
            )
        }
        replayMutationLocked(committedJournal)
    }

    override suspend fun getConnectionKeypair(identity: TonConnectionIdentity): FearlessKeypair? = mutex.withLock {
        replayPendingMutationLocked()
        val connection = getExactConnectionLocked(identity) ?: return@withLock null
        readKeypairLocked(connection)
    }

    override fun observeConnections(metaId: Long, source: ConnectionSource): Flow<List<TonDappConnection>> {
        require(metaId > 0L) {
            "TON Connect observations require a positive wallet id"
        }
        return flow {
            mutex.withLock {
                replayPendingMutationLocked()
            }
            emitAll(
                tonConnectDao.observeTonConnections(metaId, source).map {
                    mutex.withLock {
                        replayPendingMutationLocked()
                        verifiedConnectionsLocked(metaId, source)
                    }
                }
            )
        }
    }

    override suspend fun getConnections(metaId: Long, source: ConnectionSource): List<TonDappConnection> {
        return mutex.withLock {
            require(metaId > 0L) {
                "TON Connect lookups require a positive wallet id"
            }
            replayPendingMutationLocked()
            verifiedConnectionsLocked(metaId, source)
        }
    }

    override suspend fun deleteConnection(identity: TonConnectionIdentity) = mutex.withLock {
        replayPendingMutationLocked()
        identity.scopedStorageKey()
        val connection = getExactConnectionLocked(identity) ?: return@withLock
        journalCodec.validateConnection(connection)

        val operationId = operationIdFactory()
        val journal = TonConnectMutationJournal(
            operation = TonConnectMutationOperation.DELETE,
            operationId = operationId,
            connection = connection,
            stageKey = null,
            previousConnection = null
        )
        val encodedJournal = journalCodec.encode(journal)
        requireDurableStorageHealthy()
        check(!encryptedPreferences.hasKey(TonConnectMutationJournalCodec.JOURNAL_KEY)) {
            "A TON Connect mutation journal is already active"
        }

        encryptedPreferences.requireDurableStorageHealthy()
        encryptedPreferences.replaceEncryptedStringsDurably(
            valuesToPut = mapOf(
                TonConnectMutationJournalCodec.JOURNAL_KEY to encodedJournal
            ),
            keysToRemove = emptySet()
        )
        mutationBoundaryObserver(TonConnectMutationBoundary.JOURNAL_STAGED)

        val committedJournal = loadPendingMutationLocked()
            ?: throw TonConnectMutationJournalException(
                "The committed TON Connect delete journal is missing"
            )
        if (committedJournal != journal) {
            throw TonConnectMutationJournalException(
                "The committed TON Connect delete journal differs from its intent"
            )
        }
        replayMutationLocked(committedJournal)
    }

    override suspend fun getConnection(identity: TonConnectionIdentity): TonConnectionLocal? = mutex.withLock {
        replayPendingMutationLocked()
        val connection = getExactConnectionLocked(identity) ?: return@withLock null
        connection.takeIf { readKeypairLocked(it) != null }
    }

    private suspend fun replayPendingMutationLocked() {
        requireDurableStorageHealthy()
        loadPendingMutationLocked()?.let { replayMutationLocked(it) }
    }

    private fun loadPendingMutationLocked(): TonConnectMutationJournal? {
        requireDurableStorageHealthy()
        val journalKey = TonConnectMutationJournalCodec.JOURNAL_KEY
        if (!encryptedPreferences.hasKey(journalKey)) return null
        val encoded = encryptedPreferences.getDecryptedString(journalKey)
            ?: throw TonConnectMutationJournalException(
                "The TON Connect mutation journal is unreadable"
            )
        return journalCodec.decode(encoded)
    }

    private suspend fun replayMutationLocked(journal: TonConnectMutationJournal) {
        requireDurableStorageHealthy()
        journalCodec.validate(journal)
        when (journal.operation) {
            TonConnectMutationOperation.SAVE -> replaySaveLocked(journal)
            TonConnectMutationOperation.DELETE -> replayDeleteLocked(journal)
        }
    }

    private suspend fun replaySaveLocked(journal: TonConnectMutationJournal) {
        val stageKey = requireSaveStageKey(journal)
        val stagedPayload = readSaveStagePayload(stageKey)
        decodeAndValidateKeypair(stagedPayload)

        val identity = TonConnectionIdentity(journal.connection)
        val current = getExactConnectionLocked(identity)
        when {
            current == journal.connection -> Unit
            current == journal.previousConnection -> {
                requireDurableStorageHealthy()
                tonConnectDao.insertTonConnection(journal.connection)
            }
            else -> {
                throw TonConnectMutationJournalException(
                    "The TON Connect database row changed after the save was staged"
                )
            }
        }
        requireDatabaseSaveCommitted(journal.connection)
        mutationBoundaryObserver(TonConnectMutationBoundary.DATABASE_MUTATED)

        val activeKey = identity.scopedStorageKey()
        val keysToRemove = linkedSetOf(
            stageKey,
            TonConnectMutationJournalCodec.JOURNAL_KEY,
            WalletSecretQuarantine.keyFor(activeKey)
        )
        journal.previousConnection
            ?.clientId
            ?.takeIf { it != journal.connection.clientId }
            ?.let { replacedClientId ->
                if (!hasLegacyOwners(replacedClientId)) {
                    val legacyKey = TonConnectStorageKeys.legacy(replacedClientId)
                    keysToRemove += legacyKey
                    keysToRemove += WalletSecretQuarantine.keyFor(legacyKey)
                }
            }

        encryptedPreferences.requireDurableStorageHealthy()
        encryptedPreferences.replaceEncryptedStringsDurably(
            valuesToPut = mapOf(activeKey to stagedPayload),
            keysToRemove = keysToRemove
        )
        verifySaveFinalized(activeKey, stagedPayload, stageKey)
        mutationBoundaryObserver(TonConnectMutationBoundary.PREFERENCES_FINALIZED)
    }

    private fun requireSaveStageKey(journal: TonConnectMutationJournal): String {
        return journal.stageKey
            ?: throw TonConnectMutationJournalException(
                "The TON Connect save stage is missing"
            )
    }

    private fun readSaveStagePayload(stageKey: String): String {
        requireDurableStorageHealthy()
        if (!encryptedPreferences.hasKey(stageKey)) {
            throw TonConnectMutationJournalException(
                "The TON Connect save stage is absent"
            )
        }
        return encryptedPreferences.getDecryptedString(stageKey)
            ?: throw TonConnectMutationJournalException(
                "The TON Connect save stage is unreadable"
            )
    }

    private suspend fun requireDatabaseSaveCommitted(connection: TonConnectionLocal) {
        if (getExactConnectionLocked(TonConnectionIdentity(connection)) != connection) {
            throw TonConnectMutationJournalException(
                "The TON Connect database save did not commit its intended row"
            )
        }
    }

    private suspend fun replayDeleteLocked(journal: TonConnectMutationJournal) {
        val identity = TonConnectionIdentity(journal.connection)
        val current = getExactConnectionLocked(identity)
        if (current != null) {
            if (current != journal.connection) {
                throw TonConnectMutationJournalException(
                    "The TON Connect database row changed after deletion was staged"
                )
            }
            requireDurableStorageHealthy()
            tonConnectDao.deleteTonConnection(
                metaId = identity.metaId,
                url = identity.url,
                source = identity.source
            )
        }
        if (getExactConnectionLocked(identity) != null) {
            throw TonConnectMutationJournalException(
                "The TON Connect database deletion did not remove its exact row"
            )
        }
        mutationBoundaryObserver(TonConnectMutationBoundary.DATABASE_MUTATED)

        val activeKey = identity.scopedStorageKey()
        val keysToRemove = linkedSetOf(
            activeKey,
            WalletSecretQuarantine.keyFor(activeKey),
            TonConnectMutationJournalCodec.JOURNAL_KEY
        )
        if (!hasLegacyOwners(journal.connection.clientId)) {
            val legacyKey = TonConnectStorageKeys.legacy(
                journal.connection.clientId
            )
            keysToRemove += legacyKey
            keysToRemove += WalletSecretQuarantine.keyFor(legacyKey)
        }

        encryptedPreferences.requireDurableStorageHealthy()
        encryptedPreferences.replaceEncryptedStringsDurably(
            valuesToPut = emptyMap(),
            keysToRemove = keysToRemove
        )
        verifyDeleteFinalized(keysToRemove)
        mutationBoundaryObserver(TonConnectMutationBoundary.PREFERENCES_FINALIZED)
    }

    private suspend fun verifiedConnectionsLocked(metaId: Long, source: ConnectionSource): List<TonDappConnection> {
        val projections = tonConnectDao.getTonConnections(metaId, source)
        if (projections.size > MAX_CONNECTIONS_PER_WALLET_SOURCE) {
            throw TonConnectMutationJournalException(
                "The TON Connect connection list exceeds its safe bound"
            )
        }
        return projections.mapNotNull { projection ->
            val connection = requireValidProjection(projection)
            if (readKeypairLocked(connection) == null) {
                null
            } else {
                TonDappConnection(connection)
            }
        }
    }

    private suspend fun getExactConnectionLocked(identity: TonConnectionIdentity): TonConnectionLocal? {
        identity.scopedStorageKey()
        return tonConnectDao.getTonConnection(
            metaId = identity.metaId,
            url = identity.url,
            source = identity.source
        )?.let(::requireValidProjection)
    }

    private suspend fun readKeypairLocked(connection: TonConnectionLocal): FearlessKeypair? {
        journalCodec.validateConnection(connection)
        val identity = TonConnectionIdentity(connection)
        val activeKey = identity.scopedStorageKey()
        val quarantineKey = WalletSecretQuarantine.keyFor(activeKey)
        if (encryptedPreferences.hasKey(quarantineKey)) return null

        if (encryptedPreferences.hasKey(activeKey)) {
            val snapshot =
                encryptedPreferences.getDecryptedStringSnapshot(activeKey)
                ?: throw WalletSecureStorageUnavailableException(
                    "A TON Connect scoped keypair is unreadable"
                )
            return decodeOrQuarantineLocked(
                activeKey = activeKey,
                quarantineKey = quarantineKey,
                snapshot = snapshot
            )
        }

        return readLegacyKeypairLocked(connection)
    }

    private suspend fun readLegacyKeypairLocked(connection: TonConnectionLocal): FearlessKeypair? {
        val legacyKey = TonConnectStorageKeys.legacy(connection.clientId)
        val legacyQuarantineKey = WalletSecretQuarantine.keyFor(legacyKey)
        if (encryptedPreferences.hasKey(legacyQuarantineKey)) return null
        if (!encryptedPreferences.hasKey(legacyKey)) return null
        val snapshot =
            encryptedPreferences.getDecryptedStringSnapshot(legacyKey)
            ?: throw WalletSecureStorageUnavailableException(
                "A legacy TON Connect keypair is unreadable"
            )
        val keypair = decodeOrQuarantineLocked(
            activeKey = legacyKey,
            quarantineKey = legacyQuarantineKey,
            snapshot = snapshot
        ) ?: return null

        materializeLegacyForEveryOwnerLocked(
            clientId = connection.clientId,
            encoded = snapshot.plaintext
        )
        return keypair
    }

    private fun decodeOrQuarantineLocked(
        activeKey: String,
        quarantineKey: String,
        snapshot: EncryptedPreferenceSnapshot
    ): FearlessKeypair? {
        val encoded = snapshot.plaintext
        val keypair = try {
            decodeAndValidateKeypair(encoded)
        } catch (_: TonConnectKeypairCorruptionException) {
            null
        }

        if (keypair == null) {
            encryptedPreferences.quarantineEncryptedStringSnapshotDurably(
                sourceKey = activeKey,
                quarantineKey = quarantineKey,
                expectedSnapshot = snapshot
            )
        }
        return keypair
    }

    private suspend fun materializeLegacyForEveryOwnerLocked(clientId: String, encoded: String) {
        val legacyKey = TonConnectStorageKeys.legacy(clientId)
        val legacyQuarantineKey = WalletSecretQuarantine.keyFor(legacyKey)
        if (encryptedPreferences.hasKey(legacyQuarantineKey)) return
        val owners = boundedLegacyOwners(clientId)
        if (owners.isEmpty()) return

        val scopedValues = missingScopedValues(owners, encoded) ?: return

        encryptedPreferences.requireDurableStorageHealthy()
        encryptedPreferences.replaceEncryptedStringsDurably(
            valuesToPut = scopedValues,
            keysToRemove = setOf(legacyKey)
        )
        if (encryptedPreferences.hasKey(legacyKey)) {
            throw WalletSecureStorageUnavailableException(
                "The legacy TON Connect keypair was not durably retired"
            )
        }
        scopedValues.forEach { (key, expected) ->
            if (encryptedPreferences.getDecryptedString(key) != expected) {
                throw WalletSecureStorageUnavailableException(
                    "A scoped TON Connect keypair was not durably materialized"
                )
            }
        }
    }

    private fun missingScopedValues(owners: List<TonConnectionLocal>, encoded: String): Map<String, String>? {
        val scopedValues = linkedMapOf<String, String>()
        for (owner in owners) {
            val ownerActiveKey = TonConnectionIdentity(owner).scopedStorageKey()
            when (scopedKeypairState(ownerActiveKey)) {
                ScopedKeypairState.UNUSABLE -> return null
                ScopedKeypairState.MISSING -> {
                    scopedValues[ownerActiveKey] = encoded
                }

                // A scoped connection may have been deliberately rotated
                // after the shared legacy key was written. Its independently
                // valid payload wins; legacy material only fills missing keys.
                ScopedKeypairState.VALID -> Unit
            }
        }
        return scopedValues
    }

    private fun scopedKeypairState(activeKey: String): ScopedKeypairState {
        val quarantineKey = WalletSecretQuarantine.keyFor(activeKey)
        if (encryptedPreferences.hasKey(quarantineKey)) {
            return ScopedKeypairState.UNUSABLE
        }
        if (!encryptedPreferences.hasKey(activeKey)) {
            return ScopedKeypairState.MISSING
        }
        val encoded = encryptedPreferences.getDecryptedString(activeKey)
            ?: return ScopedKeypairState.UNUSABLE
        return validScopedKeypairState(encoded)
    }

    private fun validScopedKeypairState(encoded: String): ScopedKeypairState {
        return try {
            decodeAndValidateKeypair(encoded)
            ScopedKeypairState.VALID
        } catch (_: TonConnectKeypairCorruptionException) {
            ScopedKeypairState.UNUSABLE
        }
    }

    private suspend fun boundedLegacyOwners(clientId: String): List<TonConnectionLocal> {
        TonConnectStorageKeys.requireValidClientId(clientId)
        val projections = tonConnectDao.getTonConnectionsByClientId(clientId)
        if (projections.size > MAX_LEGACY_OWNERS) {
            throw TonConnectMutationJournalException(
                "A legacy TON Connect keypair has too many owners"
            )
        }
        return projections.map(::requireValidProjection)
    }

    private suspend fun hasLegacyOwners(clientId: String): Boolean {
        TonConnectStorageKeys.requireValidClientId(clientId)
        return tonConnectDao.hasTonConnectionsByClientId(clientId)
    }

    private fun decodeAndValidateKeypair(encodedKeypair: String): FearlessKeypair {
        requireBoundedKeypairPayload(encodedKeypair)
        try {
            WalletSecretScalePreflight.requireKeyPairV2(encodedKeypair)
        } catch (failure: WalletSecretScaleCorruptionException) {
            throw TonConnectKeypairCorruptionException(
                message = "The TON Connect keypair payload is malformed",
                cause = failure
            )
        }

        val schema = try {
            KeyPairSchema.read(encodedKeypair)
        } catch (failure: Exception) {
            throw TonConnectKeypairCorruptionException(
                message = "The TON Connect keypair payload cannot be decoded",
                cause = failure
            )
        }
        val privateKey = schema[KeyPairSchema.PrivateKey]
        val publicKey = schema[KeyPairSchema.PublicKey]
        requireValidKeypair(
            privateKey = privateKey,
            publicKey = publicKey,
            nonce = schema[KeyPairSchema.Nonce]
        )

        return createKeypair(publicKey, privateKey)
    }

    private fun requireBoundedKeypairPayload(encodedKeypair: String) {
        if (
            encodedKeypair.isEmpty() ||
            encodedKeypair.length > MAX_KEYPAIR_PAYLOAD_CHARS
        ) {
            throw TonConnectKeypairCorruptionException(
                "The TON Connect keypair payload exceeds its safe size bound"
            )
        }
    }

    private fun requireValidKeypair(
        privateKey: ByteArray,
        publicKey: ByteArray,
        nonce: ByteArray?
    ) {
        requireExactKeyLength(privateKey, "private")
        requireExactKeyLength(publicKey, "public")
        if (nonce != null) {
            throw TonConnectKeypairCorruptionException(
                "A TON Connect keypair must not contain a Substrate nonce"
            )
        }
        requireMatchingPublicKey(privateKey, publicKey)
    }

    private fun requireExactKeyLength(key: ByteArray, label: String) {
        if (key.size != TON_CONNECT_KEY_BYTES) {
            throw TonConnectKeypairCorruptionException(
                "A TON Connect $label key must contain exactly $TON_CONNECT_KEY_BYTES bytes"
            )
        }
    }

    private fun requireMatchingPublicKey(privateKey: ByteArray, publicKey: ByteArray) {
        val derivedPublicKey = tonPublicKeyDeriver(privateKey)
        if (derivedPublicKey.size != TON_CONNECT_KEY_BYTES) {
            throw WalletSecureStorageUnavailableException(
                "TON Connect public-key derivation returned an invalid result"
            )
        }
        if (!derivedPublicKey.contentEquals(publicKey)) {
            throw TonConnectKeypairCorruptionException(
                "The TON Connect public key does not belong to its private key"
            )
        }
    }

    private fun encodeKeypair(keypair: FearlessKeypair): String {
        return KeyPairSchema { schema ->
            schema[PublicKey] = keypair.publicKey
            schema[PrivateKey] = keypair.privateKey
            schema[Nonce] = null
        }.toHexString()
    }

    private fun requireValidProjection(projection: TonConnectionReadProjection): TonConnectionLocal {
        if (!projection.rowWithinBounds) {
            throw TonConnectMutationJournalException(
                "A stored TON Connect row exceeds its safe SQL bounds"
            )
        }
        val source = enumValues<ConnectionSource>().singleOrNull {
            it.name == projection.source
        } ?: throw TonConnectMutationJournalException(
            "A stored TON Connect row has an invalid source"
        )
        requireExactProjectedUtf8(
            value = projection.clientId,
            expectedBytes = projection.clientIdUtf8Bytes,
            label = "client id"
        )
        requireExactProjectedUtf8(
            value = projection.name,
            expectedBytes = projection.nameUtf8Bytes,
            label = "name"
        )
        requireExactProjectedUtf8(
            value = projection.icon,
            expectedBytes = projection.iconUtf8Bytes,
            label = "icon"
        )
        requireExactProjectedUtf8(
            value = projection.url,
            expectedBytes = projection.urlUtf8Bytes,
            label = "URL"
        )
        requireExactProjectedUtf8(
            value = projection.source,
            expectedBytes = projection.sourceUtf8Bytes,
            label = "source"
        )
        val connection = TonConnectionLocal(
            metaId = projection.metaId,
            clientId = projection.clientId,
            name = projection.name,
            icon = projection.icon,
            url = projection.url,
            source = source
        )
        journalCodec.validateConnection(connection)
        return connection
    }

    private fun requireExactProjectedUtf8(
        value: String,
        expectedBytes: Long,
        label: String
    ) {
        requireNoReplacementCharacter(value, label)
        val encodedBytes = try {
            Charsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(value))
                .remaining()
                .toLong()
        } catch (failure: CharacterCodingException) {
            throw TonConnectMutationJournalException(
                "A stored TON Connect $label has malformed UTF-8 text",
                failure
            )
        }
        if (encodedBytes != expectedBytes) {
            throw TonConnectMutationJournalException(
                "A stored TON Connect $label changed during UTF-8 projection"
            )
        }
    }

    private fun requireNoReplacementCharacter(value: String, label: String) {
        if (value.any { it == UNICODE_REPLACEMENT_CHARACTER }) {
            throw TonConnectMutationJournalException(
                "A stored TON Connect $label has non-exact UTF-8 text"
            )
        }
    }

    private fun verifySaveFinalized(
        activeKey: String,
        expectedPayload: String,
        stageKey: String
    ) {
        if (saveFinalizationFailed(activeKey, expectedPayload, stageKey)) {
            throw WalletSecureStorageUnavailableException(
                "The TON Connect save was not durably finalized"
            )
        }
    }

    private fun saveFinalizationFailed(
        activeKey: String,
        expectedPayload: String,
        stageKey: String
    ): Boolean {
        requireDurableStorageHealthy()
        if (
            encryptedPreferences.hasKey(TonConnectMutationJournalCodec.JOURNAL_KEY) ||
            encryptedPreferences.hasKey(stageKey)
        ) {
            return true
        }
        if (encryptedPreferences.hasKey(WalletSecretQuarantine.keyFor(activeKey))) {
            return true
        }
        return encryptedPreferences.getDecryptedString(activeKey) != expectedPayload
    }

    private fun verifyDeleteFinalized(keysToRemove: Set<String>) {
        requireDurableStorageHealthy()
        if (keysToRemove.any(encryptedPreferences::hasKey)) {
            throw WalletSecureStorageUnavailableException(
                "The TON Connect deletion was not durably finalized"
            )
        }
    }

    private fun requireDurableStorageHealthy() {
        encryptedPreferences.requireDurableStorageHealthy()
    }

    private companion object {
        const val TON_CONNECT_KEY_BYTES = 32
        const val MAX_CONNECTIONS_PER_WALLET_SOURCE =
            TonConnectStorageKeys.MAX_CONNECTIONS_PER_WALLET
        const val MAX_LEGACY_OWNERS =
            TonConnectStorageKeys.MAX_CONNECTIONS_PER_WALLET
        const val MAX_KEYPAIR_PAYLOAD_CHARS = 1_024
        const val UNICODE_REPLACEMENT_CHARACTER = '\uFFFD'
    }

    private enum class ScopedKeypairState {
        UNUSABLE,
        MISSING,
        VALID
    }
}

private fun TonConnectionIdentity.scopedStorageKey(): String {
    return TonConnectStorageKeys.scoped(
        metaId = metaId,
        url = url,
        source = source.name
    )
}

private class TonConnectKeypairCorruptionException(
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)
