package jp.co.soramitsu.runtime.multiNetwork.chain.remote

import com.google.gson.Gson
import jp.co.soramitsu.common.data.network.ton.AccountEvents
import jp.co.soramitsu.common.data.network.ton.EmulateMessageToWalletRequest
import jp.co.soramitsu.common.data.network.ton.JettonTransferPayloadRemote
import jp.co.soramitsu.common.data.network.ton.JettonsBalances
import jp.co.soramitsu.common.data.network.ton.MessageConsequences
import jp.co.soramitsu.common.data.network.ton.PublicKeyResponse
import jp.co.soramitsu.common.data.network.ton.RawTime
import jp.co.soramitsu.common.data.network.ton.SendBlockchainMessageRequest
import jp.co.soramitsu.common.data.network.ton.Seqno
import jp.co.soramitsu.common.data.network.ton.TonAccountData
import jp.co.soramitsu.common.data.network.ton.TonApi
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.ton.model.JettonTransferPayload

class TonRemoteSource(
    private val tonApi: TonApi,
    private val availableFiatCurrencies: GetAvailableFiatCurrencies,
    private val gson: Gson
) {
    suspend fun loadAccountData(chain: Chain, accountId: String): TonAccountData {
        val baseUrl = getActiveUrl(chain)
        return tonApi.getAccountData("${baseUrl}/v2/accounts/$accountId")
    }

    suspend fun loadJettonBalances(chain: Chain, accountId: String): JettonsBalances {
        val baseUrl = getActiveUrl(chain)
        val currencies = availableFiatCurrencies.invoke().joinToString(",") { it.id.uppercase() }
        return tonApi.getJettonBalances("${baseUrl}/v2/accounts/$accountId/jettons", listOf(currencies))
    }

    suspend fun getSeqno(chain: Chain, accountId: String): Int {
        val baseUrl = getActiveUrl(chain)

        val response = tonApi.getRequest("${baseUrl}/v2/wallet/$accountId/seqno")
        val timeKeyResponse = gson.fromJson(response, Seqno::class.java)
        return timeKeyResponse.seqno
    }

    suspend fun getRawTime(chain: Chain): Int {
        val baseUrl = getActiveUrl(chain)

        val response = tonApi.getRequest("${baseUrl}/v2/liteserver/get_time")
        val timeKeyResponse = gson.fromJson(response, RawTime::class.java)
        return timeKeyResponse.time
    }

    private fun getActiveUrl(chain: Chain): String {
        return chain.nodes.first().url
    }

    suspend fun getPublicKey(chain: Chain, accountId: String): String {
        val baseUrl = getActiveUrl(chain)
        val response = tonApi.getRequest("${baseUrl}/v2/accounts/$accountId/publickey")

        val publicKeyResponse = gson.fromJson(response, PublicKeyResponse::class.java)

        return publicKeyResponse.publicKey
    }

    suspend fun getJettonTransferPayload(chain: Chain, accountId: String, jettonId: String): JettonTransferPayload {
        val baseUrl = getActiveUrl(chain)
        val response = tonApi.getRequest("${baseUrl}/v2/jettons/$jettonId/transfer/$accountId/payload")
        val jettonPayload = gson.fromJson(response, JettonTransferPayloadRemote::class.java)

        return JettonTransferPayload(accountId, jettonPayload)
    }

    suspend fun sendBlockchainMessage(chain: Chain, request: SendBlockchainMessageRequest): String {
        val baseUrl = getActiveUrl(chain)
        val response = tonApi.sendBlockchainMessage("${baseUrl}/v2/blockchain/message", request)
        return response
    }

    suspend fun emulateBlockchainMessageRequest(chain: Chain, request: EmulateMessageToWalletRequest): MessageConsequences {
        val baseUrl = getActiveUrl(chain)

        return tonApi.emulateBlockchainMessage("${baseUrl}/v2/wallet/emulate", request)
    }

    suspend fun getAccountEvents(historyUrl: String, accountId: String, beforeLt: Long?, limit: Int = 100): AccountEvents {
        return  tonApi.getAccountEvents("${historyUrl}/v2/accounts/$accountId/events", limit = limit, beforeLt = beforeLt)
    }
}