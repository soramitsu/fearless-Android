package jp.co.soramitsu.polkaswap.impl.data

import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.data.network.config.RemoteConfigFetcher
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.storage.source.StorageDataSource
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import jp.co.soramitsu.wallet.impl.domain.model.Asset as WalletAsset

class PolkaswapSubmissionGuardTest {

    @Test fun `kill switch bypass cannot submit`() = assertBlocked { toggleEnabled = false }
    @Test fun `missing disclaimer bypass cannot submit`() = assertBlocked { disclaimerAccepted = false }
    @Test fun `non SORA chain bypass cannot submit`() = assertBlocked { requestChainId = "polkadot" }
    @Test fun `missing runtime bypass cannot submit`() = assertBlocked { runtimeReady = false }
    @Test fun `missing SORA account bypass cannot submit`() = assertBlocked { hasAccount = false }
    @Test fun `watch only account bypass cannot submit`() = assertBlocked { hasSigningMaterial = false }
    @Test fun `recovery required account bypass cannot submit`() = assertBlocked { recoveryRequired = true }
    @Test fun `unregistered currency id bypass cannot submit`() = assertBlocked { inputCurrencyId = "unregistered" }
    @Test fun `same input and output bypass cannot submit`() = assertBlocked { outputCurrencyId = INPUT_CURRENCY_ID }
    @Test fun `zero amount bypass cannot submit`() = assertBlocked { amount = BigInteger.ZERO }
    @Test fun `zero limit bypass cannot submit`() = assertBlocked { limit = BigInteger.ZERO }
    @Test fun `insufficient input balance bypass cannot submit`() = assertBlocked { inputBalance = BigInteger.ONE }
    @Test fun `insufficient XOR fee balance bypass cannot submit`() = assertBlocked { xorBalance = BigInteger.ONE }

    @Test
    fun `selected wallet change during fee estimation requires renewed confirmation`() = assertBlocked(
        "The selected wallet changed. Review and confirm this swap again."
    ) {
        walletChangesAfterFee = true
    }

    @Test
    fun `SORA key identity change during fee estimation requires renewed confirmation`() = assertBlocked(
        "The selected SORA account changed. Review and confirm this swap again."
    ) {
        accountIdentityChangesAfterFee = true
    }

    @Test
    fun `SORA account id change during fee estimation requires renewed confirmation`() = assertBlocked(
        "The selected SORA account changed. Review and confirm this swap again."
    ) {
        accountIdChangesAfterFee = true
    }

    @Test
    fun `runtime change during fee estimation requires renewed confirmation`() = assertBlocked(
        "The SORA runtime changed. Review and confirm this swap again."
    ) {
        runtimeChangesAfterFee = true
    }

    @Test
    fun `runtime change during final balance refresh cannot reach submit`() = assertBlocked(
        "The SORA runtime changed. Review and confirm this swap again."
    ) {
        runtimeChangesDuringBalanceRefresh = true
    }

    @Test
    fun `connection change during fee estimation requires renewed confirmation`() = assertBlocked(
        "The SORA connection changed. Review and confirm this swap again."
    ) {
        connectionChangesAfterFee = true
    }

    @Test
    fun `socket endpoint change during fee estimation requires renewed confirmation`() = assertBlocked(
        "The SORA connection changed. Review and confirm this swap again."
    ) {
        socketStateChangesAfterFee = true
    }

    @Test
    fun `node change during fee estimation requires renewed confirmation`() = assertBlocked(
        "The SORA connection changed. Review and confirm this swap again."
    ) {
        nodeChangesAfterFee = true
    }

    @Test
    fun `node change during final balance refresh cannot reach submit`() = assertBlocked(
        "The SORA connection changed. Review and confirm this swap again."
    ) {
        nodeChangesDuringBalanceRefresh = true
    }

    private fun assertBlocked(
        expectedMessage: String? = null,
        configure: Harness.() -> Unit
    ) = runBlocking {
        val harness = Harness().apply(configure)
        val result = harness.execute()

        assertTrue("Expected the final submission boundary to reject the bypass", result.isFailure)
        expectedMessage?.let { assertEquals(it, result.exceptionOrNull()?.message) }
        assertEquals("No rejected path may reach submitExtrinsic", 0, harness.submitAttempts)
    }

