package jp.co.soramitsu.polkaswap.impl.data

import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.network.config.AvailableDexId
import jp.co.soramitsu.common.data.network.config.PolkaswapRemoteConfig
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.data.network.config.RemoteConfigFetcher
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.core.runtime.models.responses.QuoteResponse
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcError
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as CoreAsset
import jp.co.soramitsu.wallet.impl.domain.model.Asset as WalletAsset

class PolkaswapRepositoryImplTest {
    private val remoteConfigFetcher = mockk<RemoteConfigFetcher>()
    private val remoteStorage = mockk<StorageDataSource>()
    private val extrinsicService = mockk<ExtrinsicService>()
    private val chainRegistry = mockk<ChainRegistry>()
    private val accountRepository = mockk<AccountRepository>()
    private val toggles = mockk<ProductFeatureToggleStore>()
    private val preferences = mockk<Preferences>()
    private val walletRepository = mockk<WalletRepository>()
    private val socket = mockk<SocketService>()
    private val connection = mockk<ChainConnection>()
    private val runtime = mockk<RuntimeSnapshot>()
    private val requests = mutableListOf<RuntimeRequest>()

    private val input = coreAsset("val", INPUT_ID, "VAL", utility = false)
    private val output = coreAsset("pswap", OUTPUT_ID, "PSWAP", utility = false)
    private val xor = coreAsset("xor", XOR_ID, "XOR", utility = true)
    private val chain = mockk<Chain> {
        every { id } returns soraMainChainId
        every { ecosystem } returns Ecosystem.Substrate
        every { assets } returns listOf(input, output, xor)
        every { minSupportedVersion } returns null
        every { externalApi } returns null
        every { addressPrefix } returns 69
    }
    private val accountId = ByteArray(32) { 7 }

    private val repository = PolkaswapRepositoryImpl(
        remoteConfigFetcher, remoteStorage, extrinsicService, chainRegistry, accountRepository, toggles, preferences, walletRepository
    )

    private fun coreAsset(id: String, currencyId: String, symbol: String, utility: Boolean): CoreAsset = mockk {
        every { this@mockk.id } returns id
        every { this@mockk.currencyId } returns currencyId
        every { this@mockk.symbol } returns symbol
        every { isUtility } returns utility
    }

    private fun metaAccount(chainAccounts: Map<String, MetaAccount.ChainAccount> = emptyMap(), substrateId: ByteArray? = accountId) =
        MetaAccount(
            id = META_ID, chainAccounts = chainAccounts, favoriteChains = emptyMap(), substratePublicKey = ByteArray(32) { 1 },
            substrateCryptoType = CryptoType.SR25519, substrateAccountId = substrateId, ethereumAddress = null,
            ethereumPublicKey = null, tonPublicKey = null, isSelected = true, isBackedUp = true, googleBackupAddress = null,
            name = "wallet", initialized = true
        )

    @Before fun setUp() {
        every { socket.jsonMapper } returns Gson()
        every { connection.socketService } returns socket
        every { connection.chain } returns chain
        coEvery { chainRegistry.awaitConnection(soraMainChainId) } returns connection
        coEvery { chainRegistry.getChain(soraMainChainId) } returns chain
    }

    /** Answers every RPC request with [result] (or with [error]) for the request's dex id. */
    private fun answerRpc(error: (RuntimeRequest) -> Boolean = { false }, result: (RuntimeRequest) -> Any?) {
        every { socket.executeRequest(any(), any(), any()) } answers {
            val request = firstArg<RuntimeRequest>()
            requests += request
            val listener = thirdArg<SocketService.ResponseListener<RpcResponse>>()
            if (error(request)) {
                listener.onNext(RpcResponse("2.0", null, request.id, RpcError(-32000, "unsupported path")))
            } else {
                listener.onNext(RpcResponse("2.0", result(request), request.id, null))
            }
            mockk(relaxed = true)
        }
    }

    @Test fun pairAvailabilityAsksTheNodeForThePath() = runTest {
        answerRpc { it.params.first() == 1 }

        assertTrue(repository.isPairAvailable(soraMainChainId, INPUT_ID, OUTPUT_ID, 1))
        assertFalse(repository.isPairAvailable(soraMainChainId, INPUT_ID, OUTPUT_ID, 2))
        assertEquals("liquidityProxy_isPathAvailable", requests.first().method)
        assertEquals(listOf<Any>(1, INPUT_ID, OUTPUT_ID), requests.first().params)
    }

