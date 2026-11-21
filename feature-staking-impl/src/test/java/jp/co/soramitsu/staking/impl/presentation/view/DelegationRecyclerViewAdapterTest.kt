package jp.co.soramitsu.staking.impl.presentation.view

import android.view.View
import android.widget.FrameLayout
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.ViewStakeSummaryBinding
import jp.co.soramitsu.feature_staking_impl.databinding.ViewStakingInfoBinding
import jp.co.soramitsu.staking.impl.presentation.StakingViewFixture
import jp.co.soramitsu.staking.impl.presentation.staking.main.DelegatorViewState.CollatorDelegationModel
import jp.co.soramitsu.staking.impl.presentation.staking.main.DelegatorViewState.CollatorDelegationModel.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DelegationRecyclerViewAdapterTest : StakingViewFixture() {
    private val handler = mockk<DelegationRecyclerViewAdapter.DelegationHandler>(relaxed = true)
    private val collatorId = byteArrayOf(1, 2, 3)

    private fun model(address: String, status: Status, fiat: String?) = CollatorDelegationModel(
        collatorId = collatorId,
        collatorAddress = address,
        collatorName = "Collator $address",
        staked = "100 GLMR",
        stakedFiat = fiat,
        rewardApy = "12.5%",
        rewardedFiat = fiat,
        status = status,
        collator = mockk(relaxed = true)
    )

    private fun bindAll(items: List<CollatorDelegationModel>): List<ViewStakeSummaryBinding> {
        val adapter = DelegationRecyclerViewAdapter(handler)
        adapter.submitList(items)
        return items.indices.map { position ->
            val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
            adapter.onBindViewHolder(holder, position)
            ViewStakeSummaryBinding.bind(holder.itemView.findViewById(R.id.summaryView))
        }
    }

    @Test
    fun rowsShowTheDelegationAndOptionalFiatAndForwardMoreActions() {
        val withFiat = model("a", Status.Active(0), "$20")
        val withoutFiat = model("b", Status.Idle, null)
        val (first, second) = bindAll(listOf(withFiat, withoutFiat))

        assertTrue(texts(first.root).containsAll(listOf("Collator a", "100 GLMR", "12.5%", "$20")))
        assertEquals(View.VISIBLE, ViewStakingInfoBinding.bind(first.stakeTotalStakedView).stakingInfoExtraBlock.visibility)
        assertEquals(View.INVISIBLE, ViewStakingInfoBinding.bind(second.stakeTotalStakedView).stakingInfoExtraBlock.visibility)
        assertEquals(View.INVISIBLE, ViewStakingInfoBinding.bind(second.stakeRewardsAprView).stakingInfoExtraBlock.visibility)

        first.stakeMoreActions.performClick()
        second.stakeMoreActions.performClick()
        verify { handler.moreClicked(withFiat) }
        verify { handler.moreClicked(withoutFiat) }
    }

    @Test
    fun everyDelegationStatusMapsToItsCollatorLabel() {
        val expected = listOf(
            Status.Active(60_000) to R.string.staking_nominator_status_active,
            Status.Leaving(null) to R.string.staking_collator_status_leaving,
            Status.Leaving(60_000) to R.string.staking_collator_status_leaving,
            Status.Inactive to R.string.staking_collator_status_idle,
            Status.Idle to R.string.staking_collator_status_idle,
            Status.ReadyToUnlock to R.string.staking_delegation_status_ready_to_unlock
        )
        val rows = bindAll(expected.mapIndexed { index, (status, _) -> model("c$index", status, null) })

        rows.zip(expected).forEach { (row, case) ->
            assertEquals(context.getString(case.second), row.stakeSummaryStatus.text.toString())
        }
    }

    @Test
    fun diffMatchesByCollatorAddressAndComparesWholeModels() {
        val original = model("a", Status.Idle, "$20")

        assertTrue(DelegationDiffCallback.areItemsTheSame(original, original.copy(staked = "200 GLMR")))
        assertFalse(DelegationDiffCallback.areItemsTheSame(original, original.copy(collatorAddress = "b")))
        assertTrue(DelegationDiffCallback.areContentsTheSame(original, original.copy()))
        assertFalse(DelegationDiffCallback.areContentsTheSame(original, original.copy(staked = "200 GLMR")))
    }
}
