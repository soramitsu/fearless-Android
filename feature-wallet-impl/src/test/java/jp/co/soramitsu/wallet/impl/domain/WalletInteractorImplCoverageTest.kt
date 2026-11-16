package jp.co.soramitsu.wallet.impl.domain

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.presentation.exporting.ExportSource
import jp.co.soramitsu.common.compose.component.ChainSelectorViewStateWithFilters
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.storage.InitialValueProducer
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.core.models.Asset.StakingType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.fearless_utils.encrypt.keypair.BaseKeypair
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.MetadataFunction
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProvider
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.domain.interfaces.AssetSorting
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.ControllerDeprecationWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.CoroutineContext

/**
 * Controller deprecation warnings, chain sync with its runtime timeout, export sources from stored secrets, the
 * initial values of the filter and sorting preferences, and the failures of accounts without an address.
 */
class WalletInteractorImplCoverageTest {

    private val dot = WalletTestModels.asset(chainId = POLKADOT_ID, currencyId = "0x0200")
    private val polkadot = WalletTestModels.chain(id = POLKADOT_ID, assets = listOf(dot), name = "Polkadot")
    private val ethereum = WalletTestModels.chain(id = ETHEREUM_ID, ecosystem = Ecosystem.Ethereum)
    private val meta = WalletTestModels.metaAccount()
    private val accountId = WalletTestModels.substrateAccountId

    private val walletRepository = mockk<WalletRepository>()
    private val accounts = mockk<AccountRepository> {
        coEvery { getSelectedMetaAccount() } returns meta
        coEvery { getSelectedLightMetaAccount() } returns meta
        every { selectedMetaAccountFlow() } returns flowOf(meta)
    }
    private val chainRegistry = mockk<ChainRegistry> {
        coEvery { getChain(POLKADOT_ID) } returns polkadot
    }
    private val preferences = mockk<Preferences> {
        // Answers every flow with its initial value, as an empty preference store does.
        every { stringFlow(any(), any()) } answers {
            val producer = secondArg<InitialValueProducer<String>?>()
            flow { emit(producer?.invoke()) }
        }
    }
    private val fiat = mockk<SelectedFiat> { coEvery { get() } returns "usd" }
    private val chains = mockk<ChainsRepository> {
        coEvery { getChain(POLKADOT_ID) } returns polkadot
        coEvery { getChain(ETHEREUM_ID) } returns ethereum
    }

    @Test
    fun `asset rates are synced for the selected fiat`() = runBlocking {
        coEvery { walletRepository.syncAssetsRates("usd") } just Runs

        val result = interactor().syncAssetsRates()

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { walletRepository.syncAssetsRates("usd") }
    }

    @Test
    fun `a Sora QR string cannot be built without an address`() {
        coEvery { accounts.getSelectedMetaAccount() } returns WalletTestModels.metaAccount(substrate = null, ethereum = null)

        val failure = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { interactor().getQrCodeSharingSoraString(POLKADOT_ID, "dot", BigDecimal.ONE) }
        }

