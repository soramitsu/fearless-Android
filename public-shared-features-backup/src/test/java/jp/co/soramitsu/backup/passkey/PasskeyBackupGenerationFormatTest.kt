package jp.co.soramitsu.backup.passkey

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.util.Base64

class PasskeyBackupGenerationFormatTest {
    private val format = PasskeyBackupGenerationFormat

    @Test
    fun `independent Node vector preserves legacy envelope and wrapper and decrypts`() {
        val generation = GenerationFixture.generation()
        val bytes = format.encode(generation)
        assertEquals(785, bytes.size)
        assertEquals("1c92b544dc25c687c202317d0e5747b5690a1056cf72e61d1dfab84c07c057a4", format.sha256(bytes))
        assertArrayEquals(GenerationFixture.bytes, bytes)
        val decoded = format.decode(bytes, generation.context, GenerationFixture.digest)
        assertArrayEquals(bytes, format.encode(decoded))
        assertArrayEquals(generation.envelope.encryptedPayload, decoded.envelope.encryptedPayload)
        val record = decoded.wrappers.single()
        val key = PasskeyBackupCredentialKeyWrapper().unwrap(record, ByteArray(32) { 0x66 }, record.context)
        try {
            assertArrayEquals(ByteArray(32) { 0x77 }, key)
            assertArrayEquals(
                "cross-platform-passkey-backup".toByteArray(),
                AesGcmPasskeyBackupEnvelopeCryptography().decrypt(decoded.envelope.encryptedPayload, decoded.envelope.envelopeMetadata(), key)
            )
        } finally {
            key.fill(0)
        }
    }

    @Test
    fun `digest and every expected context dimension are bound`() {
        val context = GenerationFixture.context
        val otherId = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 0x12 })
        for (other in listOf(
            context.copy(ownerSubject = "owner:$otherId"), context.copy(backupNamespace = "backup:$otherId"),
            context.copy(generationId = otherId), context.copy(parentHeadRevision = 5),
            context.copy(parentHeadSha256 = "bb".repeat(32)), context.copy(keyEpoch = 8),
            context.copy(storageAccountBinding = "bb".repeat(32))
        )) {
            fails { format.decode(GenerationFixture.bytes, other, GenerationFixture.digest) }
        }
        fails { format.decode(GenerationFixture.bytes, context, "00".repeat(32)) }
        fails { format.decode(GenerationFixture.bytes, context, GenerationFixture.digest.uppercase()) }
    }

    @Test
    fun `all truncations trailing data oversized length and invalid UTF8 reject before use`() {
        val bytes = GenerationFixture.bytes
        for (length in bytes.indices) {
            val truncated = bytes.copyOf(length)
            fails { format.decode(truncated, GenerationFixture.context, format.sha256(truncated)) }
        }
        val variants = listOf(
            bytes + 0,
            bytes.copyOf().also { it[0] = 0 },
            bytes.copyOf().also { it[11] = 2 },
            bytes.copyOf().also { ByteBuffer.wrap(it, 12, 4).putInt(Int.MAX_VALUE) },
            bytes.copyOf().also { it[16] = 0xff.toByte() },
            ByteArray(format.MAX_BYTES + 1)
        )
        variants.forEach { fails { format.decode(it, GenerationFixture.context, format.sha256(it)) } }
    }

    @Test
    fun `genesis parent invariant canonical IDs and epoch are enforced`() {
        val context = GenerationFixture.context
        val genesis = context.copy(parentHeadRevision = 0, parentHeadSha256 = null, keyEpoch = 1)
        assertEquals(null, genesis.parentHeadSha256)
        val first = PasskeyBackupGeneration(
            genesis, GenerationFixture.generation().envelope,
            listOf(GenerationFixture.wrapper(GenerationFixture.json["credentialId"].asString, epoch = 1))
        )
        val firstBytes = format.encode(first)
        assertEquals(genesis, format.decode(firstBytes, genesis, format.sha256(firstBytes)).context)
        listOf(-1L, 0L).forEach { epoch -> fails { context.copy(keyEpoch = epoch) } }
        fails { context.copy(parentHeadRevision = -1) }
        fails { context.copy(parentHeadRevision = 0) }
        fails { context.copy(parentHeadSha256 = null) }
        fails { context.copy(generationId = context.generationId + "=") }
        fails { context.copy(backupNamespace = context.ownerSubject) }
        fails { context.copy(ownerSubject = "owner:" + "_".repeat(43)) }
    }

    @Test
    fun `wrapper list is sorted copied and rejects duplicates or mismatched context`() {
        val one = GenerationFixture.generation()
        val second = GenerationFixture.wrapper("IyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyM")
        val input = mutableListOf(second, one.wrappers.single())
        val two = PasskeyBackupGeneration(one.context, one.envelope, input)
        input.clear()
        assertEquals(2, two.wrappers.size)
        assertTrue(two.wrappers[0].context.credentialId < two.wrappers[1].context.credentialId)
        fails { (two.wrappers as MutableList).clear() }
        fails { PasskeyBackupGeneration(one.context, one.envelope, listOf(one.wrappers[0], one.wrappers[0])) }
        fails { PasskeyBackupGeneration(one.context, one.envelope, emptyList()) }
        fails { PasskeyBackupGeneration(one.context.copy(keyEpoch = 8), one.envelope, one.wrappers) }
        fails { PasskeyBackupGeneration(one.context, one.envelope, List(33) { one.wrappers[0] }) }
        val encoded = format.encode(two)
        val entryBytes = 4 + 43 + 4 + 344
        val prefix = encoded.copyOfRange(0, encoded.size - entryBytes * 2)
        val first = encoded.copyOfRange(prefix.size, prefix.size + entryBytes)
        val last = encoded.copyOfRange(prefix.size + entryBytes, encoded.size)
        for (altered in listOf(prefix + last + first, prefix + first + first)) {
            fails { format.decode(altered, one.context, format.sha256(altered)) }
        }
    }

    @Test
    fun `full legacy envelope and maximum wrapper list fit separate generation bound`() {
        val one = GenerationFixture.generation()
        val metadata = one.envelope.envelopeMetadata()
        val plaintext = ByteArray(256 * 1024 - 44) { 1 }
        val envelope = AesGcmPasskeyBackupEnvelopeCryptography().encrypt(plaintext, metadata, ByteArray(32) { 0x77 })
        val record = PasskeyBackupEncryptedPayload(
            metadata.storageKey, metadata.walletId, metadata.accountName, metadata.createdAtMillis, envelope
        )
        val wrappers = (1..32).map {
            GenerationFixture.wrapper(Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { _ -> it.toByte() }))
        }
        val maximum = PasskeyBackupGeneration(one.context, record, wrappers)
        val encoded = format.encode(maximum)
        assertEquals(262_144, envelope.size)
        assertTrue(encoded.size > 262_144 && encoded.size <= 524_288)
        val decoded = format.decode(encoded, one.context, format.sha256(encoded))
        assertArrayEquals(envelope, decoded.envelope.encryptedPayload)
        assertEquals(32, decoded.wrappers.size)
        assertEquals(262_144, GoogleDriveHttpRequest("GET", "https://example.invalid").maxResponseBytes)
        assertEquals(524_288, GoogleDriveHttpRequest("GET", "https://example.invalid", maxResponseBytes = format.MAX_BYTES).maxResponseBytes)
    }

    @Test
    fun `binding is stable subject based and records do not disclose metadata in descriptions`() {
        assertEquals("a5b6fab414ef8a7721025c657815ec11b8723cfdc62ec716623270b6caacb490", format.storageAccountBinding("google-subject-123"))
        assertFalse(format.storageAccountBinding("other-subject") == GenerationFixture.context.storageAccountBinding)
        fails { format.storageAccountBinding("alice@example.com") }
        assertFalse(GenerationFixture.context.toString().contains("owner:"))
        assertFalse(GenerationFixture.generation().toString().contains("alice"))
    }

    @Test
    fun `long original Unicode email stays in bundle and malformed surrogate cannot encode`() {
        for (account in listOf("é".repeat(120) + "@example.com", "\uD800@example.com")) {
            val metadata = testEnvelopeMetadata(accountName = account)
            val encrypted = AesGcmPasskeyBackupEnvelopeCryptography().encrypt(
                "fixture".toByteArray(), metadata, ByteArray(32) { 0x77 }
            )
            val payload = PasskeyBackupEncryptedPayload(
                metadata.storageKey, metadata.walletId, account, metadata.createdAtMillis, encrypted
            )
            val generation = PasskeyBackupGeneration(
                GenerationFixture.context, payload,
                listOf(GenerationFixture.wrapper(GenerationFixture.json["credentialId"].asString, metadata))
            )
            if (account.startsWith("é")) {
                val bytes = format.encode(generation)
                assertEquals(account, format.decode(bytes, generation.context, format.sha256(bytes)).envelope.accountName)
            } else {
                fails { format.encode(generation) }
            }
        }
    }

    private fun fails(block: () -> Unit) = assertTrue(runCatching(block).isFailure)
}

