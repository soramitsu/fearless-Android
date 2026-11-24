package jp.co.soramitsu.wallet.impl.presentation.balance.assetActions.buy

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.domain.model.BuyTokenRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

/** The buy-provider chooser names the token, lists each provider and buys with the tapped one. */
@RunWith(RobolectricTestRunner::class)
class BuyProviderChooserBottomSheetRobolectricTest {

    private val activity: FragmentActivity = Robolectric.buildActivity(FragmentActivity::class.java).also {
        it.get().setTheme(jp.co.soramitsu.common.R.style.Theme_Soramitsu_Fearless)
    }.setup().get()

    private val ramp = FakeProvider("Ramp")
    private val moonpay = FakeProvider("Moonpay")
    private val chosen = mutableListOf<BuyProvider>()

    @Test
    fun titleNamesTheTokenInCapitals() {
        val sheet = show(WalletTestModels.asset(id = "dot", symbol = "dot"))

        assertEquals(
            activity.getString(R.string.wallet_asset_buy_with, "DOT"),
            sheet.findViewById<TextView>(jp.co.soramitsu.common.R.id.dynamicListSheetTitle)!!.text.toString()
        )
        assertTrue(sheet.isShowing)
    }

    @Test
    fun rowsNameTheProvidersAndATapBuysWithThatProvider() {
        val sheet = show(WalletTestModels.asset(id = "ksm"))

        val rows = rows(sheet)
        assertEquals(listOf("Ramp", "Moonpay"), rows.map { it.findViewById<TextView>(R.id.itemSheetBuyProviderText).text.toString() })
        rows[1].performClick()
        ShadowLooper.idleMainLooper()

        assertEquals(1, chosen.size)
        assertSame(moonpay, chosen.single())
        assertFalse(sheet.isShowing)
    }

    @Test
    fun choosingAProviderDirectlyAlsoClosesTheSheet() {
        val sheet = show(WalletTestModels.asset(id = "dot"))

        sheet.itemClicked(ramp)
        ShadowLooper.idleMainLooper()

        assertEquals(listOf<BuyProvider>(ramp), chosen)
        assertFalse(sheet.isShowing)
    }

    private fun show(asset: Asset): BuyProviderChooserBottomSheet {
        val sheet = BuyProviderChooserBottomSheet(activity, listOf(ramp, moonpay), asset, onClick = { chosen += it })
        sheet.show()
        ShadowLooper.idleMainLooper()
        return sheet
    }

    /** Lays the provider list out so its rows exist, then returns them in order. */
    private fun rows(sheet: BuyProviderChooserBottomSheet): List<View> {
        val list = sheet.findViewById<ViewGroup>(jp.co.soramitsu.common.R.id.dynamicListSheetContent)!!
        list.measure(
            View.MeasureSpec.makeMeasureSpec(LIST_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(LIST_MAX_HEIGHT, View.MeasureSpec.AT_MOST)
        )
        list.layout(0, 0, LIST_WIDTH, list.measuredHeight)
        return (0 until list.childCount).map { list.getChildAt(it) }
    }

    private class FakeProvider(override val name: String) : BuyTokenRegistry.Provider<BuyTokenRegistry.Integrator<Unit>> {
        override val icon: Int = jp.co.soramitsu.common.R.drawable.ic_info_14

        override fun createIntegrator(chainAsset: Asset, address: String): BuyTokenRegistry.Integrator<Unit> =
            error("Integration is not part of this test")
    }

    private companion object {
        const val LIST_WIDTH = 1080
        const val LIST_MAX_HEIGHT = 4000
    }
}
