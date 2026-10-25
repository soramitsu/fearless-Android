package jp.co.soramitsu.common.wallet

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.common.utils.SolanaTransactionSigner
import jp.co.soramitsu.common.utils.SolanaTransactionSigner.ErrorCode
import jp.co.soramitsu.common.utils.SolanaTransactionSigner.SolanaTransactionException
import jp.co.soramitsu.common.utils.SolanaTransactionSigner.SolanaTransactionVersion
import org.bouncycastle.util.encoders.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader

class SolanaTransactionSignerTest {

    @Test
    fun `parses and signs legacy serialized solana transactions`() {
        val fixture = loadFixture()
        val vector = fixture.getAsJsonArray("vectors")[0].asJsonObject
        val expected = vector.getAsJsonObject("expected").getAsJsonObject("solana")
        val raw = Base64.decode(LEGACY_RAW_BASE64)

        val parsed = SolanaTransactionSigner.parseSerializedTransaction(raw)
        val signed = SolanaTransactionSigner.signSerializedTransaction(
            mnemonic = vector["mnemonic"].asString,
            transaction = raw,
            expectedSigner = expected["address"].asString
        )

        assertEquals(SolanaTransactionVersion.Legacy, parsed.version)
        assertEquals(1, parsed.requiredSignatures)
        assertEquals(listOf(expected["address"].asString, SYSTEM_PROGRAM), parsed.accountKeys)
        assertEquals(0, parsed.addressTableLookupCount)
        assertEquals(1, parsed.instructionCount)
        assertEquals(0, parsed.readonlySignedAccounts)
        assertEquals(1, parsed.readonlyUnsignedAccounts)
        assertEquals(SolanaTransactionVersion.Legacy, signed.version)
        assertEquals(expected["address"].asString, signed.signer)
        assertEquals(LEGACY_SIGNATURE_BASE58, signed.signatureBase58)
        assertEquals(LEGACY_SIGNED_BASE64, signed.signedTransactionBase64)
        assertTrue(signed.signedTransaction.copyOfRange(1, 65).any { it != 0.toByte() })
    }

    @Test
    fun `parses and signs v0 serialized solana transactions from base64`() {
        val vector = loadFixture().getAsJsonArray("vectors")[0].asJsonObject

        val parsed = SolanaTransactionSigner.parseSerializedTransaction(V0_RAW_BASE64)
        val signed = SolanaTransactionSigner.signSerializedTransaction(
            mnemonic = vector["mnemonic"].asString,
            transactionBase64 = V0_RAW_BASE64
        )

        assertEquals(SolanaTransactionVersion.V0, parsed.version)
        assertEquals(0, parsed.addressTableLookupCount)
        assertEquals(0, parsed.instructionCount)
        assertEquals(SolanaTransactionVersion.V0, signed.version)
        assertEquals(V0_SIGNATURE_BASE58, signed.signatureBase58)
        assertEquals(V0_SIGNED_BASE64, signed.signedTransactionBase64)
    }

    @Test
    fun `rejects malformed solana transaction envelopes and signer states`() {
        val fixture = loadFixture()
        val vector = fixture.getAsJsonArray("vectors")[0].asJsonObject
        val otherVector = fixture.getAsJsonArray("vectors")[1].asJsonObject
        val expected = vector.getAsJsonObject("expected").getAsJsonObject("solana")
        val otherExpected = otherVector.getAsJsonObject("expected").getAsJsonObject("solana")

        assertTransactionError(ErrorCode.EMPTY_TRANSACTION) {
            SolanaTransactionSigner.parseSerializedTransaction(ByteArray(0))
        }
        assertTransactionError(ErrorCode.INVALID_TRANSACTION_BASE64) {
            SolanaTransactionSigner.parseSerializedTransaction("*not-base64*")
        }
        assertTransactionError(ErrorCode.MISSING_SIGNATURE_SLOT) {
            SolanaTransactionSigner.parseSerializedTransaction(byteArrayOf(0))
        }
        assertTransactionError(ErrorCode.TRUNCATED_SIGNATURES) {
            SolanaTransactionSigner.parseSerializedTransaction(byteArrayOf(1, 2, 3))
        }
        assertTransactionError(ErrorCode.UNSUPPORTED_TRANSACTION_VERSION) {
            SolanaTransactionSigner.parseSerializedTransaction(transaction(byteArrayOf(0x81.toByte(), 1, 0, 0)))
        }
        assertTransactionError(ErrorCode.SIGNER_MISMATCH) {
            SolanaTransactionSigner.signSerializedTransaction(
                mnemonic = vector["mnemonic"].asString,
                transaction = Base64.decode(LEGACY_RAW_BASE64),
                expectedSigner = otherExpected["address"].asString
            )
        }
        assertTransactionError(ErrorCode.SIGNER_NOT_FOUND) {
            SolanaTransactionSigner.signSerializedTransaction(
                mnemonic = otherVector["mnemonic"].asString,
                transaction = Base64.decode(LEGACY_RAW_BASE64)
            )
        }
        assertTransactionError(ErrorCode.SIGNER_NOT_REQUIRED) {
            SolanaTransactionSigner.signSerializedTransaction(
                mnemonic = vector["mnemonic"].asString,
                transaction = transaction(
                    legacyMessage(
                        accountKeys = listOf(SYSTEM_PROGRAM_KEY, expected["publicKeyHex"].asString.hexToBytes())
                    )
                )
            )
        }
    }

