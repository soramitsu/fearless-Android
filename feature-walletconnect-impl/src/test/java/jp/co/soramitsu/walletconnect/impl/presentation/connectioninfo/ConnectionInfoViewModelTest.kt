package jp.co.soramitsu.walletconnect.impl.presentation.connectioninfo

import android.graphics.drawable.Drawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import co.jp.soramitsu.feature_walletconnect_impl.R
import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import co.jp.soramitsu.walletconnect.domain.WalletConnectRouter
import co.jp.soramitsu.walletconnect.model.WalletConnectMetadata
import co.jp.soramitsu.walletconnect.model.WalletConnectSession
import co.jp.soramitsu.walletconnect.model.WalletConnectSessionNamespace
import co.jp.soramitsu.walletconnect.model.WalletConnectValidation
import co.jp.soramitsu.walletconnect.model.WalletConnectVerifyContext
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.impl.presentation.account.mixin.api.AccountListingMixin
import jp.co.soramitsu.account.impl.presentation.account.model.LightMetaAccountUi
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.IgnoredOnEquals
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionInfoViewModelTest {
    @get:Rule val executor = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val address = "0x" + "01".repeat(20)
    private val chain = mockk<Chain> {
        every { id } returns "1"
        every { name } returns "Ethereum"
        every { isEthereumChain } returns true
        every { ecosystem } returns Ecosystem.Ethereum
        every { externalApi } returns null
    }
    private val session = WalletConnectSession(
        "topic", "pair", 1000,
        mapOf("eip155" to WalletConnectSessionNamespace(listOf("eip155:1"), listOf("eip155:1:$address"), listOf("personal_sign"), listOf("accountsChanged"))),
        emptyMap(), emptyMap(), WalletConnectMetadata("Dapp", "", "https://claimed.example", listOf("icon")), true,
        WalletConnectVerifyContext(WalletConnectValidation.INVALID, "https://verified.example", "https://claimed.example", false)
    )
    private val interactor = mockk<WalletConnectInteractor>(relaxed = true) {
        every { getActiveSessionByTopic("topic") } returns session
        coEvery { getChains() } returns listOf(chain)
    }
    private val router = mockk<WalletConnectRouter>(relaxed = true)
    private val resources = mockk<ResourceManager> {
        every { getString(any()) } returns "Label"
        every { getString(R.string.common_dapp) } returns "dApp"
        every { getString(R.string.all_done) } returns "Done"
        every {
            getString(R.string.connection_disconnect_success_message, any())
        } answers { "Disconnected " + secondArg<Array<Any>>().single() }
        every { formatDate(1_000_000) } returns "Tomorrow"
    }
    private val icon = mockk<Drawable>()
    private val listing = mockk<AccountListingMixin> {
        every { accountsFlow(any()) } returns flowOf(
            listOf(
                LightMetaAccountUi(7, "Signing wallet", true, IgnoredOnEquals(icon)),
                LightMetaAccountUi(8, "Other wallet", false, IgnoredOnEquals(icon))
            )
        )
    }
    private val accounts = mockk<AccountRepository> {
        every { allMetaAccountsFlow() } returns MutableStateFlow(listOf(wallet(7, 1), wallet(8, 2)))
    }
    private val models = mutableListOf<ConnectionInfoViewModel>()

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
    fun stateUsesVerifiedHostPermissionsExpiryAndOnlyMatchingWallet() = runTest(dispatcher) {
        val model = model()
        awaitState(model)
        val state = model.state.value
        assertEquals("Dapp", state.session.title)
        assertEquals("verified.example", state.session.subtitle)
        assertEquals("icon", state.session.imageUrl)
        assertEquals("Ethereum", state.permissions?.title)
        assertEquals(listOf("personal_sign", "accountsChanged"), state.permissions?.infoItems?.map { it.subtitle })
        assertEquals(listOf(7L), state.wallets.map { it.id })
        assertEquals("Signing wallet", state.wallets.single().title)
        assertEquals("Tomorrow", state.expireDate)
    }

    @Test
    fun missingSessionNavigatesBackAndCannotDisconnect() = runTest(dispatcher) {
        every { interactor.getActiveSessionByTopic("topic") } returns null
        val model = model()
        model.onDisconnectClick()
        runCurrent()
        assertEquals(ConnectInfoViewState.default, model.state.value)
        verify(exactly = 2) { router.back() }
        verify(exactly = 0) { interactor.disconnectSession(any(), any(), any()) }
    }

    @Test
    fun successfulDisconnectShowsDappNameAndCloseNavigatesBack() = runTest(dispatcher) {
        every { interactor.disconnectSession("topic", any(), any()) } answers { secondArg<() -> Unit>()() }
        val model = model()
        model.onDisconnectClick()
        model.onClose()
        runCurrent()
        verify { router.openOperationSuccessAndPopUpToNearestRelatedScreen(null, null, "Disconnected Dapp", "Done") }
        verify { router.back() }
    }

    @Test
    fun disconnectFailureShowsErrorWithoutSuccessNavigation() = runTest(dispatcher) {
        every { interactor.disconnectSession("topic", any(), any()) } answers {
            val onError = thirdArg<(Throwable) -> Unit>()
            onError(IllegalStateException("offline"))
        }
        val model = model()
        model.onDisconnectClick()
        runCurrent()
        assertEquals("dApp", model.errorLiveData.value?.peekContent())
        verify(exactly = 0) { router.openOperationSuccessAndPopUpToNearestRelatedScreen(any(), any(), any(), any()) }
    }

    @Test
    fun absentMetadataUsesGenericNameForDisconnectSuccess() = runTest(dispatcher) {
        every { interactor.getActiveSessionByTopic("topic") } returns session.copy(metaData = null, verifyContext = null)
        every { interactor.disconnectSession("topic", any(), any()) } answers { secondArg<() -> Unit>()() }
        val model = model()
        awaitState(model)
        assertNull(model.state.value.session.title)
        assertNull(model.state.value.session.subtitle)
        model.onDisconnectClick()
        runCurrent()
        verify { router.openOperationSuccessAndPopUpToNearestRelatedScreen(null, null, "Disconnected dApp", "Done") }
    }

    private suspend fun awaitState(model: ConnectionInfoViewModel) = withContext(Dispatchers.Default) {
        withTimeout(10_000) {
            while (model.state.value.expireDate != "Tomorrow") delay(10)
        }
    }

    private fun model() = ConnectionInfoViewModel(
        listing, SavedStateHandle(mapOf(ConnectionInfoFragment.CONNECTION_TOPIC_KEY to "topic")), interactor, router, resources, accounts
    ).also { models += it }

    private fun wallet(id: Long, byte: Byte): MetaAccount = mockk {
        every { this@mockk.id } returns id
        every { chainAccounts } returns emptyMap()
        every { ethereumAddress } returns ByteArray(20) { byte }
    }
}
