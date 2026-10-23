package jp.co.soramitsu.walletconnect.impl.presentation.summary

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import jp.co.soramitsu.common.utils.readableUtf8Text
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.FixedByteArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.skipAliases
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.toByteArray
import java.math.BigInteger

/** A Substrate call as the chain's runtime decodes it: pallet, function and arguments. */
internal data class PolkadotCallSummary(
    val module: String,
    val function: String,
    val arguments: List<PolkadotArgument>
) {
    val name: String
        get() = "$module.$function"

    /** False when an argument was too long or nested too deep to be shown in full. */
    val isComplete: Boolean
        get() = arguments.all { it.value.isComplete }
}

internal data class PolkadotArgument(val name: String, val value: PolkadotValue)

internal sealed interface PolkadotValue {
    val isComplete: Boolean

    /** An account, as an address in the chain's own format. */
    data class Account(val address: String) : PolkadotValue {
        override val isComplete = true
    }

    /** An amount of the chain's native token, in planks. */
    data class NativeAmount(val planks: BigInteger) : PolkadotValue {
        override val isComplete = true
    }

    data class Text(val text: String, override val isComplete: Boolean = true) : PolkadotValue

    /** Calls the call dispatches, such as the calls of a batch or a proxy. */
    data class Calls(val calls: List<PolkadotCallSummary>) : PolkadotValue {
        override val isComplete: Boolean
            get() = calls.all { it.isComplete }
    }
}

/**
 * Decodes `method` bytes with the chain's runtime metadata. Null unless the bytes are exactly one
 * call: nothing may be left over and encoding the decoded call must give the same bytes back, so
 * the summary describes precisely the bytes the signature commits to.
 */
internal class PolkadotCallDecoder(
    private val runtime: RuntimeSnapshot,
    private val addressOf: (ByteArray) -> String
) {

    fun decode(method: ByteArray): PolkadotCallSummary? = runCatching {
        val reader = ScaleCodecReader(method)
        val call = GenericCall.decode(reader, runtime)
        if (reader.hasNext()) return null
        if (!GenericCall.toByteArray(runtime, call).contentEquals(method)) return null
        summary(call, depth = 0)
    }.getOrNull()

    private fun summary(call: GenericCall.Instance, depth: Int): PolkadotCallSummary {
        val module = call.module.name
        val function = call.function.name
        val arguments = call.function.arguments.map { definition ->
            val value = call.arguments[definition.name]
            PolkadotArgument(definition.name, argumentValue(module, definition.name, definition.type, value, depth))
        }
        return PolkadotCallSummary(module, function, arguments)
    }

    private fun argumentValue(
        module: String,
        name: String,
        type: Type<*>?,
        value: Any?,
        depth: Int
    ): PolkadotValue {
        val nestedCalls = when {
            value is GenericCall.Instance -> listOf(value)
            value is List<*> && value.isNotEmpty() && value.all { it is GenericCall.Instance } ->
                value.filterIsInstance<GenericCall.Instance>()
            else -> null
        }
        return when {
            nestedCalls != null && depth < MAX_CALL_DEPTH -> PolkadotValue.Calls(nestedCalls.map { summary(it, depth + 1) })
            nestedCalls != null -> PolkadotValue.Text(nestedCalls.joinToString { "${it.module.name}.${it.function.name}" }, false)
            value is BigInteger && isNativeAmount(module, name) -> PolkadotValue.NativeAmount(value)
            else -> accountOf(type, value)?.let(PolkadotValue::Account) ?: text(value)
        }
    }

    // A MultiAddress::Id or a plain 32-byte AccountId argument.
    private fun accountOf(type: Type<*>?, value: Any?): String? {
        val resolved = runCatching { type?.skipAliases() }.getOrNull()
        val accountId = when {
            resolved is DictEnum && resolved.isMultiAddress() && value is DictEnum.Entry<*> && value.name == MULTI_ADDRESS_ID ->
                value.value as? ByteArray
            resolved is FixedByteArray && resolved.length == ACCOUNT_ID_SIZE && resolved.isAccountId() -> value as? ByteArray
            else -> null
        }
        return accountId?.takeIf { it.size == ACCOUNT_ID_SIZE }?.let { runCatching { addressOf(it) }.getOrNull() }
    }

    private fun DictEnum.isMultiAddress(): Boolean = elements.values.map { it.name }.containsAll(MULTI_ADDRESS_VARIANTS)

    private fun FixedByteArray.isAccountId(): Boolean = ACCOUNT_ID_TYPES.any { name ->
        runCatching { runtime.typeRegistry[name]?.skipAliases() }.getOrNull() === this
    }

    private fun text(value: Any?): PolkadotValue.Text {
        val rendered = StringBuilder()
        render(value, rendered)
        return if (rendered.length > MAX_TEXT_LENGTH) {
            PolkadotValue.Text(rendered.substring(0, MAX_TEXT_LENGTH) + "…", isComplete = false)
        } else {
            PolkadotValue.Text(rendered.toString(), isComplete = !rendered.contains("…"))
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun render(value: Any?, out: StringBuilder) {
        if (out.length > MAX_TEXT_LENGTH) return
        when (value) {
            null -> out.append("None")
            is BigInteger, is Boolean, is Number -> out.append(value.toString())
            is String -> out.append(value)
            is ByteArray -> out.append(readableUtf8Text(value)?.let { "\"$it\"" } ?: value.toHexString(withPrefix = true))
            is DictEnum.Entry<*> -> {
                out.append(value.name)
                if (value.value != null) {
                    out.append('(')
                    render(value.value, out)
                    out.append(')')
                }
            }
            is Struct.Instance -> value.mapping.entries.joinTo(out, prefix = "{", postfix = "}") { (key, item) ->
                StringBuilder("$key: ").also { render(item, it) }
            }
            is GenericCall.Instance -> out.append("${value.module.name}.${value.function.name}(…)")
            is Collection<*> -> value.joinTo(out, prefix = "[", postfix = "]") { item ->
                StringBuilder().also { render(item, it) }
            }
            else -> out.append(value.toString())
        }
    }

    companion object {
        private const val MAX_CALL_DEPTH = 3
        private const val MAX_TEXT_LENGTH = 512
        private const val ACCOUNT_ID_SIZE = 32
        private const val MULTI_ADDRESS_ID = "Id"
        private val MULTI_ADDRESS_VARIANTS = listOf("Id", "Index", "Raw", "Address32", "Address20")
        private val ACCOUNT_ID_TYPES = listOf("AccountId", "AccountId32", "sp_core.crypto.AccountId32")

        // Pallets whose amounts are always in the native token. Any other number is shown as is.
        private val NATIVE_AMOUNT_ARGUMENTS = mapOf(
            "Balances" to setOf("value", "amount", "new_free"),
            "Staking" to setOf("value", "max_additional")
        )

        fun isNativeAmount(module: String, argument: String): Boolean =
            NATIVE_AMOUNT_ARGUMENTS[module]?.contains(argument) == true
    }
}
