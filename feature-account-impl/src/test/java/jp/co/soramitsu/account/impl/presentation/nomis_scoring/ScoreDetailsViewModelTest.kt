package jp.co.soramitsu.account.impl.presentation.nomis_scoring

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.interfaces.NomisScoreInteractor
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.NomisScoreData
import jp.co.soramitsu.account.impl.presentation.AccountRouter
import jp.co.soramitsu.account.impl.presentation.VmTestFixtures
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.feature_account_impl.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScoreDetailsViewModelTest {
    @get:Rule val instantTaskRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val router = mockk<AccountRouter>(relaxed = true)
    private val accountInteractor = mockk<AccountInteractor>()
    private val nomis = mockk<NomisScoreInteractor>()
    private val clipboard = mockk<ClipboardManager>(relaxed = true)
    private val resources = VmTestFixtures.resourceManager()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(scores: Flow<NomisScoreData?>, wallet: MetaAccount = VmTestFixtures.meta()): ScoreDetailsViewModel {
        every { nomis.observeAccountScore(7) } returns scores
        coEvery { accountInteractor.getMetaAccount(7) } returns wallet
        return ScoreDetailsViewModel(
            router, accountInteractor, nomis, clipboard, resources,
            SavedStateHandle(mapOf(ScoreDetailsFragment.META_ACCOUNT_ID_KEY to 7L))
        )
    }

    private fun score(score: Int, scoredAt: Long? = SCORED_AT) = NomisScoreData(
        metaId = 7, score = score, updated = 1, nativeBalanceUsd = BigDecimal("12.5"), holdTokensUsd = BigDecimal("1000"),
        walletAgeInMonths = 14, totalTransactions = 321, rejectedTransactions = 4, avgTransactionTimeInHours = 5.7,
        maxTransactionTimeInHours = 48.2, minTransactionTimeInHours = 0.4, scoredAt = scoredAt
    )

    @Test
    fun `a scored wallet shows every formatted metric and the wallet's ethereum address`() = runTest(dispatcher) {
        val vm = viewModel(flowOf(score(87)))
        runCurrent()

        val state = vm.state.value
        assertEquals("0x" + "02".repeat(20), state.address)
        val info = (state.info as ScoreDetailsViewState.Success).data
        assertEquals(87, info.score)
        assertEquals(SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(SCORED_AT)), info.updated)
        assertTrue(info.nativeBalanceUsd.startsWith("$"))
        assertTrue(info.holdTokensUsd.startsWith("$"))
        assertEquals("q${R.plurals.common_months_format}:14", info.walletAge)
        assertEquals("321", info.totalTransactions)
        assertEquals("4", info.rejectedTransactions)
        assertEquals("q${R.plurals.common_hours_format}:5", info.avgTransactionTime)
        assertEquals("q${R.plurals.common_hours_format}:48", info.maxTransactionTime)
        assertEquals("q${R.plurals.common_hours_format}:0", info.minTransactionTime)
    }

    @Test
    fun `a score without a scoring date shows zero as the update`() = runTest(dispatcher) {
        val vm = viewModel(flowOf(score(10, scoredAt = null)))
        runCurrent()

        assertEquals("0", (vm.state.value.info as ScoreDetailsViewState.Success).data.updated)
    }

    @Test
    fun `loading and error codes map to their states and a missing score keeps the last state`() = runTest(dispatcher) {
        assertEquals(ScoreDetailsViewState.Loading, viewModel(flowOf(score(NomisScoreData.LOADING_CODE))).also { runCurrent() }.state.value.info)
        assertEquals(ScoreDetailsViewState.Error, viewModel(flowOf(score(NomisScoreData.ERROR_CODE))).also { runCurrent() }.state.value.info)
        assertEquals(ScoreDetailsViewState.Loading, viewModel(flowOf(null)).also { runCurrent() }.state.value.info)
    }

    @Test
    fun `a failing score source shows the error state and its message`() = runTest(dispatcher) {
        val vm = viewModel(flow { throw IllegalStateException("nomis down") })
        val messages = mutableListOf<String>()
        vm.errorLiveData.observeForever { event -> event.getContentIfNotHandled()?.let(messages::add) }
        runCurrent()

        assertEquals(ScoreDetailsViewState.Error, vm.state.value.info)
        assertEquals(listOf("nomis down"), messages)
    }

    @Test
    fun `a wallet without an ethereum address falls back to its ethereum chain account key`() = runTest(dispatcher) {
        val ethChain = VmTestFixtures.chain(id = "eth", isEthereumBased = true)
        val key = ByteArray(20) { 9 }
        val wallet = VmTestFixtures.meta(
            ethereumAddress = null,
            chainAccounts = mapOf("eth" to MetaAccount.ChainAccount(7, ethChain, key, key, CryptoType.ECDSA, "eth"))
        )
        val vm = viewModel(flowOf(score(NomisScoreData.LOADING_CODE)), wallet)
        runCurrent()

        assertEquals("0x" + "09".repeat(20), vm.state.value.address)
    }

    @Test
    fun `a wallet with no ethereum key at all cannot be scored`() = runTest(dispatcher) {
        val vm = viewModel(flowOf(null), VmTestFixtures.meta(ethereumAddress = null))
        runCurrent()

        assertEquals("", vm.state.value.address)
        assertEquals(ScoreDetailsViewState.Error, vm.state.value.info)
    }

    @Test
    fun `copy puts the raw address on the clipboard and confirms, close goes back`() = runTest(dispatcher) {
        val vm = viewModel(flowOf(null))
        val messages = mutableListOf<String>()
        vm.messageLiveData.observeForever { event -> event.getContentIfNotHandled()?.let(messages::add) }
        runCurrent()

        vm.onCopyAddressClicked()
        vm.onCloseClicked()

        verify { clipboard.addToClipboard("0x" + "02".repeat(20), any()) }
        verify(exactly = 0) { clipboard.addSecretToClipboard(any(), any()) }
        assertEquals(listOf("s${R.string.common_copied}"), messages)
        verify { router.back() }
    }

    private companion object {
        const val SCORED_AT = 1_700_000_000_000L
    }
}