    private class Harness {
        var toggleEnabled = true
        var disclaimerAccepted = true
        var requestChainId = soraMainChainId
        var runtimeReady = true
        var hasAccount = true
        var hasSigningMaterial = true
        var recoveryRequired = false
        var inputCurrencyId = INPUT_CURRENCY_ID
        var outputCurrencyId = OUTPUT_CURRENCY_ID
        var amount: BigInteger = BigInteger.valueOf(100)
        var limit: BigInteger = BigInteger.valueOf(90)
        var inputBalance: BigInteger = BigInteger.valueOf(1_000)
        var xorBalance: BigInteger = BigInteger.valueOf(1_000)
        var walletChangesAfterFee = false
        var accountIdentityChangesAfterFee = false
        var accountIdChangesAfterFee = false
        var runtimeChangesAfterFee = false
        var runtimeChangesDuringBalanceRefresh = false
        var connectionChangesAfterFee = false
        var socketStateChangesAfterFee = false
        var nodeChangesAfterFee = false
        var nodeChangesDuringBalanceRefresh = false
        var submitAttempts = 0

        private val remoteConfigFetcher = mock<RemoteConfigFetcher>()
        private val remoteStorage = mock<StorageDataSource>()
        private val chainRegistry = mock<ChainRegistry>()
        private val accountRepository = mock<AccountRepository>()
        private val toggleStore = mock<ProductFeatureToggleStore>()
        private val preferences = mock<Preferences>()
        private val walletRepository = mock<WalletRepository>()
        private val extrinsicService: ExtrinsicService = Mockito.mock(
            ExtrinsicService::class.java
        ) { invocation ->
            if (invocation.method.name == "submitExtrinsic") submitAttempts++
            Mockito.RETURNS_DEFAULTS.answer(invocation)
        }

