package jp.co.soramitsu.staking.impl.domain.solana

import java.io.IOException
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@Singleton
class SolanaRpcService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()
    }

    suspend fun fetchValidators(rpcUrl: String): List<SolanaValidator> = withContext(Dispatchers.IO) {
        val payload = """{"jsonrpc":"2.0","id":1,"method":"getVoteAccounts"}"""
            .toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url(rpcUrl)
            .post(payload)
            .build()

        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("RPC error ${response.code}")

                val body = response.body?.string().orEmpty()
                parseValidators(body)
            }
        }.getOrElse { emptyList() }
    }

    private fun parseValidators(raw: String): List<SolanaValidator> {
        return runCatching {
            val root = JSONObject(raw)
            val result = root.getJSONObject("result")
            val current = result.getJSONArray("current")
            buildList {
                for (index in 0 until current.length()) {
                    val entry = current.getJSONObject(index)
                    val voteAccount = entry.optString("votePubkey", "")
                    if (voteAccount.isEmpty()) continue
                    val activatedStake = entry.optString("activatedStake")
                    val commission = entry.optInt("commission", 0)
                    add(
                        SolanaValidator(
                            voteAccount = voteAccount,
                            activatedStake = activatedStake.toBigIntegerOrNull() ?: BigInteger.ZERO,
                            commission = commission
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}

data class SolanaValidator(
    val voteAccount: String,
    val activatedStake: BigInteger,
    val commission: Int
)

private fun String.toBigIntegerOrNull(): BigInteger? = runCatching { BigInteger(this) }.getOrNull()
