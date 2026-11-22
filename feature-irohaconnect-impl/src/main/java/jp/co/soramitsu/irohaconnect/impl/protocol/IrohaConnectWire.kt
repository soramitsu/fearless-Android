// A one-to-one port of the browser wallet's wire.ts: wire tags, widths and limits, kept together for review against it.
// Infix bit operations keep explicit grouping.
@file:Suppress("MagicNumber", "LargeClass", "TooManyFunctions", "CommentOverPrivateFunction", "UnnecessaryParentheses")

package jp.co.soramitsu.irohaconnect.impl.protocol

import jp.co.soramitsu.irohaconnect.impl.protocol.NoritoBytes.byteOf
import jp.co.soramitsu.irohaconnect.impl.protocol.NoritoBytes.concat
import jp.co.soramitsu.irohaconnect.impl.protocol.NoritoBytes.elementFramed
import jp.co.soramitsu.irohaconnect.impl.protocol.NoritoBytes.field
import jp.co.soramitsu.irohaconnect.impl.protocol.NoritoBytes.struct
import jp.co.soramitsu.irohaconnect.impl.protocol.NoritoBytes.tagged
import jp.co.soramitsu.irohaconnect.impl.protocol.NoritoBytes.u16
import jp.co.soramitsu.irohaconnect.impl.protocol.NoritoBytes.u32
import jp.co.soramitsu.irohaconnect.impl.protocol.NoritoBytes.u64
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

// Wallet side of the current Iroha Connect v1 protocol: the one Torii relays on Taira and that
// `@iroha/iroha-js/connect-browser` and `@iroha/connect-widget` speak on the app side. A port of the browser wallet's
// `iroha-connect-service/wire.ts` (fearless-wallet-web PR #1122); upstream references are hyperledger-iroha
// `javascript/iroha_js/src/connect.browser.js` and `crates/iroha_torii_shared/examples/connect_wallet.rs`.

internal enum class IrohaConnectDirection { APP_TO_WALLET, WALLET_TO_APP }

internal enum class IrohaConnectRole { APP, WALLET }

internal data class IrohaConnectPermissions(val methods: List<String>, val events: List<String>, val resources: List<String>?)

internal data class IrohaConnectAppMeta(val name: String, val url: String? = null, val iconHash: String? = null)

internal sealed class IrohaConnectControl {
    class Open(
        val appPublicKey: ByteArray,
        val appMeta: IrohaConnectAppMeta?,
        val networkIdBytes: ByteArray,
        val permissions: IrohaConnectPermissions?,
        /** The exact `Option<Permissions>` field bytes, echoed in the approval. */
        val permissionsField: ByteArray
    ) : IrohaConnectControl()

    /** [nonce] is an unsigned 64-bit value. */
    data class Ping(val nonce: Long) : IrohaConnectControl()
    data class Pong(val nonce: Long) : IrohaConnectControl()
    data class Close(
        val who: IrohaConnectRole,
        val code: Int,
        val reason: String,
        val retryable: Boolean
    ) : IrohaConnectControl()
    data class Reject(val code: Int, val codeId: String, val reason: String) : IrohaConnectControl()
    object ServerEvent : IrohaConnectControl()
}

internal sealed class IrohaConnectFrame {
    abstract val direction: IrohaConnectDirection
    abstract val sequence: Long

    data class Control(
        override val direction: IrohaConnectDirection,
        override val sequence: Long,
        val control: IrohaConnectControl
    ) : IrohaConnectFrame()

    class Ciphertext(
        override val direction: IrohaConnectDirection,
        override val sequence: Long,
        val aead: ByteArray
    ) : IrohaConnectFrame()
}

internal sealed class IrohaConnectAppPayload {
    class SignRequestTx(val txBytes: ByteArray) : IrohaConnectAppPayload()
    class SignRequestRaw(val domainTag: String, val bytes: ByteArray) : IrohaConnectAppPayload()
    data class ControlClose(val who: IrohaConnectRole, val code: Int, val reason: String, val retryable: Boolean) :
        IrohaConnectAppPayload()
    data class ControlReject(val code: Int, val codeId: String, val reason: String) : IrohaConnectAppPayload()
    data class Unknown(val tag: Long) : IrohaConnectAppPayload()
}

internal sealed class IrohaConnectWalletPayload {
    class SignResultOk(val signature: ByteArray) : IrohaConnectWalletPayload()
    data class SignResultErr(val code: String, val message: String) : IrohaConnectWalletPayload()
    data class ControlClose(
        val code: Int,
        val reason: String,
        val retryable: Boolean = false
    ) : IrohaConnectWalletPayload()
}

