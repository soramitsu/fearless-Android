package jp.co.soramitsu.staking.impl.domain

import android.graphics.drawable.PictureDrawable
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.data.network.runtime.binding.AccountData
import jp.co.soramitsu.common.data.network.runtime.binding.AccountInfo
import jp.co.soramitsu.common.utils.balances
import jp.co.soramitsu.common.utils.numberConstant
import jp.co.soramitsu.common.validation.ValidationStatus
import jp.co.soramitsu.core.extrinsic.mortality.IChainStateRepository
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.ExtrinsicMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.MetadataFunction
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAddress
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.staking.api.data.StakingSelectionRevision
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.domain.api.StakingRepository
import jp.co.soramitsu.staking.impl.StakingLaneFixtures
import jp.co.soramitsu.staking.impl.data.repository.AssetHubMetadataContractTest
import jp.co.soramitsu.staking.impl.data.repository.StakingRewardsRepository
import jp.co.soramitsu.staking.impl.domain.validations.setup.SetupStakingPayload
import jp.co.soramitsu.staking.impl.domain.validations.setup.SetupStakingValidationFailure
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.ControllerDeprecationWarning
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real staking interactor over mocked account, wallet, chain and staking repositories. */
class StakingInteractorCoverageTest {

    private val chainAsset = StakingLaneFixtures.chainAsset()
    private val chain = StakingLaneFixtures.chain(chainAsset)
    private val asset = StakingLaneFixtures.asset(chainAsset)
    private val accountId = StakingLaneFixtures.accountId
    private val address = chain.addressOf(accountId)

    private val accountRepository = mockk<AccountRepository>(relaxed = true)
    private val stakingRepository = mockk<StakingRepository>(relaxed = true)
    private val rewardsRepository = mockk<StakingRewardsRepository>(relaxed = true)
    private val sharedState = mockk<StakingSharedState>(relaxed = true)
    private val chainStateRepository = mockk<IChainStateRepository>(relaxed = true)
    private val chainRegistry = mockk<ChainRegistry>(relaxed = true)
    private val iconGenerator = mockk<AddressIconGenerator>(relaxed = true)
    private val walletRepository = mockk<WalletRepository>(relaxed = true)

    private val interactor = StakingInteractor(
        accountRepository, stakingRepository, rewardsRepository, sharedState, chainStateRepository, chainRegistry,
        iconGenerator, walletRepository
    )

    private fun wallet(substrate: ByteArray? = accountId) = MetaAccount(
        id = 1L,
        chainAccounts = emptyMap(),
        favoriteChains = emptyMap(),
        substratePublicKey = null,
        substrateCryptoType = null,
        substrateAccountId = substrate,
        ethereumAddress = null,
        ethereumPublicKey = null,
        tonPublicKey = null,
        isSelected = true,
        isBackedUp = true,
        googleBackupAddress = null,
        name = "Main",
        initialized = true
    )

    private fun stubSelection() {
        every { sharedState.assetWithChain } returns flowOf(SingleAssetSharedState.AssetWithChain(chain, chainAsset))
        every { accountRepository.selectedMetaAccountFlow() } returns flowOf(wallet())
        coEvery { accountRepository.getSelectedMetaAccount() } returns wallet()
        coEvery { sharedState.chain() } returns chain
    }

    private fun deprecatedControllerRuntime(): RuntimeSnapshot {
        val setController = MetadataFunction("set_controller", emptyList(), emptyList(), 0 to 0)
        val staking = Module("Staking", null, mapOf("set_controller" to setController), null, emptyMap(), emptyMap(), BigInteger.ZERO)
        val metadata = RuntimeMetadata(BigInteger.ONE, mapOf("Staking" to staking), ExtrinsicMetadata(BigInteger.valueOf(4), emptyList()))
        return RuntimeSnapshot(mockk(relaxed = true), metadata)
    }

    @Test
    fun `reward sync calls the repository and then the listener`() = runTest {
        val synced = mutableListOf<String>()
        interactor.syncStakingRewardListener = { synced += it }

        assertTrue(interactor.syncStakingRewards(chain.id, address).isSuccess)
        coVerify { rewardsRepository.sync(chain.id, address) }
        assertEquals(listOf(chain.id), synced)

        coEvery { rewardsRepository.sync(any(), any()) } throws IllegalStateException("offline")
        assertTrue(interactor.syncStakingRewards(chain.id, address).isFailure)
        assertEquals(1, synced.size)
    }

