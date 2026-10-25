package jp.co.soramitsu.staking.impl.presentation.confirm.pool.create

import jp.co.soramitsu.staking.impl.data.repository.PoolJoinPreferencesFixture
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import jp.co.soramitsu.core.extrinsic.keypair_provider.GuardedKeypairProvider
import jp.co.soramitsu.core.extrinsic.keypair_provider.WalletBoundKeypairProvider
import jp.co.soramitsu.core.extrinsic.mortality.Mortality
import jp.co.soramitsu.core.extrinsic.mortality.MortalityConstructor
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.rpc.RpcCalls
import jp.co.soramitsu.core.runtime.IChainRegistry
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.dynamic.DynamicTypeResolver
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.TypeRegistry
import jp.co.soramitsu.fearless_utils.runtime.definitions.registry.v13Preset
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.TypeReference
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Era
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.FixedByteArray
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u8
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.FunctionArgument
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.MetadataFunction
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.chain.RuntimeVersion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigInteger

import jp.co.soramitsu.core.extrinsic.ExtrinsicBuilderFactory
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.mockito.kotlin.doSuspendableAnswer

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.extrinsic.MutationExecutionGuard
import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.staking.impl.data.repository.StakingPoolApi
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolSharedStateProvider
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolState
import jp.co.soramitsu.staking.impl.scenarios.StakingPoolInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.spy
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CopyOnWriteArrayList

import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Vec
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Bytes
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.Extrinsic
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.staking.impl.presentation.common.StakingPoolCreateFlowState
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.flow.emptyFlow
import org.mockito.Answers
import org.mockito.kotlin.anyVararg

