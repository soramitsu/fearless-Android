package jp.co.soramitsu.backup.passkey

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Base64

/** Canonical FPBKGEN1 bytes. Digest/context validation does not unwrap a key or decrypt a backup. */
object PasskeyBackupGenerationFormat {
    const val MAX_BYTES = 512 * 1024
    private const val VERSION = 1
    private const val HEX_RADIX = 16
    private const val IDENTIFIER_BYTES = 32
    private const val SHA256_HEX_LENGTH = 64
    private const val MAX_STRING_BYTES = 2048
    private const val MAX_WRAPPER_BYTES = 8192
    private val MAGIC = "FPBKGEN1".toByteArray(Charsets.US_ASCII)
    private val HEX = Regex("^[0-9a-f]{64}$")
    private val ID = Regex("^[A-Za-z0-9_-]{43}$")

    fun encode(generation: PasskeyBackupGeneration): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.write(MAGIC)
            data.writeInt(VERSION)
            data.writeContext(generation.context)
            val record = generation.envelope
            data.writeText(record.storageKey)
            data.writeText(record.walletId)
            data.writeText(record.accountName)
            data.writeLong(record.createdAtMillis)
            data.writeInt(record.schemaVersion)
            data.writeBytesWithLength(record.encryptedPayload)
            data.writeInt(generation.wrappers.size)
            val wrapper = PasskeyBackupCredentialKeyWrapper()
            generation.wrappers.forEach {
                data.writeText(it.context.credentialId)
                data.writeBytesWithLength(wrapper.encodeRecord(it))
            }
        }
        return output.toByteArray().also { require(it.size <= MAX_BYTES) { "Generation is too large" } }
    }

    /** Caller must obtain both expected values from its authenticated head or durable candidate journal. */
    fun decode(
        encoded: ByteArray,
        expectedContext: PasskeyBackupGeneration.Context,
        expectedSha256: String
    ): PasskeyBackupGeneration {
        require(encoded.size in 1..MAX_BYTES) { "Invalid generation size" }
        requireSha256(expectedSha256)
        val snapshot = encoded.copyOf()
        require(sha256(snapshot) == expectedSha256) { "Generation digest mismatch" }
        return DataInputStream(ByteArrayInputStream(snapshot)).use { data ->
            require(data.readExact(MAGIC.size).contentEquals(MAGIC) && data.readInt() == VERSION) {
                "Invalid generation format"
            }
            val context = data.readContext()
            require(context == expectedContext) { "Generation context mismatch" }
            val storageKey = data.readText()
            val walletId = data.readText()
            val accountName = data.readText()
            val createdAt = data.readLong()
            val schema = data.readInt()
            val record = PasskeyBackupEncryptedPayload(
                storageKey, walletId, accountName, createdAt, data.readBytesWithLength(MAX_BYTES), schema
            )
            val wrappers = data.readWrappers(context, record.envelopeMetadata())
            require(data.read() == -1) { "Trailing generation bytes" }
            PasskeyBackupGeneration(context, record, wrappers)
        }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    fun storageAccountBinding(googleSubject: String): String {
        GoogleDriveVerifiedIdentity.requireSubject(googleSubject)
        return sha256("FPBK-GOOGLE-SUB-v1\u0000".toByteArray(Charsets.US_ASCII) + googleSubject.toByteArray(Charsets.US_ASCII))
    }

    internal fun requireSha256(value: String) {
        require(value.length == SHA256_HEX_LENGTH && HEX.matches(value)) { "Invalid generation digest" }
    }

    internal fun requireIdentifier(value: String, prefix: String = "") {
        require(value.startsWith(prefix)) { "Invalid generation identifier" }
        val raw = value.removePrefix(prefix)
        require(ID.matches(raw)) { "Invalid generation identifier" }
        val bytes = Base64.getUrlDecoder().decode(raw)
        require(bytes.size == IDENTIFIER_BYTES && Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) == raw) {
            "Noncanonical generation identifier"
        }
    }

    private fun DataOutputStream.writeContext(context: PasskeyBackupGeneration.Context) {
        writeText(context.ownerSubject)
        writeText(context.backupNamespace)
        writeText(context.generationId)
        writeLong(context.parentHeadRevision)
        writeByte(if (context.parentHeadSha256 == null) 0 else 1)
        context.parentHeadSha256?.let { write(hexBytes(it)) }
        writeLong(context.keyEpoch)
        write(hexBytes(context.storageAccountBinding))
    }

    private fun DataInputStream.readContext(): PasskeyBackupGeneration.Context {
        val owner = readText()
        val namespace = readText()
        val generationId = readText()
        val parentRevision = readLong()
        val parentPresent = readUnsignedByte()
        require(parentPresent in 0..1) { "Invalid generation parent marker" }
        val parent = if (parentPresent == 1) readExact(IDENTIFIER_BYTES).toHex() else null
        return PasskeyBackupGeneration.Context(
            owner, namespace, generationId, parentRevision, parent, readLong(), readExact(IDENTIFIER_BYTES).toHex()
        )
    }

    private fun DataInputStream.readWrappers(
        context: PasskeyBackupGeneration.Context,
        metadata: PasskeyBackupEnvelopeMetadata
    ): List<PasskeyBackupCredentialKeyWrapperRecord> {
        val count = readInt()
        require(count in 1..PasskeyBackupGeneration.MAX_CREDENTIALS) { "Invalid generation wrapper count" }
        val codec = PasskeyBackupCredentialKeyWrapper()
        var previousId: String? = null
        return List(count) {
            val id = readText()
            require(previousId == null || id > requireNotNull(previousId)) { "Noncanonical generation wrapper order" }
            previousId = id
            val expected = PasskeyBackupKeyWrapperContext(context.ownerSubject, id, context.keyEpoch, metadata)
            codec.decodeRecord(readBytesWithLength(MAX_WRAPPER_BYTES), expected)
        }
    }

    private fun DataOutputStream.writeText(text: String) {
        val encoder = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
        val buffer = encoder.encode(CharBuffer.wrap(text))
        require(buffer.remaining() <= MAX_STRING_BYTES) { "Generation string is too long" }
        writeBytesWithLength(ByteArray(buffer.remaining()).also(buffer::get))
    }

    private fun DataInputStream.readText(): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(readBytesWithLength(MAX_STRING_BYTES))).toString()

    private fun DataOutputStream.writeBytesWithLength(bytes: ByteArray) {
        require(bytes.size <= MAX_BYTES) { "Generation field is too large" }
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readBytesWithLength(maximum: Int): ByteArray {
        val count = readInt()
        require(count in 1..maximum && count <= available()) { "Invalid generation field length" }
        return readExact(count)
    }

    private fun DataInputStream.readExact(count: Int): ByteArray = ByteArray(count).also(::readFully)
    private fun hexBytes(value: String): ByteArray = value.chunked(2).map { it.toInt(HEX_RADIX).toByte() }.toByteArray()
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