    @Test fun quotesCarryTheDesiredSideAndMarketFilter() = runTest {
        answerRpc { mapOf("amount" to "1500", "route" to listOf(INPUT_ID, XOR_ID, OUTPUT_ID)) }

        val quote = repository.getSwapQuote(soraMainChainId, INPUT_ID, OUTPUT_ID, BigInteger("500"), WithDesired.OUTPUT, listOf(Market.XYK), 0)

        assertEquals(QuoteResponse(BigInteger("1500"), listOf(INPUT_ID, XOR_ID, OUTPUT_ID)), quote)
        assertEquals("liquidityProxy_quote", requests.single().method)
        assertEquals(
            listOf<Any>(0, INPUT_ID, OUTPUT_ID, "500", "WithDesiredOutput", listOf("XYKPool"), "AllowSelected"),
            requests.single().params
        )
    }

    @Test fun failedQuoteMeansNoQuote() = runTest {
        every { socket.executeRequest(any(), any(), any()) } answers {
            thirdArg<SocketService.ResponseListener<RpcResponse>>().onError(IllegalStateException("socket closed"))
            mockk(relaxed = true)
        }

        assertNull(repository.getSwapQuote(soraMainChainId, INPUT_ID, OUTPUT_ID, BigInteger.TEN, WithDesired.INPUT, emptyList(), 0))
    }

    @Test fun enabledSourcesAreMappedToMarketsAndRpcErrorsToNone() = runTest {
        answerRpc(error = { it.params.first() == 1 }) { listOf("XYKPool", "MulticollateralBondingCurvePool", "Unknown") }

        val sources = repository.getAvailableSources(soraMainChainId, INPUT_ID, OUTPUT_ID, listOf(0, 1))

        assertEquals(mapOf(0 to listOf(Market.TBC, Market.XYK), 1 to emptyList<Market>()), sources)
        assertTrue(requests.all { it.method == "liquidityProxy_listEnabledSourcesForPath" })
    }

    @Test fun availableDexesAreTheOnChainDexesThatTheConfigAllows() = runTest {
        val prefix = slot<(RuntimeSnapshot) -> String?>()
        val keyExtractor = slot<(String) -> BigInteger>()
        val binding = slot<(String?, RuntimeSnapshot, BigInteger) -> String?>()
        coEvery {
            remoteStorage.queryByPrefix(soraMainChainId, capture(prefix), capture(keyExtractor), capture(binding))
        } returns mapOf(BigInteger.ZERO to "0x00", BigInteger.ONE to "0x01", BigInteger.TWO to null)
        coEvery { remoteConfigFetcher.getPolkaswapConfig() } returns PolkaswapRemoteConfig(
            version = "1", availableSources = emptyList(), forceSmartIds = emptyList(), xstusdId = "",
            availableDexIds = listOf(AvailableDexId("XOR", BigInteger.ZERO, XOR_ID), AvailableDexId("XSTUSD", BigInteger.TWO, "0x"))
        )

        assertEquals(listOf(BigInteger.ZERO, BigInteger.TWO), repository.getAvailableDexes(soraMainChainId))
        coVerify { chainRegistry.getChain(soraMainChainId) }

        // A runtime without the DEX manager has no DEX infos key; keys end with the little-endian dex id.
        val metadata = mockk<RuntimeMetadata> { every { modules } returns emptyMap() }
        every { runtime.metadata } returns metadata
        assertNull(prefix.captured(runtime))
        assertEquals(BigInteger.valueOf(5), keyExtractor.captured("0xabcdef05000000"))
        assertNull(binding.captured(null, runtime, BigInteger.ZERO))
    }