/** Per-direction ChaCha20-Poly1305 keys of an approved session. */
internal class IrohaConnectDirectionKeys(val appKey: ByteArray, val walletKey: ByteArray) {
    fun wipe() {
        appKey.fill(0)
        walletKey.fill(0)
    }
}

internal object IrohaConnectLimits {
    const val FRAME_BYTES = 2L * 1024 * 1024
    const val TRANSACTION_BYTES = 1024L * 1024
    const val RAW_BYTES = 64L * 1024
    const val ACCOUNT_ID_BYTES = 1024
    const val APP_NAME_BYTES = 128
    const val APP_URL_BYTES = 2048
    const val ICON_HASH_BYTES = 256
    const val REASON_BYTES = 1024
    const val CODE_BYTES = 128
    const val DOMAIN_TAG_BYTES = 256
    const val PERMISSION_ENTRIES = 32
    const val PERMISSION_ENTRY_BYTES = 256

    /** Sequence numbers stay within JavaScript's safe-integer range, as the app SDK requires. */
    const val MAX_SEQUENCE = (1L shl 53) - 1
}

internal object IrohaConnectWire {
    private val AAD_PREFIX = "connect:v1".ascii()
    private val SALT_PREFIX = "iroha-connect|salt|".ascii()
    private val K_APP = "iroha-connect|k_app".ascii()
    private val K_WALLET = "iroha-connect|k_wallet".ascii()
    private val X25519_HKDF_SALT = "iroha:x25519:hkdf:v1".ascii()
    private val X25519_HKDF_INFO = "iroha:x25519:session-key".ascii()
    private val APPROVE_DOMAIN = "iroha-connect|approve|v1".ascii()
    private val RELAY_AUTH_DOMAIN = "iroha-connect|relay-auth|v1".ascii()
    private const val ENVELOPE_TYPE_NAME = "iroha_torii_shared::connect::EnvelopeV1"
    private val NORITO_MAGIC = "NRT0".ascii()
    const val NORITO_HEADER_BYTES = 40

    private const val FRAME_KIND_CONTROL = 0L
    private const val FRAME_KIND_CIPHERTEXT = 1L
    private const val DIR_APP_TO_WALLET = 0L
    private const val DIR_WALLET_TO_APP = 1L
    private const val ROLE_APP = 0L
    private const val ROLE_WALLET = 1L
    private const val CONTROL_OPEN = 0L
    private const val CONTROL_APPROVE = 1L
    private const val CONTROL_REJECT = 2L
    private const val CONTROL_CLOSE = 3L
    private const val CONTROL_PING = 4L
    private const val CONTROL_PONG = 5L
    private const val CONTROL_SERVER_EVENT = 6L
    private const val PAYLOAD_CONTROL = 0L
    private const val PAYLOAD_SIGN_REQUEST_RAW = 1L
    private const val PAYLOAD_SIGN_REQUEST_TX = 2L
    private const val PAYLOAD_SIGN_RESULT_OK = 3L
    private const val PAYLOAD_SIGN_RESULT_ERR = 4L
    private const val CONTROL_AFTER_KEY_CLOSE = 0L
    private const val CONTROL_AFTER_KEY_REJECT = 1L
    private const val ALGORITHM_ED25519 = 0
    private const val FIXED_BYTES = 32
    private val CODE_ID = Regex("^[A-Za-z][A-Za-z0-9_]*$")

    val envelopeSchemaHash: ByteArray = IrohaConnectCrypto.sha256(
        concat("norito:v1:type-name\u0000".ascii(), ENVELOPE_TYPE_NAME.ascii())
    ).copyOf(16)

    private fun String.ascii(): ByteArray = toByteArray(Charsets.UTF_8)

    private fun reader(bytes: ByteArray, label: String) =
        NoritoReader(bytes, label, compactLengths = false, fail = ::invalidFrame)

    // -----------------------------------------------------------------------------------------------------------
    // Text

    internal fun isJsWhitespace(character: Char): Boolean =
        character in "\t\n\u000B\u000C\r       　﻿" || character in ' '..' '

    internal fun isDisplayText(value: String): Boolean =
        value.isNotEmpty() && !isJsWhitespace(value.first()) && !isJsWhitespace(value.last()) &&
            value.none { it.code <= 0x1f || it.code in 0x7f..0x9f }

    private fun strictUtf8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