    @Test
    fun `selection flows pair the wallet with the staking chain`() = runTest {
        stubSelection()
        val revision = StakingSelectionRevision()
        every { sharedState.selectionRevision } returns revision
        every { sharedState.currentAssetFlow() } returns flowOf(asset)

        assertEquals(chain, interactor.selectedChainFlow().first())
        val (meta, selected) = interactor.selectionStateFlow().first()
        assertEquals(1L, meta.id)
        assertEquals(chain, selected.chain)
        assertSame(revision, interactor.selectionRevision)
        assertSame(asset, interactor.currentAssetFlow().first())
        assertTrue(interactor.validatorDetailsCache.value.isEmpty())
    }

    @Test
    fun `account projections use the chain address`() = runTest {
        stubSelection()
        coEvery { accountRepository.allMetaAccounts() } returns listOf(wallet(), wallet(substrate = null))

        val projections = interactor.getAccountProjectionsInSelectedChains()
        val selected = interactor.selectedAccountProjectionFlow().first()
        val current = interactor.getSelectedAccountProjection()!!

        assertEquals(listOf(address), projections.map { it.address })
        assertEquals(address, selected.address)
        assertEquals("Main", current.name)
    }

    @Test
    fun `wallet without a chain address has no projection`() = runTest {
        stubSelection()
        every { accountRepository.selectedMetaAccountFlow() } returns flowOf(wallet(substrate = null))
        coEvery { accountRepository.getSelectedMetaAccount() } returns wallet(substrate = null)

        assertTrue(interactor.selectedAccountProjectionFlow().toList().isEmpty())
        assertNull(interactor.getSelectedAccountProjection())
    }

    @Test
    fun `projected account carries the known wallet name`() = runTest {
        stubSelection()
        coEvery { accountRepository.findMetaAccount(any()) } returns wallet()
        assertEquals("Main", interactor.getProjectedAccount(address).name)

        coEvery { accountRepository.findMetaAccount(any()) } returns null
        val unknown = interactor.getProjectedAccount(address)
        assertNull(unknown.name)
        assertEquals(address, unknown.address)
    }

    @Test
    fun `chain reads go through the registry`() = runTest {
        stubSelection()
        val runtime = AssetHubMetadataContractTest.runtimes.getValue("polkadot")
        coEvery { chainRegistry.getChain(chain.id) } returns chain
        coEvery { chainRegistry.getRuntime(chain.id) } returns runtime
        coEvery { chainStateRepository.currentBlock(chain.id) } returns BigInteger.valueOf(123)
        coEvery { walletRepository.getAsset(1L, any(), chainAsset, null) } returns asset

        assertSame(chain, interactor.getChain(chain.id))
        assertSame(runtime.metadata, interactor.getChainMetadata(chain.id))
        assertEquals(BigInteger.valueOf(123), interactor.currentBlockNumber())
        assertSame(asset, interactor.getUtilityAsset(chain.id))
        assertEquals(runtime.metadata.balances().numberConstant("ExistentialDeposit", runtime), interactor.existentialDeposit(chain.id))
        assertEquals(1L, interactor.getCurrentMetaAccount().id)
    }

    @Test
    fun `fee validation leaves room for the bond and the fee`() = runTest {
        every { sharedState.currentAssetFlow() } returns flowOf(asset)
        val validation = interactor.feeValidation()

        fun payload(bond: BigDecimal?, fee: BigDecimal) = SetupStakingPayload(bond, fee, asset, address, false)

        assertTrue(validation.validate(payload(BigDecimal.ONE, BigDecimal("0.1"))) is ValidationStatus.Valid)
        val tooMuch = validation.validate(payload(BigDecimal(200), BigDecimal("0.1"))) as ValidationStatus.NotValid
        val feeOnly = validation.validate(payload(null, BigDecimal(1_000))) as ValidationStatus.NotValid
        assertSame(SetupStakingValidationFailure.CannotPayFee, tooMuch.reason)
        assertSame(SetupStakingValidationFailure.CannotPayFee, feeOnly.reason)
    }

