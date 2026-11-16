package jp.co.soramitsu.wallet.impl.presentation.transaction.detail.reward

import android.graphics.Picture
import android.graphics.drawable.PictureDrawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.data.network.BlockExplorerUrlBuilder
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.awaitValue
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.model.OperationParcelizeModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RewardDetailViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val router = mockk<WalletRouter>(relaxed = true)
    private val clipboard = mockk<ClipboardManager>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true) {
        every { getString(R.string.staking_era_index_no_prefix, 12) } returns "Era #12"
        every { getString(any()) } returns "Copied"
    }
    private val icons = mockk<AddressIconGenerator> { coEvery { createAddressIcon(any(), any(), any()) } returns PictureDrawable(Picture()) }
    private val names = mockk<AddressDisplayUseCase> { coEvery { this@mockk.invoke(any()) } returns null }
    private val explorers = listOf(Chain.Explorer(Chain.Explorer.Type.SUBSCAN, listOf("event", "account"), "https://subscan/{type}/{value}"))
    private val registry = mockk<ChainRegistry> { coEvery { getChain("kusama") } returns WalletTestModels.chain(id = "kusama", explorers = explorers) }

    @Test
    fun rewardWithAValidatorShowsItsIconAndEra() {
        val viewModel = viewModel(validator = WalletTestModels.BOB)

        assertEquals(WalletTestModels.BOB, viewModel.validatorAddressModelLiveData.awaitValue()!!.address)
        assertEquals("Era #12", viewModel.eraLiveData.awaitValue())
        assertEquals(1, viewModel.getSupportedExplorers(BlockExplorerUrlBuilder.Type.EVENT, "1-2").size)
        assertEquals(1, viewModel.getSupportedAddressExplorers(WalletTestModels.BOB).size)

        viewModel.copyStringClicked("1-2")
        viewModel.showExternalActionsClicked(ExternalActionsSource.VALIDATOR_ADDRESS)
        viewModel.openUrl("https://subscan")
        viewModel.backClicked()

        verify { clipboard.addToClipboard("1-2") }
        assertEquals(ExternalActionsSource.VALIDATOR_ADDRESS, viewModel.showExternalRewardActionsEvent.value!!.peekContent())
        assertEquals("https://subscan", viewModel.openBrowserEvent.value!!.peekContent())
        verify { router.back() }
    }

    @Test
    fun rewardWithoutValidatorHasNoIcon() {
        val viewModel = viewModel(validator = null)

        assertNull(viewModel.validatorAddressModelLiveData.awaitValue(timeoutMs = 300))
    }

    private fun viewModel(validator: String?) = RewardDetailViewModel(
        clipboard, resources, icons, names, router, registry,
        SavedStateHandle(
            mapOf(
                RewardDetailFragment.KEY_PAYLOAD to RewardDetailsPayload(
                    OperationParcelizeModel.Reward("1-2", WalletTestModels.ALICE, 1L, "1 KSM", true, 12, validator), "kusama"
                )
            )
        )
    )
}
