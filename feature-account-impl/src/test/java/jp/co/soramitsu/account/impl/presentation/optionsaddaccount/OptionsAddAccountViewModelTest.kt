package jp.co.soramitsu.account.impl.presentation.optionsaddaccount

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.domain.model.ImportMode
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.common.model.WalletEcosystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OptionsAddAccountViewModelTest {
    @get:Rule val instantTaskRule = InstantTaskExecutorRule()
    private val dispatcher = StandardTestDispatcher()
    private val router = mockk<AccountRouter>(relaxed = true)
    private lateinit var viewModel: OptionsAddAccountViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        viewModel = OptionsAddAccountViewModel(
            SavedStateHandle(
                mapOf(
                    OptionsAddAccountFragment.KEY_WALLET_ID to 7L,
                    OptionsAddAccountFragment.KEY_TYPE to WalletEcosystem.Ethereum
                )
            ),
            mockk(relaxed = true),
            router
        )
    }

    @After
    fun teardown() {
        viewModel.coroutineContext.cancel()
        Dispatchers.resetMain()
    }

    // Google Drive and pre-installed imports restore whole wallets; this screen adds one account.
    @Test
    fun `importing an account asks only for the modes that add an account to this wallet`() = runTest(dispatcher) {
        every { router.openSelectAccountImportModeForResult() } returns flowOf(ImportMode.RawSeed)

        viewModel.importAccount()
        runCurrent()

        verify(exactly = 0) { router.openSelectImportModeForResult() }
        verify(exactly = 1) { router.openImportAddAccountScreen(7L, WalletEcosystem.Ethereum, ImportMode.RawSeed) }
    }
}
