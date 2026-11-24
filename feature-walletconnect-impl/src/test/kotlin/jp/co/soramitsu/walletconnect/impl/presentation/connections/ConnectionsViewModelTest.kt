package jp.co.soramitsu.walletconnect.impl.presentation.connections

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import co.jp.soramitsu.feature_walletconnect_impl.R
import co.jp.soramitsu.walletconnect.domain.WalletConnectInteractor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.wallet.impl.domain.QR_PREFIX_WALLET_CONNECT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionsViewModelTest {

    @get:Rule
    val instantTasks = InstantTaskExecutorRule()

    private val interactor = mockk<WalletConnectInteractor>(relaxed = true)
    private val resourceManager = mockk<ResourceManager> {
        every { getString(any()) } answers { "string:${firstArg<Int>()}" }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ConnectionsViewModel(mockk(relaxed = true), resourceManager, interactor)

    @Test
    fun `a QR code that is not a WalletConnect link shows the translated error and pairs nothing`() {
        val viewModel = viewModel()

        viewModel.qrCodeScanned("https://example.org")

        assertEquals("string:${R.string.walletconnect_qr_invalid}", viewModel.errorLiveData.value?.peekContent())
        verify(exactly = 0) { interactor.pair(any(), any(), any()) }
    }

    @Test
    fun `a WalletConnect link pairs and shows no error`() {
        val viewModel = viewModel()
        val uri = "$QR_PREFIX_WALLET_CONNECT:topic@2"

        viewModel.qrCodeScanned(uri)

        assertEquals(null, viewModel.errorLiveData.value)
        verify(exactly = 1) { interactor.pair(uri, any(), any()) }
    }
}
