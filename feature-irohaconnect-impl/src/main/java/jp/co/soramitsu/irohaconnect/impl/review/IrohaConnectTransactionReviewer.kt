// A port of transactionReview.ts and the iroha-js codec's validators and encoders, kept field-for-field reviewable against them.
// Infix bit operations keep explicit grouping.
@file:Suppress("MagicNumber", "LargeClass", "TooManyFunctions", "CyclomaticComplexMethod", "ComplexCondition", "CommentOverPrivateFunction", "UnnecessaryParentheses")

package jp.co.soramitsu.irohaconnect.impl.review

import jp.co.soramitsu.common.utils.IrohaAddressCodec
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectCrypto
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectEncoding
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectProfiles
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaConnectWire
import jp.co.soramitsu.irohaconnect.impl.protocol.IrohaNetworkId
import jp.co.soramitsu.irohaconnect.impl.protocol.NoritoBytes
import jp.co.soramitsu.irohaconnect.impl.protocol.NoritoReader
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * A Connect `SignRequestTx` carries an unsigned transaction payload. The wallet signs its hash only after decoding
 * every signature-bound field into a review and proving the review is the payload: the decoded values must satisfy
 * the iroha-js browser codec's canonical rules and re-encode to the exact bytes received. Anything outside the
 * reviewable set (instructions that need the native decoder, proof attachments, foreign authorities) is refused,
 * never signed. A port of the browser wallet's `transactionReview.ts` plus the iroha-js 0.0.3 transaction codec's
 * encoders and validators, cross-checked against both by `src/test/resources/irohaconnect/review-vectors.json`.
 */
object IrohaConnectTransactionReviewer {
    private const val MAX_PAYLOAD_BYTES = 1024 * 1024
    private const val MAX_REVIEW_TEXT = 512
    private const val MAX_METADATA_TEXT = 2048
    private const val MAX_ENTRIES = 64
    private const val MAX_FEE_LIMITS = 2
    private const val MAX_NUMERIC_SCALE = 28
    private const val MAX_NUMERIC_BITS = 511
    private const val MAX_MANTISSA_BYTES = 64
    private const val MAX_CONTRACT_ARGUMENT_BYTES = 1024 * 1024
    private const val MAX_CONTRACT_ADDRESS_BYTES = 256
    private const val MAX_ENTRYPOINT_BYTES = 1024
    private const val MAX_PROGRAM_NAME_BYTES = 255
    private const val ASSET_DEFINITION_VERSION = 1
    private const val WIRE_ID_TRANSFER = "iroha.transfer"
    private const val COMPACT_LEN_FLAG = 0x02
    private const val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val TRANSFER_SCHEMA_HASH = IrohaConnectEncoding.unhex("a4174c78d6341f8f98fc2adae8ed67b9")
    private val LEGACY_FEE_METADATA_KEYS = setOf("fee_sponsor", "gas_limit", "gas_asset_id")

    private fun invalid(detail: String): Nothing =
        throw IrohaConnectTransactionException(IrohaConnectTransactionErrorCode.INVALID_TRANSACTION, detail)

    private fun unsupported(detail: String): Nothing =
        throw IrohaConnectTransactionException(IrohaConnectTransactionErrorCode.UNSUPPORTED_TRANSACTION, detail)

    private fun reader(bytes: ByteArray, label: String) =
        NoritoReader(bytes, label, compactLengths = true, fail = ::invalid)

    // ------------------------------------------------------------------------------------------------------------
    // Review text

    /** Escape control, format (including bidi) and line-separator characters, then bound the text. */
    fun sanitize(value: String, maximum: Int = MAX_REVIEW_TEXT): String {
        val escaped = buildString {
            var index = 0
            while (index < value.length) {
                val codePoint = value.codePointAt(index)
                val type = Character.getType(codePoint)
                if (type == Character.CONTROL.toInt() || type == Character.FORMAT.toInt() || codePoint == 0x2028 || codePoint == 0x2029) {
                    append("\\u{").append(Integer.toHexString(codePoint).uppercase()).append('}')
                } else {
                    appendCodePoint(codePoint)
                }
                index += Character.charCount(codePoint)
            }
        }
        return if (escaped.length > maximum) escaped.substring(0, maximum - 1) + "…" else escaped
    }

