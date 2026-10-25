package jp.co.soramitsu.tonconnect.impl.data

import jp.co.soramitsu.coredb.model.ConnectionSource
import jp.co.soramitsu.coredb.model.TonConnectionLocal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class TonConnectMutationJournalTest {

    private val codec = TonConnectMutationJournalCodec()

    @Test
    fun saveJournalRoundTripsCanonically() {
        val operationId = "00112233445566778899aabbccddeeff"
        val journal = TonConnectMutationJournal(
            operation = TonConnectMutationOperation.SAVE,
            operationId = operationId,
            connection = connection(),
            stageKey = codec.stageKey(operationId),
            previousConnection = connection().copy(clientId = CLIENT_ID.uppercase())
        )

        val encoded = codec.encode(journal)

        assertEquals(journal, codec.decode(encoded))
        assertFalse(encoded.contains("privateKey"))
        assertFalse(encoded.contains("publicKey"))
    }

    @Test
    fun deleteJournalRoundTripsWithoutStageOrReplacementState() {
        val journal = TonConnectMutationJournal(
            operation = TonConnectMutationOperation.DELETE,
            operationId = "00112233445566778899aabbccddeeff",
            connection = connection(),
            stageKey = null,
            previousConnection = null
        )

        assertEquals(journal, codec.decode(codec.encode(journal)))
    }

    @Test
    fun noncanonicalUnknownTruncatedAndOversizedJournalsAreRejected() {
        val valid = codec.encode(
            TonConnectMutationJournal(
                operation = TonConnectMutationOperation.DELETE,
                operationId = "00112233445566778899aabbccddeeff",
                connection = connection(),
                stageKey = null,
                previousConnection = null
            )
        )
        val hostile = listOf(
            " $valid",
            valid.dropLast(1),
            valid.replace("\"DELETE\"", "\"UNKNOWN\""),
            valid.dropLast(1) + ",\"unknown\":true}",
            "x".repeat(32_769)
        )

        hostile.forEach {
            assertThrows(TonConnectMutationJournalException::class.java) {
                codec.decode(it)
            }
        }
    }

    @Test
    fun wrongStageKeyAndDeleteSaveStateAreRejected() {
        assertThrows(TonConnectMutationJournalException::class.java) {
            codec.encode(
                TonConnectMutationJournal(
                    operation = TonConnectMutationOperation.SAVE,
                    operationId = "00112233445566778899aabbccddeeff",
                    connection = connection(),
                    stageKey = "TON_CONNECT_MUTATION_STAGE_V1_redirected",
                    previousConnection = null
                )
            )
        }
        assertThrows(TonConnectMutationJournalException::class.java) {
            codec.encode(
                TonConnectMutationJournal(
                    operation = TonConnectMutationOperation.DELETE,
                    operationId = "00112233445566778899aabbccddeeff",
                    connection = connection(),
                    stageKey = null,
                    previousConnection = connection()
                )
            )
        }
    }

    @Test
    fun saveJournalRejectsMismatchedOrInvalidPreviousRows() {
        val current = connection()
        val hostilePreviousRows = listOf(
            current.copy(metaId = current.metaId + 1),
            current.copy(url = "https://attacker.example"),
            current.copy(source = ConnectionSource.WEB),
            current.copy(clientId = "not-hex")
        )

        hostilePreviousRows.forEach { previous ->
            assertThrows(TonConnectMutationJournalException::class.java) {
                codec.encode(
                    TonConnectMutationJournal(
                        operation = TonConnectMutationOperation.SAVE,
                        operationId = "00112233445566778899aabbccddeeff",
                        connection = current,
                        stageKey = codec.stageKey(
                            "00112233445566778899aabbccddeeff"
                        ),
                        previousConnection = previous
                    )
                )
            }
        }
    }

    @Test
    fun incompletePreviousRowIsRejectedDuringDecode() {
        val operationId = "00112233445566778899aabbccddeeff"
        val valid = codec.encode(
            TonConnectMutationJournal(
                operation = TonConnectMutationOperation.SAVE,
                operationId = operationId,
                connection = connection(),
                stageKey = codec.stageKey(operationId),
                previousConnection = connection()
            )
        )
        val incomplete = valid.replace(
            "\"previousMetaId\":1",
            "\"previousMetaId\":null"
        )

        assertFalse(valid == incomplete)
        assertThrows(TonConnectMutationJournalException::class.java) {
            codec.decode(incomplete)
        }
    }

    @Test
    fun allJournalTextFieldsAreBoundedBeforeEncoding() {
        val oversizedValues = listOf(
            connection().copy(name = "n".repeat(513)),
            connection().copy(icon = "i".repeat(4_097)),
            connection().copy(url = "u".repeat(4_097)),
            connection().copy(clientId = "a".repeat(1_000_000))
        )

        oversizedValues.forEach { oversized ->
            assertThrows(TonConnectMutationJournalException::class.java) {
                codec.encode(
                    TonConnectMutationJournal(
                        operation = TonConnectMutationOperation.DELETE,
                        operationId = "00112233445566778899aabbccddeeff",
                        connection = oversized,
                        stageKey = null,
                        previousConnection = null
                    )
                )
            }
        }
    }

    @Test
    fun persistedTextMustBeExactUtf8BeforeEncoding() {
        val hostileValues = listOf(
            connection().copy(name = "Dapp\uFFFD"),
            connection().copy(icon = "https://example.com/\uFFFD.png"),
            connection().copy(url = "https://example.com/\uFFFD"),
            connection().copy(name = "Dapp\uD800"),
            connection().copy(icon = "https://example.com/\uD800.png"),
            connection().copy(url = "https://example.com/\uD800")
        )

        hostileValues.forEach { hostile ->
            assertThrows(TonConnectMutationJournalException::class.java) {
                codec.encode(
                    TonConnectMutationJournal(
                        operation = TonConnectMutationOperation.DELETE,
                        operationId = "00112233445566778899aabbccddeeff",
                        connection = hostile,
                        stageKey = null,
                        previousConnection = null
                    )
                )
            }
        }
    }

    @Test
    fun validSupplementaryUnicodeRoundTripsCanonically() {
        val connection = connection().copy(
            name = "Dapp \uD83D\uDE80",
            icon = "https://example.com/\uD83D\uDE80.png"
        )
        val journal = TonConnectMutationJournal(
            operation = TonConnectMutationOperation.DELETE,
            operationId = "00112233445566778899aabbccddeeff",
            connection = connection,
            stageKey = null,
            previousConnection = null
        )

        assertEquals(journal, codec.decode(codec.encode(journal)))
    }

    private fun connection() = TonConnectionLocal(
        metaId = 1L,
        clientId = CLIENT_ID,
        name = "Dapp",
        icon = "https://example.com/icon.png",
        url = "https://example.com",
        source = ConnectionSource.QR
    )

    private companion object {
        const val CLIENT_ID =
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
    }
}
