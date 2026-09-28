package jp.co.soramitsu.tonconnect.impl.data

import com.google.gson.Gson
import com.google.gson.JsonParseException
import jp.co.soramitsu.common.data.storage.encrypt.TonConnectStorageKeys
import jp.co.soramitsu.coredb.model.ConnectionSource
import jp.co.soramitsu.coredb.model.TonConnectionLocal
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

internal enum class TonConnectMutationOperation {
    SAVE,
    DELETE
}

internal enum class TonConnectMutationBoundary {
    JOURNAL_STAGED,
    DATABASE_MUTATED,
    PREFERENCES_FINALIZED
}

internal data class TonConnectMutationJournal(
    val operation: TonConnectMutationOperation,
    val operationId: String,
    val connection: TonConnectionLocal,
    val stageKey: String?,
    val previousConnection: TonConnectionLocal?
)

class TonConnectMutationJournalException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

internal class TonConnectMutationJournalCodec(
    private val gson: Gson = Gson()
) {

    fun encode(journal: TonConnectMutationJournal): String {
        validate(journal)
        val encoded = gson.toJson(journal.toStored())
        if (encoded.length > MAX_JOURNAL_CHARS) {
            throw TonConnectMutationJournalException(
                "The TON Connect mutation journal exceeds its safe size limit"
            )
        }
        return encoded
    }

    fun decode(encoded: String): TonConnectMutationJournal {
        if (encoded.isEmpty() || encoded.length > MAX_JOURNAL_CHARS) {
            fail(
                "The TON Connect mutation journal is empty or oversized"
            )
        }

        val stored = try {
            gson.fromJson(encoded, StoredJournal::class.java)
        } catch (failure: JsonParseException) {
            fail(
                "The TON Connect mutation journal is malformed",
                failure
            )
        } catch (failure: RuntimeException) {
            fail(
                "The TON Connect mutation journal is unreadable",
                failure
            )
        } ?: fail(
            "The TON Connect mutation journal is missing"
        )

        val journal = stored.toJournal()
        validate(journal)
        if (encode(journal) != encoded) {
            fail(
                "The TON Connect mutation journal is not canonical"
            )
        }
        return journal
    }

    fun validate(journal: TonConnectMutationJournal) {
        if (!OPERATION_ID.matches(journal.operationId)) {
            fail(
                "The TON Connect mutation journal has an invalid operation id"
            )
        }
        validateConnection(journal.connection)

        when (journal.operation) {
            TonConnectMutationOperation.SAVE -> validateSaveState(journal)
            TonConnectMutationOperation.DELETE -> validateDeleteState(journal)
        }
    }

    fun validateConnection(connection: TonConnectionLocal) {
        if (connection.metaId <= 0L) {
            fail(
                "A TON Connect journal requires a positive wallet id"
            )
        }
        try {
            TonConnectStorageKeys.requireValidClientId(connection.clientId)
        } catch (failure: IllegalArgumentException) {
            fail(
                "A TON Connect journal contains an invalid client id",
                failure
            )
        }
        requireBoundedText(
            value = connection.name,
            label = "name",
            maximumChars = MAX_NAME_CHARS,
            allowEmpty = true
        )
        requireBoundedText(
            value = connection.icon,
            label = "icon",
            maximumChars = MAX_ICON_CHARS,
            allowEmpty = true
        )
        requireBoundedText(
            value = connection.url,
            label = "URL",
            maximumChars = MAX_URL_CHARS,
            allowEmpty = false
        )
        try {
            TonConnectStorageKeys.scoped(
                metaId = connection.metaId,
                url = connection.url,
                source = connection.source.name
            )
        } catch (failure: IllegalArgumentException) {
            fail(
                "A TON Connect journal contains an invalid row identity",
                failure
            )
        }
    }

    fun stageKey(operationId: String): String {
        if (!OPERATION_ID.matches(operationId)) {
            throw TonConnectMutationJournalException(
                "A TON Connect mutation stage requires a valid operation id"
            )
        }
        return STAGE_KEY_PREFIX + operationId
    }

    private fun TonConnectMutationJournal.toStored() = StoredJournal(
        version = CURRENT_VERSION,
        operation = operation.name,
        operationId = operationId,
        metaId = connection.metaId,
        clientId = connection.clientId,
        name = connection.name,
        icon = connection.icon,
        url = connection.url,
        source = connection.source.name,
        stageKey = stageKey,
        previousMetaId = previousConnection?.metaId,
        previousClientId = previousConnection?.clientId,
        previousName = previousConnection?.name,
        previousIcon = previousConnection?.icon,
        previousUrl = previousConnection?.url,
        previousSource = previousConnection?.source?.name
    )

    private fun validateSaveState(journal: TonConnectMutationJournal) {
        val expectedStageKey = stageKey(journal.operationId)
        if (journal.stageKey != expectedStageKey) {
            fail("A TON Connect save journal has an invalid stage key")
        }

        val previous = journal.previousConnection ?: return
        validateConnection(previous)
        if (
            previous.metaId != journal.connection.metaId ||
            previous.url != journal.connection.url ||
            previous.source != journal.connection.source
        ) {
            fail(
                "A TON Connect save journal has a mismatched previous row identity"
            )
        }
    }

    private fun validateDeleteState(journal: TonConnectMutationJournal) {
        if (journal.stageKey != null || journal.previousConnection != null) {
            fail("A TON Connect delete journal contains invalid save state")
        }
    }

    private fun StoredJournal.toJournal(): TonConnectMutationJournal {
        if (version != CURRENT_VERSION) {
            fail(
                "The TON Connect mutation journal version is unsupported"
            )
        }
        val decodedOperation = operation?.let {
            enumValues<TonConnectMutationOperation>().singleOrNull { value ->
                value.name == it
            }
        } ?: fail(
            "The TON Connect mutation journal operation is unsupported"
        )
        val decodedSource = decodeSource(
            encoded = source,
            label = "mutation journal"
        )
        val previousFields = listOf(
            previousMetaId,
            previousClientId,
            previousName,
            previousIcon,
            previousUrl,
            previousSource
        )
        val previousConnection = when (
            previousFields.count { it != null }
        ) {
            0 -> null
            previousFields.size -> TonConnectionLocal(
                metaId = checkNotNull(previousMetaId),
                clientId = checkNotNull(previousClientId),
                name = checkNotNull(previousName),
                icon = checkNotNull(previousIcon),
                url = checkNotNull(previousUrl),
                source = decodeSource(
                    encoded = previousSource,
                    label = "previous TON Connect row"
                )
            )
            else -> fail(
                "The TON Connect mutation journal has an incomplete previous row"
            )
        }

        return TonConnectMutationJournal(
            operation = decodedOperation,
            operationId = operationId ?: missingField(),
            connection = TonConnectionLocal(
                metaId = metaId ?: missingField(),
                clientId = clientId ?: missingField(),
                name = name ?: missingField(),
                icon = icon ?: missingField(),
                url = url ?: missingField(),
                source = decodedSource
            ),
            stageKey = stageKey,
            previousConnection = previousConnection
        )
    }

    private fun decodeSource(encoded: String?, label: String): ConnectionSource {
        return encoded?.let {
            enumValues<ConnectionSource>().singleOrNull { value ->
                value.name == it
            }
        } ?: fail(
            "The $label source is unsupported"
        )
    }

    private fun requireBoundedText(
        value: String,
        label: String,
        maximumChars: Int,
        allowEmpty: Boolean
    ) {
        val invalidTextMessage = "The TON Connect journal contains an invalid $label"
        if (value.length > maximumChars) {
            fail(invalidTextMessage)
        }
        if (!allowEmpty && value.isBlank()) {
            fail(invalidTextMessage)
        }
        if (value.any { it == NULL_CHARACTER || it == UNICODE_REPLACEMENT_CHARACTER }) {
            fail(invalidTextMessage)
        }
        try {
            Charsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(value))
        } catch (failure: CharacterCodingException) {
            fail(
                "The TON Connect journal contains malformed $label text",
                failure
            )
        }
    }

    private fun missingField(): Nothing = fail(
        "The TON Connect mutation journal is incomplete"
    )

    private fun fail(message: String, cause: Throwable? = null): Nothing {
        throw TonConnectMutationJournalException(message, cause)
    }

    private data class StoredJournal(
        val version: Int?,
        val operation: String?,
        val operationId: String?,
        val metaId: Long?,
        val clientId: String?,
        val name: String?,
        val icon: String?,
        val url: String?,
        val source: String?,
        val stageKey: String?,
        val previousMetaId: Long?,
        val previousClientId: String?,
        val previousName: String?,
        val previousIcon: String?,
        val previousUrl: String?,
        val previousSource: String?
    )

    companion object {
        const val JOURNAL_KEY = TonConnectStorageKeys.MUTATION_JOURNAL_KEY
        private const val STAGE_KEY_PREFIX = "TON_CONNECT_MUTATION_STAGE_V1_"
        private const val CURRENT_VERSION = 1
        private const val MAX_JOURNAL_CHARS = 32_768
        private const val MAX_NAME_CHARS = 512
        private const val MAX_ICON_CHARS = 4_096
        private const val MAX_URL_CHARS = TonConnectStorageKeys.MAX_URL_CHARS
        private const val NULL_CHARACTER = '\u0000'
        private const val UNICODE_REPLACEMENT_CHARACTER = '\uFFFD'
        private val OPERATION_ID = Regex("^[0-9a-f]{32}$")
    }
}
