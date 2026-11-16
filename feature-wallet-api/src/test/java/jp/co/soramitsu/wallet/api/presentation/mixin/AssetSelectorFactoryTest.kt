package jp.co.soramitsu.wallet.api.presentation.mixin

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.wallet.api.domain.AssetUseCase
import jp.co.soramitsu.wallet.api.presentation.mixin.assetSelector.AssetSelectorFactory
import jp.co.soramitsu.wallet.api.presentation.model.AssetModel
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as CoreAsset

class AssetSelectorFactoryTest {
    @get:Rule val instantTasks = InstantTaskExecutorRule()

    private val resources = mockk<ResourceManager> { every { getString(any()) } returns "Balance unavailable" }
    private val polkadot = asset("polkadot", "dot", "Polkadot", "DOT")
    private val kusama = asset("kusama", "ksm", "Kusama", "KSM")
    private val useCase = mockk<AssetUseCase> {
        every { currentAssetFlow() } returns flowOf(kusama)
        coEvery { availableAssetsToSelect() } returns listOf(polkadot, kusama)
    }
    private val sharedState = mockk<SingleAssetSharedState>(relaxed = true)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val presentation = AssetSelectorFactory(useCase, sharedState, resources).create(scope)

    @After
    fun stopScope() {
        scope.cancel()
    }

    @Test
    fun selectedAssetIsSharedWithItsModel(): Unit = runBlocking {
        val model = withTimeout(5_000) { presentation.selectedAssetModelFlow.first() }

        assertSame(kusama, withTimeout(5_000) { presentation.selectedAssetFlow.first() })
        assertEquals("kusama", model.chainId)
        assertEquals("ksm", model.chainAssetId)
        assertEquals("Kusama", model.tokenName)
        assertEquals("icon-ksm", model.imageUrl)
        assertEquals("1 KSM", model.assetBalance)
    }

    @Test
    fun chooserListsEveryAssetAndHighlightsTheSelectedOne() {
        presentation.assetSelectorClicked()

        val payload = presentation.showAssetChooser.value!!.peekContent()
        assertEquals(listOf("dot", "ksm"), payload.data.map(AssetModel::chainAssetId))
        assertEquals("ksm", payload.selected?.chainAssetId)
    }

    @Test
    fun chooserFallsBackToTheFirstAssetWhenTheSelectedOneIsNotOffered() {
        coEvery { useCase.availableAssetsToSelect() } returns listOf(polkadot)

        presentation.assetSelectorClicked()

        assertEquals("dot", presentation.showAssetChooser.value!!.peekContent().selected?.chainAssetId)
    }

    @Test
    fun choosingAnAssetMakesItTheSharedSelection() {
        presentation.assetChosen(AssetModel("polkadot", "dot", "", "Polkadot", ""))

        verify { sharedState.update("polkadot", "dot") }
    }

    private fun asset(
        chainId: String,
        id: String,
        chainName: String,
        symbol: String
    ): Asset {
        val configuration = CoreAsset(
            id = id,
            name = chainName,
            symbol = symbol,
            iconUrl = "icon-$id",
            chainId = chainId,
            chainName = chainName,
            chainIcon = null,
            isTestNet = false,
            priceId = null,
            precision = 0,
            staking = CoreAsset.StakingType.UNSUPPORTED,
            purchaseProviders = null,
            supportStakingPool = false,
            isUtility = true,
            type = ChainAssetType.Normal,
            currencyId = null,
            existentialDeposit = null,
            color = null,
            isNative = true
        )
        return Asset(
            metaId = 1,
            token = Token(configuration = configuration, fiatRate = null, fiatSymbol = null, recentRateChange = null),
            accountId = byteArrayOf(1),
            freeInPlanks = BigInteger.ONE,
            reservedInPlanks = null,
            miscFrozenInPlanks = null,
            feeFrozenInPlanks = null,
            bondedInPlanks = null,
            redeemableInPlanks = null,
            unbondingInPlanks = null,
            sortIndex = 0,
            enabled = true,
            minSupportedVersion = null,
            chainAccountName = null,
            markedNotNeed = false,
            status = null
        )
    }
}
