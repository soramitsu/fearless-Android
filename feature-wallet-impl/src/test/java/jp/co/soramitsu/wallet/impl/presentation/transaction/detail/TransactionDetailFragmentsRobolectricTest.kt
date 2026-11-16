package jp.co.soramitsu.wallet.impl.presentation.transaction.detail

import android.graphics.Picture
import android.graphics.drawable.PictureDrawable
import android.view.View
import android.widget.TextView
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.account.api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.account.api.presentation.actions.ExternalActionsSheet
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.HiltFragmentHost
import jp.co.soramitsu.wallet.impl.presentation.StubHiltActivity
import jp.co.soramitsu.wallet.impl.presentation.StubHiltApplication
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.model.OperationParcelizeModel
import jp.co.soramitsu.wallet.impl.presentation.model.OperationStatusAppearance
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.extrinsic.ExtrinsicDetailFragment
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.extrinsic.ExtrinsicDetailViewModel
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.extrinsic.ExtrinsicDetailsPayload
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.reward.RewardDetailFragment
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.reward.RewardDetailViewModel
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.reward.RewardDetailsPayload
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.transfer.KEY_ASSET_PAYLOAD
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.transfer.KEY_EXPLORER_TYPE
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.transfer.KEY_TRANSACTION
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.transfer.TransactionDetailViewModel
import jp.co.soramitsu.wallet.impl.presentation.transaction.detail.transfer.TransferDetailFragment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