    /** A Norito `String` field value: non-empty trimmed display text within [maximum] UTF-8 bytes. */
    fun noritoString(
        value: String,
        label: String,
        maximum: Int
    ): ByteArray {
        require(isDisplayText(value)) { "$label must be non-empty trimmed text without control characters" }
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= maximum) { "$label exceeds $maximum UTF-8 bytes" }
        return field(bytes)
    }

    private fun decodeText(
        bytes: ByteArray,
        label: String,
        maximum: Int
    ): String {
        val textReader = reader(bytes, label)
        val value = textReader.readField("utf8", maximum.toLong())
        textReader.done()
        val text = strictUtf8(value) ?: invalidFrame("$label is not UTF-8")
        if (!isDisplayText(text)) invalidFrame("$label is not display text")
        return text
    }

    private fun decodeOptionText(
        bytes: ByteArray,
        label: String,
        maximum: Int
    ): String? {
        val optionReader = reader(bytes, label)
        return when (optionReader.readU8("tag")) {
            0 -> null.also { optionReader.done() }
            1 -> decodeText(optionReader.readField("value", maximum + 8L), label, maximum).also { optionReader.done() }
            else -> invalidFrame("$label has an invalid option tag")
        }
    }

    // -----------------------------------------------------------------------------------------------------------
    // Plain frames

    private fun fixed32(bytes: ByteArray, label: String): ByteArray {
        require(bytes.size == FIXED_BYTES) { "$label must be $FIXED_BYTES bytes" }
        return elementFramed(bytes)
    }

    private fun decodeFixed32(bytes: ByteArray, label: String): ByteArray {
        if (bytes.size != FIXED_BYTES * NoritoBytes.ELEMENT_FRAME_BYTES) invalidFrame("$label must be element-framed")
        val elementReader = reader(bytes, label)
        val output = ByteArray(FIXED_BYTES) { index ->
            val element = elementReader.readField("[$index]", 1)
            if (element.size != 1) invalidFrame("$label[$index] must be one byte")
            element[0]
        }
        elementReader.done()
        if (output.all { it.toInt() == 0 }) invalidFrame("$label must not be all zeroes")
        return output
    }

    private fun exactWidth(
        bytes: ByteArray,
        width: Int,
        label: String
    ): NoritoReader {
        if (bytes.size != width) invalidFrame("$label must be $width bytes")
        return reader(bytes, label)
    }

    private fun decodeAppMeta(bytes: ByteArray): IrohaConnectAppMeta? {
        val optionReader = reader(bytes, "app_meta")
        return when (optionReader.readU8("tag")) {
            0 -> null.also { optionReader.done() }
            1 -> {
                val meta = reader(optionReader.readField("value", 4096), "app_meta.value")
                optionReader.done()
                val name = decodeText(meta.readField("name", 256), "app_meta.name", IrohaConnectLimits.APP_NAME_BYTES)
                val url = decodeOptionText(meta.readField("url", 4096), "app_meta.url", IrohaConnectLimits.APP_URL_BYTES)
                val iconHash = decodeOptionText(meta.readField("icon_hash", 512), "app_meta.icon_hash", IrohaConnectLimits.ICON_HASH_BYTES)
                meta.done()
                IrohaConnectAppMeta(name, url, iconHash)
            }
            else -> invalidFrame("app_meta has an invalid option tag")
        }
    }

    private fun decodeStringVec(bytes: ByteArray, label: String): List<String> {
        val vecReader = reader(bytes, label)
        val count = vecReader.readU64("count")
        if (count < 0 || count > IrohaConnectLimits.PERMISSION_ENTRIES) invalidFrame("$label has too many entries")
        val values = List(count.toInt()) { index ->
            decodeText(
                vecReader.readField("[$index]", IrohaConnectLimits.PERMISSION_ENTRY_BYTES + 8L),
                "$label[$index]",
                IrohaConnectLimits.PERMISSION_ENTRY_BYTES
            )
        }
        vecReader.done()
        return values
    }

    private fun decodePermissions(bytes: ByteArray): IrohaConnectPermissions? {
        val optionReader = reader(bytes, "permissions")
        when (optionReader.readU8("tag")) {
            0 -> return null.also { optionReader.done() }
            1 -> Unit
            else -> invalidFrame("permissions has an invalid option tag")
        }
        val value = reader(optionReader.readField("value", 32 * 1024), "permissions.value")
        optionReader.done()
        val methods = decodeStringVec(value.readField("methods", 16 * 1024), "permissions.methods")
        val events = decodeStringVec(value.readField("events", 16 * 1024), "permissions.events")
        val resourcesOption = reader(value.readField("resources", 16 * 1024), "permissions.resources")
        value.done()
        val resources = when (resourcesOption.readU8("tag")) {
            0 -> null
            1 -> decodeStringVec(resourcesOption.readField("value", 16 * 1024), "permissions.resources")
            else -> invalidFrame("permissions.resources has an invalid option tag")
        }
        resourcesOption.done()
        return IrohaConnectPermissions(methods, events, resources)
    }

    private fun decodeRole(bytes: ByteArray, label: String): IrohaConnectRole =
        when (exactWidth(bytes, 4, label).readU32(label)) {
            ROLE_APP -> IrohaConnectRole.APP
            ROLE_WALLET -> IrohaConnectRole.WALLET
            else -> invalidFrame("$label is not a Connect role")
        }

    private fun decodeBool(bytes: ByteArray, label: String): Boolean {
        if (bytes.size != 1 || (bytes[0].toInt() and 0xff) > 1) invalidFrame("$label must be one boolean byte")
        return bytes[0].toInt() == 1
    }

    private fun decodeNonce(body: ByteArray, label: String): Long {
        val nonceReader = reader(body, label)
        val nonce = exactWidth(nonceReader.readField("nonce", 8), 8, "$label.nonce").readU64("nonce")
        nonceReader.done()
        return nonce
    }

    private fun decodeCloseBody(bodyReader: NoritoReader): IrohaConnectControl.Close {
        val who = decodeRole(bodyReader.readField("who", 4), "close.who")
        val code = exactWidth(bodyReader.readField("code", 2), 2, "close.code").readU16("code")
        val reason = decodeText(bodyReader.readField("reason"), "close.reason", IrohaConnectLimits.REASON_BYTES)
        val retryable = decodeBool(bodyReader.readField("retryable", 1), "close.retryable")
        bodyReader.done()
        return IrohaConnectControl.Close(who, code, reason, retryable)
    }

    private fun decodeRejectBody(bodyReader: NoritoReader): IrohaConnectControl.Reject {
        val code = exactWidth(bodyReader.readField("code", 2), 2, "reject.code").readU16("code")
        val codeId = decodeText(bodyReader.readField("code_id"), "reject.code_id", IrohaConnectLimits.CODE_BYTES)
        val reason = decodeText(bodyReader.readField("reason"), "reject.reason", IrohaConnectLimits.REASON_BYTES)
        bodyReader.done()
        return IrohaConnectControl.Reject(code, codeId, reason)
    }

    private fun decodeControl(bytes: ByteArray): IrohaConnectControl {
        val outer = reader(bytes, "control")
        val tag = outer.readU32("tag")
        val body = outer.readField("body", IrohaConnectLimits.FRAME_BYTES)
        outer.done()
        val bodyReader = reader(body, "control.body")
        return when (tag) {
            CONTROL_OPEN -> {
                val appPublicKey = decodeFixed32(
                    bodyReader.readField("app_pk", FIXED_BYTES * NoritoBytes.ELEMENT_FRAME_BYTES.toLong()),
                    "app_pk"
                )
                val appMeta = decodeAppMeta(bodyReader.readField("app_meta", 8192))
                val constraints = reader(bodyReader.readField("constraints", 64), "constraints")
                val networkIdBytes = constraints.readField("network_id", FIXED_BYTES.toLong())
                constraints.done()
                if (!IrohaNetworkId.isMarked(networkIdBytes)) invalidFrame("constraints.network_id must be 32 marked hash bytes")
                val permissionsField = bodyReader.readField("permissions", 64 * 1024)
                bodyReader.done()
                IrohaConnectControl.Open(appPublicKey, appMeta, networkIdBytes, decodePermissions(permissionsField), permissionsField)
            }
            CONTROL_PING -> IrohaConnectControl.Ping(decodeNonce(body, "heartbeat"))
            CONTROL_PONG -> IrohaConnectControl.Pong(decodeNonce(body, "heartbeat"))
            CONTROL_CLOSE -> decodeCloseBody(bodyReader)
            CONTROL_REJECT -> decodeRejectBody(bodyReader)
            CONTROL_SERVER_EVENT -> IrohaConnectControl.ServerEvent
            else -> invalidFrame("the wallet does not accept control tag $tag")
        }
    }

    private fun decodeDirection(bytes: ByteArray, label: String): IrohaConnectDirection =
        when (exactWidth(bytes, 4, label).readU32(label)) {
            DIR_APP_TO_WALLET -> IrohaConnectDirection.APP_TO_WALLET
            DIR_WALLET_TO_APP -> IrohaConnectDirection.WALLET_TO_APP
            else -> invalidFrame("$label is not a Connect direction")
        }

    fun decodeFrame(bytes: ByteArray, sidBytes: ByteArray): IrohaConnectFrame {
        if (bytes.isEmpty() || bytes.size > IrohaConnectLimits.FRAME_BYTES) invalidFrame("frame size is invalid")
        val frameReader = reader(bytes, "frame")
        val sid = decodeFixed32(frameReader.readField("sid", FIXED_BYTES * NoritoBytes.ELEMENT_FRAME_BYTES.toLong()), "frame.sid")
        if (!NoritoBytes.constantTimeEquals(sid, sidBytes)) invalidFrame("frame belongs to another session")
        val direction = decodeDirection(frameReader.readField("dir", 4), "frame.dir")
        val sequence = exactWidth(frameReader.readField("seq", 8), 8, "frame.seq").readU64("seq")
        if (sequence < 0 || sequence > IrohaConnectLimits.MAX_SEQUENCE) invalidFrame("frame.seq is too large")
        if (sequence == 0L) invalidFrame("frame.seq must be positive")
        val kind = reader(frameReader.readField("kind", IrohaConnectLimits.FRAME_BYTES), "frame.kind")
        frameReader.done()
        val kindTag = kind.readU32("tag")
        val body = kind.readField("body", IrohaConnectLimits.FRAME_BYTES)
        kind.done()

        if (kindTag == FRAME_KIND_CONTROL) return IrohaConnectFrame.Control(direction, sequence, decodeControl(body))
        if (kindTag != FRAME_KIND_CIPHERTEXT) invalidFrame("unsupported frame kind $kindTag")

        val ciphertext = reader(body, "ciphertext")
        val innerDirection = decodeDirection(ciphertext.readField("dir", 4), "ciphertext.dir")
        val aeadField = reader(ciphertext.readField("aead", IrohaConnectLimits.FRAME_BYTES), "ciphertext.aead")
        ciphertext.done()
        val aead = aeadField.readField("bytes", IrohaConnectLimits.FRAME_BYTES)
        aeadField.done()
        if (innerDirection != direction) invalidFrame("ciphertext direction does not match the frame")
        if (aead.isEmpty()) invalidFrame("ciphertext is empty")
        return IrohaConnectFrame.Ciphertext(direction, sequence, aead)
    }

    private fun encodeFrame(
        sidBytes: ByteArray,
        sequence: Long,
        kind: ByteArray
    ): ByteArray {
        require(sequence in 1..IrohaConnectLimits.MAX_SEQUENCE) { "sequence must be a positive safe integer" }
        return struct(listOf(fixed32(sidBytes, "sid"), u32(DIR_WALLET_TO_APP), u64(sequence), kind))
    }

    private fun controlFrame(
        sidBytes: ByteArray,
        sequence: Long,
        controlTag: Long,
        body: ByteArray
    ): ByteArray = encodeFrame(sidBytes, sequence, tagged(FRAME_KIND_CONTROL, tagged(controlTag, body)))

    private fun taggedApprovalField(tag: String, value: ByteArray): ByteArray {
        val tagBytes = tag.ascii()
        return concat(u16(tagBytes.size), tagBytes, u64(value.size.toLong()), value)
    }

    /** Returns the `Option<Permissions>` value bytes, or null for `None`. */
    private fun unwrapOption(bytes: ByteArray, label: String): ByteArray? {
        if (bytes.size == 1 && bytes[0].toInt() == 0) return null
        val optionReader = reader(bytes, label)
        if (optionReader.readU8("tag") != 1) invalidFrame("$label has an invalid option tag")
        val value = optionReader.readField("value", IrohaConnectLimits.FRAME_BYTES)
        optionReader.done()
        return value
    }

    fun relayAuthHash(sidBytes: ByteArray, relayToken: String): ByteArray =
        IrohaConnectCrypto.sha256(concat(RELAY_AUTH_DOMAIN, sidBytes, relayToken.toByteArray(Charsets.UTF_8)))

    /** The tagged structure the wallet signs with the account key; the app verifies it before deriving keys. */
    fun approvalPreimage(
        sidBytes: ByteArray,
        networkIdBytes: ByteArray,
        appPublicKey: ByteArray,
        walletPublicKey: ByteArray,
        accountId: String,
        permissionsField: ByteArray,
        relayToken: String
    ): ByteArray {
        require(networkIdBytes.size == FIXED_BYTES) { "networkId must be 32 bytes" }
        noritoString(accountId, "accountId", IrohaConnectLimits.ACCOUNT_ID_BYTES)
        val permissions = unwrapOption(permissionsField, "permissions")
        val fields = mutableListOf(
            taggedApprovalField("domain", APPROVE_DOMAIN),
            taggedApprovalField("network_id", networkIdBytes),
            taggedApprovalField("constraints", IrohaConnectCrypto.blake2b256(struct(listOf(networkIdBytes)))),
            taggedApprovalField("sid", sidBytes),
            taggedApprovalField("app_pk", appPublicKey),
            taggedApprovalField("wallet_pk", walletPublicKey),
            taggedApprovalField("account_id", accountId.toByteArray(Charsets.UTF_8))
        )
        if (permissions != null) fields += taggedApprovalField("permissions", IrohaConnectCrypto.blake2b256(permissions))
        fields += taggedApprovalField("relay_auth", relayAuthHash(sidBytes, relayToken))
        return concat(fields)
    }

    fun encodeApproveFrame(
        sidBytes: ByteArray,
        sequence: Long,
        walletPublicKey: ByteArray,
        accountId: String,
        permissionsField: ByteArray,
        signature: ByteArray
    ): ByteArray {
        require(signature.size == IrohaConnectCrypto.SIGNATURE_BYTES) { "approval signature must be 64 bytes" }
        unwrapOption(permissionsField, "permissions")
        // Torii decodes and re-encodes plain frames: `[u8; 32]` element-framed, and the wallet signature as a u32
        // algorithm plus a counted, element-framed byte sequence.
        val walletSignature = struct(
            listOf(u32(ALGORITHM_ED25519.toLong()), concat(u64(signature.size.toLong()), elementFramed(signature)))
        )
        val body = struct(
            listOf(
                fixed32(walletPublicKey, "walletPublicKey"),
                noritoString(accountId, "accountId", IrohaConnectLimits.ACCOUNT_ID_BYTES),
                permissionsField,
                byteOf(0),
                walletSignature
            )
        )
        return controlFrame(sidBytes, sequence, CONTROL_APPROVE, body)
    }

    fun encodeRejectFrame(
        sidBytes: ByteArray,
        sequence: Long,
        code: Int,
        codeId: String,
        reason: String
    ): ByteArray {
        require(CODE_ID.matches(codeId)) { "codeId must be an identifier" }
        return controlFrame(
            sidBytes,
            sequence,
            CONTROL_REJECT,
            struct(
                listOf(
                    u16(code),
                    noritoString(codeId, "codeId", IrohaConnectLimits.CODE_BYTES),
                    noritoString(reason, "reason", IrohaConnectLimits.REASON_BYTES)
                )
            )
        )
    }

    fun encodePingFrame(
        sidBytes: ByteArray,
        sequence: Long,
        nonce: Long
    ): ByteArray = controlFrame(sidBytes, sequence, CONTROL_PING, struct(listOf(u64(nonce))))

    fun encodePongFrame(
        sidBytes: ByteArray,
        sequence: Long,
        nonce: Long
    ): ByteArray = controlFrame(sidBytes, sequence, CONTROL_PONG, struct(listOf(u64(nonce))))

    // -----------------------------------------------------------------------------------------------------------
    // Session keys and encrypted envelopes

    fun deriveDirectionKeys(
        walletPrivateKey: ByteArray,
        appPublicKey: ByteArray,
        sidBytes: ByteArray
    ): IrohaConnectDirectionKeys {
        val shared = IrohaConnectCrypto.x25519SharedSecret(walletPrivateKey, appPublicKey)
        val sessionKey = IrohaConnectCrypto.hkdfSha256(shared, X25519_HKDF_SALT, X25519_HKDF_INFO, 32)
        try {
            val salt = IrohaConnectCrypto.blake2b256(concat(SALT_PREFIX, sidBytes))
            return IrohaConnectDirectionKeys(
                appKey = IrohaConnectCrypto.hkdfSha256(sessionKey, salt, K_APP, 32),
                walletKey = IrohaConnectCrypto.hkdfSha256(sessionKey, salt, K_WALLET, 32)
            )
        } finally {
            shared.fill(0)
            sessionKey.fill(0)
        }
    }

    internal fun frameNorito(
        payload: ByteArray,
        schemaHash: ByteArray = envelopeSchemaHash,
        flags: Int = 0
    ): ByteArray = concat(
        NORITO_MAGIC,
        byteArrayOf(0, 0),
        schemaHash,
        byteOf(0),
        u64(payload.size.toLong()),
        u64(IrohaConnectCrypto.crc64Xz(payload)),
        byteOf(flags),
        payload
    )

    private fun unframeNorito(bytes: ByteArray): ByteArray {
        if (bytes.size < NORITO_HEADER_BYTES || !bytes.copyOfRange(0, 4).contentEquals(NORITO_MAGIC)) {
            invalidFrame("envelope is not an NRT0 frame")
        }
        if (bytes[4].toInt() != 0 || bytes[5].toInt() != 0) invalidFrame("envelope uses an unsupported Norito version")
        if (!bytes.copyOfRange(6, 22).contentEquals(envelopeSchemaHash)) invalidFrame("envelope schema does not match")
        if (bytes[22].toInt() != 0 || bytes[39].toInt() != 0) invalidFrame("envelope uses unsupported layout flags")
        val header = reader(bytes.copyOfRange(23, 39), "envelope.header")
        val length = header.readU64("length")
        val checksum = header.readU64("checksum")
        if (length != (bytes.size - NORITO_HEADER_BYTES).toLong()) invalidFrame("envelope length does not match")
        val payload = bytes.copyOfRange(NORITO_HEADER_BYTES, bytes.size)
        if (IrohaConnectCrypto.crc64Xz(payload) != checksum) invalidFrame("envelope checksum does not match")
        return payload
    }

    private fun aeadNonce(sequence: Long): ByteArray = concat(ByteArray(4), u64(sequence))

    private fun aeadData(
        sidBytes: ByteArray,
        direction: Long,
        sequence: Long
    ): ByteArray = concat(AAD_PREFIX, sidBytes, byteOf(direction.toInt()), u64(sequence), byteOf(1))

    private fun decodeNoritoBytesField(
        bytes: ByteArray,
        label: String,
        maximum: Long
    ): ByteArray {
        val bytesReader = reader(bytes, label)
        val value = bytesReader.readField("bytes", maximum)
        bytesReader.done()
        return value
    }

    private fun decodeAfterKeyControl(bytes: ByteArray): IrohaConnectAppPayload {
        val controlReader = reader(bytes, "payload.control")
        return when (controlReader.readU32("tag")) {
            CONTROL_AFTER_KEY_CLOSE -> decodeCloseBody(controlReader).let {
                IrohaConnectAppPayload.ControlClose(it.who, it.code, it.reason, it.retryable)
            }
            CONTROL_AFTER_KEY_REJECT -> decodeRejectBody(controlReader).let {
                IrohaConnectAppPayload.ControlReject(it.code, it.codeId, it.reason)
            }
            else -> IrohaConnectAppPayload.Unknown(PAYLOAD_CONTROL)
        }
    }

    private fun decodeAppPayload(bytes: ByteArray): IrohaConnectAppPayload {
        val payloadReader = reader(bytes, "payload")
        return when (val tag = payloadReader.readU32("tag")) {
            PAYLOAD_SIGN_REQUEST_TX -> {
                val txBytes = decodeNoritoBytesField(
                    payloadReader.readField("tx_bytes", IrohaConnectLimits.FRAME_BYTES),
                    "tx_bytes",
                    IrohaConnectLimits.TRANSACTION_BYTES
                )
                payloadReader.done()
                IrohaConnectAppPayload.SignRequestTx(txBytes)
            }
            PAYLOAD_SIGN_REQUEST_RAW -> {
                val domainTag = decodeText(
                    payloadReader.readField("domain_tag", IrohaConnectLimits.FRAME_BYTES),
                    "domain_tag",
                    IrohaConnectLimits.DOMAIN_TAG_BYTES
                )
                val raw = decodeNoritoBytesField(payloadReader.readField("bytes", IrohaConnectLimits.FRAME_BYTES), "bytes", IrohaConnectLimits.RAW_BYTES)
                payloadReader.done()
                IrohaConnectAppPayload.SignRequestRaw(domainTag, raw)
            }
            PAYLOAD_CONTROL -> {
                val control = payloadReader.readField("control", IrohaConnectLimits.FRAME_BYTES)
                payloadReader.done()
                decodeAfterKeyControl(control)
            }
            else -> IrohaConnectAppPayload.Unknown(tag)
        }
    }

    fun openEnvelope(
        appKey: ByteArray,
        sidBytes: ByteArray,
        frame: IrohaConnectFrame.Ciphertext
    ): IrohaConnectAppPayload {
        if (frame.direction != IrohaConnectDirection.APP_TO_WALLET) invalidFrame("the wallet opens only app ciphertext")
        val plaintext = IrohaConnectCrypto.chaCha20Poly1305Open(
            appKey,
            aeadNonce(frame.sequence),
            aeadData(sidBytes, DIR_APP_TO_WALLET, frame.sequence),
            frame.aead
        ) ?: invalidFrame("ciphertext does not authenticate")
        try {
            val envelope = reader(unframeNorito(plaintext), "envelope")
            val sequence = exactWidth(envelope.readField("seq", 8), 8, "envelope.seq").readU64("seq")
            val payload = envelope.readField("payload", IrohaConnectLimits.FRAME_BYTES)
            envelope.done()
            if (sequence != frame.sequence) invalidFrame("envelope sequence does not match the frame")
            return decodeAppPayload(payload)
        } finally {
            plaintext.fill(0)
        }
    }

    private fun encodeWalletPayload(payload: IrohaConnectWalletPayload): ByteArray = when (payload) {
        is IrohaConnectWalletPayload.SignResultOk -> {
            require(payload.signature.size == IrohaConnectCrypto.SIGNATURE_BYTES) { "signature must be 64 bytes" }
            concat(u32(PAYLOAD_SIGN_RESULT_OK), field(struct(listOf(byteOf(ALGORITHM_ED25519), field(payload.signature)))))
        }
        is IrohaConnectWalletPayload.SignResultErr -> concat(
            u32(PAYLOAD_SIGN_RESULT_ERR),
            field(noritoString(payload.code, "code", IrohaConnectLimits.CODE_BYTES)),
            field(noritoString(payload.message, "message", IrohaConnectLimits.REASON_BYTES))
        )
        is IrohaConnectWalletPayload.ControlClose -> concat(
            u32(PAYLOAD_CONTROL),
            field(
                concat(
                    u32(CONTROL_AFTER_KEY_CLOSE),
                    field(u32(ROLE_WALLET)),
                    field(u16(payload.code)),
                    field(noritoString(payload.reason, "reason", IrohaConnectLimits.REASON_BYTES)),
                    field(byteOf(if (payload.retryable) 1 else 0))
                )
            )
        )
    }

    fun sealEnvelope(
        walletKey: ByteArray,
        sidBytes: ByteArray,
        sequence: Long,
        payload: IrohaConnectWalletPayload
    ): ByteArray {
        val plaintext = frameNorito(struct(listOf(u64(sequence), encodeWalletPayload(payload))))
        try {
            val aead = IrohaConnectCrypto.chaCha20Poly1305Seal(
                walletKey,
                aeadNonce(sequence),
                aeadData(sidBytes, DIR_WALLET_TO_APP, sequence),
                plaintext
            )
            return encodeFrame(sidBytes, sequence, tagged(FRAME_KIND_CIPHERTEXT, struct(listOf(u32(DIR_WALLET_TO_APP), field(aead)))))
        } finally {
            plaintext.fill(0)
        }
    }

    /** Test seam: the app side's sealing, so unit tests can play the dApp against the wallet. */
    internal fun sealAppEnvelopeForTest(
        appKey: ByteArray,
        sidBytes: ByteArray,
        sequence: Long,
        payload: ByteArray
    ): ByteArray {
        val plaintext = frameNorito(struct(listOf(u64(sequence), payload)))
        val aead = IrohaConnectCrypto.chaCha20Poly1305Seal(appKey, aeadNonce(sequence), aeadData(sidBytes, DIR_APP_TO_WALLET, sequence), plaintext)
        return struct(
            listOf(
                fixed32(sidBytes, "sid"),
                u32(DIR_APP_TO_WALLET),
                u64(sequence),
                tagged(FRAME_KIND_CIPHERTEXT, struct(listOf(u32(DIR_APP_TO_WALLET), field(aead))))
            )
        )
    }

    /** Test seam: opens a wallet envelope with the wallet key, as the app SDK does. */
    internal fun openWalletEnvelopeForTest(
        walletKey: ByteArray,
        sidBytes: ByteArray,
        frame: IrohaConnectFrame.Ciphertext
    ): ByteArray {
        val plaintext = IrohaConnectCrypto.chaCha20Poly1305Open(
            walletKey,
            aeadNonce(frame.sequence),
            aeadData(sidBytes, DIR_WALLET_TO_APP, frame.sequence),
            frame.aead
        ) ?: invalidFrame("ciphertext does not authenticate")
        val envelope = reader(unframeNorito(plaintext), "envelope")
        envelope.readField("seq", 8)
        return envelope.readField("payload", IrohaConnectLimits.FRAME_BYTES).also { envelope.done() }
    }
}
