package jp.co.soramitsu.staking.impl.presentation.common

import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import java.math.BigInteger
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.staking.api.data.StakingAssetSelection
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import jp.co.soramitsu.core.models.Asset as ChainAsset

@OptIn(ExperimentalCoroutinesApi::class)
class StakingAssetSelectorIdentityTest {
    @get:Rule val instant = InstantTaskExecutorRule()

    private fun asset(chain: String, id: String, amount: Long): Asset {
        val config = mock<ChainAsset> {
            on { chainId } doReturn chain
            on { this.id } doReturn id
            on { symbol } doReturn chain.uppercase()
            on { chainName } doReturn "Network $chain"
            on { iconUrl } doReturn "public://$chain/$id"
            on { precision } doReturn 2
        }
        return Asset.createEmpty(config, 7, ByteArray(32) { 1 }, minSupportedVersion = null)
            .copy(freeInPlanksState = AssetAmountState.Known(BigInteger.valueOf(amount)))
    }

    @Test fun sharedAssetIdAlwaysUsesItsOwnNetworkDisplayAndBalance() = runTest {
        val a = asset("alpha", "native", 1234)
        val b = asset("beta", "native", 9876)
        val selections = listOf(
            StakingAssetSelection.RelayChainStaking("alpha", "native"),
            StakingAssetSelection.Pool("beta", "native")
        )
        for (rows in listOf(listOf(a, b), listOf(b, a))) {
            val shared = mock<StakingSharedState>()
            whenever(shared.selectionItem).thenReturn(flowOf(selections[1]))
            whenever(shared.settledSelectionOrNull()).thenReturn(selections[1])
            whenever(shared.currentAssetFlow()).thenReturn(flowOf(b))
            whenever(shared.availableToSelect()).thenReturn(selections)
            whenever(shared.availableAssetsToSelect()).thenReturn(rows)
            val selector = StakingAssetSelector(shared, backgroundScope)
            selector.assetSelectorClicked()
            runCurrent()
            val payload = requireNotNull(selector.showAssetChooser.value).peekContent()
            assertEquals(2, payload.data.size)
            for ((selection, expected) in selections.zip(listOf(a, b))) {
                val model = payload.data.single { it.selectionItem == selection }
                assertEquals(expected.token.configuration.iconUrl, model.imageUrl)
                assertEquals(expected.token.configuration.chainName, model.chainName)
                val balance = if (selection is StakingAssetSelection.Pool) expected.transferable else expected.availableForStaking
                assertEquals(requireNotNull(balance).formatCrypto(expected.token.configuration.symbol), model.assetBalance)
            }
            assertEquals(selections[1], payload.selected?.selectionItem)
            selector.assetChosen(requireNotNull(payload.selected))
            verify(shared).update(selections[1])
        }
    }

    @Test fun missingNetworkCannotBorrowAnotherNetworksSameAssetId() = runTest {
        val alpha = asset("alpha", "native", 1234)
        val selected = StakingAssetSelection.RelayChainStaking("alpha", "native")
        val foreign = StakingAssetSelection.Pool("beta", "native")
        val shared = mock<StakingSharedState>()
        whenever(shared.selectionItem).thenReturn(flowOf(selected))
        whenever(shared.settledSelectionOrNull()).thenReturn(selected)
        whenever(shared.currentAssetFlow()).thenReturn(flowOf(alpha))
        whenever(shared.availableToSelect()).thenReturn(listOf(foreign, selected))
        whenever(shared.availableAssetsToSelect()).thenReturn(listOf(alpha))
        val selector = StakingAssetSelector(shared, backgroundScope)
        selector.assetSelectorClicked()
        runCurrent()
        val payload = requireNotNull(selector.showAssetChooser.value).peekContent()
        assertEquals(listOf(selected), payload.data.map { it.selectionItem })
        assertEquals(selected, payload.selected?.selectionItem)
        verify(shared, never()).update(any())
    }