internal object GenerationFixture {
    val json: JsonObject = requireNotNull(javaClass.getResourceAsStream("/passkey-generation-v1.json"))
        .bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
    val bytes: ByteArray get() = Base64.getUrlDecoder().decode(json["encodedBase64Url"].asString)
    val digest: String get() = json["sha256"].asString
    val context: PasskeyBackupGeneration.Context get() = json["context"].asJsonObject.let {
        PasskeyBackupGeneration.Context(
            it["ownerSubject"].asString, it["backupNamespace"].asString, it["generationId"].asString,
            it["parentHeadRevision"].asString.toLong(), it["parentHeadSha256"].asString,
            it["keyEpoch"].asString.toLong(), it["storageAccountBinding"].asString
        )
    }
    fun generation(): PasskeyBackupGeneration {
        val metadata = testEnvelopeMetadata()
        val record = PasskeyBackupEncryptedPayload(
            metadata.storageKey, metadata.walletId, metadata.accountName, metadata.createdAtMillis,
            Base64.getUrlDecoder().decode(json["envelopeBase64Url"].asString)
        )
        return PasskeyBackupGeneration(context, record, listOf(wrapper(json["credentialId"].asString)))
    }

    fun wrapper(
        credential: String,
        metadata: PasskeyBackupEnvelopeMetadata = testEnvelopeMetadata(),
        epoch: Long = context.keyEpoch
    ): PasskeyBackupCredentialKeyWrapperRecord {
        val wrapperContext = PasskeyBackupKeyWrapperContext(context.ownerSubject, credential, epoch, metadata)
        return PasskeyBackupCredentialKeyWrapper().wrapWithParameters(
            ByteArray(32) { 0x77 }, ByteArray(32) { 0x66 }, ByteArray(32) { 0x33 },
            ByteArray(32) { 0x44 }, ByteArray(12) { 0x55 }, wrapperContext
        )
    }
}
