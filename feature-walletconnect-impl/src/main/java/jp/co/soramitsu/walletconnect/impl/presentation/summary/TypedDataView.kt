package jp.co.soramitsu.walletconnect.impl.presentation.summary

import jp.co.soramitsu.fearless_utils.extensions.toHexString
import org.web3j.crypto.Hash
import org.web3j.crypto.StructuredDataEncoder
import java.math.BigInteger

/**
 * EIP-712 typed data as the signer hashes it. The signer hashes with web3j's
 * [StructuredDataEncoder]; every value shown is read back from that encoder's own encoding of the
 * struct, never from a second parse of the JSON, so what is shown is what the signature commits to.
 */
internal class TypedDataView private constructor(private val encoder: StructuredDataEncoder) {

    private val types = encoder.jsonMessageObject.types

    val primaryType: String = encoder.jsonMessageObject.primaryType

    /** The domain fields the hash includes, or null when the domain cannot be read exactly. */
    val domain: TypedStruct? by lazy {
        val domainValues = runCatching {
            StructuredDataEncoder.mapper.convertValue(encoder.jsonMessageObject.domain, HashMap::class.java)
        }.getOrNull()
        struct(DOMAIN_TYPE, domainValues)
    }

    /** The message, or null when it cannot be read exactly. */
    val message: TypedStruct? by lazy { struct(primaryType, encoder.jsonMessageObject.message) }

    /**
     * [value] as struct [type], when every declared field is present. web3j leaves absent fields out
     * of the encoding instead of failing, which would shift every later field: such data is not read.
     */
    internal fun struct(type: String, value: Any?): TypedStruct? {
        val fields = types[type]?.takeIf { fields -> fields.map { it.name }.distinct().size == fields.size } ?: return null
        val values = presentValues(value)?.takeIf { values -> fields.all { it.name in values } } ?: return null
        val encoded = runCatching { encoder.encodeData(type, values) }.getOrNull()
            ?.takeIf { it.size == WORD_SIZE * (fields.size + 1) }
            ?: return null
        val words = fields.mapIndexed { index, field ->
            val start = WORD_SIZE * (index + 1)
            field.name to TypedField(field.type, encoded.copyOfRange(start, start + WORD_SIZE), values.getValue(field.name))
        }.toMap()
        return TypedStruct(this, type, encoded, words)
    }

    private fun presentValues(value: Any?): HashMap<String, Any>? {
        val map = value as? Map<*, *> ?: return null
        val values = HashMap<String, Any>()
        for ((key, item) in map) {
            if (key is String && item != null) values[key] = item
        }
        return values
    }

    internal class TypedField(val type: String, val word: ByteArray, val value: Any)

    companion object {
        const val DOMAIN_TYPE = "EIP712Domain"
        const val WORD_SIZE = 32

        /** Null for typed data the signer could not hash either. */
        fun parse(typedData: String): TypedDataView? =
            runCatching { TypedDataView(StructuredDataEncoder(typedData)) }.getOrNull()
    }
}

/** One struct of typed data with the 32-byte word its encoding holds for each field. */
internal class TypedStruct(
    private val view: TypedDataView,
    val type: String,
    /** The struct's EIP-712 encoding: its type hash followed by one word per field. */
    val encoded: ByteArray,
    private val fields: Map<String, TypedDataView.TypedField>
) {
    val fieldNames: Set<String>
        get() = fields.keys

    fun has(name: String, type: String): Boolean = fields[name]?.type == type

    /** Whether the struct has exactly these fields, of exactly these types. */
    fun hasExactly(fieldTypes: Map<String, String>): Boolean =
        fieldNames == fieldTypes.keys && fieldTypes.all { (name, type) -> has(name, type) }

    fun address(name: String): String? {
        val field = fields[name]?.takeIf { it.type == "address" } ?: return null
        if (field.word.copyOfRange(0, ADDRESS_PADDING).any { it != 0.toByte() }) return null
        return checksumAddress(field.word.copyOfRange(ADDRESS_PADDING, TypedDataView.WORD_SIZE).toHexString(withPrefix = true))
    }

    fun uint(name: String): BigInteger? {
        val field = fields[name]?.takeIf { it.type.startsWith("uint") } ?: return null
        return BigInteger(1, field.word)
    }

    fun bool(name: String): Boolean? {
        val field = fields[name]?.takeIf { it.type == "bool" } ?: return null
        return when (BigInteger(1, field.word)) {
            BigInteger.ZERO -> false
            BigInteger.ONE -> true
            else -> null
        }
    }

    fun string(name: String): String? {
        val field = fields[name]?.takeIf { it.type == "string" } ?: return null
        val text = field.value as? String ?: return null
        return text.takeIf { Hash.sha3(it.toByteArray(Charsets.UTF_8)).contentEquals(field.word) }
    }

    fun struct(name: String): TypedStruct? {
        val field = fields[name] ?: return null
        val nested = view.struct(field.type, field.value) ?: return null
        return nested.takeIf { Hash.sha3(it.encoded).contentEquals(field.word) }
    }

    fun structs(name: String): List<TypedStruct>? {
        val field = fields[name]?.takeIf { it.type.endsWith("[]") } ?: return null
        val itemType = field.type.removeSuffix("[]")
        val items = (field.value as? List<*>)?.map { view.struct(itemType, it) ?: return null } ?: return null
        val itemHashes = items.fold(ByteArray(0)) { hashes, item -> hashes + Hash.sha3(item.encoded) }
        return items.takeIf { Hash.sha3(itemHashes).contentEquals(field.word) }
    }

    private companion object {
        const val ADDRESS_PADDING = 12
    }
}