    @Test fun sameNetworkStillRequiresTheExactAssetIdentifier() = runTest {
        val wrong = asset("alpha", "other", 9999)
        val exact = asset("alpha", "native", 1234)
        val selected = StakingAssetSelection.RelayChainStaking("alpha", "native")
        val shared = mock<StakingSharedState>()
        whenever(shared.selectionItem).thenReturn(flowOf(selected))
        whenever(shared.settledSelectionOrNull()).thenReturn(selected)
        whenever(shared.currentAssetFlow()).thenReturn(flowOf(exact))
        whenever(shared.availableToSelect()).thenReturn(listOf(selected))
        whenever(shared.availableAssetsToSelect()).thenReturn(listOf(wrong, exact))
        val selector = StakingAssetSelector(shared, backgroundScope)
        selector.assetSelectorClicked()
        runCurrent()
        val model = requireNotNull(selector.showAssetChooser.value).peekContent().data.single()
        assertEquals(exact.token.configuration.iconUrl, model.imageUrl)
        assertEquals(requireNotNull(exact.availableForStaking).formatCrypto("ALPHA"), model.assetBalance)
    }
    @Test fun unknownBalanceRevokesBothSelectedAndChooserAmountsAndKnownZeroRecovers() = runTest {
        val known = asset("alpha", "native", 0)
        val unknown = known.copy(freeInPlanksState = AssetAmountState.Unavailable("invalid-cache-number"))
        val rows = MutableStateFlow(unknown)
        val selected = StakingAssetSelection.Pool("alpha", "native")
        val shared = mock<StakingSharedState>()
        whenever(shared.selectionItem).thenReturn(flowOf(selected))
        whenever(shared.currentAssetFlow()).thenReturn(rows)
        whenever(shared.availableToSelect()).thenReturn(listOf(selected))
        whenever(shared.availableAssetsToSelect()).thenReturn(listOf(unknown))
        val selector = StakingAssetSelector(shared, backgroundScope, "unavailable")
        assertEquals("unavailable", selector.selectedAssetModelFlow.first().assetBalance)
        selector.assetSelectorClicked()
        runCurrent()
        assertEquals("unavailable", requireNotNull(selector.showAssetChooser.value).peekContent().data.single().assetBalance)
        rows.value = known
        runCurrent()
        assertEquals(requireNotNull(known.transferable).formatCrypto("ALPHA"), selector.selectedAssetModelFlow.first().assetBalance)
    }

    @Test fun changedSelectionCannotPublishThePriorNetworksAmountOrIcon() = runTest {
        val selected = MutableStateFlow<StakingAssetSelection>(StakingAssetSelection.Pool("alpha", "native"))
        val shared = mock<StakingSharedState>()
        whenever(shared.selectionItem).thenReturn(selected)
        val priorNetworkAsset = asset("alpha", "native", 100)
        whenever(shared.currentAssetFlow()).thenReturn(flowOf(priorNetworkAsset))
        val selector = StakingAssetSelector(shared, backgroundScope, "unavailable")
        selector.selectedAssetModelFlow.first()
        selected.value = StakingAssetSelection.Pool("beta", "native")
        runCurrent()
        val current = selector.selectedAssetModelFlow.first()
        assertEquals(selected.value, current.selectionItem)
        assertEquals("unavailable", current.assetBalance)
        assertEquals("", current.imageUrl)
        assertEquals("", current.chainName)
    }

    @Test fun storedChoiceThatIsNoLongerOfferedStillOpensTheChooser() = runTest {
        val beta = asset("beta", "native", 500)
        val offered = StakingAssetSelection.RelayChainStaking("beta", "native")
        val shared = mock<StakingSharedState>()
        // The stored choice (for example Polkadot relay staking) left the catalog: no selection ever arrives.
        whenever(shared.selectionItem).thenReturn(emptyFlow())
        whenever(shared.currentAssetFlow()).thenReturn(emptyFlow())
        whenever(shared.settledSelectionOrNull()).thenReturn(null)
        whenever(shared.availableToSelect()).thenReturn(listOf(offered))
        whenever(shared.availableAssetsToSelect()).thenReturn(listOf(beta))
        val selector = StakingAssetSelector(shared, backgroundScope)
        selector.assetSelectorClicked()
        runCurrent()
        val payload = requireNotNull(selector.showAssetChooser.value).peekContent()
        assertEquals(listOf(offered), payload.data.map { it.selectionItem })
        assertNull(payload.selected)
        verify(shared, never()).update(any())
    }

}