        suspend fun execute(): Result<String> {
            val input = coreAsset("input", INPUT_CURRENCY_ID, "VAL", isUtility = false)
            val output = coreAsset("output", OUTPUT_CURRENCY_ID, "PSWAP", isUtility = false)
            val xor = coreAsset("xor", XOR_CURRENCY_ID, "XOR", isUtility = true)
            val chain = mock<Chain> {
                on { id }.thenReturn(soraMainChainId)
                on { ecosystem }.thenReturn(Ecosystem.Substrate)
                on { assets }.thenReturn(listOf(input, output, xor))
                on { minSupportedVersion }.thenReturn(null)
            }
            fun metaAccount(
                metaId: Long,
                accountId: ByteArray?,
                publicKey: ByteArray?
            ) = mock<MetaAccount> {
                on { id }.thenReturn(metaId)
                on { chainAccounts }.thenReturn(emptyMap())
                on { substrateAccountId }.thenReturn(accountId)
                on { substratePublicKey }.thenReturn(publicKey)
            }
            val accountId = if (hasAccount) ByteArray(32) { 7 } else null
            val metaAccount = metaAccount(7L, accountId, ByteArray(32) { 1 })
            val metaAccountAfterFee = when {
                walletChangesAfterFee -> metaAccount(8L, accountId, ByteArray(32) { 1 })
                accountIdChangesAfterFee -> metaAccount(7L, ByteArray(32) { 9 }, ByteArray(32) { 1 })
                accountIdentityChangesAfterFee -> metaAccount(7L, accountId, ByteArray(32) { 9 })
                else -> metaAccount
            }
            val initialRuntime = mock<RuntimeSnapshot>()
            val runtimeAfterFee = if (runtimeChangesAfterFee) mock<RuntimeSnapshot>() else initialRuntime
            val runtimeAtFinalBoundary = if (runtimeChangesDuringBalanceRefresh) {
                mock<RuntimeSnapshot>()
            } else {
                runtimeAfterFee
            }
            val initialNode = ChainNode(NODE_URL, "SORA", isActive = true, isDefault = true)
            val nodeAfterFee = if (nodeChangesAfterFee) {
                ChainNode(SECOND_NODE_URL, "SORA 2", isActive = true, isDefault = false)
            } else {
                initialNode
            }
            val nodeAtFinalBoundary = if (nodeChangesDuringBalanceRefresh) {
                ChainNode(SECOND_NODE_URL, "SORA 2", isActive = true, isDefault = false)
            } else {
                nodeAfterFee
            }
            val socketService = mock<SocketService>()
            val initialSocketState = MutableStateFlow<SocketStateMachine.State>(
                SocketStateMachine.State.Connecting(NODE_URL)
            )
            val socketStateAfterFee = if (socketStateChangesAfterFee) {
                MutableStateFlow<SocketStateMachine.State>(
                    SocketStateMachine.State.Connecting(SECOND_NODE_URL)
                )
            } else {
                initialSocketState
            }
            val initialConnection = mock<ChainConnection> {
                on { this.chain }.thenReturn(chain)
                on { this.socketService }.thenReturn(socketService)
                on { selectedNode }.thenReturn(initialNode, nodeAfterFee, nodeAtFinalBoundary)
                on { state }.thenReturn(
                    initialSocketState,
                    socketStateAfterFee,
                    socketStateAfterFee
                )
            }
            val connectionAfterFee = if (connectionChangesAfterFee) {
                mock<ChainConnection> {
                    on { this.chain }.thenReturn(chain)
                    on { this.socketService }.thenReturn(mock<SocketService>())
                    on { selectedNode }.thenReturn(nodeAfterFee)
                    on { state }.thenReturn(
                        MutableStateFlow<SocketStateMachine.State>(
                            SocketStateMachine.State.Connecting(SECOND_NODE_URL)
                        )
                    )
                }
            } else {
                initialConnection
            }
            val inputWalletAsset = mock<WalletAsset> {
                on { sendAvailableInPlanks }.thenReturn(inputBalance)
            }
            val xorWalletAsset = mock<WalletAsset> {
                on { sendAvailableInPlanks }.thenReturn(xorBalance)
            }

            whenever(toggleStore.polkaswapMutationsEnabled).thenReturn(toggleEnabled)
            whenever(
                preferences.getBoolean(PolkaswapInteractor.HAS_READ_DISCLAIMER_KEY, false)
            ).thenReturn(disclaimerAccepted)
            whenever(chainRegistry.getChain(soraMainChainId)).thenReturn(chain)
            if (runtimeReady) {
                whenever(chainRegistry.getRuntimeOrNull(soraMainChainId)).thenReturn(
                    initialRuntime,
                    runtimeAfterFee,
                    runtimeAtFinalBoundary
                )
            } else {
                whenever(chainRegistry.getRuntimeOrNull(soraMainChainId)).thenReturn(null)
            }
            whenever(chainRegistry.awaitConnection(soraMainChainId)).thenReturn(
                initialConnection,
                connectionAfterFee,
                connectionAfterFee
            )
            whenever(accountRepository.getSelectedMetaAccount()).thenReturn(
                metaAccount,
                metaAccountAfterFee,
                metaAccountAfterFee
            )
            whenever(accountRepository.isWalletRecoveryRequired(any())).thenReturn(recoveryRequired)
            whenever(accountRepository.getSubstrateSecrets(any())).thenReturn(
                if (hasSigningMaterial) mock<EncodableStruct<SubstrateSecrets>>() else null
            )
            whenever(
                walletRepository.getAsset(any(), any(), any(), anyOrNull())
            ).thenAnswer { invocation ->
                when (invocation.getArgument<Asset>(2).id) {
                    input.id -> inputWalletAsset
                    xor.id -> xorWalletAsset
                    else -> null
                }
            }
            whenever(
                extrinsicService.estimateFee(
                    chain = eq(chain),
                    accountId = any(),
                    useBatchAll = any(),
                    tip = anyOrNull(),
                    appId = anyOrNull(),
                    formExtrinsic = any()
                )
            ).thenReturn(BigInteger.TEN)

            return PolkaswapRepositoryImpl(
                remoteConfigFetcher = remoteConfigFetcher,
                remoteStorage = remoteStorage,
                extrinsicService = extrinsicService,
                chainRegistry = chainRegistry,
                accountRepository = accountRepository,
                featureToggleStore = toggleStore,
                preferences = preferences,
                walletRepository = walletRepository
            ).swap(
                chainId = requestChainId,
                dexId = 0,
                inputAssetId = inputCurrencyId,
                outputAssetId = outputCurrencyId,
                amount = amount,
                limit = limit,
                filter = "Disabled",
                markets = emptyList(),
                desired = WithDesired.INPUT
            )
        }

        private fun coreAsset(
            id: String,
            currencyId: String,
            symbol: String,
            isUtility: Boolean
        ) = mock<Asset> {
            on { this.id }.thenReturn(id)
            on { this.currencyId }.thenReturn(currencyId)
            on { this.symbol }.thenReturn(symbol)
            on { this.isUtility }.thenReturn(isUtility)
        }
    }

    private companion object {
        const val INPUT_CURRENCY_ID = "0x01"
        const val OUTPUT_CURRENCY_ID = "0x02"
        const val XOR_CURRENCY_ID = "0x03"
        const val NODE_URL = "wss://sora.example"
        const val SECOND_NODE_URL = "wss://sora-2.example"
    }
}