    @Test fun unreadableStorageOrConfigLeavesNoDexes() = runTest {
        coEvery { remoteStorage.queryByPrefix<BigInteger, String?>(any(), any(), any(), any()) } throws IllegalStateException("offline")
        coEvery { remoteConfigFetcher.getPolkaswapConfig() } throws IllegalStateException("offline")

        assertTrue(repository.getAvailableDexes(soraMainChainId).isEmpty())

        coEvery { remoteStorage.queryByPrefix<BigInteger, String?>(any(), any(), any(), any()) } returns mapOf(BigInteger.ZERO to "0x00")
        assertTrue(repository.getAvailableDexes(soraMainChainId).isEmpty())
    }

    @Test fun poolReservesAreObservedOnceTheChainIsKnown() = runTest {
        val xykKey = slot<(RuntimeSnapshot) -> String?>()
        val xykBinder = slot<(String?, RuntimeSnapshot) -> String>()
        val tbcKey = slot<(RuntimeSnapshot) -> String?>()
        val tbcBinder = slot<(String?, RuntimeSnapshot) -> String>()
        every { remoteStorage.observe(soraMainChainId, capture(xykKey), capture(xykBinder)) } returns flowOf("0xxyk")

        assertEquals("0xxyk", repository.observePoolXYKReserves(soraMainChainId, INPUT_ID, OUTPUT_ID).first())

        every { remoteStorage.observe(soraMainChainId, capture(tbcKey), capture(tbcBinder)) } returns flowOf("0xtbc")
        assertEquals("0xtbc", repository.observePoolTBCReserves(soraMainChainId, INPUT_ID).first())
        coVerify(exactly = 2) { chainRegistry.getChain(soraMainChainId) }

        val metadata = mockk<RuntimeMetadata> { every { modules } returns emptyMap() }
        every { runtime.metadata } returns metadata
        assertNull(xykKey.captured(runtime))
        assertNull(tbcKey.captured(runtime))
        assertEquals("", xykBinder.captured(null, runtime))
        assertEquals("0x01", tbcBinder.captured("0x01", runtime))
    }

    @Test fun feeEstimationNeedsASoraAccount() = runTest {
        coEvery { accountRepository.getSelectedMetaAccount() } returns metaAccount()
        coEvery { extrinsicService.estimateFee(chain, accountId, any(), any(), any(), any()) } returns BigInteger("77")

        assertEquals(
            BigInteger("77"),
            repository.estimateSwapFee(soraMainChainId, 0, INPUT_ID, OUTPUT_ID, BigInteger.TEN, BigInteger.ONE, "Disabled", emptyList(), WithDesired.INPUT)
        )

        coEvery { accountRepository.getSelectedMetaAccount() } returns metaAccount(substrateId = null)
        val failure = runCatching {
            repository.estimateSwapFee(soraMainChainId, 0, INPUT_ID, OUTPUT_ID, BigInteger.TEN, BigInteger.ONE, "Disabled", emptyList(), WithDesired.INPUT)
        }.exceptionOrNull()
        assertEquals("Add a SORA account to estimate this swap", failure?.message)
    }

    private fun prepareSwap(
        state: SocketStateMachine.State,
        meta: MetaAccount = metaAccount(),
        inputBalance: BigInteger = BigInteger.valueOf(1_000),
        xorBalance: BigInteger = BigInteger.valueOf(1_000),
        togglesAnswers: List<Boolean> = listOf(true),
        disclaimerAnswers: List<Boolean> = listOf(true)
    ) {
        every { toggles.polkaswapMutationsEnabled } returnsMany togglesAnswers
        every { preferences.getBoolean(PolkaswapInteractor.HAS_READ_DISCLAIMER_KEY, false) } returnsMany disclaimerAnswers
        coEvery { chainRegistry.getRuntimeOrNull(soraMainChainId) } returns runtime
        every { connection.selectedNode } returns ChainNode(NODE_URL, "SORA", isActive = true, isDefault = true)
        every { connection.state } returns MutableStateFlow(state)
        coEvery { accountRepository.getSelectedMetaAccount() } returns meta
        coEvery { accountRepository.isSigningRecoveryRequired(META_ID, any<IChain>()) } returns false
        coEvery { accountRepository.hasSigningMaterial(META_ID, any<IChain>()) } returns true
        val balances = mapOf(
            input.id to mockk<WalletAsset> { every { sendAvailableInPlanks } returns inputBalance },
            xor.id to mockk<WalletAsset> { every { sendAvailableInPlanks } returns xorBalance }
        )
        coEvery { walletRepository.getAsset(META_ID, any(), any(), null) } answers { balances[thirdArg<CoreAsset>().id] }
        coEvery { extrinsicService.estimateFee(chain, any(), any(), any(), any(), any()) } returns BigInteger.TEN
        coEvery { extrinsicService.submitExtrinsic(chain, any(), any(), any(), any(), any()) } returns Result.success("0xhash")
    }

