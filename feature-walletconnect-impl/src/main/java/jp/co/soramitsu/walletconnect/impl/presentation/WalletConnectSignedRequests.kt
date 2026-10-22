package jp.co.soramitsu.walletconnect.impl.presentation

import com.reown.walletkit.client.Wallet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What signing a request produced: a response to send, or a transaction still to broadcast. */
internal sealed interface SignedRequest {
    class Response(val result: String) : SignedRequest

    /** Retried as these exact bytes, so a failed broadcast never becomes a second transaction. */
    class Transaction(val chainId: String, val signedTransaction: String) : SignedRequest
}

/**
 * Remembers, for the process lifetime, what was signed for each WalletConnect request. When the
 * response to the dApp fails and the user retries, the same answer is sent again: a request is
 * never signed twice and an `eth_sendTransaction` is never broadcast again with a fresh nonce.
 */
internal class WalletConnectSignedRequests {
    private val mutex = Mutex()
    private val signed = HashMap<RequestId, Record>()

    suspend fun responseFor(
        request: Wallet.Model.SessionRequest,
        sign: suspend () -> SignedRequest,
        broadcast: suspend (SignedRequest.Transaction) -> String
    ): String = mutex.withLock {
        val id = RequestId(request.topic, request.request.id)
        val content = RequestContent(request)
        val recorded = signed[id]
        // One id is signed for once: a dApp reusing it for other content is refused, never signed again.
        requireRequest(recorded == null || recorded.content == content) { "The request id was already used" }
        val entry = recorded?.signed ?: sign().also { signed[id] = Record(content, it) }
        when (entry) {
            is SignedRequest.Response -> entry.result
            is SignedRequest.Transaction -> broadcast(entry).also { hash ->
                signed[id] = Record(content, SignedRequest.Response(hash))
            }
        }
    }

    private data class RequestId(val topic: String, val id: Long)

    private data class RequestContent(val chainId: String?, val method: String, val params: String) {
        constructor(request: Wallet.Model.SessionRequest) : this(
            chainId = request.chainId,
            method = request.request.method,
            params = request.request.params
        )
    }

    private class Record(val content: RequestContent, val signed: SignedRequest)

    companion object {
        /** Shared by every interactor: Hilt creates one per screen, a retry may come from a new one. */
        val processWide = WalletConnectSignedRequests()
    }
}
