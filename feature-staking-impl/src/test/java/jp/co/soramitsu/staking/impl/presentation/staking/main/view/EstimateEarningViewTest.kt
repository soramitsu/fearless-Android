package jp.co.soramitsu.staking.impl.presentation.staking.main.view

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import coil.ImageLoader
import coil.request.ImageRequest
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jp.co.soramitsu.common.view.AmountView
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.databinding.ViewStakingInfoBinding
import jp.co.soramitsu.staking.impl.presentation.StakingViewFixture
import jp.co.soramitsu.staking.impl.presentation.staking.main.model.RewardEstimation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EstimateEarningViewTest : StakingViewFixture() {

    private fun EstimateEarningView.amountView() = findViewById<AmountView>(R.id.estimateEarningAmount)

    private fun EstimateEarningView.gain(id: Int) = ViewStakingInfoBinding.bind(findViewById(id))

    @Test
    fun assetSettersFillTheAmountFieldAndExposeItsInput() {
        val view = EstimateEarningView(context)
        val icon = ColorDrawable(Color.RED)
        view.setAssetImage(icon)
        view.setAssetName("DOT")
        view.setAssetBalance("Balance: 100 DOT")
        view.setAssetBalanceFiatAmount("$200")
        view.amountInput.setText("10")

        val amount = view.amountView()
        assertSame(amount.amountInput, view.amountInput)
        assertEquals("10", amount.amountInput.text.toString())
        assertSame(icon, amount.findViewById<ImageView>(jp.co.soramitsu.common.R.id.stakingAssetImage).drawable)
        assertTrue(texts(view).containsAll(listOf("DOT", "Balance: 100 DOT", "$200")))
        assertSame(view.findViewById<View>(R.id.stakeMoreActions), view.infoActions)

        val fiat = amount.findViewById<TextView>(jp.co.soramitsu.common.R.id.stakingAssetFiatAmount)
        view.hideAssetBalanceFiatAmount()
        assertEquals(View.GONE, fiat.visibility)
        view.showAssetBalanceFiatAmount()
        assertEquals(View.VISIBLE, fiat.visibility)
        view.setAssetBalanceFiatAmount(null)
        assertEquals(View.GONE, fiat.visibility)
    }

    @Test
    fun assetImageLoadsFromAResourceOrThroughTheImageLoader() {
        val view = EstimateEarningView(context)
        val image = view.amountView().findViewById<ImageView>(jp.co.soramitsu.common.R.id.stakingAssetImage)
        view.setAssetImageResource(R.drawable.ic_arrow_back_24dp)
        assertTrue(image.drawable != null)

        val imageLoader = mockk<ImageLoader>(relaxed = true)
        val request = slot<ImageRequest>()
        view.setAssetImageUrl("https://example.invalid/dot.svg", imageLoader)

        verify { imageLoader.enqueue(capture(request)) }
        assertEquals("https://example.invalid/dot.svg", request.captured.data)
    }

    @Test
    fun estimationsReplaceLoadingAndShowFiatOnlyWhenKnown() {
        val view = EstimateEarningView(context)
        val month = view.gain(R.id.stakingMonthGain)
        val year = view.gain(R.id.stakingYearGain)

        view.showReturnsLoading()
        assertEquals(View.VISIBLE, month.stakingInfoBodyShimmer.visibility)
        assertEquals(View.VISIBLE, year.stakingInfoBodyShimmer.visibility)
        view.hideReturnsLoading()
        assertEquals(View.GONE, month.stakingInfoBodyShimmer.visibility)
        assertEquals(View.GONE, year.stakingInfoBodyShimmer.visibility)

        view.populateMonthEstimation(RewardEstimation("1 DOT", "$2", "+1.2%"))
        view.populateYearEstimation(RewardEstimation("12 DOT", null, "+14%"))

        assertEquals("1 DOT", month.stakingInfoBody.text.toString())
        assertEquals("$2", month.stakingInfoExtraBlockValue.text.toString())
        assertEquals(View.VISIBLE, month.stakingInfoExtraBlockValue.visibility)
        assertEquals("+1.2%", month.stakingInfoExtraBlockAdditional.text.toString())
        assertEquals("12 DOT", year.stakingInfoBody.text.toString())
        assertEquals(View.GONE, year.stakingInfoExtraBlockValue.visibility)
        assertEquals("+14%", year.stakingInfoExtraBlockAdditional.text.toString())
        assertEquals(View.VISIBLE, year.stakingInfoExtraTitleDetailView.visibility)
    }
}