/** Actual create caller/API/SCALE signing pipeline with fixed public keys and mocked RPC only. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConfirmCreatePoolExecutionOwnershipTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    private val owned = mutableListOf<Harness>()
    private val uncaught = CopyOnWriteArrayList<Throwable>()
    private val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        Thread.setDefaultUncaughtExceptionHandler { _, error -> uncaught += error }
    }
    @After fun teardown() {
        for (h in owned) {
            h.resume.complete(Unit)
            h.outputResume.countDown()
            h.vm.viewModelScope.cancel()
            runBlocking { withTimeout(10_000) { h.vm.viewModelScope.coroutineContext.job.join() } }
        }
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        Dispatchers.resetMain()
        assertEquals(emptyList<Throwable>(), uncaught.toList())
    }

    @Test fun `healthy create preserves reviewed calls and exact addresses in one atomic batch`() {
        val h = harness()
        h.submitAndJoin()
        assertEquals(1, h.sent.size)
        assertEquals(1, h.keys.get())
        assertEquals(null, h.shared.createFlowState.get())
        assertBatch(h, h.sent.single().second)
    }
    @Test fun `create fee uses only public metadata and preserves original calls`() {
        val h = harness()
        assertEquals(0, h.keys.get())
        assertTrue(h.sent.isEmpty())
        assertBatch(h, h.estimated.single().second)
    }
    @Test fun `create fee binds the original wallet namespace`() {
        val h = harness()
        assertEquals(listOf(7L), h.walletNamespaces.toList())
        assertEquals(0, h.keys.get())
    }
    @Test fun `create submission binds the original wallet namespace`() {
        val h = harness()
        h.walletNamespaces.clear()
        h.submitAndJoin()
        assertEquals(listOf(7L), h.walletNamespaces.toList())
        assertEquals(1, h.sent.size)
    }
    @Test fun `changing the global chain after fee cannot change the submitted chain`() {
        val h = harness()
        h.selectedChain = h.otherChain
        h.submitAndJoin()
        assertEquals(listOf(GENESIS), h.sent.map { it.first })
    }
    @Test fun `Back at nonce prevents key access and output`() = check("nonce") { vm.onBackClick() }
    @Test fun `wallet round trip at runtime prevents key access and output`() = check("runtime") { switchWallet() }
    @Test fun `main replacement at runtime prevents key access and output`() = check("runtime") {
        shared.mainState.set(shared.requireMainState.copy(address = ByteArray(32) { 8 }.toAddress(42)))
    }
    @Test fun `draft replacement at nonce prevents key access and output`() = check("nonce") {
        shared.createFlowState.set(shared.requireCreateState.copy(poolName = "Another draft"))
    }
    @Test fun `dismissal at key boundary prevents key access and output`() = check("key") { vm.onDismissed() }
    @Test fun `Back at queued transport prevents output`() = check("transport") { vm.onBackClick() }
    @Test fun `wallet round trip at queued transport prevents output`() = check("transport") { switchWallet() }
    @Test fun `unchanged context through suspended preparation still succeeds`() = check("nonce", succeeds = true) {}

    @Test fun `fee retains original chain even when global selection already changed`() {
        val h = harness(initialOtherChain = true)
        assertEquals(listOf(GENESIS), h.estimated.map { it.first })
        assertEquals(0, h.keys.get())
    }
    @Test fun `legacy role runtime preserves state toggler and atomic metadata order`() {
        val h = harness(legacyRoles = true)
        assertBatch(h, h.estimated.single().second)
        h.submitAndJoin()
        assertEquals(1, h.sent.size)
        assertBatch(h, h.sent.single().second)
    }
    @Test fun `clearing actual ViewModel at nonce prevents key access and output`() = check("nonce") {
        androidx.lifecycle.ViewModelStore().also { it.put("create", vm); it.clear() }
    }
    @Test fun `changed frozen intent cannot reach output`() {
        val h = harness()
        h.wrongIntent = true
        h.submitAndJoin()
        assertEquals(0, h.sent.size)
        assertTrue(h.shared.createFlowState.get() != null)
    }
    @Test fun `Back remains responsive during admitted actual writer output`() = checkHeldOutput { vm.onBackClick() }
    @Test fun `wallet change remains responsive during admitted actual writer output`() = checkHeldOutput { switchWallet() }

    @Test fun `lost acknowledgement after admitted create cannot create a second payment`() = checkAmbiguousRetry(
        IllegalStateException("Local lost response"), beforeAdmission = false)
    @Test fun `cancellation after admitted create cannot create a second payment`() = checkAmbiguousRetry(
        kotlinx.coroutines.CancellationException("Local cancelled response"), beforeAdmission = false)
    @Test fun `proven unadmitted create failure can retry normally`() = checkAmbiguousRetry(
        IllegalStateException("Local queued request failed"), beforeAdmission = true)
    @Test fun `recreated create review cannot repeat unresolved local output`() = checkRecreatedReview(false)
    @Test fun `recreated create review cannot repeat known accepted output`() = checkRecreatedReview(true)
    @Test fun `wrong node acknowledgement cannot consume create review`() {
        val h = harness()
        h.responseOverride = "0x" + "ff".repeat(32)
        h.submitAndJoin()
        assertEquals(1, h.sent.size)
        assertTrue("Unmatched response must not become known success", h.shared.createFlowState.get() != null)
    }
    @Test fun `known matching acknowledgement survives cancellation on return dispatch`() {
        val h = harness()
        h.boundary = "transport"
        val before = h.vm.viewModelScope.coroutineContext.job.children.toSet()
        h.vm.onConfirm()
        await { h.entered.isCompleted }
        val submitting = (h.vm.viewModelScope.coroutineContext.job.children.toSet() - before).single()
        h.afterAcknowledgement = { submitting.cancel() }
        h.resume.complete(Unit)
        runBlocking { withTimeout(10_000) { submitting.join() } }
        assertEquals(1, h.sent.size)
        assertEquals("Known original success must survive cancelled dispatch to the ViewModel", null, h.shared.createFlowState.get())
    }

    private fun checkAmbiguousRetry(failure: Throwable, beforeAdmission: Boolean) {
        val h = harness()
        h.transportFailure = failure
        h.failBeforeAdmission = beforeAdmission
        h.submitAndJoin()
        assertEquals(if (beforeAdmission) 0 else 1, h.sent.size)
        h.transportFailure = null
        h.failBeforeAdmission = false
        h.submitAndJoin()
        assertEquals("An uncertain admitted create must not become a new signed payment", 1, h.sent.size)
    }
    private fun checkRecreatedReview(knownAcknowledgement: Boolean) {
        val h = harness()
        if (!knownAcknowledgement) h.transportFailure = IllegalStateException("Local lost response")
        h.submitAndJoin()
        assertEquals(1, h.sent.size)
        val recreated = harness(storage = h.storage)
        recreated.submitAndJoin()
        assertEquals("A recreated API and review must retain unresolved original output", 0, recreated.sent.size)
        assertEquals(0, recreated.keys.get())
    }

    private fun checkHeldOutput(invalidate: Harness.() -> Unit) {
        val h = harness()
        h.boundary = "output"
        h.vm.onConfirm()
        await { h.entered.isCompleted }
        assertFalse(requireNotNull(h.outputHandle).cancel())
        val departed = java.util.concurrent.CountDownLatch(1)
        val worker = kotlin.concurrent.thread { h.invalidate(); departed.countDown() }
        val responsive: Boolean
        try { responsive = departed.await(500, java.util.concurrent.TimeUnit.MILLISECONDS) }
        finally { h.outputResume.countDown(); worker.join(5_000) }
        assertTrue("Admitted blocking output must not hold navigation or wallet selection", responsive)
        await { h.sent.size == 1 && h.shared.createFlowState.get() == null }
    }

    private fun check(boundary: String, succeeds: Boolean = false, revoke: Harness.() -> Unit) {
        val h = harness()
        h.boundary = boundary
        val before = h.vm.viewModelScope.coroutineContext.job.children.toSet()
        h.vm.onConfirm()
        await { h.entered.isCompleted }
        val submitting = (h.vm.viewModelScope.coroutineContext.job.children.toSet() - before).single()
        h.revoke()
        h.resume.complete(Unit)
        runBlocking { withTimeout(10_000) { submitting.join() } }
        assertEquals("Actual local transport handoff count", if (succeeds) 1 else 0, h.sent.size)
        if (!succeeds && boundary != "transport") assertEquals("Revoked preparation must not read a private key", 0, h.keys.get())
        if (succeeds) assertEquals(null, h.shared.createFlowState.get())
    }
    private fun harness(legacyRoles: Boolean = false, initialOtherChain: Boolean = false,
        storage: PoolJoinPreferencesFixture = PoolJoinPreferencesFixture()): Harness = Harness(legacyRoles, initialOtherChain, storage).also {
        owned += it
        runBlocking { withTimeout(10_000) { it.vm.feeViewStateFlow.first { fee -> fee.value != null } } }
    }
    private fun assertBatch(h: Harness, encoded: String) {
        val decoded = Extrinsic.fromHex(h.runtime, encoded)
        assertArrayEquals(h.account, decoded.signature!!.accountIdentifier as ByteArray)
        assertEquals("batch_all", decoded.call.function.name)
        val calls = (decoded.call.arguments["calls"] as List<*>).map { it as GenericCall.Instance }
        assertEquals(listOf("create", "set_metadata"), calls.map { it.function.name })
        val create = calls[0].arguments
        assertEquals(BigInteger.TEN, create["amount"])
        assertArrayEquals(h.account, (create["root"] as DictEnum.Entry<*>).value as ByteArray)
        assertArrayEquals(h.nominator, (create["nominator"] as DictEnum.Entry<*>).value as ByteArray)
        assertArrayEquals(h.bouncer, (create[if (h.legacyRoles) "state_toggler" else "bouncer"] as DictEnum.Entry<*>).value as ByteArray)
        assertEquals(BigInteger.valueOf(8), calls[1].arguments["pool_id"])
        assertArrayEquals("Original local pool".toByteArray(), calls[1].arguments["metadata"] as ByteArray)
    }

    private class Harness(val legacyRoles: Boolean, initialOtherChain: Boolean, val storage: PoolJoinPreferencesFixture) {
        val chain = mock<Chain> {
            on { id } doReturn GENESIS
            on { name } doReturn "Local execution fixture"
            on { ecosystem } doReturn Ecosystem.Substrate
        }
        val otherChain = mock<Chain> {
            on { id } doReturn OTHER_GENESIS
            on { name } doReturn "Other local fixture"
            on { ecosystem } doReturn Ecosystem.Substrate
        }
        @Volatile var selectedChain = if (initialOtherChain) otherChain else chain
        val account = ByteArray(32) { 5 }
        val nominator = ByteArray(32) { 6 }
        val bouncer = ByteArray(32) { 9 }
        val asset = mock<Asset>(defaultAnswer = Answers.RETURNS_DEEP_STUBS).also { a ->
            whenever(a.metaId).thenReturn(7L)
            whenever(a.transferable).thenReturn(BigDecimal.valueOf(100))
            whenever(a.transferableInPlanks).thenReturn(BigInteger.valueOf(100))
            whenever(a.token.configuration.id).thenReturn("native")
            whenever(a.token.configuration.chainId).thenReturn(GENESIS)
            whenever(a.token.configuration.symbol).thenReturn("LOCAL")
            whenever(a.token.configuration.precision).thenReturn(0)
            whenever(a.token.fiatRate).thenReturn(null)
        }
        val shared = StakingPoolSharedStateProvider().apply {
            mainState.set(StakingPoolState(chain = chain, asset = asset, address = account.toAddress(42)))
            createFlowState.set(StakingPoolCreateFlowState("Original local pool", BigInteger.TEN, 8,
                nominator.toAddress(42), bouncer.toAddress(42), mainState.capture()))
        }
        @Volatile var outputHandle: com.neovisionaries.ws.client.GuardedWriteHandle? = null
        val outputResume = java.util.concurrent.CountDownLatch(1)
        @Volatile var wrongIntent = false
        @Volatile var transportFailure: Throwable? = null
        @Volatile var failBeforeAdmission = false
        @Volatile var responseOverride: String? = null
        @Volatile var afterAcknowledgement: (() -> Unit)? = null
        val entered = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val keys = AtomicInteger(0)
        val sent = CopyOnWriteArrayList<Pair<String, String>>()
        val estimated = CopyOnWriteArrayList<Pair<String, String>>()
        val walletNamespaces = CopyOnWriteArrayList<Long>()
        @Volatile var boundary: String? = null
        val router = mock<jp.co.soramitsu.staking.impl.presentation.StakingRouter>()
        val rpc = mock<RpcCalls>()
        val provider = mock<GuardedKeypairProvider>()
        val wallets = mock<WalletBoundKeypairProvider>()
        val runtime = runtime(POLKADOT_EXTENSIONS, listOf(poolModule(legacyRoles), UTILITY))
        val registry = mock<IChainRegistry>()
        val chainRegistry = mock<ChainRegistry>()
        val staking = mock<StakingSharedState>()
        private val mortality = mock<MortalityConstructor>()
        private val service = ExtrinsicService(rpc, wallets, ExtrinsicBuilderFactory(rpc, registry, mortality))
        val finality = mock<jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader> {
            onBlocking { anchor(any()) } doReturn jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader.Anchor(
                "0x" + GENESIS, BigInteger.ZERO, "0x" + "20".repeat(32))
        }
        val api = StakingPoolApi(service, staking, chainRegistry, storage.journal(), finality)
        val interactor = spy(StakingPoolInteractor(api, mock(), mock(), mock(), mock(), mock(), mock(), mock()))
        private val resources = mock<ResourceManager> {
            on { getString(any()) } doAnswer { "fixture-text" }
            on { getString(any(), anyVararg()) } doAnswer { "fixture-text" }
        }
        val vm: ConfirmCreatePoolViewModel
        init {
            runBlocking {
                whenever(staking.chain()).doSuspendableAnswer { selectedChain }
                whenever(chainRegistry.getRemoteRuntimeVersion(any())).thenReturn(if (legacyRoles) 9389 else 1_005_000)
                whenever(registry.getRuntime(any())).doSuspendableAnswer { hold("runtime"); runtime }
                whenever(rpc.getRuntimeVersion(any())).thenReturn(RuntimeVersion(1_005_000, 26))
                whenever(rpc.getAccountNonce(any(), any())).doSuspendableAnswer { hold("nonce"); BigInteger.valueOf(3) }
                whenever(rpc.estimateExtrinsicFee(any(), any())).doSuspendableAnswer { call ->
                    estimated += call.getArgument<String>(0) to call.getArgument<String>(1); BigInteger.ONE
                }
                whenever(mortality.construct(any())).thenReturn(Mortality(Era.Mortal(64,12), BLOCK_HASH))
                whenever(wallets.getCryptoTypeFor(any(), any())).thenReturn(CryptoType.ED25519)
                whenever(wallets.getKeypairFor(any(), any())).doSuspendableAnswer {
                    hold("key"); keys.incrementAndGet(); BaseKeypair(ByteArray(32) { 7 }, account)
                }
                whenever(wallets.forWallet(any())).thenAnswer { call -> walletNamespaces += call.getArgument<Long>(0); provider }
                whenever(provider.getCryptoTypeFor(any(), any())).thenReturn(CryptoType.ED25519)
                whenever(provider.getAuthorizedKeypairFor(any(), any(), any(), any())).doSuspendableAnswer { call ->
                    hold("key")
                    call.getArgument<MutationExecutionGuard>(2).runIfAuthorized(call.getArgument(3)) {
                        keys.incrementAndGet(); BaseKeypair(ByteArray(32) { 7 }, account)
                    }
                }
                whenever(rpc.submitExtrinsic(any(), any())).doSuspendableAnswer { call ->
                    hold("transport")
                    sent += call.getArgument<String>(0) to call.getArgument<String>(1)
                    call.getArgument<String>(1).fromHex().blake2b256().toHexString(withPrefix = true)
                }
                whenever(rpc.submitAuthorizedExtrinsic(any(), any(), any(), any())).doSuspendableAnswer { call ->
                    hold("transport")
                    if (failBeforeAdmission) transportFailure?.let { throw it }
                    val guard = call.getArgument<MutationExecutionGuard>(2)
                    val intent = call.getArgument<String>(3) + if (wrongIntent) "changed" else ""
                    if (boundary == "output") {
                        writeThroughActualHandle(guard, intent, call.getArgument(0), call.getArgument(1))
                    } else guard.runSubmissionIfAuthorized(intent) {
                        sent += call.getArgument<String>(0) to call.getArgument<String>(1)
                    }
                    transportFailure?.let { throw it }
                    afterAcknowledgement?.invoke()
                    responseOverride ?: call.getArgument<String>(1).fromHex().blake2b256().toHexString(withPrefix = true)
                }
                doReturn(null).whenever(interactor).getAccountName(any())
            }
            vm = ConfirmCreatePoolViewModel(
                mock<WalletInteractor> { on { assetFlow(any(), any()) } doReturn emptyFlow() },
                object : ExistentialDepositUseCase {
                    override suspend fun invoke(chainAsset: jp.co.soramitsu.core.models.Asset) = Result.success(BigInteger.ZERO)
                }, shared, interactor, resources, router, interactor)
        }
        fun submitAndJoin() {
            val before = vm.viewModelScope.coroutineContext.job.children.toSet()
            vm.onConfirm()
            val submitted = vm.viewModelScope.coroutineContext.job.children.toSet() - before
            runBlocking { withTimeout(10_000) { submitted.forEach { it.join() } } }
        }
        /** Exercises the source-pinned writer handle against a local OutputStream, without a socket. */
        private fun writeThroughActualHandle(guard: MutationExecutionGuard, intent: String, chainId: String, encoded: String) {
            val frozenBytes = encoded.toByteArray()
            val writerGuard = com.neovisionaries.ws.client.WebSocketWriteGuard { write ->
                guard.runSubmissionIfAuthorized(intent) { write.write() }
            }
            val callback = com.neovisionaries.ws.client.GuardedWriteCallback { throw AssertionError("Unexpected writer failure callback", it) }
            // The handle constructor/writeTo are transport-package internal; preserve production visibility.
            val constructor = com.neovisionaries.ws.client.GuardedWriteHandle::class.java.getDeclaredConstructor(
                com.neovisionaries.ws.client.WebSocketWriteGuard::class.java,
                com.neovisionaries.ws.client.GuardedWriteCallback::class.java
            ).apply { isAccessible = true }
            val handle = constructor.newInstance(writerGuard, callback)
            outputHandle = handle
            val method = handle.javaClass.getDeclaredMethod("writeTo", java.io.OutputStream::class.java, ByteArray::class.java)
                .apply { isAccessible = true }
            val output = object : java.io.OutputStream() {
                override fun write(value: Int) { error("Expected one frozen byte batch") }
                override fun write(bytes: ByteArray) {
                    assertArrayEquals(frozenBytes, bytes)
                    if (boundary == "output-write") holdOutput()
                    sent += chainId to encoded
                }
                override fun flush() { if (boundary == "output") holdOutput() }
            }
            try { method.invoke(handle, output, frozenBytes) }
            catch (error: java.lang.reflect.InvocationTargetException) { throw error.targetException }
            val duplicate = org.junit.Assert.assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
                method.invoke(handle, output, frozenBytes)
            }
            assertTrue(duplicate.targetException is IllegalStateException)
            assertEquals("Duplicate final writer invocation cannot replay the primitive", 1, sent.size)
        }

        private fun holdOutput() {
            entered.complete(Unit)
            check(outputResume.await(10, java.util.concurrent.TimeUnit.SECONDS))
        }

        private suspend fun hold(stage: String) { if (boundary == stage) { entered.complete(Unit); resume.await() } }
        fun switchWallet() { WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }
    }
    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue("Local held boundary was reached", condition())
    }
    private companion object {
        val GENESIS = "91".repeat(32)
        val BLOCK_HASH = ByteArray(32) { 0x11 }
        val FEE: BigInteger = BigInteger.valueOf(15_000_000)

        // Order and names as Polkadot 2005000 and Avail list them; unmapped ones encode nothing.
        val POLKADOT_EXTENSIONS = listOf(
            "CheckNonZeroSender", "CheckSpecVersion", "CheckTxVersion", "CheckGenesis", "CheckMortality",
            "CheckNonce", "CheckWeight", "ChargeTransactionPayment", "PrevalidateAttests", "CheckMetadataHash"
        )
        val OTHER_GENESIS = "92".repeat(32)
        val MULTI = DictEnum("MultiAddress", listOf(DictEnum.Entry("Id", TypeReference(FixedByteArray("AccountId", 32)))))
        fun poolModule(legacy: Boolean) = Module("NominationPools", null, mapOf(
            "create" to MetadataFunction("create", listOf(FunctionArgument("amount", u8), FunctionArgument("root", MULTI),
                FunctionArgument("nominator", MULTI), FunctionArgument(if (legacy) "state_toggler" else "bouncer", MULTI)), emptyList(), 39 to 1),
            "set_metadata" to MetadataFunction("set_metadata", listOf(FunctionArgument("pool_id", u8), FunctionArgument("metadata", Bytes)), emptyList(), 39 to 2)
        ), emptyMap(), emptyMap(), emptyMap(), BigInteger.valueOf(39))
        val UTILITY = Module("Utility", null, listOf("batch", "batch_all").mapIndexed { index, name ->
            name to MetadataFunction(name, listOf(FunctionArgument("calls", Vec("Calls", TypeReference(GenericCall)))), emptyList(), 26 to index)
        }.toMap(), emptyMap(), emptyMap(), emptyMap(), BigInteger.valueOf(26))

        fun runtime(signedExtensions: List<String>, modules: List<Module>): RuntimeSnapshot {
            val signature = DictEnum(
                "ExtrinsicSignature",
                listOf(
                    DictEnum.Entry("Ed25519", TypeReference(FixedByteArray("Ed25519", 64))),
                    DictEnum.Entry("Sr25519", TypeReference(FixedByteArray("Sr25519", 64))),
                    DictEnum.Entry("Ecdsa", TypeReference(FixedByteArray("Ecdsa", 65)))
                )
            )
            val registry = TypeRegistry(
                v13Preset() + mapOf(
                    "Address" to TypeReference(FixedByteArray("Address", 32)),
                    "ExtrinsicSignature" to TypeReference(signature)
                ),
                DynamicTypeResolver.defaultCompoundResolver()
            )
            return RuntimeSnapshot(
                registry,
                RuntimeMetadata(
                    BigInteger.valueOf(14),
                    modules.associateBy { it.name },
                    ExtrinsicMetadata(BigInteger.valueOf(4), signedExtensions)
                )
            )
        }
    }
}
