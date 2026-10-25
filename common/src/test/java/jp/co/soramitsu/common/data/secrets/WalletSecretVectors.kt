package jp.co.soramitsu.common.data.secrets

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStreamReader
import jp.co.soramitsu.fearless_utils.extensions.fromHex

/**
 * Golden vectors produced by the production-parity oracle (AL-4) from the
 * released 4.2.0 sources (`b5e22d49d`, shared-features `7508bc83`). All key
 * material in them is synthetic.
 */
internal object WalletSecretVectors {

    fun family(name: String): List<JsonObject> {
        val stream = checkNotNull(javaClass.getResourceAsStream("/wallet-secret-vectors/$name")) {
            "Missing wallet-secret vector resource $name"
        }
        val root = InputStreamReader(stream, Charsets.UTF_8).use(JsonParser::parseReader).asJsonObject
        val vectors = root.getAsJsonArray("vectors").map { it.asJsonObject }
        check(vectors.size == root["count"].asInt) { "Vector count mismatch in $name" }
        return vectors
    }

    fun ton(): List<TonVector> = family("08-ton.json").map {
        TonVector(
            kind = it["kind"].asString,
            variant = it["variant"].asString,
            typedText = it["typedText"].asString,
            privateKey = it["privateKey"].asString.fromHex(),
            publicKey = it["publicKey"].asString.fromHex()
        )
    }

    class TonVector(
        val kind: String,
        val variant: String,
        val typedText: String,
        val privateKey: ByteArray,
        val publicKey: ByteArray
    )
}