    @Test
    fun `address models follow the chain kind`() = runTest {
        coEvery { iconGenerator.createAddressIcon(any<ByteArray>(), any<Int>(), any<Int>()) } returns mockk<PictureDrawable>(relaxed = true)
        coEvery { iconGenerator.createEthereumAddressIcon(any<ByteArray>(), any<Int>(), any<Int>()) } returns mockk<PictureDrawable>(relaxed = true)
        every { sharedState.assetWithChain } returns flowOf(SingleAssetSharedState.AssetWithChain(chain, chainAsset))
        assertEquals("Alice", interactor.getAddressModel(address, 24, "Alice").name)

        val evm = StakingLaneFixtures.chain(ethereum = true)
        every { sharedState.assetWithChain } returns flowOf(SingleAssetSharedState.AssetWithChain(evm, chainAsset))
        assertEquals("0xabc", interactor.getAddressModel("0xabc", 24).address)
        coVerify { iconGenerator.createEthereumAddressIcon(any<ByteArray>(), 24, any<Int>()) }
    }

    @Test
    fun `wallet address model uses the polkadot address`() = runTest {
        coEvery { iconGenerator.createAddressIcon(any<ByteArray>(), any<Int>(), any<Int>()) } returns mockk<PictureDrawable>(relaxed = true)
        val polkadot = StakingLaneFixtures.chain()
        coEvery { chainRegistry.getChain(polkadotChainId) } returns polkadot
        coEvery { accountRepository.getSelectedMetaAccount() } returns wallet()

        val model = interactor.getWalletAddressModel(24)
        assertEquals(polkadot.addressOf(accountId), model.address)
        assertEquals("Main", model.name)

        coEvery { accountRepository.getSelectedMetaAccount() } returns wallet(substrate = null)
        assertTrue(runCatching { interactor.getWalletAddressModel(24) }.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `stash balance prefers the cached wallet asset`() = runTest {
        val stash = ByteArray(32) { 6 }
        coEvery { accountRepository.findMetaAccount(stash) } returns wallet(stash)
        coEvery { walletRepository.getAsset(1L, stash, chainAsset, null) } returns asset
        assertEquals(asset.availableForStaking, interactor.getStashBalance(stash, chainAsset))

        val info = AccountInfo(BigInteger.ZERO, AccountData(StakingLaneFixtures.planks(30), BigInteger.ZERO, BigInteger.ZERO, StakingLaneFixtures.planks(5)))
        coEvery { stakingRepository.getAccountInfo(chain.id, stash) } returns info
        coEvery { accountRepository.findMetaAccount(stash) } returns null
        assertEquals(0, interactor.getStashBalance(stash, chainAsset).compareTo(BigDecimal(25)))
        assertSame(info, interactor.getAccountBalance(chain.id, stash))
    }

    @Test
    fun `controller deprecation is reported only for runtimes without a controller`() = runTest {
        coEvery { chainRegistry.getRuntimeOrNull(chain.id) } returns null
        assertNull(interactor.checkControllerDeprecations(wallet(), chain))

        coEvery { chainRegistry.getRuntimeOrNull(chain.id) } returns deprecatedControllerRuntime()
        assertNull(interactor.checkControllerDeprecations(wallet(substrate = null), chain))

        coEvery { walletRepository.getControllerAccount(chain.id, accountId) } returns accountId
        assertNull(interactor.checkControllerDeprecations(wallet(), chain))
    }

    @Test
    fun `separate controller must be changed`() = runTest {
        coEvery { chainRegistry.getRuntimeOrNull(chain.id) } returns deprecatedControllerRuntime()
        coEvery { walletRepository.getControllerAccount(chain.id, accountId) } returns ByteArray(32) { 3 }

        val warning = interactor.checkControllerDeprecations(wallet(), chain) as ControllerDeprecationWarning.ChangeController

        assertEquals(chain.id, warning.chainId)
        assertEquals(chain.name, warning.chainName)
    }

    @Test
    fun `controller account is asked to import its stash`() = runTest {
        val stash = ByteArray(32) { 4 }
        coEvery { chainRegistry.getRuntimeOrNull(chain.id) } returns deprecatedControllerRuntime()
        coEvery { walletRepository.getControllerAccount(chain.id, accountId) } returns null
        coEvery { walletRepository.getStashAccount(chain.id, accountId) } returns stash

        val warning = interactor.checkControllerDeprecations(wallet(), chain) as ControllerDeprecationWarning.ImportStash
        assertEquals(stash.toAddress(chain.addressPrefix.toShort()), warning.stashAddress)

        coEvery { walletRepository.getStashAccount(chain.id, accountId) } returns null
        assertNull(interactor.checkControllerDeprecations(wallet(), chain))
    }
}