    private fun loadFixture(): JsonObject {
        val stream = javaClass.classLoader?.getResourceAsStream("universal-wallet-v2-vectors.json")
            ?: error("universal-wallet-v2-vectors.json is missing from test resources")

        return stream.use {
            JsonParser.parseReader(InputStreamReader(it)).asJsonObject
        }
    }

    private fun assertTransactionError(expected: ErrorCode, block: () -> Unit) {
        val error = assertThrows(SolanaTransactionException::class.java) { block() }
        assertEquals(expected, error.code)
    }

    private fun transaction(message: ByteArray): ByteArray {
        return byteArrayOf(1) + ByteArray(64) + message
    }

    private fun legacyMessage(
        accountKeys: List<ByteArray>,
        requiredSignatures: Int = 1
    ): ByteArray {
        val readonlyUnsignedAccounts = if (accountKeys.size > requiredSignatures) 1 else 0

        return byteArrayOf(requiredSignatures.toByte(), 0, readonlyUnsignedAccounts.toByte()) +
            compact(accountKeys.size) +
            accountKeys.fold(ByteArray(0)) { result, key -> result + key } +
            ByteArray(32) { 9 } +
            compact(1) +
            byteArrayOf(1) +
            compact(1) +
            byteArrayOf(0) +
            compact(0)
    }

    private fun compact(value: Int): ByteArray {
        val result = mutableListOf<Byte>()
        var next = value

        do {
            var byte = next and 0x7f
            next = next shr 7
            if (next > 0) {
                byte = byte or 0x80
            }
            result.add(byte.toByte())
        } while (next > 0)

        return result.toByteArray()
    }

    private fun String.hexToBytes(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private companion object {
        const val SYSTEM_PROGRAM = "11111111111111111111111111111111"
        val SYSTEM_PROGRAM_KEY = ByteArray(32)
        const val LEGACY_RAW_BASE64 = "AQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAAEC8DYnYkanW53jNJ7UKxXiMvZRj8IPX81PHWToH5vSWPcAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJAQEBAAA="
        const val LEGACY_SIGNATURE_BASE58 = "2ThwZApYQ5MZxP3JNL2Mn3t5Vt1Yzgkx3T3QRxpxgiCngvVSvScRgiX61f8Bp8E6KsXVr28sXMzGTzRvXJyarvGv"
        const val LEGACY_SIGNED_BASE64 = "AUkMEKU0Py6fPLWbTMeNdpmB19EgEAmgZZUCQb7ZIhsqaZswN1kb3eQxPXumIsv5ufYBItmez8Hu3ia4j/yyFgcBAAEC8DYnYkanW53jNJ7UKxXiMvZRj8IPX81PHWToH5vSWPcAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJAQEBAAA="
        const val V0_RAW_BASE64 = "AQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACAAQABAvA2J2JGp1ud4zSe1CsV4jL2UY/CD1/NTx1k6B+b0lj3AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwAA"
        const val V0_SIGNATURE_BASE58 = "3CuDBJeYdH4aTrEW6J3EHAfdFrBFqrbdZymtX55eBpDmA5MWGobPnWm8iK9dA8nRkbXVsZJhPVaMymsCFJvciJtW"
        const val V0_SIGNED_BASE64 = "AW5L1z7qDYCdFiFsof2uB0Mlm2sckSBc25Q/jZEg6BQYUnvnDZHOmB+jspSxyQv2X2FrECOOHQg3Qp1AEYoPKweAAQABAvA2J2JGp1ud4zSe1CsV4jL2UY/CD1/NTx1k6B+b0lj3AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwAA"
    }
}
