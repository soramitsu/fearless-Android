package jp.co.soramitsu.tonconnect.impl.presentation.connectioninfo

import android.graphics.drawable.Drawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.impl.presentation.account.mixin.api.AccountListingMixin
import jp.co.soramitsu.account.impl.presentation.account.model.LightMetaAccountUi
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.IgnoredOnEquals
import jp.co.soramitsu.coredb.model.ConnectionSource
import jp.co.soramitsu.feature_tonconnect_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.tonconnect.api.domain.TonConnectInteractor
import jp.co.soramitsu.tonconnect.api.domain.TonConnectRouter
import jp.co.soramitsu.tonconnect.api.model.DappModel
import jp.co.soramitsu.tonconnect.api.model.TonConnectionIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TonConnectionInfoViewModelTest {
    @get:Rule val executor = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val walletIcon = mockk<Drawable>()
    private val chain = mockk<Chain> {
        every { name } returns "TON"
        every { icon } returns "https://ton.example/icon.svg"
    }
    private val interactor = mockk<TonConnectInteractor>(relaxed = true) {
        coEvery { getChain() } returns chain
    }
    private val router = mockk<TonConnectRouter>(relaxed = true)
    private val listing = mockk<AccountListingMixin> {
        coEvery { getAccount(7, AddressIconGenerator.SIZE_BIG) } returns
            LightMetaAccountUi(7, "TON wallet", false, IgnoredOnEquals(walletIcon))
    }
    private val resources = mockk<ResourceManager> {
        every { getString(R.string.connection_required_networks) } returns "Required networks"
        every { getString(R.string.common_dapp) } returns "dApp"
        every { getString(R.string.all_done) } returns "All done"
        every {
            getString(R.string.connection_disconnect_success_message, any())
        } answers { "Disconnected " + secondArg<Array<Any>>().single() }
    }
    private val dapp = DappModel(
        identifier = "client",
        chains = listOf("ton"),
        name = "Ston",
        url = "https://ston.example",
        description = null,
        background = null,
        icon = "https://ston.example/icon.png",
        metaId = 7,
        connectionSource = ConnectionSource.WEB
    )
    private val models = mutableListOf<TonConnectionInfoViewModel>()

    @Before
    fun installMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun clearMain() {
        runTest(dispatcher) {
            models.forEach { it.cancel() }
            models.forEach { it.coroutineContext[Job]?.join() }
        }
        Dispatchers.resetMain()
    }

    @Test
    fun stateShowsTheConnectedWalletAndRequiredNetwork() = runTest(dispatcher) {
        val model = model(dapp)
        awaitState(model)

        val state = model.state.value
        assertSame(dapp, state.appInfo)
        assertEquals("Required networks", state.requiredNetworksSelectorState?.title)
        assertEquals("TON", state.requiredNetworksSelectorState?.subTitle)
        assertEquals("https://ton.example/icon.svg", state.requiredNetworksSelectorState?.iconUrl)
        assertEquals(7L, state.wallet?.id)
        assertEquals("TON wallet", state.wallet?.title)
        assertTrue(state.wallet!!.isSelected)
        assertSame(walletIcon, state.wallet!!.walletIcon)
    }

    @Test
    fun disconnectRemovesTheExactConnectionAndReportsTheDappName() = runTest(dispatcher) {
        val model = model(dapp)
        model.onDisconnectClick()
        runCurrent()

        coVerify { interactor.disconnect(TonConnectionIdentity(7, "https://ston.example", ConnectionSource.WEB)) }
        verify { router.back() }
        verify { router.openOperationSuccess(null, null, "Disconnected Ston", "All done") }
    }

    @Test
    fun unnamedDappUsesTheGenericName() = runTest(dispatcher) {
        val model = model(dapp.copy(name = null))
        model.onDisconnectClick()
        runCurrent()

        verify { router.openOperationSuccess(null, null, "Disconnected dApp", "All done") }
    }

    @Test
    fun connectionWithoutAnIdentityIsNotDisconnected() = runTest(dispatcher) {
        val model = model(dapp.copy(connectionSource = null))
        model.onDisconnectClick()
        runCurrent()

        coVerify(exactly = 0) { interactor.disconnect(any()) }
        verify(exactly = 0) { router.openOperationSuccess(any(), any(), any(), any()) }
    }

    @Test
    fun closeNavigatesBack() = runTest(dispatcher) {
        model(dapp).onClose()

        verify(exactly = 1) { router.back() }
    }

    @Test
    fun missingArgumentsAreRejected() {
        val noDapp = runCatching { model(null) }.exceptionOrNull()
        assertEquals("No connection info provided", noDapp?.message)

        val noWallet = runCatching { model(dapp.copy(metaId = null)) }.exceptionOrNull()
        assertEquals("Wallet for the connection not specified", noWallet?.message)
    }

    private suspend fun awaitState(model: TonConnectionInfoViewModel) = withContext(Dispatchers.Default) {
        withTimeout(10_000) {
            while (model.state.value.wallet == null) delay(10)
        }
    }

    private fun model(info: DappModel?) = TonConnectionInfoViewModel(
        listing,
        interactor,
        router,
        resources,
        SavedStateHandle(mapOf(TonConnectionInfoFragment.TON_CONNECTION_INFO_KEY to info))
    ).also { models += it }
}