/** The transfer, reward and extrinsic detail screens render their operation and open explorer actions. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = StubHiltApplication::class)
class TransactionDetailFragmentsRobolectricTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val router = mockk<WalletRouter>(relaxed = true)
    private val clipboard = mockk<ClipboardManager>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true) {
        every { getString(any()) } returns "Copied"
        every { getString(any(), *anyVararg()) } returns "Era #12"
    }
    private val icons = mockk<AddressIconGenerator> {
        coEvery { createAddressIcon(any(), any(), any()) } returns PictureDrawable(Picture())
    }
    private val explorers = listOf(
        Chain.Explorer(Chain.Explorer.Type.SUBSCAN, listOf("extrinsic", "account", "transfer", "event"), "https://subscan/{type}/{value}")
    )
    private val registry = mockk<ChainRegistry> {
        coEvery { getChain(any()) } returns WalletTestModels.chain(explorers = explorers)
    }
    private val payload = AssetPayload("polkadot", "dot")
    private var host: ActivityController<StubHiltActivity>? = null

    @After
    fun closeHost() {
        HiltFragmentHost.close(host)
    }

    @Test
    fun outgoingTransferShowsItsFeeAndOpensHashAndAddressActions() {
        val viewModel = transferViewModel(isIncome = false, hash = "0xhash", names = "Contact")
        val fragment = TransferDetailFragment()
        host = HiltFragmentHost.host(fragment, mapOf(TransactionDetailViewModel::class.java to viewModel))
        val view = fragment.requireView()

        assertEquals("1 DOT", view.text(R.id.transactionDetailAmount))
        assertEquals("0.01 DOT", view.text(R.id.transactionDetailFee))
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.transactionDetailFee).visibility)
        assertEquals("0xhash", view.labeled(R.id.transactionDetailHash))
        assertEquals("Contact", view.labeled(R.id.transactionDetailFrom))
        assertEquals("Contact", view.labeled(R.id.transactionDetailTo))

        view.findViewById<View>(R.id.transactionDetailHash).performClick()
        assertExternalSheetShown()
        view.findViewById<View>(R.id.transactionDetailFrom).performClick()
        assertExternalSheetShown()
        view.findViewById<View>(R.id.transactionDetailTo).performClick()
        assertExternalSheetShown()
        view.findViewById<View>(R.id.transactionDetailRepeat).performClick()
        verify { router.openSend(payload, WalletTestModels.ALICE, any(), any()) }
        view.findViewById<View>(R.id.transactionDetailToolbar).findViewById<View>(jp.co.soramitsu.common.R.id.backImg).performClick()
        verify { router.back() }
    }

    @Test
    fun incomingTransferWithoutHashHidesTheFeeAndHash() {
        val viewModel = transferViewModel(isIncome = true, hash = null, names = null)
        val fragment = TransferDetailFragment()
        host = HiltFragmentHost.host(fragment, mapOf(TransactionDetailViewModel::class.java to viewModel))
        val view = fragment.requireView()

        assertEquals(View.GONE, view.findViewById<View>(R.id.transactionDetailFee).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.transactionDetailFeeLabel).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.transactionDetailHash).visibility)
        assertEquals(WalletTestModels.BOB, view.labeled(R.id.transactionDetailFrom))
        assertEquals(
            WalletTestModels.BOB,
            view.findViewById<View>(R.id.transactionDetailRepeat).findViewById<TextView>(jp.co.soramitsu.common.R.id.accountTitle).text.toString()
        )
    }

    @Test
    fun rewardShowsValidatorEraAndExplorerActions() {
        val operation = OperationParcelizeModel.Reward("1-2", WalletTestModels.ALICE, 1L, "+2 KSM", true, 12, WalletTestModels.BOB)
        val viewModel = RewardDetailViewModel(
            clipboard, resources, icons, names(null), router, registry,
            SavedStateHandle(mapOf(RewardDetailFragment.KEY_PAYLOAD to RewardDetailsPayload(operation, "kusama")))
        )
        val fragment = RewardDetailFragment()
        host = HiltFragmentHost.host(fragment, mapOf(RewardDetailViewModel::class.java to viewModel))
        val view = fragment.requireView()

        assertEquals("+2 KSM", view.text(R.id.rewardDetailReward))
        assertEquals("1-2", view.labeled(R.id.rewardDetailHash))
        assertEquals(WalletTestModels.BOB, view.labeled(R.id.rewardDetailValidator))
        assertEquals("Era #12", view.text(R.id.rewardDetailEra))
        assertEquals(fragment.getString(R.string.staking_reward), view.text(R.id.rewardDetailRewardLabel))

        view.findViewById<View>(R.id.rewardDetailHash).performClick()
        assertExternalSheetShown()
        view.findViewById<View>(R.id.rewardDetailValidator).performClick()
        assertExternalSheetShown()
    }

    @Test
    fun slashWithoutValidatorHidesTheValidatorRow() {
        val operation = OperationParcelizeModel.Reward("3-4", WalletTestModels.ALICE, 1L, "-1 KSM", false, 12, null)
        val viewModel = RewardDetailViewModel(
            clipboard, resources, icons, names(null), router, registry,
            SavedStateHandle(mapOf(RewardDetailFragment.KEY_PAYLOAD to RewardDetailsPayload(operation, "kusama")))
        )
        val fragment = RewardDetailFragment()
        host = HiltFragmentHost.host(fragment, mapOf(RewardDetailViewModel::class.java to viewModel))
        val view = fragment.requireView()

        assertEquals(fragment.getString(R.string.staking_slash), view.text(R.id.rewardDetailRewardLabel))
        assertEquals(View.GONE, view.findViewById<View>(R.id.rewardDetailValidator).visibility)
    }

    @Test
    fun extrinsicShowsItsCallAndOpensExplorerActions() {
        val operation = OperationParcelizeModel.Extrinsic(
            1L, WalletTestModels.ALICE, "0xextrinsic", "staking", "bond", "0.02 DOT", OperationStatusAppearance.FAILED
        )
        val viewModel = ExtrinsicDetailViewModel(
            clipboard, resources, names(null), icons, router, registry,
            SavedStateHandle(mapOf(ExtrinsicDetailFragment.PAYLOAD_KEY to ExtrinsicDetailsPayload(operation, "polkadot")))
        )
        val fragment = ExtrinsicDetailFragment()
        host = HiltFragmentHost.host(fragment, mapOf(ExtrinsicDetailViewModel::class.java to viewModel))
        val view = fragment.requireView()

        assertEquals("staking", view.text(R.id.extrinsicDetailModule))
        assertEquals("bond", view.text(R.id.extrinsicDetailCall))
        assertEquals("0.02 DOT", view.text(R.id.extrinsicDetailFee))
        assertEquals("0xextrinsic", view.labeled(R.id.extrinsicDetailHash))
        assertEquals(WalletTestModels.ALICE, view.labeled(R.id.extrinsicDetailFrom))

        view.findViewById<View>(R.id.extrinsicDetailHash).performClick()
        assertExternalSheetShown()
        view.findViewById<View>(R.id.extrinsicDetailFrom).performClick()
        assertExternalSheetShown()
        view.findViewById<View>(R.id.extrinsicDetailToolbar).findViewById<View>(jp.co.soramitsu.common.R.id.backImg).performClick()
        verify { router.back() }
    }

    private fun transferViewModel(isIncome: Boolean, hash: String?, names: String?): TransactionDetailViewModel {
        val transfer = OperationParcelizeModel.Transfer(
            time = 1L, address = WalletTestModels.ALICE, hash = hash, isIncome = isIncome, amount = "1 DOT",
            receiver = WalletTestModels.ALICE, sender = WalletTestModels.BOB, fee = "0.01 DOT",
            statusAppearance = OperationStatusAppearance.COMPLETED
        )
        return TransactionDetailViewModel(
            mockk<WalletInteractor>(relaxed = true), router, resources, icons, clipboard, names(names), registry,
            SavedStateHandle(mapOf(KEY_TRANSACTION to transfer, KEY_ASSET_PAYLOAD to payload, KEY_EXPLORER_TYPE to Chain.Explorer.Type.SUBSCAN))
        )
    }

    private fun names(name: String?) = mockk<AddressDisplayUseCase> { coEvery { this@mockk.invoke(any()) } returns name }

    private fun assertExternalSheetShown() {
        ShadowLooper.idleMainLooper()
        val dialog = ShadowDialog.getLatestDialog()
        assertTrue(dialog is ExternalActionsSheet)
        assertTrue(dialog.isShowing)
        dialog.dismiss()
        ShadowLooper.idleMainLooper()
    }

    private fun View.text(id: Int): String = findViewById<TextView>(id).text.toString()

    private fun View.labeled(id: Int): String =
        findViewById<View>(id).findViewById<TextView>(jp.co.soramitsu.common.R.id.labeledTextText).text.toString()
}
