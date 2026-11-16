package jp.co.soramitsu.wallet.impl.presentation.balance.detail.legacy

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.crowdloan.api.domain.LegacyCrowdloanEvidence
import jp.co.soramitsu.crowdloan.api.domain.LegacyCrowdloanRecovery
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.awaitFirst
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.detail.frozen.FrozenAssetPayload
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LegacyCrowdloanViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val dot = WalletTestModels.asset(chainId = polkadotChainId)
    private val chain = WalletTestModels.chain(id = polkadotChainId, name = "Polkadot", assets = listOf(dot))
    private val asset: Asset = mockk<Asset>(relaxed = true) {
        every { token.configuration } returns dot
        every { locked } returns BigDecimal.ONE
        every { reserved } returns BigDecimal("2")
        every { redeemable } returns BigDecimal.ZERO
    }
    private val interactor: WalletInteractor = mockk<WalletInteractor>(relaxed = true) {
        coEvery { getChain(polkadotChainId) } returns chain
        coEvery { getCurrentAsset(polkadotChainId, "dot") } returns asset
    }
    private val router = mockk<WalletRouter>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true) {
        every { getString(any()) } answers { "s${firstArg<Int>()}" }
        every { getString(R.string.legacy_crowdloan_description, "Polkadot") } returns "Locked on Polkadot"
    }

    @Test
    fun evidenceListsContributionsLocksAndClaims() {
        val recovery = recovery(LegacyCrowdloanEvidence(2, BigInteger.TEN.pow(10), 3, 1))
        val viewModel = LegacyCrowdloanViewModel(handle(), interactor, recovery, router, resources)

        val state = (viewModel.state.awaitFirst { it is LoadingState.Loaded } as LoadingState.Loaded).data
        assertTrue(state.hasEvidence)
        assertEquals("Locked on Polkadot", state.description)
        assertEquals(listOf("3", "1"), state.evidenceRows.drop(1).map { it.value })
        assertTrue(state.evidenceRows.first().value!!.startsWith("2 · "))
        assertTrue(state.canOpenLockedBalance)
        assertEquals("Polkadot", viewModel.toolbarState.value.selectorViewState.selectedChainName)

        val payload = slot<FrozenAssetPayload>()
        every { router.openFrozenTokens(capture(payload)) } returns Unit
        viewModel.openLockedBalance()
        viewModel.backClicked()

        assertEquals(BigDecimal.ONE, payload.captured.locked)
        assertEquals("DOT", payload.captured.assetSymbol)
        verify { router.back() }
    }

    @Test
    fun claimsOnlyEvidenceShowsZeroContributions() {
        val viewModel = LegacyCrowdloanViewModel(handle(), interactor, recovery(LegacyCrowdloanEvidence(historicalClaimCount = 1)), router, resources)

        val state = (viewModel.state.awaitFirst { it is LoadingState.Loaded } as LoadingState.Loaded).data
        assertEquals("0", state.evidenceRows.first().value)
    }

    @Test
    fun noEvidenceOrAFailureExplainsThereIsNothing() {
        val none = LegacyCrowdloanViewModel(handle(), interactor, recovery(LegacyCrowdloanEvidence.None), router, resources)
        val failing = mockk<LegacyCrowdloanRecovery> { coEvery { findEvidence(any(), any()) } throws IllegalStateException("offline") }
        val failed = LegacyCrowdloanViewModel(handle(), interactor, failing, router, resources)

        listOf(none, failed).forEach { viewModel ->
            val state = (viewModel.state.awaitFirst { it is LoadingState.Loaded } as LoadingState.Loaded).data
            assertFalse(state.hasEvidence)
            assertEquals("s${R.string.legacy_crowdloan_no_evidence}", state.description)
            assertTrue(state.evidenceRows.isEmpty())
            assertFalse(state.canOpenLockedBalance)
        }
    }

    private fun recovery(evidence: LegacyCrowdloanEvidence) = mockk<LegacyCrowdloanRecovery> {
        coEvery { findEvidence(polkadotChainId, "dot") } returns evidence
    }

    private fun handle() = SavedStateHandle(mapOf(LegacyCrowdloanFragment.KEY_ASSET_PAYLOAD to AssetPayload(polkadotChainId, "dot")))
}