    private suspend fun swap(inputId: String = INPUT_ID, desired: WithDesired = WithDesired.INPUT) =
        repository.swap(soraMainChainId, 0, inputId, OUTPUT_ID, BigInteger.valueOf(100), BigInteger.valueOf(90), "Disabled", emptyList(), desired)

    @Test fun validSwapIsSubmittedForEveryStableConnectionState() = runTest {
        val states = listOf(
            mockk<SocketStateMachine.State.Connected> { every { url } returns NODE_URL },
            mockk<SocketStateMachine.State.WaitingForReconnect> {
                every { url } returns NODE_URL
                every { attempt } returns 2
            },
            SocketStateMachine.State.Paused(NODE_URL),
            SocketStateMachine.State.Disconnected
        )
        states.forEach { state ->
            prepareSwap(state)
            assertEquals("0xhash", swap().getOrThrow())
        }
        coVerify(exactly = states.size) { extrinsicService.submitExtrinsic(chain, any(), any(), any(), any(), any()) }
    }

    @Test fun xorInputPaysAmountAndFeeFromOneBalance() = runTest {
        prepareSwap(SocketStateMachine.State.Disconnected, inputBalance = BigInteger.ZERO, xorBalance = BigInteger.valueOf(110))
        assertEquals("0xhash", swap(inputId = XOR_ID).getOrThrow())

        prepareSwap(SocketStateMachine.State.Disconnected, inputBalance = BigInteger.ZERO, xorBalance = BigInteger.valueOf(109))
        assertEquals("Input asset balance is insufficient for this swap", swap(inputId = XOR_ID).exceptionOrNull()?.message)
    }

    @Test fun exactOutputReservesTheLimitAndChainAccountsAuthorize() = runTest {
        val chainAccount = MetaAccount.ChainAccount(META_ID, null, ByteArray(32) { 3 }, ByteArray(32) { 4 }, CryptoType.ED25519, "sora")
        prepareSwap(
            SocketStateMachine.State.Paused(NODE_URL),
            meta = metaAccount(chainAccounts = mapOf(soraMainChainId to chainAccount)),
            inputBalance = BigInteger.valueOf(90)
        )

        assertEquals("0xhash", swap(desired = WithDesired.OUTPUT).getOrThrow())
        coVerify { extrinsicService.submitExtrinsic(chain, match { it.contentEquals(ByteArray(32) { 4 }) }, any(), any(), any(), any()) }
    }

    @Test fun killSwitchOrRevokedDisclaimerAfterReviewStopsSubmission() = runTest {
        prepareSwap(SocketStateMachine.State.Disconnected, togglesAnswers = listOf(true, true, false))
        assertEquals("Polkaswap actions are temporarily disabled", swap().exceptionOrNull()?.message)

        prepareSwap(SocketStateMachine.State.Disconnected, disclaimerAnswers = listOf(true, true, false))
        assertEquals("Accept the Polkaswap disclaimer before swapping", swap().exceptionOrNull()?.message)

        coVerify(exactly = 0) { extrinsicService.submitExtrinsic(any(), any(), any(), any(), any(), any()) }
    }

    @Test fun refusedSubmissionIsReturnedAsFailure() = runTest {
        prepareSwap(SocketStateMachine.State.Disconnected)
        coEvery { extrinsicService.submitExtrinsic(chain, any(), any(), any(), any(), any()) } returns
            Result.failure(IllegalStateException("1010: Invalid Transaction"))

        assertEquals("1010: Invalid Transaction", swap().exceptionOrNull()?.message)
    }

    private companion object {
        const val META_ID = 7L
        const val INPUT_ID = "0x01"
        const val OUTPUT_ID = "0x02"
        const val XOR_ID = "0x03"
        const val NODE_URL = "wss://sora.example"
    }
}
