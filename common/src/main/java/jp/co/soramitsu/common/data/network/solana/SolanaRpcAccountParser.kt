package jp.co.soramitsu.common.data.network.solana

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import java.math.BigInteger
import jp.co.soramitsu.core.utils.toLongExact
import jp.co.soramitsu.core.utils.toIntExact
import java.util.Base64

/** Strict parsing of RPC identity and token amount fields used before signing. */
internal object SolanaRpcAccountParser {
    fun account(value: JsonElement): SolanaRpcAccountInfo? {
        if (value is JsonNull) return null
        val row = value.objectValue()
        val owner = address(row, "owner")
        val lamports = unsignedNumber(row["lamports"]).toLongExact()
        val executable = row["executable"].also { require(it != null && it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) }.asBoolean
        val data = row["data"].also { require(it != null && it.isJsonArray && it.asJsonArray.size() == 2) }.asJsonArray
        require(data[0].isJsonPrimitive && data[0].asJsonPrimitive.isString && data[1].asString == "base64")
        val encoded = data[0].asString
        require(encoded.length <= 220)
        val bytes = Base64.getDecoder().decode(encoded)
        require(bytes.size <= 165 && Base64.getEncoder().encodeToString(bytes) == encoded)
        return SolanaRpcAccountInfo(owner, lamports, executable, encoded)
    }

    fun tokenAccounts(value: JsonElement, wallet: String, program: String): List<SolanaRpcTokenAccount> {
        require(value.isJsonArray && value.asJsonArray.size() <= 512)
        require(program in setOf(SolanaTransferTransactionBuilder.SPL_TOKEN_PROGRAM_ADDRESS, SolanaTransferTransactionBuilder.TOKEN_2022_PROGRAM_ADDRESS))
        val seen = mutableSetOf<String>()
        return value.asJsonArray.map { element ->
            val row = element.objectValue()
            val tokenAddress = address(row, "pubkey")
            require(seen.add(tokenAddress))
            val account = row["account"].objectValue()
            require(address(account, "owner") == program)
            require(account["executable"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean == false)
            unsignedNumber(account["lamports"]).toLongExact()
            val parsed = account["data"].objectValue()["parsed"].objectValue()
            require(text(parsed, "type") == "account")
            val info = parsed["info"].objectValue()
            require(address(info, "owner") == wallet)
            val mint = address(info, "mint")
            val state = text(info, "state")
            require(state in setOf("initialized", "frozen"))
            val amount = info["tokenAmount"].objectValue()
            val raw = text(amount, "amount")
            require(raw.matches(Regex("^(0|[1-9][0-9]{0,19})$")))
            val units = BigInteger(raw)
            require(units <= MAX_U64)
            val decimals = unsignedNumber(amount["decimals"]).toIntExact()
            require(decimals in 0..255)
            SolanaRpcTokenAccount(tokenAddress, wallet, mint, program, units, decimals, state == "frozen")
        }
    }

    private fun JsonElement?.objectValue(): JsonObject {
        require(this != null && isJsonObject)
        return asJsonObject
    }

    private fun text(row: JsonObject, key: String): String {
        val value = row[key]
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return value.asString
    }

    private fun address(row: JsonObject, key: String): String = text(row, key).also { SolanaPublicKeyCodec.decode(it) }

    private fun unsignedNumber(value: JsonElement?): BigInteger {
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
        val raw = value.asString
        require(raw.matches(Regex("^(0|[1-9][0-9]{0,19})$")))
        return BigInteger(raw).also { require(it <= MAX_U64) }
    }

    private val MAX_U64 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)
}