    // ------------------------------------------------------------------------------------------------------------
    // Decoding

    private fun strictUtf8(bytes: ByteArray, label: String): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        invalid("$label is not UTF-8")
    }

    private fun readText(archive: ByteArray, label: String): String {
        val textReader = reader(archive, label)
        val value = textReader.readField("utf8")
        textReader.done()
        return strictUtf8(value, label)
    }

    private fun readU64Field(bytes: ByteArray, label: String): Long {
        if (bytes.size != 8) invalid("$label must be a u64")
        return reader(bytes, label).readU64(label)
    }

    /** Returns null for `None`; the value is unsigned. */
    private fun readOptionUnsigned(
        bytes: ByteArray,
        width: Int,
        label: String
    ): Long? {
        val optionReader = reader(bytes, label)
        return when (optionReader.readU8("tag")) {
            0 -> null.also { optionReader.done() }
            1 -> {
                val value = optionReader.readField("value")
                optionReader.done()
                if (value.size != width) invalid("$label has the wrong width")
                val valueReader = reader(value, label)
                if (width == 8) valueReader.readU64(label) else valueReader.readU32(label)
            }
            else -> invalid("$label has an invalid option tag")
        }
    }

    private fun readElementBytes(
        bytes: ByteArray,
        count: Int,
        label: String
    ): ByteArray {
        val elementReader = reader(bytes, label)
        val output = ByteArray(count) { index ->
            val element = elementReader.readField("[$index]")
            if (element.size != 1) invalid("$label[$index] must be one byte")
            element[0]
        }
        elementReader.done()
        return output
    }

    private class DecodedAccount(val literal: String, val publicKey: ByteArray)

    private fun readAccount(
        archive: ByteArray,
        label: String,
        networkPrefix: Int
    ): DecodedAccount {
        val accountReader = reader(archive, label)
        if (accountReader.readU32("controller") != 0L) unsupported("$label is not a single-key account")
        val controller = reader(accountReader.readField("key"), "$label.key")
        accountReader.done()
        if (controller.readU64("count") != 33L) invalid("$label key must be 33 bytes")
        val key = readElementBytes(controller.readBytes(controller.remaining.toLong(), "bytes"), 33, "$label.key")
        if (key[0].toInt() != 0) unsupported("$label is not an Ed25519 account")
        val publicKey = key.copyOfRange(1, 33)
        val literal = try {
            IrohaAddressCodec.encode(IrohaConnectEncoding.hex(publicKey), networkPrefix)
        } catch (_: Exception) {
            invalid("$label has an invalid public key")
        }
        return DecodedAccount(literal, publicKey)
    }

    private fun base58(bytes: ByteArray): String {
        var value = BigInteger(1, bytes)
        val output = StringBuilder()
        val base = BigInteger.valueOf(58)
        while (value.signum() > 0) {
            val (quotient, remainder) = value.divideAndRemainder(base)
            output.append(BASE58_ALPHABET[remainder.toInt()])
            value = quotient
        }
        for (byte in bytes) {
            if (byte.toInt() != 0) break
            output.append('1')
        }
        return output.reverse().toString()
    }

    private class DecodedAsset(val definition: ByteArray, val review: IrohaConnectReviewAsset)

    private fun readAssetDefinition(archive: ByteArray, label: String): DecodedAsset {
        val definition = readElementBytes(archive, 16, label)
        val versioned = byteArrayOf(ASSET_DEFINITION_VERSION.toByte()) + definition
        val definitionId = base58(versioned + IrohaConnectCrypto.blake3(versioned).copyOf(4))
        return DecodedAsset(definition, IrohaConnectReviewAsset(definitionId, IrohaConnectProfiles.knownAssets[definitionId]))
    }

    /** Returns the dataspace id, or null for an unscoped holding. */
    private fun readScope(archive: ByteArray, label: String): Long? {
        val scopeReader = reader(archive, label)
        return when (scopeReader.readU32("tag")) {
            0L -> null.also { scopeReader.done() }
            1L -> {
                val outer = reader(scopeReader.readField("dataspace"), "$label.dataspace")
                scopeReader.done()
                val value = readU64Field(outer.readField("value"), "$label.dataspace")
                outer.done()
                value
            }
            else -> invalid("$label has an unsupported tag")
        }
    }

    private class DecodedQuantity(val mantissa: BigInteger, val mantissaBytes: ByteArray, val scale: Int) {
        val literal: String
            get() {
                val digits = mantissa.toString().padStart(scale + 1, '0')
                return if (scale == 0) digits else "${digits.substring(0, digits.length - scale)}.${digits.substring(digits.length - scale)}"
            }
    }

    private fun readQuantity(archive: ByteArray, label: String): DecodedQuantity {
        val quantityReader = reader(archive, label)
        val mantissaArchive = reader(quantityReader.readField("mantissa"), "$label.mantissa")
        val scaleBytes = quantityReader.readField("scale")
        quantityReader.done()
        val mantissaBytes = mantissaArchive.readBytes(mantissaArchive.readU32("length"), "bytes")
        mantissaArchive.done()
        if (scaleBytes.size != 4) invalid("$label.scale must be a u32")
        val scale = reader(scaleBytes, "$label.scale").readU32("scale")
        if (scale > MAX_NUMERIC_SCALE || mantissaBytes.isEmpty() || mantissaBytes.size > MAX_MANTISSA_BYTES) {
            invalid("$label is out of range")
        }
        if ((mantissaBytes.last().toInt() and 0x80) != 0) invalid("$label must be positive")
        val mantissa = BigInteger(1, mantissaBytes.reversedArray())
        return DecodedQuantity(mantissa, mantissaBytes, scale.toInt())
    }

    private class DecodedFeeLimit(val tag: Int, val asset: DecodedAsset, val maxAmount: DecodedQuantity)

    private class DecodedFee(
        val sponsor: DecodedAccount?,
        val programName: String?,
        val programRevision: Long?,
        val limits: List<DecodedFeeLimit>,
        val gasLimit: Long?
    )

    private fun readFee(archive: ByteArray, networkPrefix: Int): DecodedFee {
        val feeReader = reader(archive, "feePayment")
        val payer = feeReader.readU32("payer")
        val body = reader(feeReader.readField("value"), "feePayment.value")
        feeReader.done()
        if (payer > 1) unsupported("feePayment uses an unknown payer")
        var sponsor: DecodedAccount? = null
        var programName: String? = null
        var programRevision: Long? = null
        if (payer == 1L) {
            val programId = reader(body.readField("programId"), "feePayment.programId")
            sponsor = readAccount(programId.readField("sponsor"), "feePayment.sponsor", networkPrefix)
            programName = readText(programId.readField("name"), "feePayment.programId.name")
            programId.done()
            programRevision = readU64Field(body.readField("programRevision"), "feePayment.programRevision")
        }
        val limitsReader = reader(body.readField("chargeLimits"), "feePayment.chargeLimits")
        val count = limitsReader.readU64("count")
        if (count < 0 || count > MAX_FEE_LIMITS) invalid("feePayment has too many charge limits")
        val limits = List(count.toInt()) { index ->
            val entry = reader(limitsReader.readField("[$index]"), "feePayment.chargeLimits[$index]")
            val kindBytes = entry.readField("kind")
            if (kindBytes.size != 4) invalid("feePayment charge kind must be a u32")
            val kindTag = reader(kindBytes, "kind").readU32("kind")
            if (kindTag > 1) unsupported("feePayment uses an unknown charge kind")
            val asset = readAssetDefinition(entry.readField("assetDefinitionId"), "feePayment.assetDefinitionId")
            val maxAmount = readQuantity(entry.readField("maxAmount"), "feePayment.maxAmount")
            entry.done()
            DecodedFeeLimit(kindTag.toInt(), asset, maxAmount)
        }
        limitsReader.done()
        val gasLimit = readOptionUnsigned(body.readField("gasLimit"), 8, "feePayment.gasLimit")
        body.done()
        return DecodedFee(sponsor, programName, programRevision, limits, gasLimit)
    }

    private class DecodedMetadataEntry(val key: String, val json: String, val value: JsonValue)

    private fun readMetadata(archive: ByteArray): List<DecodedMetadataEntry> {
        val metadataReader = reader(archive, "metadata")
        val count = metadataReader.readU64("count")
        if (count < 0 || count > MAX_ENTRIES) invalid("metadata has too many entries")
        val entries = mutableListOf<DecodedMetadataEntry>()
        repeat(count.toInt()) { index ->
            val entry = reader(metadataReader.readField("[$index]"), "metadata[$index]")
            val key = readText(entry.readField("key"), "metadata[$index].key")
            val json = reader(entry.readField("json"), "metadata[$index].json")
            val text = readText(json.readField("value"), "metadata[$index].json")
            json.done()
            entry.done()
            if (entries.any { it.key == key }) invalid("metadata keys must be unique")
            val value = try {
                IrohaConnectMetadataJson.parse(text)
            } catch (_: MetadataJsonException) {
                invalid("metadata.${sanitize(key, 96)} is not JSON")
            }
            entries += DecodedMetadataEntry(key, text, value)
        }
        metadataReader.done()
        return entries
    }

    private sealed class DecodedExecutable {
        class Transfer(
            val sourceOwner: DecodedAccount,
            val asset: DecodedAsset,
            val scope: Long?,
            val quantity: DecodedQuantity,
            val destination: DecodedAccount
        ) : DecodedExecutable()

        class ContractCalls(val calls: List<DecodedContractCall>) : DecodedExecutable()
    }

    private class DecodedContractCall(
        val contractAddress: String,
        val expectedCodeHash: ByteArray,
        val entrypoint: String,
        val arguments: ByteArray?
    )

    private fun readTransferInstruction(archive: ByteArray, networkPrefix: Int): DecodedExecutable.Transfer {
        val instruction = reader(archive, "instruction")
        val wireId = readText(instruction.readField("wireId"), "instruction.wireId")
        if (wireId != WIRE_ID_TRANSFER) unsupported("instruction ${sanitize(wireId, 96)} needs the native decoder")
        val container = reader(instruction.readField("frameContainer"), "instruction.frameContainer")
        instruction.done()
        val frame = container.readFieldU64("frame")
        container.done()
        // NRT0 header: magic(4) version(2) schema(16) reserved(1) length(8) crc64(8) flags(1). The re-encoding
        // below checks the schema hash, checksum and flags byte for byte.
        if (frame.size < IrohaConnectWire.NORITO_HEADER_BYTES) invalid("transfer frame is truncated")
        val transfer = reader(frame.copyOfRange(IrohaConnectWire.NORITO_HEADER_BYTES, frame.size), "transfer")
        if (transfer.readU32("variant") != 2L) unsupported("only Transfer::Asset is reviewable")
        val body = reader(transfer.readField("body"), "transfer.body")
        transfer.done()
        val source = reader(body.readField("source"), "transfer.source")
        val sourceOwner = readAccount(source.readField("owner"), "transfer.source.owner", networkPrefix)
        val asset = readAssetDefinition(source.readField("definition"), "transfer.source.definition")
        val scope = readScope(source.readField("scope"), "transfer.source.scope")
        source.done()
        val quantity = readQuantity(body.readField("quantity"), "transfer.quantity")
        val destination = readAccount(body.readField("destination"), "transfer.destination", networkPrefix)
        body.done()
        return DecodedExecutable.Transfer(sourceOwner, asset, scope, quantity, destination)
    }

    private fun readContractCall(archive: ByteArray): DecodedContractCall {
        val invocation = reader(archive, "contractCall")
        val contractAddress = readText(invocation.readField("contractAddress"), "contractCall.contractAddress")
        val expectedCodeHash = invocation.readField("expectedCodeHash")
        if (expectedCodeHash.size != 32) invalid("contractCall.expectedCodeHash must be 32 bytes")
        val entrypoint = readText(invocation.readField("entrypoint"), "contractCall.entrypoint")
        val option = reader(invocation.readField("arguments"), "contractCall.arguments")
        invocation.done()
        val arguments = when (option.readU8("tag")) {
            0 -> null
            1 -> {
                val record = reader(option.readField("value"), "contractCall.arguments.value")
                val length = record.readU64("length")
                if (length < 0) invalid("contractCall.arguments is too large")
                record.readBytes(length, "bytes").also { record.done() }
            }
            else -> invalid("contractCall.arguments has an invalid option tag")
        }
        option.done()
        return DecodedContractCall(contractAddress, expectedCodeHash, entrypoint, arguments)
    }

    private fun readExecutable(archive: ByteArray, networkPrefix: Int): DecodedExecutable {
        val executable = reader(archive, "executable")
        val variant = executable.readU32("variant")
        val list = reader(executable.readField("items"), "executable.items")
        executable.done()
        val count = list.readU64("count")
        if (count <= 0 || count > MAX_ENTRIES) invalid("executable has no or too many entries")

        if (variant == 0L) {
            if (count != 1L) unsupported("instruction lists other than one asset transfer need the native decoder")
            val decoded = readTransferInstruction(list.readField("[0]"), networkPrefix)
            list.done()
            return decoded
        }
        if (variant != 4L) unsupported("executable variant $variant is not reviewable")

        val calls = List(count.toInt()) { index ->
            val entry = reader(list.readField("[$index]"), "executable.entries[$index]")
            val tag = entry.readU32("variant")
            val value = entry.readField("value")
            entry.done()
            if (tag != 1L) unsupported("batches with native instructions need the native decoder")
            readContractCall(value)
        }
        list.done()
        return DecodedExecutable.ContractCalls(calls)
    }

    // ------------------------------------------------------------------------------------------------------------
    // Canonical rules of the iroha-js browser codec (its builders refuse anything else)

    private fun isJsTrimmed(value: String): Boolean =
        value.isNotEmpty() && !IrohaConnectWire.isJsWhitespace(value.first()) && !IrohaConnectWire.isJsWhitespace(value.last())

    /** iroha-js `exactString`. */
    private fun requireExactString(
        value: String,
        label: String,
        maxBytes: Int
    ) {
        if (value.isEmpty() || value.length > maxBytes) invalid("$label is not an exact string")
        if (!IrohaConnectMetadataJson.isWellFormedUnicode(value) || !isJsTrimmed(value)) invalid("$label is not an exact string")
        if (IrohaConnectMetadataJson.utf8Length(value) > maxBytes) invalid("$label exceeds $maxBytes UTF-8 bytes")
        if (value.any { it.code <= 0x1f || it.code in 0x7f..0x9f }) invalid("$label must not contain control characters")
        if (!IrohaConnectMetadataJson.isNfc(value)) invalid("$label must use NFC normalization")
    }

    private fun requireCanonicalQuantity(quantity: DecodedQuantity, label: String) {
        if (quantity.mantissa.signum() <= 0) invalid("$label must be greater than zero")
        if (!twosBytes(quantity.mantissa).contentEquals(quantity.mantissaBytes)) invalid("$label mantissa is not canonical")
        if (quantity.mantissa.bitLength() > MAX_NUMERIC_BITS) invalid("$label exceeds $MAX_NUMERIC_BITS bits")
        if (quantity.scale > 0 && quantity.mantissa.mod(BigInteger.TEN).signum() == 0) {
            invalid("$label has a non-canonical fractional trailing zero")
        }
    }

    private fun requireCanonicalContractAddress(value: String, label: String) {
        requireExactString(value, label, MAX_CONTRACT_ADDRESS_BYTES)
        if (!IrohaContractAddress.isCanonical(value)) invalid("$label is not a canonical V1 contract address")
    }

    private fun requireCanonicalFields(
        executable: DecodedExecutable,
        ttlMs: Long?,
        nonce: Long?,
        fee: DecodedFee,
        authority: DecodedAccount
    ) {
        when (executable) {
            is DecodedExecutable.Transfer -> {
                if (!executable.sourceOwner.publicKey.contentEquals(authority.publicKey)) {
                    invalid("the source asset owner must be the transaction authority")
                }
                requireCanonicalQuantity(executable.quantity, "transfer.quantity")
            }
            is DecodedExecutable.ContractCalls -> {
                executable.calls.forEachIndexed { index, call ->
                    requireCanonicalContractAddress(call.contractAddress, "entries[$index].contractAddress")
                    if ((call.expectedCodeHash[31].toInt() and 1) == 0) invalid("entries[$index].expectedCodeHash must carry the hash marker bit")
                    requireExactString(call.entrypoint, "entries[$index].entrypoint", MAX_ENTRYPOINT_BYTES)
                    if ((call.arguments?.size ?: 0) > MAX_CONTRACT_ARGUMENT_BYTES) invalid("entries[$index].arguments is too large")
                }
                if (fee.gasLimit == null) invalid("feePayment.gasLimit is required when entries contain a contract call")
            }
        }
        if (ttlMs == null || ttlMs == 0L) invalid("ttlMs must be present and non-zero")
        if (nonce == 0L) invalid("nonce must be non-zero when provided")
        if (fee.gasLimit == 0L) invalid("feePayment.gasLimit must be non-zero when provided")
        var previousTag = -1
        fee.limits.forEachIndexed { index, limit ->
            if (limit.tag <= previousTag) invalid("feePayment.chargeLimits must be unique and ordered nexus before pipelineGas")
            previousTag = limit.tag
            requireCanonicalQuantity(limit.maxAmount, "feePayment.chargeLimits[$index].maxAmount")
        }
        if (fee.sponsor != null) {
            if (fee.programRevision == 0L) invalid("feePayment.programRevision must be non-zero")
            val name = fee.programName.orEmpty()
            requireExactString(name, "feePayment.programId.name", MAX_PROGRAM_NAME_BYTES)
            if (IrohaConnectMetadataJson.isRustUnicodeWhitespace(name) || name.any { it == '@' || it == '#' || it == '$' }) {
                invalid("feePayment.programId.name is not a canonical program Name")
            }
        }
    }

    private fun requireCanonicalMetadata(entries: List<DecodedMetadataEntry>) {
        val state = IrohaConnectMetadataJson.State(nodes = 1)
        var previousKey: String? = null
        entries.forEach { entry ->
            if (!IrohaConnectMetadataJson.isValidKey(entry.key)) invalid("metadata key is not a canonical Name")
            if (previousKey != null && IrohaConnectMetadataJson.compareUtf8(previousKey!!, entry.key) >= 0) {
                invalid("metadata keys must be unique and sorted")
            }
            previousKey = entry.key
            try {
                IrohaConnectMetadataJson.normalize(entry.value, 1, state)
            } catch (error: MetadataJsonException) {
                invalid("metadata.${sanitize(entry.key, 96)}: ${error.message}")
            }
            if (IrohaConnectMetadataJson.canonical(entry.value) != entry.json) invalid("metadata.${sanitize(entry.key, 96)} JSON is not canonical")
            if (entry.key in LEGACY_FEE_METADATA_KEYS) invalid("metadata.${entry.key} is retired; use the signature-bound feePayment field")
        }
        val whole = entries.joinToString(",", "{", "}") { "${IrohaConnectMetadataJson.quote(it.key)}:${it.json}" }
        if (IrohaConnectMetadataJson.utf8Length(whole) > IrohaConnectMetadataJson.MAX_JSON_BYTES) invalid("metadata JSON is too large")
    }

    // ------------------------------------------------------------------------------------------------------------
    // Canonical re-encoding (iroha-js transaction codec encoders)

    /** iroha-js `bigintToTwosBytes`: minimal little-endian two's complement, empty for zero. */
    private fun twosBytes(value: BigInteger): ByteArray =
        if (value.signum() == 0) ByteArray(0) else value.toByteArray().reversedArray()

    private fun cfield(bytes: ByteArray) = NoritoBytes.compactField(bytes)
    private fun cstruct(vararg fields: ByteArray) = NoritoBytes.compactStruct(fields.toList())
    private fun stringValue(value: String) = cfield(value.toByteArray(Charsets.UTF_8))
    private fun option(value: ByteArray?) =
        if (value == null) byteArrayOf(0) else NoritoBytes.concat(byteArrayOf(1), cfield(value))
    private fun vector(values: List<ByteArray>) =
        NoritoBytes.concat(listOf(NoritoBytes.u64(values.size.toLong())) + values.map(::cfield))

    private fun encodeAccount(publicKey: ByteArray): ByteArray {
        val controller = byteArrayOf(0) + publicKey
        val elements = controller.map { cfield(byteArrayOf(it)) }
        val constVec = NoritoBytes.concat(listOf(NoritoBytes.u64(controller.size.toLong())) + elements)
        return NoritoBytes.concat(NoritoBytes.u32(0), cfield(constVec))
    }

    private fun encodeAssetDefinition(definition: ByteArray): ByteArray {
        val elements = definition.map { cfield(byteArrayOf(it)) }
        return NoritoBytes.concat(elements)
    }

    private fun encodeScope(scope: Long?) =
        if (scope == null) NoritoBytes.u32(0) else NoritoBytes.concat(NoritoBytes.u32(1), cfield(cfield(NoritoBytes.u64(scope))))

    private fun encodeNumeric(quantity: DecodedQuantity): ByteArray {
        val mantissa = twosBytes(quantity.mantissa)
        return cstruct(NoritoBytes.concat(NoritoBytes.u32(mantissa.size.toLong()), mantissa), NoritoBytes.u32(quantity.scale.toLong()))
    }

    private fun encodeFee(fee: DecodedFee): ByteArray {
        val entries = fee.limits.map { limit ->
            cstruct(NoritoBytes.u32(limit.tag.toLong()), encodeAssetDefinition(limit.asset.definition), encodeNumeric(limit.maxAmount))
        }
        val limits = vector(entries)
        val gasLimit = option(fee.gasLimit?.let(NoritoBytes::u64))
        val sponsor = fee.sponsor ?: return NoritoBytes.concat(NoritoBytes.u32(0), cfield(cstruct(limits, gasLimit)))
        val programId = cstruct(encodeAccount(sponsor.publicKey), stringValue(fee.programName.orEmpty()))
        return NoritoBytes.concat(
            NoritoBytes.u32(1),
            cfield(cstruct(programId, NoritoBytes.u64(fee.programRevision ?: 0L), limits, gasLimit))
        )
    }

    private fun encodeMetadata(entries: List<DecodedMetadataEntry>) =
        vector(entries.map { cstruct(stringValue(it.key), cstruct(stringValue(it.json))) })

    private fun encodeExecutable(executable: DecodedExecutable): ByteArray = when (executable) {
        is DecodedExecutable.Transfer -> {
            val body = cstruct(
                cstruct(encodeAccount(executable.sourceOwner.publicKey), encodeAssetDefinition(executable.asset.definition), encodeScope(executable.scope)),
                encodeNumeric(executable.quantity),
                encodeAccount(executable.destination.publicKey)
            )
            val inner = NoritoBytes.concat(NoritoBytes.u32(2), cfield(body))
            val frame = IrohaConnectWire.frameNorito(inner, TRANSFER_SCHEMA_HASH, COMPACT_LEN_FLAG)
            val instruction = NoritoBytes.concat(cfield(stringValue(WIRE_ID_TRANSFER)), cfield(NoritoBytes.field(frame)))
            NoritoBytes.concat(NoritoBytes.u32(0), cfield(vector(listOf(instruction))))
        }
        is DecodedExecutable.ContractCalls -> {
            val entries = executable.calls.map { call ->
                val arguments = call.arguments?.let { NoritoBytes.concat(NoritoBytes.u64(it.size.toLong()), it) }
                val invocation = cstruct(stringValue(call.contractAddress), call.expectedCodeHash, stringValue(call.entrypoint), option(arguments))
                NoritoBytes.concat(NoritoBytes.u32(1), cfield(invocation))
            }
            NoritoBytes.concat(NoritoBytes.u32(4), cfield(vector(entries)))
        }
    }

    // ------------------------------------------------------------------------------------------------------------

    fun review(txBytes: ByteArray, expected: IrohaConnectTransactionExpectation): ReviewedIrohaConnectTransaction {
        if (txBytes.isEmpty() || txBytes.size > MAX_PAYLOAD_BYTES) invalid("payload size is out of range")
        val networkId = IrohaNetworkId.parse(expected.networkIdLiteral) ?: invalid("the session NetworkId is not canonical")
        val payload = reader(txBytes, "payload")

        val domain = reader(payload.readField("domain"), "payload.domain")
        if (domain.readU32("variant") != 0L) unsupported("payload must target a network domain")
        val payloadNetwork = domain.readField("networkId")
        domain.done()
        if (!payloadNetwork.contentEquals(networkId.toBytes())) {
            throw IrohaConnectTransactionException(IrohaConnectTransactionErrorCode.WRONG_NETWORK, "payload targets another network")
        }
        val authority = readAccount(payload.readField("authority"), "payload.authority", expected.networkPrefix)
        if (authority.literal != expected.authority) {
            throw IrohaConnectTransactionException(IrohaConnectTransactionErrorCode.WRONG_AUTHORITY, "payload is not for the connected account")
        }
        val creationTimeMs = readU64Field(payload.readField("creationTimeMs"), "payload.creationTimeMs")
        val executable = readExecutable(payload.readField("executable"), expected.networkPrefix)
        val ttlMs = readOptionUnsigned(payload.readField("ttlMs"), 8, "payload.ttlMs")
        val nonce = readOptionUnsigned(payload.readField("nonce"), 4, "payload.nonce")
        val fee = readFee(payload.readField("feePayment"), expected.networkPrefix)
        val metadata = readMetadata(payload.readField("metadata"))
        val attachments = payload.readField("attachments")
        payload.done()
        if (attachments.size != 1 || attachments[0].toInt() != 0) unsupported("proof attachments are not reviewable")

        requireCanonicalFields(executable, ttlMs, nonce, fee, authority)
        requireCanonicalMetadata(metadata)
        val rebuilt = NoritoBytes.compactStruct(
            listOf(
                NoritoBytes.concat(NoritoBytes.u32(0), cfield(networkId.toBytes())),
                encodeAccount(authority.publicKey),
                NoritoBytes.u64(creationTimeMs),
                encodeExecutable(executable),
                option(ttlMs?.let(NoritoBytes::u64)),
                option(nonce?.let(NoritoBytes::u32)),
                encodeFee(fee),
                encodeMetadata(metadata),
                byteArrayOf(0)
            )
        )
        if (!rebuilt.contentEquals(txBytes)) invalid("payload is not the canonical encoding of its fields")
        val signingKey = runCatching { IrohaConnectEncoding.unhex(expected.signingPublicKeyHex) }.getOrNull()
        if (signingKey == null || !signingKey.contentEquals(authority.publicKey)) {
            invalid("the signing key does not control the authority")
        }

        val payloadHash = IrohaConnectCrypto.irohaHash(txBytes)
        val unsigned = { value: Long -> java.lang.Long.toUnsignedString(value) }
        val metadataText = if (metadata.isEmpty()) {
            null
        } else {
            val ordered = IrohaConnectMetadataJson.jsPropertyOrder(metadata.map { it.key })
            val byKey = metadata.associateBy { it.key }
            sanitize(
                ordered.joinToString(",", "{", "}") {
                    "${IrohaConnectMetadataJson.quote(it)}:${IrohaConnectMetadataJson.display(byKey.getValue(it).value)}"
                },
                MAX_METADATA_TEXT
            )
        }
        val feeReview = IrohaConnectReviewFee(
            payer = if (fee.sponsor == null) "authority" else "sponsor",
            program = fee.sponsor?.let { sanitize("${it.literal}/${fee.programName}") },
            programRevision = fee.programRevision?.let(unsigned),
            chargeLimits = fee.limits.map {
                IrohaConnectReviewFeeLimit(if (it.tag == 0) "nexus" else "pipelineGas", it.asset.review, it.maxAmount.literal)
            },
            gasLimit = fee.gasLimit?.let(unsigned)
        )
        val review = IrohaConnectTransactionReview(
            kind = if (executable is DecodedExecutable.Transfer) IrohaConnectTransactionReview.Kind.TRANSFER else IrohaConnectTransactionReview.Kind.CONTRACT_CALLS,
            authority = authority.literal,
            networkId = networkId.literal,
            payloadBytes = txBytes.size,
            payloadHashHex = IrohaConnectEncoding.hex(payloadHash),
            creationTimeMs = unsigned(creationTimeMs),
            ttlMs = ttlMs?.let(unsigned),
            nonce = nonce?.let(unsigned),
            fee = feeReview,
            metadata = metadataText,
            transfer = (executable as? DecodedExecutable.Transfer)?.let {
                IrohaConnectReviewTransfer(it.asset.review, it.scope?.let { scope -> "dataspace:${unsigned(scope)}" }, it.quantity.literal, it.destination.literal)
            },
            calls = (executable as? DecodedExecutable.ContractCalls)?.calls?.map { call ->
                IrohaConnectReviewContractCall(
                    contractAddress = sanitize(call.contractAddress),
                    entrypoint = sanitize(call.entrypoint),
                    codeHashHex = IrohaConnectEncoding.hex(call.expectedCodeHash),
                    argumentBytes = call.arguments?.size,
                    argumentsSha256 = call.arguments?.let { IrohaConnectEncoding.hex(IrohaConnectCrypto.sha256(it)) }
                )
            }
        )
        return ReviewedIrohaConnectTransaction(review, payloadHash)
    }
}
