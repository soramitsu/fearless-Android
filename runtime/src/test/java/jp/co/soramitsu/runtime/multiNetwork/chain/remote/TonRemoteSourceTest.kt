package jp.co.soramitsu.runtime.multiNetwork.chain.remote

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.data.network.coingecko.CoingeckoApi
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.data.network.ton.AccountAddress
import jp.co.soramitsu.common.data.network.ton.AccountEvent
import jp.co.soramitsu.common.data.network.ton.AccountEventAction
import jp.co.soramitsu.common.data.network.ton.AccountEvents
import jp.co.soramitsu.common.data.network.ton.AccountStatus
import jp.co.soramitsu.common.data.network.ton.ActionSimplePreview
import jp.co.soramitsu.common.data.network.ton.DappConfigRemote
import jp.co.soramitsu.common.data.network.ton.EmulateMessageToWalletRequest
import jp.co.soramitsu.common.data.network.ton.EmulateMessageToWalletRequestParamsInner
import jp.co.soramitsu.common.data.network.ton.JettonTransferPayloadRemote
import jp.co.soramitsu.common.data.network.ton.JettonsBalances
import jp.co.soramitsu.common.data.network.ton.MessageConsequences
import jp.co.soramitsu.common.data.network.ton.Risk
import jp.co.soramitsu.common.data.network.ton.RatesResponse
import jp.co.soramitsu.common.data.network.ton.SendBlockchainMessageRequest
import jp.co.soramitsu.common.data.network.ton.TonAccountData
import jp.co.soramitsu.common.data.network.ton.TonApi
import jp.co.soramitsu.common.data.network.ton.TonAppManifest
import jp.co.soramitsu.common.data.network.ton.TonIndexerBalancesResponse
import jp.co.soramitsu.common.data.network.ton.TonIndexerJsonRpcRequest
import jp.co.soramitsu.common.data.network.ton.TonIndexerJsonRpcResponse
import jp.co.soramitsu.common.data.network.ton.TonIndexerMessage
import jp.co.soramitsu.common.data.network.ton.TonIndexerRunGetMethodRequest
import jp.co.soramitsu.common.data.network.ton.TonIndexerRunGetMethodResponse
import jp.co.soramitsu.common.data.network.ton.TonIndexerStateResponse
import jp.co.soramitsu.common.data.network.ton.TonIndexerTransactionDetail
import jp.co.soramitsu.common.data.network.ton.TonIndexerTransaction
import jp.co.soramitsu.common.data.network.ton.TonIndexerTransactionsResponse
import jp.co.soramitsu.common.data.network.ton.Trace
import jp.co.soramitsu.common.data.network.ton.Transaction
import jp.co.soramitsu.common.data.network.ton.totalFees
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainNodeRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.toChain
import kotlinx.coroutines.CancellationException
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class TonRemoteSourceTest {

    private lateinit var tonApi: FakeTonApi
    private lateinit var source: TonRemoteSource

    private val chain: Chain = ChainRemote(
        chainId = "-239",
        paraId = null,
        rank = null,
        name = "TON",
        minSupportedVersion = null,
        assets = emptyList(),
        nodes = listOf(ChainNodeRemote(url = "https://tonapi.io", name = "tonapi")),
        externalApi = null,
        icon = null,
        addressPrefix = 0,
        options = listOf("ethereumBased"),
        parentId = null,
        ecosystem = "Ton"
    ).toChain()

    private val chainWithoutTonApi: Chain = ChainRemote(
        chainId = "-239",
        paraId = null,
        rank = null,
        name = "TON",
        minSupportedVersion = null,
        assets = emptyList(),
        nodes = listOf(ChainNodeRemote(url = "https://history.example", name = "history")),
        externalApi = null,
        icon = null,
        addressPrefix = 0,
        options = listOf("ethereumBased"),
        parentId = null,
        ecosystem = "Ton"
    ).toChain()

    @Before
    fun setup() {
        tonApi = FakeTonApi()
        source = TonRemoteSource(
            tonApi = tonApi,
            availableFiatCurrencies = GetAvailableFiatCurrencies(FakeCoingeckoApi()),
            gson = Gson(),
            tonIndexerUrl = "https://indexer.example"
        )
    }

    @Test
    fun `loadAccountData should use indexer data first`() {
        kotlinx.coroutines.runBlocking {
            tonApi.indexerBalancesResult = Result.success(
                TonIndexerBalancesResponse(
                    address = "addr",
                    tonRaw = "123",
                    assets = emptyList()
                )
            )
            tonApi.indexerStateResult = Result.success(
                TonIndexerStateResponse(
                    address = "addr",
                    accountState = "active",
                    lastSeenUtime = 100
                )
            )

            val result = source.loadAccountData(chainWithoutTonApi, "addr")

            assertEquals(123L, result.balance)
            assertEquals(AccountStatus.active, result.status)
            assertEquals(null, tonApi.lastAccountDataUrl)
        }
    }

    @Test
    fun `loadAccountData should fallback to tonapi when indexer fails`() {
        kotlinx.coroutines.runBlocking {
            tonApi.indexerBalancesResult = Result.failure(IllegalStateException("indexer down"))
            tonApi.accountDataResult = Result.success(
                TonAccountData(
                    address = "fallback",
                    balance = 42,
                    lastActivity = 1,
                    status = AccountStatus.active,
                    getMethods = emptyList(),
                    isWallet = true
                )
            )

            val result = source.loadAccountData(chain, "addr")

            assertEquals("fallback", result.address)
            assertEquals(42L, result.balance)
            assertEquals("https://tonapi.io/v2/accounts/addr", tonApi.lastAccountDataUrl)
        }
    }

    @Test
    fun `loadAccountData should propagate cancellation from indexer`() {
        kotlinx.coroutines.runBlocking {
            tonApi.indexerBalancesResult = Result.failure(CancellationException("cancelled"))

            try {
                source.loadAccountData(chain, "addr")
                fail("CancellationException expected")
            } catch (_: CancellationException) {
                // expected
            }

            assertEquals(null, tonApi.lastAccountDataUrl)
        }
    }

    @Test
    fun `getSeqno should use indexer account state confirmed seqno`() {
        kotlinx.coroutines.runBlocking {
            tonApi.indexerStateResult = Result.success(
                TonIndexerStateResponse(
                    address = "addr",
                    lastConfirmedSeqno = 9,
                    accountState = "active"
                )
            )

            val seqno = source.getSeqno(chain, "addr")

            assertEquals(9, seqno)
            assertEquals(0, tonApi.runGetMethodCalls)
            assertEquals(null, tonApi.lastGetRequestUrl)
        }
    }

    @Test
    fun `getRawTime should use indexer jsonrpc first`() {
        kotlinx.coroutines.runBlocking {
            tonApi.indexerJsonRpcResults["getAddressInformation"] = Result.success(
                TonIndexerJsonRpcResponse(
                    result = JsonParser.parseString("""{"sync_utime":1700000000}""")
                )
            )

            val time = source.getRawTime(chain, "addr")

            assertEquals(1700000000, time)
            assertEquals("https://indexer.example/jsonRPC", tonApi.lastIndexerJsonRpcUrl)
            assertEquals("getAddressInformation", tonApi.lastIndexerJsonRpcMethod)
            assertEquals(null, tonApi.lastGetRequestUrl)
        }
    }

    @Test
    fun `getRawTime should fallback to tonapi when indexer jsonrpc fails`() {
        kotlinx.coroutines.runBlocking {
            tonApi.indexerJsonRpcResults["getAddressInformation"] = Result.failure(IllegalStateException("indexer down"))
            tonApi.getRequestResult = Result.success("""{"time":1234}""")

            val time = source.getRawTime(chainWithoutTonApi, "addr")

            assertEquals(1234, time)
            assertEquals("https://tonapi.io/v2/liteserver/get_time", tonApi.lastGetRequestUrl)
        }
    }

    @Test
    fun `sendBlockchainMessage should use indexer jsonrpc first`() {
        kotlinx.coroutines.runBlocking {
            tonApi.indexerJsonRpcResults["sendBoc"] = Result.success(
                TonIndexerJsonRpcResponse(
                    result = JsonParser.parseString("""{"@type":"ok"}""")
                )
            )

            source.sendBlockchainMessage(chain, SendBlockchainMessageRequest(boc = "boc-data"))

            assertEquals("https://indexer.example/jsonRPC", tonApi.lastIndexerJsonRpcUrl)
            assertEquals("sendBoc", tonApi.lastIndexerJsonRpcMethod)
            assertEquals(null, tonApi.lastSendBlockchainMessageUrl)
        }
    }

    @Test
    fun `getJettonTransferPayload should use indexer endpoint first`() {
        kotlinx.coroutines.runBlocking {
            tonApi.indexerJettonTransferPayloadResult = Result.success(
                JettonTransferPayloadRemote(
                    customPayload = null,
                    stateInit = null
                )
            )

            source.getJettonTransferPayload(chain, "owner", "jetton")

            assertEquals(
                "https://indexer.example/api/indexer/v1/jettons/jetton/transfer/owner/payload",
                tonApi.lastIndexerJettonTransferPayloadUrl
            )
            assertEquals(null, tonApi.lastGetRequestUrl)
        }
    }

    @Test
    fun `getJettonTransferPayload should fallback to tonapi when indexer fails`() {
        kotlinx.coroutines.runBlocking {
            tonApi.indexerJettonTransferPayloadResult = Result.failure(IllegalStateException("indexer down"))
            tonApi.getRequestResult = Result.success("""{"custom_payload":null,"state_init":null}""")

            source.getJettonTransferPayload(chainWithoutTonApi, "owner", "jetton")

            assertEquals("https://tonapi.io/v2/jettons/jetton/transfer/owner/payload", tonApi.lastGetRequestUrl)
        }
    }

    @Test
    fun `sendBlockchainMessage should fallback to tonapi when indexer jsonrpc fails`() {
        kotlinx.coroutines.runBlocking {
            tonApi.indexerJsonRpcResults["sendBoc"] = Result.failure(IllegalStateException("indexer down"))
            tonApi.sendBlockchainMessageResult = Result.success("""{"ok":true}""")

            source.sendBlockchainMessage(chainWithoutTonApi, SendBlockchainMessageRequest(boc = "boc-data"))

            assertEquals("https://tonapi.io/v2/blockchain/message", tonApi.lastSendBlockchainMessageUrl)
        }
    }

    @Test
    fun `emulateBlockchainMessageRequest should use indexer estimate fee first`() {
        kotlinx.coroutines.runBlocking {
            tonApi.indexerJsonRpcResults["estimateFee"] = Result.success(
                TonIndexerJsonRpcResponse(
                    result = JsonParser.parseString(
                        """{"source_fees":{"in_fwd_fee":"10","storage_fee":"20","gas_fee":"30","fwd_fee":"40"}}"""
                    )
                )
            )

            val result = source.emulateBlockchainMessageRequest(
                chain,
                EmulateMessageToWalletRequest(
                    boc = "boc-data",
                    params = listOf(EmulateMessageToWalletRequestParamsInner(address = "addr"))
                )
            )

            assertEquals(BigInteger.valueOf(100L), result.totalFees)
            assertEquals(null, tonApi.lastEmulateBlockchainMessageUrl)
        }
    }

    @Test
    fun `emulateBlockchainMessageRequest should fallback to tonapi when indexer estimate fee fails`() {
        kotlinx.coroutines.runBlocking {
            tonApi.indexerJsonRpcResults["estimateFee"] = Result.failure(IllegalStateException("indexer down"))
            tonApi.emulateBlockchainMessageResult = Result.success(sampleMessageConsequences(totalFees = BigInteger.valueOf(77)))

            val result = source.emulateBlockchainMessageRequest(
                chainWithoutTonApi,
                EmulateMessageToWalletRequest(
                    boc = "boc-data",
                    params = listOf(EmulateMessageToWalletRequestParamsInner(address = "addr"))
                )
            )

            assertEquals(BigInteger.valueOf(77), result.totalFees)
            assertEquals("https://tonapi.io/v2/wallet/emulate", tonApi.lastEmulateBlockchainMessageUrl)
        }
    }

    @Test
    fun `getAccountEvents should use indexer transactions first`() {
        kotlinx.coroutines.runBlocking {
            tonApi.indexerTransactionsResults[1] = Result.success(
                TonIndexerTransactionsResponse(
                    page = 1,
                    pageSize = 50,
                    totalTxs = 1,
                    totalPages = 1,
                    historyComplete = true,
                    txs = listOf(
                        TonIndexerTransaction(
                            txId = "100:hash1",
                            utime = 1000L,
                            status = "success",
                            txType = "Transfer",
                            inValue = "123",
                            lt = "100",
                            hash = "hash1",
                            inMessage = TonIndexerMessage(
                                source = "sender",
                                destination = "recipient",
                                value = "123"
                            )
                        )
                    )
                )
            )

            val events = source.getAccountEvents(chain, "https://tonapi.io", "addr", beforeLt = null, limit = 10)

            assertEquals(1, events.events.size)
            assertEquals("https://indexer.example/api/indexer/v1/accounts/addr/txs", tonApi.lastIndexerTransactionsUrl)
            assertEquals(null, tonApi.lastAccountEventsUrl)
            assertEquals("100:hash1", events.events.first().eventId)
            assertEquals("sender", events.events.first().actions.first().tonTransfer?.sender?.address)
            assertEquals("recipient", events.events.first().actions.first().tonTransfer?.recipient?.address)
        }
    }

    @Test
    fun `getAccountEvents should fallback to tonapi when indexer fails`() {
        kotlinx.coroutines.runBlocking {
            tonApi.indexerTransactionsResults[1] = Result.failure(IllegalStateException("indexer down"))
            tonApi.accountEventsResult = Result.success(
                AccountEvents(
                    events = listOf(
                        AccountEvent(
                            eventId = "fallback",
                            account = AccountAddress(address = "addr", isScam = false, isWallet = true),
                            timestamp = 1L,
                            actions = listOf(
                                AccountEventAction(
                                    type = AccountEventAction.Type.unknown,
                                    status = AccountEventAction.Status.ok,
                                    simplePreview = ActionSimplePreview(
                                        name = "Fallback",
                                        description = "Fallback",
                                        accounts = emptyList()
                                    ),
                                    baseTransactions = emptyList()
                                )
                            ),
                            isScam = false,
                            lt = 1L,
                            inProgress = false,
                            extra = 0L
                        )
                    )
                )
            )

            val events = source.getAccountEvents(chainWithoutTonApi, "https://history.example", "addr", beforeLt = 99L, limit = 10)

            assertEquals(1, events.events.size)
            assertEquals("https://tonapi.io/v2/accounts/addr/events", tonApi.lastAccountEventsUrl)
            assertEquals(99L, tonApi.lastAccountEventsBeforeLt)
        }
    }

    @Test
    fun `getAccountEvents should keep jetton transfer mapped as jetton action`() {
        kotlinx.coroutines.runBlocking {
            val jettonAddress = "EQCjetton00000000000000000000000000000000000000000000000000000"
            tonApi.indexerTransactionsResults[1] = Result.success(
                TonIndexerTransactionsResponse(
                    page = 1,
                    pageSize = 50,
                    totalTxs = 1,
                    totalPages = 1,
                    historyComplete = true,
                    txs = listOf(
                        TonIndexerTransaction(
                            txId = "200:hash2",
                            utime = 2000L,
                            status = "success",
                            txType = "JettonTransfer",
                            inValue = "1",
                            lt = "200",
                            hash = "hash2",
                            detail = TonIndexerTransactionDetail(
                                kind = "transfer",
                                asset = jettonAddress,
                                amount = "555"
                            ),
                            inMessage = TonIndexerMessage(
                                source = "sender",
                                destination = "recipient",
                                value = "1"
                            )
                        )
                    )
                )
            )

            val action = source.getAccountEvents(chain, "https://tonapi.io", "addr", beforeLt = null, limit = 10)
                .events
                .first()
                .actions
                .first()

            assertEquals(AccountEventAction.Type.jettonTransfer, action.type)
            assertEquals(null, action.tonTransfer)
            assertEquals(jettonAddress, action.jettonTransfer?.jetton?.address)
        }
    }

    @Test
    fun `getAccountEvents should map indexer fee into event extra`() {
        kotlinx.coroutines.runBlocking {
            tonApi.indexerTransactionsResults[1] = Result.success(
                TonIndexerTransactionsResponse(
                    page = 1,
                    pageSize = 50,
                    totalTxs = 1,
                    totalPages = 1,
                    historyComplete = true,
                    txs = listOf(
                        TonIndexerTransaction(
                            txId = "300:hash-fee",
                            utime = 3000L,
                            status = "success",
                            txType = "Transfer",
                            lt = "300",
                            hash = "hash-fee",
                            fee = "100",
                            inMessage = TonIndexerMessage(
                                source = "sender",
                                destination = "recipient",
                                value = "10"
                            )
                        )
                    )
                )
            )

            val extra = source.getAccountEvents(chain, "https://tonapi.io", "addr", beforeLt = null, limit = 10)
                .events
                .first()
                .extra

            assertEquals(-100L, extra)
        }
    }

    @Test
    fun `getAccountEvents should use indexer cursor parameters when hash is provided`() {
        kotlinx.coroutines.runBlocking {
            tonApi.defaultIndexerTransactionsResult = Result.success(
                TonIndexerTransactionsResponse(
                    txs = emptyList()
                )
            )

            source.getAccountEvents(
                chain = chain,
                historyUrl = "https://tonapi.io",
                accountId = "addr",
                beforeLt = 321L,
                beforeHash = "abc123",
                limit = 10
            )

            assertEquals(null, tonApi.lastIndexerTransactionsPage)
            assertEquals("321", tonApi.lastIndexerTransactionsCursorLt)
            assertEquals("abc123", tonApi.lastIndexerTransactionsCursorHash)
        }
    }

    private fun sampleMessageConsequences(totalFees: BigInteger): MessageConsequences {
        return MessageConsequences(
            trace = Trace(
                transaction = Transaction(
                    hash = null,
                    lt = 1L,
                    account = AccountAddress(address = "addr", isScam = false, isWallet = true),
                    success = true,
                    utime = 1L,
                    origStatus = AccountStatus.active,
                    endStatus = AccountStatus.active,
                    totalFees = totalFees,
                    endBalance = 0L,
                    transactionType = "Generic",
                    stateUpdateOld = "",
                    stateUpdateNew = "",
                    outMsgs = emptyList(),
                    block = "",
                    aborted = false,
                    destroyed = false
                ),
                interfaces = emptyList(),
                children = emptyList(),
                emulated = true
            ),
            risk = Risk(
                transferAllRemainingBalance = false,
                ton = 0L,
                jettons = emptyList()
            ),
            event = AccountEvent(
                eventId = "sample",
                account = AccountAddress(address = "addr", isScam = false, isWallet = true),
                timestamp = 1L,
                actions = emptyList(),
                isScam = false,
                lt = 1L,
                inProgress = false,
                extra = 0L
            )
        )
    }

    private class FakeTonApi : TonApi {

        var indexerBalancesResult: Result<TonIndexerBalancesResponse> =
            Result.failure(IllegalStateException("not stubbed"))
        var indexerStateResult: Result<TonIndexerStateResponse> =
            Result.failure(IllegalStateException("not stubbed"))
        var accountDataResult: Result<TonAccountData> =
            Result.failure(IllegalStateException("not stubbed"))
        var accountEventsResult: Result<AccountEvents> =
            Result.failure(IllegalStateException("not stubbed"))
        var defaultIndexerTransactionsResult: Result<TonIndexerTransactionsResponse> =
            Result.failure(IllegalStateException("not stubbed"))
        var indexerJettonTransferPayloadResult: Result<JettonTransferPayloadRemote> =
            Result.failure(IllegalStateException("not stubbed"))
        val indexerTransactionsResults: MutableMap<Int, Result<TonIndexerTransactionsResponse>> = mutableMapOf()
        val indexerJsonRpcResults: MutableMap<String, Result<TonIndexerJsonRpcResponse>> = mutableMapOf()
        var defaultIndexerJsonRpcResult: Result<TonIndexerJsonRpcResponse> =
            Result.failure(IllegalStateException("not stubbed"))
        var getRequestResult: Result<String> =
            Result.failure(IllegalStateException("not stubbed"))
        var sendBlockchainMessageResult: Result<String> =
            Result.failure(IllegalStateException("not stubbed"))
        var emulateBlockchainMessageResult: Result<MessageConsequences> =
            Result.failure(IllegalStateException("not stubbed"))

        var lastAccountDataUrl: String? = null
        var lastAccountEventsUrl: String? = null
        var lastAccountEventsBeforeLt: Long? = null
        var lastIndexerTransactionsUrl: String? = null
        var lastIndexerJettonTransferPayloadUrl: String? = null
        var lastIndexerJsonRpcUrl: String? = null
        var lastIndexerJsonRpcMethod: String? = null
        var lastIndexerTransactionsPage: Int? = null
        var lastIndexerTransactionsCursorLt: String? = null
        var lastIndexerTransactionsCursorHash: String? = null
        var lastGetRequestUrl: String? = null
        var lastSendBlockchainMessageUrl: String? = null
        var lastEmulateBlockchainMessageUrl: String? = null
        var runGetMethodCalls: Int = 0

        var runGetMethodResult: Result<TonIndexerRunGetMethodResponse> =
            Result.failure(IllegalStateException("not stubbed"))

        override suspend fun getAccountData(url: String): TonAccountData {
            lastAccountDataUrl = url
            return accountDataResult.getOrThrow()
        }

        override suspend fun getJettonBalances(url: String, currencies: List<String>?): JettonsBalances {
            throw UnsupportedOperationException("Not needed in this test")
        }

        override suspend fun getIndexerBalances(url: String): TonIndexerBalancesResponse {
            return indexerBalancesResult.getOrThrow()
        }

        override suspend fun getIndexerState(url: String): TonIndexerStateResponse {
            return indexerStateResult.getOrThrow()
        }

        override suspend fun runIndexerGetMethod(
            url: String,
            body: TonIndexerRunGetMethodRequest
        ): TonIndexerRunGetMethodResponse {
            runGetMethodCalls += 1
            return runGetMethodResult.getOrThrow()
        }

        override suspend fun getIndexerTransactions(
            url: String,
            page: Int?,
            cursorLt: String?,
            cursorHash: String?
        ): TonIndexerTransactionsResponse {
            lastIndexerTransactionsUrl = url
            lastIndexerTransactionsPage = page
            lastIndexerTransactionsCursorLt = cursorLt
            lastIndexerTransactionsCursorHash = cursorHash
            val pageIndex = page ?: 1
            return indexerTransactionsResults[pageIndex]?.getOrThrow() ?: defaultIndexerTransactionsResult.getOrThrow()
        }

        override suspend fun getIndexerJettonTransferPayload(url: String): JettonTransferPayloadRemote {
            lastIndexerJettonTransferPayloadUrl = url
            return indexerJettonTransferPayloadResult.getOrThrow()
        }

        override suspend fun callIndexerJsonRpc(
            url: String,
            body: TonIndexerJsonRpcRequest
        ): TonIndexerJsonRpcResponse {
            lastIndexerJsonRpcUrl = url
            lastIndexerJsonRpcMethod = body.method
            return indexerJsonRpcResults[body.method]?.getOrThrow() ?: defaultIndexerJsonRpcResult.getOrThrow()
        }

        override suspend fun getRequest(url: String): String {
            lastGetRequestUrl = url
            return getRequestResult.getOrThrow()
        }

        override suspend fun sendBlockchainMessage(url: String, body: SendBlockchainMessageRequest): String {
            lastSendBlockchainMessageUrl = url
            return sendBlockchainMessageResult.getOrThrow()
        }

        override suspend fun emulateBlockchainMessage(url: String, body: EmulateMessageToWalletRequest): MessageConsequences {
            lastEmulateBlockchainMessageUrl = url
            return emulateBlockchainMessageResult.getOrThrow()
        }

        override suspend fun getAccountEvents(
            url: String,
            limit: Int,
            initiator: Boolean?,
            subjectOnly: Boolean?,
            beforeLt: Long?,
            startFate: Long?,
            endDate: Long?
        ): AccountEvents {
            lastAccountEventsUrl = url
            lastAccountEventsBeforeLt = beforeLt
            return accountEventsResult.getOrThrow()
        }

        override suspend fun getManifest(url: String): TonAppManifest {
            throw UnsupportedOperationException("Not needed in this test")
        }

        override suspend fun tonconnectSend(url: String, body: RequestBody): ResponseBody {
            throw UnsupportedOperationException("Not needed in this test")
        }

        override suspend fun getDappsConfig(): List<DappConfigRemote> {
            throw UnsupportedOperationException("Not needed in this test")
        }

        override fun getTonCoinPrice(currencies: List<String>?): RatesResponse {
            throw UnsupportedOperationException("Not needed in this test")
        }
    }

    private class FakeCoingeckoApi : CoingeckoApi {

        override suspend fun getAssetPrice(
            priceIds: String,
            currency: String,
            includeRateChange: Boolean
        ): Map<String, Map<String, BigDecimal>> {
            throw UnsupportedOperationException("Not needed in this test")
        }

        override suspend fun getSingleAssetPrice(
            priceIds: String,
            currency: String
        ): Map<String, Map<String, Double>> {
            throw UnsupportedOperationException("Not needed in this test")
        }

        override suspend fun getSupportedCurrencies(): List<String> = listOf()

        override suspend fun getFiatConfig(): List<FiatCurrency> = listOf()
    }
}
