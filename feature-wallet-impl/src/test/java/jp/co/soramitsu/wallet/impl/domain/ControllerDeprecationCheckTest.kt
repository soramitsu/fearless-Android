package jp.co.soramitsu.wallet.impl.domain

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.MetadataFunction
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import jp.co.soramitsu.wallet.impl.domain.model.ControllerDeprecationWarning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The home page asks every staking network whether the selected wallet still relies on a separate controller. One
 * network that cannot answer used to close the app from the home page (229: the Staking pallet left the relay chains).
 */
class ControllerDeprecationCheckTest {
    private val stash = byteArrayOf(1)
    private val otherController = byteArrayOf(2)
    private val accounts = mockk<AccountRepository>()
    private val repository = mockk<WalletRepository>()
    private val chainsRepository = mockk<ChainsRepository>()
    private val registry = mockk<ChainRegistry>()
    private val wallet = mockk<MetaAccount>()

    private val interactor = WalletInteractorImpl(
        walletRepository = repository, addressBookRepository = mockk(), accountRepository = accounts,
        historyRepository = mockk(), chainRegistry = registry, fileProvider = mockk(), preferences = mockk(),
        selectedFiat = mockk(), xcmEntitiesFetcher = mockk(), chainsRepository = chainsRepository,
        networkStateService = mockk(), tokenRepository = mockk(), coroutineContext = Dispatchers.Unconfined
    )

    @Before fun setup() {
        mockkStatic("jp.co.soramitsu.account.api.domain.model.MetaAccountKt")
        coEvery { accounts.getSelectedMetaAccount() } returns wallet
        every { wallet.accountId(any()) } returns stash
        coEvery { chainsRepository.getChainsById() } returns mapOf("failing" to chain("failing"), "working" to chain("working"))
        coEvery { registry.getRuntimeOrNull(any()) } returns runtimeWithoutControllerArgument()
    }

    @After fun teardown() {
        unmockkStatic("jp.co.soramitsu.account.api.domain.model.MetaAccountKt")
    }

    @Test fun `a network that cannot answer is skipped and the others still warn`() = runBlocking {
        coEvery { repository.getControllerAccount("failing", any()) } throws NoSuchElementException()
        coEvery { repository.getControllerAccount("working", any()) } returns otherController

        val warnings = interactor.checkControllerDeprecations()

        assertEquals(listOf("working"), warnings.map { (it as ControllerDeprecationWarning.ChangeController).chainId })
    }

    @Test fun `every network failing gives no warning, not a crash`() = runBlocking {
        coEvery { repository.getControllerAccount(any(), any()) } throws IllegalStateException("node error")

        assertTrue(interactor.checkControllerDeprecations().isEmpty())
    }

    @Test(expected = CancellationException::class)
    fun `cancellation still cancels`(): Unit = runBlocking {
        coEvery { repository.getControllerAccount(any(), any()) } throws CancellationException("page closed")

        interactor.checkControllerDeprecations()
    }

    private fun chain(id: String): Chain {
        val asset = mockk<Asset>()
        every { asset.staking } returns Asset.StakingType.RELAYCHAIN
        every { asset.chainId } returns id
        return mockk {
            every { this@mockk.id } returns id
            every { name } returns id
            every { assets } returns listOf(asset)
        }
    }

    private fun runtimeWithoutControllerArgument(): RuntimeSnapshot {
        val setController = mockk<MetadataFunction>()
        every { setController.arguments } returns emptyList()
        val staking = mockk<Module>()
        every { staking.calls } returns mapOf("set_controller" to setController)
        return mockk {
            every { metadata.modules } returns mapOf("Staking" to staking)
        }
    }
}