        assertEquals("There is no address found to getQrCodeSharingSoraString", failure.message)
    }

    @Test
    fun `rewards cannot be claimed without an account on the chain`() {
        coEvery { accounts.getSelectedMetaAccount() } returns WalletTestModels.metaAccount(substrate = null, ethereum = null)

        val failure = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { interactor().claimRewards(POLKADOT_ID) }
        }

        assertEquals("Error retrieving accountId for chain Polkadot", failure.message)
    }

    @Test
    fun `controller deprecations name the chains whose staking account needs action`() = runBlocking {
        val stash = ByteArray(32) { 9 }
        val relayChains = listOf("resolved", "change", "stash", "none", "no-runtime").map(::relayChain)
        val normal = WalletTestModels.chain(id = "normal")
        coEvery { chains.getChainsById() } returns (relayChains + normal).associateBy(Chain::id)
        coEvery { chainRegistry.getRuntimeOrNull(any()) } returns runtimeWithoutControllerArguments()
        coEvery { chainRegistry.getRuntimeOrNull("no-runtime") } returns null
        coEvery { walletRepository.getControllerAccount("resolved", any()) } returns accountId
        coEvery { walletRepository.getControllerAccount("change", any()) } returns ByteArray(32) { 5 }
        coEvery { walletRepository.getControllerAccount("stash", any()) } returns null
        coEvery { walletRepository.getControllerAccount("none", any()) } returns null
        coEvery { walletRepository.getStashAccount("stash", any()) } returns stash
        coEvery { walletRepository.getStashAccount("none", any()) } returns null

        val warnings = interactor().checkControllerDeprecations()

        assertEquals(listOf("change", "stash"), warnings.map { it.chainId })
        val change = warnings[0] as ControllerDeprecationWarning.ChangeController
        val importStash = warnings[1] as ControllerDeprecationWarning.ImportStash
        assertEquals("Change", change.chainName)
        assertEquals(stash.toAddress(0.toShort()), importStash.stashAddress)
        coVerify(exactly = 0) { walletRepository.getControllerAccount("no-runtime", any()) }
        coVerify(exactly = 0) { walletRepository.getStashAccount("change", any()) }
    }

    @Test
    fun `a chain sync succeeds once the runtime is ready`() = runTest {
        val provider = mockk<RuntimeProvider> { coEvery { get() } returns mockk() }
        coEvery { chainRegistry.setupChain(polkadot) } just Runs
        coEvery { chainRegistry.awaitRuntimeProvider(POLKADOT_ID) } returns provider

        val result = interactor(StandardTestDispatcher(testScheduler)).retryChainSync(POLKADOT_ID)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { chainRegistry.setupChain(polkadot) }
    }

    @Test
    fun `a chain sync fails when the runtime does not arrive within its timeout`() = runTest {
        val provider = mockk<RuntimeProvider> { coEvery { get() } coAnswers { awaitCancellation() } }
        coEvery { chainRegistry.setupChain(polkadot) } just Runs
        coEvery { chainRegistry.awaitRuntimeProvider(POLKADOT_ID) } returns provider

        val result = interactor(StandardTestDispatcher(testScheduler)).retryChainSync(POLKADOT_ID)

        assertEquals("Failed to sync chain", result.exceptionOrNull()?.message)
    }

    @Test
    fun `Substrate export sources follow the stored entropy or seed`() = runBlocking {
        coEvery { accounts.getSubstrateSecrets(1L) } returns
            SubstrateSecrets(substrateKeyPair = KEYPAIR, entropy = ByteArray(16) { 1 })
        coEvery { accounts.getSubstrateSecrets(2L) } returns SubstrateSecrets(substrateKeyPair = KEYPAIR, seed = ByteArray(32) { 2 })

        // Without a wallet id the selected wallet (1) is used.
        val fromEntropy = interactor().getExportSourceTypes(POLKADOT_ID, null)
        val fromSeed = interactor().getExportSourceTypes(POLKADOT_ID, 2L)

        assertEquals(setOf(ExportSource.Mnemonic, ExportSource.Seed, ExportSource.Json), fromEntropy.toSet())
        assertEquals(setOf(ExportSource.Seed, ExportSource.Json), fromSeed.toSet())
    }

    @Test
    fun `Ethereum export sources follow the stored entropy or seed`() = runBlocking {
        coEvery { accounts.getEthereumSecrets(1L) } returns
            EthereumSecrets(entropy = ByteArray(16) { 1 }, ethereumKeypair = KEYPAIR)
        coEvery { accounts.getEthereumSecrets(2L) } returns EthereumSecrets(seed = ByteArray(32) { 2 }, ethereumKeypair = KEYPAIR)

        val fromEntropy = interactor().getExportSourceTypes(ETHEREUM_ID, 1L)
        val fromSeed = interactor().getExportSourceTypes(ETHEREUM_ID, 2L)

        assertEquals(setOf(ExportSource.Mnemonic, ExportSource.Seed, ExportSource.Json), fromEntropy.toSet())
        assertEquals(setOf(ExportSource.Seed, ExportSource.Json), fromSeed.toSet())
    }

    @Test
    fun `an unset chain filter reads as All and an unset sorting as fiat balance`() = runBlocking {
        val interactor = interactor()

        assertEquals(ChainSelectorViewStateWithFilters.Filter.All, interactor.observeSelectedAccountChainSelectFilter().first())
        assertEquals(AssetSorting.FiatBalance, interactor.observeAssetSorting().first())
    }

    private fun interactor(context: CoroutineContext = Dispatchers.Unconfined) = WalletInteractorImpl(
        walletRepository = walletRepository,
        addressBookRepository = mockk(),
        accountRepository = accounts,
        historyRepository = mockk(),
        chainRegistry = chainRegistry,
        fileProvider = mockk(),
        preferences = preferences,
        selectedFiat = fiat,
        xcmEntitiesFetcher = mockk(),
        chainsRepository = chains,
        networkStateService = mockk(),
        tokenRepository = mockk(),
        coroutineContext = context
    )

    private fun relayChain(id: String) = WalletTestModels.chain(
        id = id,
        assets = listOf(WalletTestModels.asset(id = "$id-token", chainId = id, staking = StakingType.RELAYCHAIN))
    )

    /** Staking whose set_controller takes no arguments: the controller account is deprecated on this runtime. */
    private fun runtimeWithoutControllerArguments(): RuntimeSnapshot {
        val setController = MetadataFunction("set_controller", emptyList(), emptyList(), 0 to 0)
        val staking = Module(
            name = "Staking",
            storage = null,
            calls = mapOf("set_controller" to setController),
            events = null,
            constants = emptyMap(),
            errors = emptyMap(),
            index = BigInteger.ZERO
        )
        return RuntimeSnapshot(mockk(), RuntimeMetadata(BigInteger.ONE, mapOf("Staking" to staking), mockk()))
    }

    private companion object {
        const val POLKADOT_ID = "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
        const val ETHEREUM_ID = "1"
        val KEYPAIR = BaseKeypair(privateKey = ByteArray(32) { 3 }, publicKey = ByteArray(32) { 4 })
    }
}
